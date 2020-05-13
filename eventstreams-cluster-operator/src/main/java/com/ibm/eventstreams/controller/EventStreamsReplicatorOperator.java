/*
 * IBM Confidential
 * OCO Source Materials
 *
 * 5737-H33
 *
 * (C) Copyright IBM Corp. 2019  All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 */
package com.ibm.eventstreams.controller;

import com.ibm.eventstreams.api.model.ReplicatorModel;
import com.ibm.eventstreams.api.model.ReplicatorDestinationUsersModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsReplicator;
import com.ibm.eventstreams.api.spec.EventStreamsReplicatorBuilder;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.status.EventStreamsReplicatorStatusBuilder;
import com.ibm.eventstreams.api.status.EventStreamsReplicatorStatus;
import com.ibm.eventstreams.replicator.ReplicatorCredentials;
import com.ibm.eventstreams.rest.VersionValidation;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.strimzi.api.kafka.model.status.ConditionBuilder;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerTls;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.strimzi.operator.cluster.model.ModelUtils;
import io.strimzi.operator.common.AbstractOperator;
import io.strimzi.operator.common.MetricsProvider;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.operator.resource.NetworkPolicyOperator;
import io.strimzi.operator.common.operator.resource.RouteOperator;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;


public class EventStreamsReplicatorOperator extends AbstractOperator<EventStreamsReplicator, EventStreamsReplicatorResourceOperator> {

    private static final Logger log = LogManager.getLogger(EventStreamsReplicatorOperator.class.getName());

    private final KubernetesClient client;
    private final EventStreamsReplicatorResourceOperator replicatorResourceOperator;
    private final KafkaMirrorMaker2Operator kafkaMirrorMaker2Operator;
    private final NetworkPolicyOperator networkPolicyOperator;
    private final SecretOperator secretOperator;
    private final EventStreamsResourceOperator resourceOperator;
    private final KafkaUserOperator kafkaUserOperator;
    private final MetricsProvider metricsProvider;

    private PlatformFeaturesAvailability pfa;

    private static final String CLUSTER_CA_CERT_SECRET_NAME = "cluster-ca-cert";


    public EventStreamsReplicatorOperator(Vertx vertx, KubernetesClient client, String kind, PlatformFeaturesAvailability pfa,
                                          EventStreamsReplicatorResourceOperator replicatorResourceOperator,
                                          EventStreamsResourceOperator resourceOperator,
                                          RouteOperator routeOperator,
                                          MetricsProvider metricsProvider,
                                          long kafkaStatusReadyTimeoutMs) {
        super(vertx, kind, replicatorResourceOperator, metricsProvider);

        this.replicatorResourceOperator = replicatorResourceOperator;
        this.resourceOperator = resourceOperator;
        this.kafkaMirrorMaker2Operator = new KafkaMirrorMaker2Operator(vertx, client);
        this.networkPolicyOperator = new NetworkPolicyOperator(vertx, client);
        this.secretOperator = new SecretOperator(vertx, client);
        this.kafkaUserOperator = new KafkaUserOperator(vertx, client);
        this.metricsProvider = metricsProvider;
        this.client = client;
        this.pfa = pfa;
    }

    @Override
    protected Future<Void> createOrUpdate(Reconciliation reconciliation, EventStreamsReplicator replicatorInstance) {
        log.debug("createOrUpdate reconciliation {} for instance {}", reconciliation, replicatorInstance);
        EventStreamsReplicatorOperator.ReconciliationState reconcileState = new EventStreamsReplicatorOperator.ReconciliationState(replicatorInstance);

        return reconcile(reconcileState);
    }

    private Future<Void> reconcile(EventStreamsReplicatorOperator.ReconciliationState reconcileState) {

        return reconcileState.validateCustomResource()
                .compose(state -> {
                    return state.createReplicator();
                })
                .onSuccess(state -> {
                    state.finalStatusUpdate();
                    log.debug("GeoReplication reconciliation success");
                })
                .onFailure(thr -> {
                    reconcileState.recordFailure(thr);
                    log.error("GeoReplication reconciliation failed with error {} ", thr.getMessage());
                }).map(res -> null);


    }

    @Override
    protected Future<Boolean> delete(Reconciliation reconciliation) {
        return Future.succeededFuture(Boolean.FALSE);
    }

