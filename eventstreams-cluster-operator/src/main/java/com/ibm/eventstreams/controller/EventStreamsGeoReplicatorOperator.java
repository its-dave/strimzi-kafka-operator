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

import com.ibm.eventstreams.api.model.GeoReplicatorDestinationUsersModel;
import com.ibm.eventstreams.api.model.GeoReplicatorModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicator;
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicatorBuilder;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.status.EventStreamsGeoReplicatorStatus;
import com.ibm.eventstreams.api.status.EventStreamsGeoReplicatorStatusBuilder;
import com.ibm.eventstreams.controller.models.PhaseState;
import com.ibm.eventstreams.controller.models.StatusCondition;
import com.ibm.eventstreams.georeplicator.GeoReplicatorCredentials;
import com.ibm.eventstreams.rest.eventstreams.VersionValidation;
import com.ibm.eventstreams.rest.replicator.ReplicatorKafkaListenerValidation;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerTls;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.operator.PlatformFeaturesAvailability;
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
import java.util.List;
import java.util.Optional;

public class EventStreamsGeoReplicatorOperator extends AbstractOperator<EventStreamsGeoReplicator, EventStreamsGeoReplicatorResourceOperator> {

    private static final Logger log = LogManager.getLogger(EventStreamsGeoReplicatorOperator.class.getName());

    private final KubernetesClient client;
    private final EventStreamsGeoReplicatorResourceOperator replicatorResourceOperator;
    private final KafkaMirrorMaker2Operator kafkaMirrorMaker2Operator;
    private final NetworkPolicyOperator networkPolicyOperator;
    private final SecretOperator secretOperator;
    private final EventStreamsResourceOperator resourceOperator;
    private final KafkaUserOperator kafkaUserOperator;
    private final MetricsProvider metricsProvider;

    private PlatformFeaturesAvailability pfa;

    private static final String CLUSTER_CA_CERT_SECRET_NAME = "cluster-ca-cert";


