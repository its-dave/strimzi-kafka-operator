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
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.status.EventStreamsReplicatorStatusBuilder;
import com.ibm.eventstreams.api.status.EventStreamsReplicatorStatus;
import com.ibm.eventstreams.replicator.ReplicatorCredentials;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerTls;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


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
                .compose(state -> state.updateStatus())
                .compose(state -> {
                    log.debug("after update status" + state.toString());
                    return state.createReplicator(); })
                .onSuccess(ar -> {
                    log.debug("GeoReplication reconciliation success");
                })
                .onFailure(ar -> {
                    log.error("GeoReplication reconciliation failed with error {} ", ar.getMessage());
                }).map(res -> null);

    }

    class ReconciliationState {
        final EventStreamsReplicator replicatorInstance;
        final String namespace;
        final EventStreamsReplicatorStatusBuilder status;


        ReconciliationState(EventStreamsReplicator replicatorInstance) {
            this.replicatorInstance = replicatorInstance;
            this.namespace = replicatorInstance.getMetadata().getNamespace();
            this.status = replicatorInstance.getStatus() == null ? new EventStreamsReplicatorStatusBuilder()
                    .withConditions()
                    .withNewVersions()
                    .endVersions()
                    : new EventStreamsReplicatorStatusBuilder(replicatorInstance.getStatus());

        }
        Future<EventStreamsReplicatorOperator.ReconciliationState> validateCustomResource() {
            // read conditions in status valid CR if no conditions in errored state
            Boolean isValidCR = Optional.ofNullable(status.getConditions())
                    .map(list -> list.stream().filter(con -> Optional.ofNullable(con.getReason()).orElse("").equals("Errored")).collect(Collectors.toList()))
                    .map(List::isEmpty)
                    .orElse(true);


            // fail straight away if cr has errored conditions
            if (!isValidCR) {
                return Future.failedFuture("Invalid Custom Resource");
            }

            return Future.succeededFuture(this);
        }


        Future<ReconciliationState> createReplicator() {

            String eventStreamsInstanceName = replicatorInstance.getMetadata().getLabels().get(Labels.STRIMZI_CLUSTER_LABEL);

            log.debug("Create replicator called {} ", eventStreamsInstanceName);

            return resourceOperator.getAsync(namespace, eventStreamsInstanceName).compose(instance -> {
                if (instance != null) {
                    ReplicatorCredentials replicatorCredentials = new ReplicatorCredentials(instance);
                    ReplicatorDestinationUsersModel replicatorUsersModel = new ReplicatorDestinationUsersModel(replicatorInstance, instance);
                    return kafkaUserOperator.reconcile(namespace, replicatorUsersModel.getConnectKafkaUserName(), replicatorUsersModel.getConnectKafkaUser())
                            .compose(state -> kafkaUserOperator.reconcile(namespace, replicatorUsersModel.getTargetConnectorKafkaUserName(), replicatorUsersModel.getTargetConnectorKafkaUser()))
                            .compose(state -> setTrustStoreForReplicator(replicatorCredentials, instance))
                            .compose(state -> setClientAuthForReplicator(replicatorCredentials, replicatorInstance, instance))

                            //Can't make the ReplicatorModel until after setTrustStoreForReplicator and setClientAuthForReplicator have completed
                            .compose(state -> {
                                ReplicatorModel replicatorModel = new ReplicatorModel(replicatorInstance, instance, replicatorCredentials);
                                List<Future> replicatorFutures = new ArrayList<>();
                                replicatorFutures.add(networkPolicyOperator.reconcile(namespace, replicatorModel.getDefaultResourceName(), replicatorModel.getNetworkPolicy()));
                                replicatorFutures.add(kafkaMirrorMaker2Operator.reconcile(namespace, replicatorModel.getReplicatorName(), replicatorModel.getReplicator()));
                                return CompositeFuture.join(replicatorFutures);
                            })
                            .map(cf -> this);


                } else {
                    //TODO update status too
                    log.error("Can't find Event Streams instance {} in namespace {} ", eventStreamsInstanceName, namespace);
                    return Future.failedFuture("EventStreams instance "
                            + replicatorInstance.getMetadata().getLabels().get(Labels.STRIMZI_CLUSTER_LABEL)
                            + " not available. Ensure the Event Streams instance is created before deploying the Event Streams Replicator ").map(rr -> this);
                }
            });

        }

        Future<EventStreamsReplicatorOperator.ReconciliationState> updateStatus() {

            EventStreamsReplicatorStatus esRepStatus = status.build();

            if (replicatorInstance.getStatus() != esRepStatus) {
                log.debug("Updating status");
                replicatorInstance.setStatus(esRepStatus);
                return replicatorResourceOperator.createOrUpdate(replicatorInstance)
                        .map(res -> this);
            }
            return Future.succeededFuture(this);
        }

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


    @Override
    protected Future<Boolean> delete(Reconciliation reconciliation) {
        return Future.succeededFuture(Boolean.FALSE);
    }

}