    class ReconciliationState {
        final EventStreamsReplicator replicatorInstance;
        final String namespace;
        final EventStreamsReplicatorStatusBuilder status;
        final List<Condition> previousConditions;


        ReconciliationState(EventStreamsReplicator replicatorInstance) {
            this.replicatorInstance = replicatorInstance;
            this.namespace = replicatorInstance.getMetadata().getNamespace();
            this.previousConditions = Optional.ofNullable(replicatorInstance.getStatus()).map(EventStreamsReplicatorStatus::getConditions).orElse(new ArrayList<>());
            this.status = replicatorInstance.getStatus() == null ? new EventStreamsReplicatorStatusBuilder()
                    .withConditions()
                    .withNewVersions()
                    .endVersions()
                    : new EventStreamsReplicatorStatusBuilder(replicatorInstance.getStatus()).withConditions(new ArrayList<>());

        }

        Future<EventStreamsReplicatorOperator.ReconciliationState> validateCustomResource() {

            String phase = Optional.ofNullable(status.getPhase()).orElse("Pending");

            boolean isValidCR = true;

            if (VersionValidation.shouldReject(replicatorInstance)) {
                addNotReadyCondition("InvalidVersion", "Invalid custom resource: Unsupported version. Supported versions are " + VersionValidation.VALID_APP_VERSIONS.toString());
                isValidCR = false;
            }

            //the name of the replicator instance doesn't match what has been set in the strimzi cluster label
            if (!replicatorInstance.getMetadata().getName().equals(replicatorInstance.getMetadata().getLabels().get(Labels.STRIMZI_CLUSTER_LABEL))) {
                addNotReadyCondition("NotMatchedEventStreamsInstanceName", "The name of the eventstreamsgeoreplicator instance does not match the value of " + Labels.STRIMZI_CLUSTER_LABEL);
                isValidCR = false;
            }

            if (isValidCR) {
                addCondition(previousConditions
                        .stream()
                        // restore any previous readiness condition if this was set, so
                        // that timestamps remain consistent
                        .filter(c -> "Ready".equals(c.getType()) || "Creating".equals(c.getType()))
                        .findFirst()
                        // otherwise set a new condition saying that the reconcile loop is running
                        .orElse(new ConditionBuilder()
                                .withLastTransitionTime(ModelUtils.formatTimestamp(dateSupplier()))
                                .withType("NotReady")
                                .withStatus("True")
                                .withReason("Creating")
                                .withMessage("Event Streams is being deployed")
                                .build()));
            } else {
                phase = "Failed";
            }


            EventStreamsReplicatorStatus statusSubresource = status.withPhase(phase).build();
            replicatorInstance.setStatus(statusSubresource);

            // Update if we need to notify the user of an error, otherwise
            //  on the first run only, otherwise the user will see status
            //  warning conditions flicker in and out of the list
            if (!isValidCR || previousConditions.isEmpty()) {
                updateStatus(statusSubresource);
            }

            if (isValidCR) {
                return Future.succeededFuture(this);
            } else {
                // we don't want the reconcile loop to continue any further if the CR is not valid
                return Future.failedFuture("Invalid Event Streams geo-replicator specification: further details in the status conditions");
            }
        }