    public EventStreamsGeoReplicatorOperator(Vertx vertx, KubernetesClient client, String kind, PlatformFeaturesAvailability pfa,
                                             EventStreamsGeoReplicatorResourceOperator replicatorResourceOperator,
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
    protected Future<Void> createOrUpdate(Reconciliation reconciliation, EventStreamsGeoReplicator replicatorInstance) {
        log.debug("createOrUpdate reconciliation {} for instance {}", reconciliation, replicatorInstance);
        EventStreamsGeoReplicatorOperator.ReconciliationState reconcileState = new EventStreamsGeoReplicatorOperator.ReconciliationState(replicatorInstance);

        return reconcile(reconcileState);
    }

    private Future<Void> reconcile(EventStreamsGeoReplicatorOperator.ReconciliationState reconcileState) {

        return reconcileState.validateCustomResource()
                .compose(ReconciliationState::createReplicator)
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
        final EventStreamsGeoReplicator replicatorInstance;
        final String namespace;
        final EventStreamsGeoReplicatorStatusBuilder status;
        final List<Condition> previousConditions;

        ReconciliationState(EventStreamsGeoReplicator replicatorInstance) {
            this.replicatorInstance = replicatorInstance;
            this.namespace = replicatorInstance.getMetadata().getNamespace();
            this.previousConditions = Optional.ofNullable(replicatorInstance.getStatus()).map(EventStreamsGeoReplicatorStatus::getConditions).orElse(new ArrayList<>());
            this.status = replicatorInstance.getStatus() == null ? new EventStreamsGeoReplicatorStatusBuilder()
                    .withConditions()
                    .withNewVersions()
                    .endVersions()
                    : new EventStreamsGeoReplicatorStatusBuilder(replicatorInstance.getStatus()).withConditions(new ArrayList<>());

        }

        Future<EventStreamsGeoReplicatorOperator.ReconciliationState> validateCustomResource() {

            PhaseState phase = Optional.ofNullable(status.getPhase()).orElse(PhaseState.PENDING);

            boolean isValidCR = true;

            List<StatusCondition> conditions = new VersionValidation().validateCr(replicatorInstance);
            if (!conditions.isEmpty()) {
                conditions.forEach(condition -> addCondition(condition.toCondition()));
                isValidCR = false;
            }

            //the name of the replicator instance doesn't match what has been set in the strimzi cluster label
            if (!replicatorInstance.getMetadata().getName().equals(replicatorInstance.getMetadata().getLabels().get(Labels.STRIMZI_CLUSTER_LABEL))) {
                addToConditions(StatusCondition.createErrorCondition("MismatchEventStreamsAndReplictorInstanceNames",
                    String.format("The name of the Event Streams geo-replicator instance '%s' does not match the Event Streams instance '%s'. ",
                        replicatorInstance.getMetadata().getName(), replicatorInstance.getMetadata().getLabels().get(Labels.STRIMZI_CLUSTER_LABEL)) +
                        String.format("Edit spec.metadata.name to provide the value of '%s'. ", replicatorInstance.getMetadata().getLabels().get(Labels.STRIMZI_CLUSTER_LABEL))).toCondition());
                isValidCR = false;
            }

            if (isValidCR) {
                addCondition(previousConditions
                        .stream()
                        // restore any previous readiness condition if this was set, so
                        // that timestamps remain consistent
                        .filter(c -> c.getType().equals(PhaseState.PENDING.toValue()))
                        .findFirst()
                        // otherwise set a new condition saying that the reconcile loop is running
                        .orElse(StatusCondition.createPendingCondition("Creating", "Event Streams geo-replicator is being deployed").toCondition()));
            } else {
                phase = PhaseState.FAILED;
            }

            EventStreamsGeoReplicatorStatus statusSubresource = status.withPhase(phase).build();
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
                                String errorMessage = String.format("AdminApi is required to enable geo-replication for Event Streams instance '%s'. ", replicatorInstance.getMetadata().getLabels().get(Labels.STRIMZI_CLUSTER_LABEL))
                                    + "Edit spec.adminApi to provide a valid adminApi value.";
                                addCondition(StatusCondition.createErrorCondition("AdminApiMissingDependency", errorMessage).toCondition());

                                EventStreamsGeoReplicatorStatus statusSubresource = status.withPhase(PhaseState.FAILED).build();
                                replicatorInstance.setStatus(statusSubresource);

                                Promise<EventStreamsGeoReplicatorOperator.ReconciliationState> failReconcile = Promise.promise();
                                updateStatus(statusSubresource).onComplete(f -> {
                                    log.info("AdminApi not present : " + f.succeeded());
                                    failReconcile.fail("Exit Reconcile as AdminApi not present");
                                });
                                return failReconcile.future();

                            }
                            ReplicatorKafkaListenerValidation replicatorKafkaListenerValidation = new ReplicatorKafkaListenerValidation(instance);
                            if (replicatorKafkaListenerValidation.hasInvalidAuthenticationCondition()) {
                                EventStreamsGeoReplicatorStatus statusSubresource = status.withPhase(PhaseState.FAILED).build();
                                replicatorInstance.setStatus(statusSubresource);

                                Promise<EventStreamsGeoReplicatorOperator.ReconciliationState> failReconcile = Promise.promise();
                                updateStatus(statusSubresource).onComplete(f -> {
                                    log.info("UnsupportedAuthorization : " + f.succeeded());
                                    failReconcile.fail("Exit Reconcile as listener client authentication unsupported for GeoReplication");
                                });
                                return failReconcile.future();
                            }
                            //if internal auth is on then external must be too.  Vice versa is ok
                            if (replicatorKafkaListenerValidation.hasMismatchedExternalAndInternalListenerAuthentication()) {
                                EventStreamsGeoReplicatorStatus statusSubresource = status.withPhase(PhaseState.FAILED).build();
                                replicatorInstance.setStatus(statusSubresource);

                                Promise<EventStreamsGeoReplicatorOperator.ReconciliationState> failReconcile = Promise.promise();
                                updateStatus(statusSubresource).onComplete(f -> {
                                    log.info("UnsupportedAuthorization : " + f.succeeded());
                                    failReconcile.fail("Exit Reconcile as listener client authentication unsupported for GeoReplication");
                                });
                                return failReconcile.future();
                            }
                            replicatorKafkaListenerValidation.getConditions().forEach(condition -> addToConditions(condition.toCondition()));

                            GeoReplicatorCredentials geoReplicatorCredentials = new GeoReplicatorCredentials(instance);
                            GeoReplicatorDestinationUsersModel replicatorUsersModel = new GeoReplicatorDestinationUsersModel(replicatorInstance, instance);
                            return kafkaUserOperator.reconcile(namespace, replicatorUsersModel.getConnectKafkaUserName(), replicatorUsersModel.getConnectKafkaUser())
                                    .compose(state -> kafkaUserOperator.reconcile(namespace, replicatorUsersModel.getTargetConnectorKafkaUserName(), replicatorUsersModel.getTargetConnectorKafkaUser()))
                                    .compose(state -> kafkaUserOperator.reconcile(namespace, replicatorUsersModel.getConnectExternalKafkaUserName(), replicatorUsersModel.getConnectExternalKafkaUser()))
                                    .compose(state -> setTrustStoreForReplicator(geoReplicatorCredentials, instance))
                                    .compose(state -> setClientAuthForReplicator(geoReplicatorCredentials, replicatorInstance, instance))
                                    .compose(state -> kafkaMirrorMaker2Operator.getAsync(namespace, instance.getMetadata().getName()))
                                    //Can't make the ReplicatorModel until after setTrustStoreForReplicator and setClientAuthForReplicator have completed
                                    .compose(mirrorMaker2 -> {
                                        GeoReplicatorModel geoReplicatorModel = new GeoReplicatorModel(replicatorInstance, instance, geoReplicatorCredentials, mirrorMaker2);
                                        List<Future> replicatorFutures = new ArrayList<>();
                                        replicatorFutures.add(networkPolicyOperator.reconcile(namespace, geoReplicatorModel.getDefaultResourceName(), geoReplicatorModel.getNetworkPolicy()));
                                        replicatorFutures.add(kafkaMirrorMaker2Operator.reconcile(namespace, geoReplicatorModel.getReplicatorName(), geoReplicatorModel.getReplicator()));
                                        return CompositeFuture.join(replicatorFutures);
                                    })
                                    .map(cf -> this);


                        } else {
                            addToConditions(StatusCondition.createErrorCondition("EventStreamsInstanceNotFound",
                                String.format("Could not find Event Streams instance '%s' in namespace '%s'. ", eventStreamsInstanceName, namespace)
                                + "Geo-replication requires a running Event Streams instance. "
                                + "Create an Event Streams instance before deploying the Event Streams geo-replicator").toCondition());

                            EventStreamsGeoReplicatorStatus statusSubresource = status.withPhase(PhaseState.FAILED).build();
                            replicatorInstance.setStatus(statusSubresource);

                            Promise<EventStreamsGeoReplicatorOperator.ReconciliationState> failReconcile = Promise.promise();
                            updateStatus(statusSubresource).onComplete(f -> {
                                log.info("Can't find Event Streams instance : " + f.succeeded());
                                failReconcile.fail("Exit Reconcile as can't find Event Streams instance for GeoReplication");
                            });
                            return failReconcile.future();

                        }
                    });

        }

        Future<EventStreamsGeoReplicatorOperator.ReconciliationState> updateStatus(EventStreamsGeoReplicatorStatus newStatus) {
            Promise<EventStreamsGeoReplicatorOperator.ReconciliationState> updateStatusPromise = Promise.promise();

            replicatorResourceOperator.getAsync(namespace, replicatorInstance.getMetadata().getName()).setHandler(getRes -> {
                if (getRes.succeeded()) {
                    EventStreamsGeoReplicator current = getRes.result();
                    if (current != null) {
                        EventStreamsGeoReplicator updatedStatus = new EventStreamsGeoReplicatorBuilder(current).withStatus(newStatus).build();
                        replicatorResourceOperator.updateEventStreamsGeoReplicatorStatus(updatedStatus)
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

        void recordFailure(Throwable thr) {
            log.error("Recording reconcile failure", thr);
            addToConditions(StatusCondition.createErrorCondition("DeploymentFailed",
                String.format("An unexpected exception was encountered: %s. More detail can be found in the Event Streams geo-replication operator log.", thr.getMessage())).toCondition());

            EventStreamsGeoReplicatorStatus statusSubresource = status.withPhase(PhaseState.FAILED).build();
            replicatorInstance.setStatus(statusSubresource);
            updateStatus(statusSubresource);
        }

        Future<EventStreamsGeoReplicatorOperator.ReconciliationState> finalStatusUpdate() {
            status.withPhase(PhaseState.READY);

            log.info("Updating status");
            EventStreamsGeoReplicatorStatus esStatus = status.build();
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

        private Future<Void> setClientAuthForReplicator(GeoReplicatorCredentials geoReplicatorCredentials, EventStreamsGeoReplicator replicatorInstance, EventStreams instance) {

            Promise<Void> setAuthSet = Promise.promise();

            String connectKafkaUserName = GeoReplicatorDestinationUsersModel.getConnectKafkaUserName(replicatorInstance.getMetadata().getName());

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
                            geoReplicatorCredentials.setGeoReplicatorClientAuth(getRes.result());
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

        private Future<Void> setTrustStoreForReplicator(GeoReplicatorCredentials geoReplicatorCredentials, EventStreams instance) {

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
                            geoReplicatorCredentials.setGeoReplicatorTrustStore(getRes.result());
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