        Future<ReconciliationState> createReplicator() {

            String eventStreamsInstanceName = replicatorInstance.getMetadata().getLabels().get(Labels.STRIMZI_CLUSTER_LABEL);

            log.debug("Create replicator called {} ", eventStreamsInstanceName);

            return resourceOperator.getAsync(namespace, eventStreamsInstanceName)
                    .compose(instance -> {
                        if (instance != null) {
                            if (!adminAPIInstanceFound(instance)) {
                                addNotReadyCondition("DependencyMissing", "AdminApi is required for Event Streams instance "
                                        + replicatorInstance.getMetadata().getLabels().get(Labels.STRIMZI_CLUSTER_LABEL)
                                        + " to enable replicator");

                                EventStreamsReplicatorStatus statusSubresource = status.withPhase("Failed").build();
                                replicatorInstance.setStatus(statusSubresource);

                                Promise<EventStreamsReplicatorOperator.ReconciliationState> failReconcile = Promise.promise();
                                updateStatus(statusSubresource).onComplete(f -> {
                                    log.info("AdminApi not present : " + f.succeeded());
                                    failReconcile.fail("Exit Reconcile as AdminApi not present");
                                });
                                return failReconcile.future();

                            }
                            if (!ReplicatorDestinationUsersModel.isValidInstance(instance)) {

                                addNotReadyCondition("UnsupportedAuthorization", "  Listener client authentication " +
                                        "unsupported for GeoReplication. Supported versions are TLS and SCRAM");

                                EventStreamsReplicatorStatus statusSubresource = status.withPhase("Failed").build();
                                replicatorInstance.setStatus(statusSubresource);

                                Promise<EventStreamsReplicatorOperator.ReconciliationState> failReconcile = Promise.promise();
                                updateStatus(statusSubresource).onComplete(f -> {
                                    log.info("UnsupportedAuthorization : " + f.succeeded());
                                    failReconcile.fail("Exit Reconcile as listener client authentication unsupported for GeoReplication");
                                });
                                return failReconcile.future();

                            }
                            //if internal auth is on then external must be too.  Vice versa is ok
                            if (!ReplicatorDestinationUsersModel.isValidInternExternalConfig(instance)) {
                                addNotReadyCondition("UnsupoprtedInternalExternalListenerConfig", "If internal listener client authentication " +
                                        "enabled, then external listner client authentication must also be enabled");

                                EventStreamsReplicatorStatus statusSubresource = status.withPhase("Failed").build();
                                replicatorInstance.setStatus(statusSubresource);

                                Promise<EventStreamsReplicatorOperator.ReconciliationState> failReconcile = Promise.promise();
                                updateStatus(statusSubresource).onComplete(f -> {
                                    log.info("UnsupportedAuthorization : " + f.succeeded());
                                    failReconcile.fail("Exit Reconcile as listener client authentication unsupported for GeoReplication");
                                });
                                return failReconcile.future();
                            }


                            ReplicatorCredentials replicatorCredentials = new ReplicatorCredentials(instance);
                            ReplicatorDestinationUsersModel replicatorUsersModel = new ReplicatorDestinationUsersModel(replicatorInstance, instance);
                            return kafkaUserOperator.reconcile(namespace, replicatorUsersModel.getConnectKafkaUserName(), replicatorUsersModel.getConnectKafkaUser())
                                    .compose(state -> kafkaUserOperator.reconcile(namespace, replicatorUsersModel.getTargetConnectorKafkaUserName(), replicatorUsersModel.getTargetConnectorKafkaUser()))
                                    .compose(state -> kafkaUserOperator.reconcile(namespace, replicatorUsersModel.getConnectExternalKafkaUserName(), replicatorUsersModel.getConnectExternalKafkaUser()))
                                    .compose(state -> setTrustStoreForReplicator(replicatorCredentials, instance))
                                    .compose(state -> setClientAuthForReplicator(replicatorCredentials, replicatorInstance, instance))
                                    .compose(state -> kafkaMirrorMaker2Operator.getAsync(namespace, instance.getMetadata().getName()))
                                    //Can't make the ReplicatorModel until after setTrustStoreForReplicator and setClientAuthForReplicator have completed
                                    .compose(mirrorMaker2 -> {
                                        ReplicatorModel replicatorModel = new ReplicatorModel(replicatorInstance, instance, replicatorCredentials, mirrorMaker2);
                                        List<Future> replicatorFutures = new ArrayList<>();
                                        replicatorFutures.add(networkPolicyOperator.reconcile(namespace, replicatorModel.getDefaultResourceName(), replicatorModel.getNetworkPolicy()));
                                        replicatorFutures.add(kafkaMirrorMaker2Operator.reconcile(namespace, replicatorModel.getReplicatorName(), replicatorModel.getReplicator()));
                                        return CompositeFuture.join(replicatorFutures);
                                    })
                                    .map(cf -> this);


                        } else {
                            addNotReadyCondition("EventStreamsInstanceNotFound", "Can't find Event Streams instance "
                                    + eventStreamsInstanceName + " in namespace " + namespace + ". Ensure the Event Streams instance is created before deploying the Event Streams geo-replicator ");

                            EventStreamsReplicatorStatus statusSubresource = status.withPhase("Failed").build();
                            replicatorInstance.setStatus(statusSubresource);

                            Promise<EventStreamsReplicatorOperator.ReconciliationState> failReconcile = Promise.promise();
                            updateStatus(statusSubresource).onComplete(f -> {
                                log.info("Can't find Event Streams instance : " + f.succeeded());
                                failReconcile.fail("Exit Reconcile as can't find Event Streams instance for GeoReplication");
                            });
                            return failReconcile.future();

                        }
                    });

        }

        Future<EventStreamsReplicatorOperator.ReconciliationState> updateStatus(EventStreamsReplicatorStatus newStatus) {
            Promise<EventStreamsReplicatorOperator.ReconciliationState> updateStatusPromise = Promise.promise();

            replicatorResourceOperator.getAsync(namespace, replicatorInstance.getMetadata().getName()).setHandler(getRes -> {
                if (getRes.succeeded()) {
                    EventStreamsReplicator current = getRes.result();
                    if (current != null) {
                        EventStreamsReplicator updatedStatus = new EventStreamsReplicatorBuilder(current).withStatus(newStatus).build();
                        replicatorResourceOperator.updateEventStreamsReplicatorStatus(updatedStatus)
                                .setHandler(updateRes -> {
                                    if (updateRes.succeeded()) {
                                        updateStatusPromise.complete(this);
                                    } else {
                                        log.error("Failed to update status", updateRes.cause());
                                        updateStatusPromise.fail(updateRes.cause());
                                    }
                                });
                    } else {
                        log.error("Event Streams geo-replicator resource not found");
                        updateStatusPromise.fail("Event Streams resource not found");
                    }
                } else {
                    log.error("Event Streams geo-replicator resource not found", getRes.cause());
                    updateStatusPromise.fail(getRes.cause());
                }
            });

            return updateStatusPromise.future();
        }

        /**
         * Adds a "Ready" condition to the status if there is not already one.
         * This does nothing if there is already an existing ready condition from
         * a previous reconcile.
         */
        private void addReadyCondition() {
            boolean needsReadyCondition = status.getMatchingCondition(cond -> "Ready".equals(cond.getType())) == null;
            if (needsReadyCondition) {
                addCondition(new ConditionBuilder()
                        .withLastTransitionTime(ModelUtils.formatTimestamp(dateSupplier()))
                        .withType("Ready")
                        .withStatus("True")
                        .build());
            }
        }

        /**
         * Adds a "NotReady" condition to the status that documents a reason why the
         * Event Streams geo-replicator operand is not ready yet.
         *
         * @param reason  A unique, one-word, CamelCase reason for why the operand is not ready.
         * @param message A human-readable message indicating why the operand is not ready.
         */
        private void addNotReadyCondition(String reason, String message) {
            addToConditions(
                    new ConditionBuilder()
                            .withLastTransitionTime(ModelUtils.formatTimestamp(dateSupplier()))
                            .withType("NotReady")
                            .withStatus("True")
                            .withReason(reason)
                            .withMessage(message)
                            .build());
        }

        /**
         * Adds the provided condition to the current status.
         * <p>
         * This will check to see if a matching condition was previously seen, and if so
         * that will be reused (allowing for timestamps to remain consistent). If not,
         * the provided condition will be added as-is.
         *
         * @param condition Condition to add to the status conditions list.
         */
        private void addToConditions(Condition condition) {
            // restore the equivalent previous condition if found, otherwise
            //  add the new condition to the status
            addCondition(previousConditions
                    .stream()
                    .filter(c -> condition.getReason().equals(c.getReason()))
                    .findFirst()
                    .orElse(condition));
        }

        /**
         * Adds a condition to the status, and then sorts the list to ensure
         * that they are maintained in order of timestamp.
         */
        private void addCondition(Condition condition) {
            status.addToConditions(condition);
            status.getConditions().sort(Comparator.comparing(Condition::getLastTransitionTime)
                    .thenComparing(Condition::getType, Comparator.reverseOrder()));
        }

        Date dateSupplier() {
            return new Date();
        }

        void recordFailure(Throwable thr) {
            log.error("Recording reconcile failure", thr);
            addNotReadyCondition("DeploymentFailed", thr.getMessage());

            EventStreamsReplicatorStatus statusSubresource = status.withPhase("Failed").build();
            replicatorInstance.setStatus(statusSubresource);
            updateStatus(statusSubresource);
        }

        Future<EventStreamsReplicatorOperator.ReconciliationState> finalStatusUpdate() {
            status.withPhase("Running");
            addReadyCondition();

            log.info("Updating status");
            EventStreamsReplicatorStatus esStatus = status.build();
            replicatorInstance.setStatus(esStatus);
            return updateStatus(esStatus);
        }


        private boolean adminAPIInstanceFound(EventStreams instance) {

            if (!Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getAdminApi).isPresent()) {
                return false;
            }
            if (Optional.ofNullable(instance.getSpec().getAdminApi().getReplicas()).isPresent() && instance.getSpec().getAdminApi().getReplicas() < 1) {
                return false;
            }
            return true;
        }

        private Future<Void> setClientAuthForReplicator(ReplicatorCredentials replicatorCredentials, EventStreamsReplicator replicatorInstance, EventStreams instance) {

            Promise<Void> setAuthSet = Promise.promise();

            String connectKafkaUserName = ReplicatorDestinationUsersModel.getConnectKafkaUserName(replicatorInstance.getMetadata().getName());

            Optional<KafkaListenerAuthentication> internalClientAuth =
                    Optional.ofNullable(instance.getSpec())
                            .map(EventStreamsSpec::getStrimziOverrides)
                            .map(KafkaSpec::getKafka)
                            .map(KafkaClusterSpec::getListeners)
                            .map(KafkaListeners::getTls)
                            .map(KafkaListenerTls::getAuth);

            //need to null check on getTls first
            if (internalClientAuth.isPresent()) {

                // get the secret created by the KafkaUser
                secretOperator.getAsync(instance.getMetadata().getNamespace(), connectKafkaUserName).setHandler(getRes -> {
                    if (getRes.succeeded()) {
                        if (getRes.result() != null) {
                            replicatorCredentials.setReplicatorClientAuth(getRes.result());
                            setAuthSet.complete();
                        } else {
                            log.info("Replicator Connect User secret " + connectKafkaUserName + " doesn't exist");
                            setAuthSet.fail("Replicator Connect User secret " + connectKafkaUserName + " doesn't exist");
                        }
                    } else {
                        log.error("Failed to query for the Replicator Connect User Secret" + connectKafkaUserName + " " + getRes.cause().toString());
                        setAuthSet.fail("Failed to query for the Replicator Connect User Secret" + connectKafkaUserName + " " + getRes.cause().toString());
                    }
                });
            } else {
                setAuthSet.complete();
            }
            return setAuthSet.future();
        }

        private Future<Void> setTrustStoreForReplicator(ReplicatorCredentials replicatorCredentials, EventStreams instance) {

            Promise<Void> setAuthSet = Promise.promise();

            Optional<KafkaListenerTls> internalServerAuth =
                    Optional.ofNullable(instance.getSpec())
                            .map(EventStreamsSpec::getStrimziOverrides)
                            .map(KafkaSpec::getKafka)
                            .map(KafkaClusterSpec::getListeners)
                            .map(KafkaListeners::getTls);

            if (internalServerAuth.isPresent()) {
                //get the truststore from the cluster
                String resourceNameCA = instance.getMetadata().getName() + "-" + CLUSTER_CA_CERT_SECRET_NAME;
                secretOperator.getAsync(instance.getMetadata().getNamespace(), resourceNameCA).setHandler(getRes -> {

                    if (getRes.succeeded()) {
                        if (getRes.result() != null) {
                            replicatorCredentials.setReplicatorTrustStore(getRes.result());
                            setAuthSet.complete();
                        } else {
                            log.info("Setting up Replicator TrustStore - CA cert " + resourceNameCA + " does not exist");
                            setAuthSet.fail("Setting up Replicator TrustStore - CA cert " + resourceNameCA + " does not exist");
                        }
                    } else {
                        log.error("Failed to query for the  Replicator TrustStore - CA cert " + resourceNameCA, getRes.cause());
                        setAuthSet.fail("Failed to query for the  Replicator TrustStore - CA cert " + resourceNameCA);
                    }
                });
            } else {
                setAuthSet.complete();
            }
            return setAuthSet.future();
        }


    }
}
