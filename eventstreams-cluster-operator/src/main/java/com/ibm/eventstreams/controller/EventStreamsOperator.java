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

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.ibm.eventstreams.api.Listener;
import com.ibm.eventstreams.api.model.AbstractModel;
import com.ibm.eventstreams.api.model.AbstractSecureEndpointModel;
import com.ibm.eventstreams.api.model.AdminApiModel;
import com.ibm.eventstreams.api.model.AdminUIModel;
import com.ibm.eventstreams.api.model.ClusterSecretsModel;
import com.ibm.eventstreams.api.model.CollectorModel;
import com.ibm.eventstreams.api.model.EventStreamsKafkaModel;
import com.ibm.eventstreams.api.model.InternalKafkaUserModel;
import com.ibm.eventstreams.api.model.ReplicatorModel;
import com.ibm.eventstreams.api.model.ReplicatorUsersModel;
import com.ibm.eventstreams.api.model.RestProducerModel;
import com.ibm.eventstreams.api.model.SchemaRegistryModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.status.EventStreamsEndpoint;
import com.ibm.eventstreams.api.status.EventStreamsStatus;
import com.ibm.eventstreams.api.status.EventStreamsStatusBuilder;
import com.ibm.eventstreams.controller.certificates.EventStreamsCertificateException;
import com.ibm.eventstreams.controller.certificates.EventStreamsCertificateManager;
import com.ibm.eventstreams.replicator.ReplicatorCredentials;
import com.ibm.eventstreams.rest.NameValidation;
import com.ibm.eventstreams.rest.VersionValidation;
import com.ibm.iam.api.model.ClientModel;
import com.ibm.iam.api.spec.Client;
import com.ibm.iam.api.spec.ClientDoneable;
import com.ibm.iam.api.spec.ClientList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.Route;
import io.strimzi.api.kafka.Crds;
import io.strimzi.api.kafka.model.CertAndKeySecretSource;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationOAuth;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternal;
import io.strimzi.api.kafka.model.listener.KafkaListenerTls;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.api.kafka.model.status.ConditionBuilder;
import io.strimzi.api.kafka.model.status.KafkaStatus;
import io.strimzi.certs.CertAndKey;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.strimzi.operator.common.AbstractOperator;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.operator.resource.ConfigMapOperator;
import io.strimzi.operator.common.operator.resource.DeploymentOperator;
import io.strimzi.operator.common.operator.resource.NetworkPolicyOperator;
import io.strimzi.operator.common.operator.resource.PvcOperator;
import io.strimzi.operator.common.operator.resource.ReconcileResult;
import io.strimzi.operator.common.operator.resource.RoleBindingOperator;
import io.strimzi.operator.common.operator.resource.RouteOperator;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.strimzi.operator.common.operator.resource.ServiceAccountOperator;
import io.strimzi.operator.common.operator.resource.ServiceOperator;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class EventStreamsOperator extends AbstractOperator<EventStreams, EventStreamsResourceOperator> {

    private static final Logger log = LogManager.getLogger(EventStreamsOperator.class.getName());

    private final KubernetesClient client;
    private final EventStreamsResourceOperator resourceOperator;
    private final DeploymentOperator deploymentOperator;
    private final ServiceAccountOperator serviceAccountOperator;
    private final RoleBindingOperator roleBindingOperator;
    private final ConfigMapOperator configMapOperator;
    private final ServiceOperator serviceOperator;
    private final SecretOperator secretOperator;
    private final RouteOperator routeOperator;
    private final PvcOperator pvcOperator;
    private final NetworkPolicyOperator networkPolicyOperator;
    private final EventStreamsOperatorConfig.ImageLookup imageConfig;
    private PlatformFeaturesAvailability pfa;

    private long defaultPollIntervalMs = 1000;
    private long kafkaStatusReadyTimeoutMs;

    private int customImageCount = 0;
    private static final String ENCODED_IBMCLOUD_CA_CERT = "icp_public_cacert_encoded";
    private static final String CLUSTER_CA_CERT_SECRET_NAME = "cluster-ca-cert";
    private static final String OAUTH_REPLICATOR_ERROR = "Listener Oauth client authentication unsupported for Geo Replication";

    public EventStreamsOperator(Vertx vertx, KubernetesClient client, String kind, PlatformFeaturesAvailability pfa,
                                EventStreamsResourceOperator resourceOperator,
                                EventStreamsOperatorConfig.ImageLookup imageConfig,
                                RouteOperator routeOperator,
                                long kafkaStatusReadyTimeoutMs) {
        super(vertx, kind, resourceOperator);
        log.info("Creating EventStreamsOperator");
        this.resourceOperator = resourceOperator;
        this.client = client;
        this.pfa = pfa;
        this.deploymentOperator = new DeploymentOperator(vertx, client);
        this.serviceAccountOperator = new ServiceAccountOperator(vertx, client);
        this.roleBindingOperator = new RoleBindingOperator(vertx, client);
        this.configMapOperator = new ConfigMapOperator(vertx, client);
        this.serviceOperator = new ServiceOperator(vertx, client);
        this.secretOperator = new SecretOperator(vertx, client);
        this.routeOperator = routeOperator;
        this.pvcOperator = new PvcOperator(vertx, client);
        this.imageConfig = imageConfig;
        this.networkPolicyOperator = new NetworkPolicyOperator(vertx, client);

        this.kafkaStatusReadyTimeoutMs = kafkaStatusReadyTimeoutMs;
    }

    @Override
    protected Future<Void> createOrUpdate(Reconciliation reconciliation, EventStreams instance) {
        log.debug("createOrUpdate reconciliation {} for instance {}", reconciliation, instance);
        ReconciliationState reconcileState = new ReconciliationState(reconciliation, instance, imageConfig);

        return reconcile(reconcileState);
    }

    Date dateSupplier() {
        return new Date();
    }

    private Future<Void> reconcile(ReconciliationState reconcileState) {
        Promise<Void> chainPromise = Promise.promise();
        customImageCount =  0;

        reconcileState.validateCustomResource()
                .compose(state -> state.getCloudPakClusterData())
                .compose(state -> state.getCloudPakClusterCert())
                .compose(state -> state.createCloudPakClusterCertSecret())
                .compose(state -> state.createKafkaCustomResource())
                .compose(state -> state.waitForKafkaStatus())
                .compose(state -> state.createReplicatorUsers()) //needs to be before createReplicator and createAdminAPI
                .compose(state -> state.createInternalKafkaUser())
                .compose(state -> state.createRestProducer(this::dateSupplier))
                .compose(state -> state.createReplicator())
                .compose(state -> state.createAdminApi(this::dateSupplier))
                .compose(state -> state.createSchemaRegistry(this::dateSupplier))
                .compose(state -> state.createAdminUI())
                .compose(state -> state.createCollector())
                .compose(state -> state.createOAuthClient())
                .compose(state -> state.updateStatus())
                .onSuccess(state -> chainPromise.complete())
                .onFailure(t -> chainPromise.fail(t));

        return chainPromise.future();
    }

    @Override
    protected Future<Boolean> delete(Reconciliation reconciliation) {
        return Future.succeededFuture(Boolean.FALSE);
    }

    class ReconciliationState {
        final Reconciliation reconciliation;
        final EventStreams instance;
        final String namespace;
        final EventStreamsStatusBuilder status;
        final EventStreamsOperatorConfig.ImageLookup imageConfig;
        final EventStreamsCertificateManager certificateManager;
        Map<String, String> icpClusterData = null;

        ReconciliationState(Reconciliation reconciliation, EventStreams instance, EventStreamsOperatorConfig.ImageLookup imageConfig) {
            this.reconciliation = reconciliation;
            this.instance = instance;
            this.namespace = instance.getMetadata().getNamespace();
            this.status = instance.getStatus() == null ? new EventStreamsStatusBuilder()
                .withRoutes(new HashMap<>())
                .withConditions()
                .withNewVersions()
                .endVersions()
                : new EventStreamsStatusBuilder(instance.getStatus());
            this.imageConfig = imageConfig;
            this.certificateManager = new EventStreamsCertificateManager(secretOperator, reconciliation.namespace(), EventStreamsKafkaModel.getKafkaInstanceName(instance.getMetadata().getName()));
        }

        Future<ReconciliationState> validateCustomResource() {
            // read conditions in status valid CR if no conditions in errored state
            Boolean isValidCR = Optional.ofNullable(status.getConditions())
                .map(list -> list.stream().filter(con -> Optional.ofNullable(con.getReason()).orElse("").equals("Errored")).collect(Collectors.toList()))
                .map(List::isEmpty)
                .orElse(true);

            // fail straight away if cr has errored conditions
            if (!isValidCR) {
                return Future.failedFuture("Invalid Custom Resource");
            }
            List<Condition> conditions = new ArrayList<>();

            if (NameValidation.shouldReject(instance)) {
                Condition nameTooLongCondition = buildErrorCondition("Invalid custom resource: EventStreams metadata name too long. Maximum length is " + NameValidation.MAX_NAME_LENGTH);
                conditions.add(nameTooLongCondition);
                isValidCR = false;
            }
            if (VersionValidation.shouldReject(instance)) {
                Condition invalidVersionCondition = buildErrorCondition("Invalid custom resource: Unsupported version. Supported versions are " + VersionValidation.VALID_APP_VERSIONS.toString());
                conditions.add(invalidVersionCondition);
                isValidCR = false;
            }

            // can add additional validation here
            if (!isValidCR) {
                EventStreamsStatus informativeStatus = status
                    .withConditions(conditions)
                    .build();
                instance.setStatus(informativeStatus);
                resourceOperator.createOrUpdate(instance);
                return Future.failedFuture("Invalid Custom Resource: check status");
            }
            return Future.succeededFuture(this);
        }

        Future<ReconciliationState> getCloudPakClusterData() {
            String namespace = "kube-public";
            String icpConfigMapName = "ibmcloud-cluster-info";
            boolean iamPresent = false;
            ConfigMap icpConfigMap = null;
            try {
                icpConfigMap = client.configMaps().inNamespace(namespace).withName(icpConfigMapName).get();
                iamPresent = icpConfigMap != null;
            } catch (KubernetesClientException kubeError) {
                log.error("IAM config map could not be retrieved.");
            }

            log.info("{}: IAM status: {}", reconciliation, iamPresent);

            if (!iamPresent) {
                Condition iamNotPresent = buildErrorCondition("Could not retrieve cloud pak resources");
                EventStreamsStatus informativeStatus = status
                    .withConditions(iamNotPresent)
                    .build();
                instance.setStatus(informativeStatus);
                Promise<ReconciliationState> failReconcile = Promise.promise();
                Future<ReconcileResult<EventStreams>> updateStatus = resourceOperator.createOrUpdate(instance);
                updateStatus.onComplete(f -> {
                    log.info("IAM not present : " + f.succeeded());
                    failReconcile.fail("Exit Reconcile as IAM not present");
                });
                return failReconcile.future();
            }
            icpClusterData = icpConfigMap.getData();

            return Future.succeededFuture(this);
        }

        Future<ReconciliationState> getCloudPakClusterCert() {
            Promise<Void> clusterCaSecretPromise = Promise.promise();
            // get common services info for prometheus metrics
            secretOperator.getAsync("kube-public", "ibmcloud-cluster-ca-cert").setHandler(getRes -> {
                if (getRes.result() != null)  {
                    try {
                        String clusterCaCert = getRes.result().getData().get("ca.crt");
                        icpClusterData.put(EventStreamsOperator.ENCODED_IBMCLOUD_CA_CERT, clusterCaCert);
                        byte[] clusterCaCertArray = Base64.getDecoder().decode(clusterCaCert);
                        icpClusterData.put("icp_public_cacert", new String(clusterCaCertArray, "US-ASCII"));
                        clusterCaSecretPromise.complete();
                    } catch (UnsupportedEncodingException e) {
                        log.error("unable to decode icp public cert", e);
                        clusterCaSecretPromise.fail(e);
                    }
                } else {
                    Condition caCertNotPresent = buildErrorCondition("could not get secret 'ibmcloud-cluster-ca-cert' in namespace 'kube-public'");

                    EventStreamsStatus informativeStatus = status
                        .withConditions(caCertNotPresent)
                        .build();
                    instance.setStatus(informativeStatus);
                    Future<ReconcileResult<EventStreams>> updateStatus = resourceOperator.createOrUpdate(instance);
                    updateStatus.onComplete(f -> {
                        log.info("'ibmcloud-cluster-ca-cert' not present : " + f.succeeded());
                        clusterCaSecretPromise.fail("could not get secret 'ibmcloud-cluster-ca-cert' in namespace 'kube-public'");
                    });
                }
            });

            return clusterCaSecretPromise.future().map(v -> this);
        }

        Future<ReconciliationState> createCloudPakClusterCertSecret() {
            Promise<Void> clusterCaSecretPromise = Promise.promise();
            ClusterSecretsModel clusterSecrets = new ClusterSecretsModel(instance, secretOperator);
            // get common services info for prometheus metrics
            String clusterCert = icpClusterData.get(EventStreamsOperator.ENCODED_IBMCLOUD_CA_CERT);
            if (clusterCert != null) {
                clusterSecrets.createIBMCloudCASecret(clusterCert)
                    .onSuccess(ar -> {
                        clusterCaSecretPromise.complete();
                    })
                    .onFailure(err -> {
                        Condition caCertSecretNotCreated = buildErrorCondition(err.getMessage());
                        EventStreamsStatus informativeStatus = status
                            .withConditions(caCertSecretNotCreated)
                            .build();
                        instance.setStatus(informativeStatus);
                        Future<ReconcileResult<EventStreams>> updateStatus = resourceOperator.createOrUpdate(instance);
                        updateStatus.onComplete(f -> {
                            log.info("Failure to create ICP Cluster CA Cert secret " + f.succeeded());
                            clusterCaSecretPromise.fail(err);
                        });
                    });
            } else {
                Condition caCertNotPresent = buildErrorCondition("Encoded ICP CA Cert not in ICPClusterData");
                EventStreamsStatus informativeStatus = status
                    .withConditions(caCertNotPresent)
                    .build();
                instance.setStatus(informativeStatus);
                Future<ReconcileResult<EventStreams>> updateStatus = resourceOperator.createOrUpdate(instance);
                updateStatus.onComplete(f -> {
                    log.info("Encoded ICP CA Cert not in ICPClusterData: " + f.succeeded());
                    clusterCaSecretPromise.fail("Encoded ICP CA Cert not in ICPClusterData");
                });
            }

            return clusterCaSecretPromise.future().map(v -> this);
        }

        Future<ReconciliationState> createKafkaCustomResource() {
            EventStreamsKafkaModel kafka = new EventStreamsKafkaModel(instance);
            Future<Kafka> createdKafka = toFuture(() -> Crds.kafkaOperation(client)
                .inNamespace(namespace)
                .createOrReplace(kafka.getKafka()));

            return createdKafka.map(v -> this);
        }

        Future<ReconciliationState> waitForKafkaStatus() {
            String kafkaInstanceName = EventStreamsKafkaModel.getKafkaInstanceName(instance.getMetadata().getName());

            return resourceOperator.kafkaCRHasReadyStatus(namespace, kafkaInstanceName, defaultPollIntervalMs, kafkaStatusReadyTimeoutMs)
                    .compose(v -> {
                        log.debug("Retrieve Kafka instances in namespace : " + namespace);
                        Optional<Kafka> kafkaInstance = resourceOperator.getKafkaInstance(namespace, kafkaInstanceName);
                        if (kafkaInstance.isPresent()) {
                            log.debug("Found Kafka instance with name : " + kafkaInstance.get().getMetadata().getName());
                            KafkaStatus kafkaStatus = kafkaInstance.get().getStatus();
                            log.debug("{}: kafkaStatus: {}", reconciliation, kafkaStatus);
                            status.withKafkaListeners(kafkaStatus.getListeners());
                        } else {
                            return Future.failedFuture("Failed to retrieve kafkaInstance");
                        }
                        return Future.succeededFuture(this);
                    });
        }

        Future<ReconciliationState> createReplicatorUsers() {

            List<Future> usersCreated = new ArrayList<>();

            Optional<KafkaListenerAuthentication> internalClientAuth =
                    Optional.ofNullable(instance.getSpec())
                        .map(EventStreamsSpec::getStrimziOverrides)
                        .map(KafkaSpec::getKafka)
                        .map(KafkaClusterSpec::getListeners)
                        .map(KafkaListeners::getTls)
                        .map(KafkaListenerTls::getAuth);

            Optional<KafkaListenerAuthentication> externalClientAuth =
                    Optional.ofNullable(instance.getSpec())
                    .map(EventStreamsSpec::getStrimziOverrides)
                    .map(KafkaSpec::getKafka)
                    .map(KafkaClusterSpec::getListeners)
                    .map(KafkaListeners::getExternal)
                    .map(KafkaListenerExternal::getAuth);

            ReplicatorUsersModel replicatorUsersModel = new ReplicatorUsersModel(instance);

            if (externalClientAuth.isPresent() && !(externalClientAuth.get() instanceof KafkaListenerAuthenticationOAuth)) {

                KafkaUser sourceConnectorUser = replicatorUsersModel.getReplicatorSourceConnectorUser();
                if (sourceConnectorUser != null) {
                    Future<KafkaUser> createdSourceConnectorKafkaUser = toFuture(() -> Crds
                            .kafkaUserOperation(client)
                            .inNamespace(namespace)
                            .createOrReplace(sourceConnectorUser));
                    usersCreated.add(createdSourceConnectorKafkaUser.map(v -> this));
                } else {
                    Crds.kafkaUserOperation(client)
                        .inNamespace(namespace)
                        .withName(ReplicatorModel.REPLICATOR_SOURCE_CLUSTER_CONNECTOR_USER_NAME)
                        .delete();
                }
            } else if (externalClientAuth.isPresent() && externalClientAuth.get() instanceof KafkaListenerAuthenticationOAuth) {

                Condition authConfigNotSupportedForReplication = buildErrorCondition(OAUTH_REPLICATOR_ERROR);
                EventStreamsStatus informativeStatus = status
                        .withConditions(authConfigNotSupportedForReplication)
                        .build();
                instance.setStatus(informativeStatus);
                resourceOperator.createOrUpdate(instance);
                usersCreated.add(Future.failedFuture(OAUTH_REPLICATOR_ERROR));
            }

            if (internalClientAuth.isPresent()
                    && !(internalClientAuth.get() instanceof KafkaListenerAuthenticationOAuth)) {

                KafkaUser connectUser = replicatorUsersModel.getReplicatorConnectUser();
                if (connectUser != null) {
                    Future<KafkaUser> createKafkaConnectUser = toFuture(() -> Crds
                            .kafkaUserOperation(client)
                            .inNamespace(namespace)
                            .createOrReplace(connectUser));
                    usersCreated.add(createKafkaConnectUser.map(v -> this));
                } else {
                    Crds.kafkaUserOperation(client)
                        .inNamespace(namespace)
                        .withName(ReplicatorModel.REPLICATOR_CONNECT_USER_NAME)
                        .delete();
                }

                KafkaUser targetConnectorUser = replicatorUsersModel.getReplicatorTargetConnectorUser();

                if (targetConnectorUser != null) {
                    Future<KafkaUser> createdDestinationConnectorKafkaUser = toFuture(() -> Crds
                        .kafkaUserOperation(client)
                        .inNamespace(namespace)
                        .createOrReplace(targetConnectorUser));
                    usersCreated.add(createdDestinationConnectorKafkaUser.map(v -> this));
                } else {
                    Crds.kafkaUserOperation(client)
                        .inNamespace(namespace)
                        .withName(ReplicatorModel.REPLICATOR_TARGET_CLUSTER_CONNNECTOR_USER_NAME)
                        .delete();
                }

            } else if (internalClientAuth.isPresent() && internalClientAuth.get() instanceof KafkaListenerAuthenticationOAuth) {

                String oauthReplicatorError = "Listener Oauth client authentication unsupported for Geo Replication";
                Condition authConfigNotSupportedForReplication = buildErrorCondition(oauthReplicatorError);
                EventStreamsStatus informativeStatus = status
                        .withConditions(authConfigNotSupportedForReplication)
                        .build();
                instance.setStatus(informativeStatus);
                resourceOperator.createOrUpdate(instance);
                usersCreated.add(Future.failedFuture(oauthReplicatorError));
            }
            return CompositeFuture.join(usersCreated)
                    .map(v -> this);
        }


        Future<ReconciliationState> createReplicator() {

            ReplicatorCredentials replicatorCredentials = new ReplicatorCredentials(instance);
            
            return setTrustStoreForReplicator(replicatorCredentials)
                .compose(v -> setClientAuthForReplicator(replicatorCredentials))
                
                //Can't make the replicatorModel until after setTrustStoreForReplicator and setClientAuthForReplicator have completed
                .compose(v -> { 
                    ReplicatorModel replicatorModel = new ReplicatorModel(instance, replicatorCredentials);
                    String secretName = replicatorModel.getSecretName();
                    
                    List<Future> replicatorFutures = new ArrayList<>();

                    replicatorFutures.add(secretOperator.getAsync(namespace, secretName).compose(secret -> {
                        if (secret == null) {
                            log.debug("reconcilling replicator secret {} with value {}", secretName, secret);
                            return secretOperator.reconcile(namespace, secretName, replicatorModel.getSecret()).map(res -> this);
                        }
                        return Future.succeededFuture(this);
                    }));
                    replicatorFutures.add(networkPolicyOperator.reconcile(namespace, replicatorModel.getDefaultResourceName(), replicatorModel.getNetworkPolicy()));
                    if (replicatorModel.getReplicator() != null) {
                        replicatorFutures.add(toFuture(() -> Crds.kafkaMirrorMaker2Operation(client)
                            .inNamespace(namespace)
                            .createOrReplace(replicatorModel.getReplicator())));
                    } else {
                        Crds.kafkaMirrorMaker2Operation(client)
                            .inNamespace(namespace)
                            .withName(replicatorModel.getDefaultResourceName())
                            .delete(); 
                    }
                    return CompositeFuture.join(replicatorFutures)
                        .map(res -> this);
                }).map(v -> this);
        }

        Future<ReconciliationState> createInternalKafkaUser() {
            InternalKafkaUserModel internalUserModel = new InternalKafkaUserModel(instance);
            KafkaUser kafkaUser = internalUserModel
                    .getKafkaUser();
            Future<KafkaUser> createdKafkaUser = toFuture(() -> Crds
                .kafkaUserOperation(client)
                .inNamespace(namespace)
                .createOrReplace(kafkaUser));
            return createdKafkaUser.map(v -> this);
        }

        Future<ReconciliationState> createRestProducer(Supplier<Date> dateSupplier) {
            log.info("Starting rest producer reconcile");
            List<Future> restProducerFutures = new ArrayList<>();
            RestProducerModel restProducer = new RestProducerModel(instance, imageConfig, status.getKafkaListeners());
            if (restProducer.getCustomImage()) {
                customImageCount++;
            }
            restProducerFutures.add(serviceAccountOperator.reconcile(namespace, restProducer.getDefaultResourceName(), restProducer.getServiceAccount()));
            // Keep old service for Route
            restProducerFutures.add(serviceOperator.reconcile(namespace, restProducer.getInternalServiceName(), restProducer.getInternalService()));
            restProducerFutures.add(serviceOperator.reconcile(namespace, restProducer.getExternalServiceName(), restProducer.getExternalService()));
            restProducerFutures.add(networkPolicyOperator.reconcile(namespace, restProducer.getDefaultResourceName(), restProducer.getNetworkPolicy()));
            return CompositeFuture.join(restProducerFutures)
                .compose(res -> {
                    return reconcileRoutes(restProducer, restProducer.getRoutes());
                })
                .compose(res -> reconcileCerts(restProducer, res, dateSupplier))
                .compose(secretResult -> {
                    String certGenerationID = null;
                    if (secretResult.resourceOpt().isPresent()) {
                        certGenerationID = secretResult.resource().getMetadata().getResourceVersion();
                    }
                    return deploymentOperator.reconcile(namespace, restProducer.getDefaultResourceName(), restProducer.getDeployment(certGenerationID));
                }).map(this);
        }

        Future<ReconciliationState> createAdminApi(Supplier<Date> dateSupplier) {
            List<Future> adminApiFutures = new ArrayList<>();
            AdminApiModel adminApi = new AdminApiModel(instance, imageConfig, status.getKafkaListeners(), icpClusterData);
            if (adminApi.getCustomImage()) {
                customImageCount++;
            }
            adminApiFutures.add(serviceAccountOperator.createOrUpdate(adminApi.getServiceAccount()));
            adminApiFutures.add(serviceOperator.createOrUpdate(adminApi.getExternalService()));
            adminApiFutures.add(serviceOperator.createOrUpdate(adminApi.getInternalService()));
            adminApiFutures.add(networkPolicyOperator.createOrUpdate(adminApi.getNetworkPolicy()));
            adminApiFutures.add(roleBindingOperator.createOrUpdate(adminApi.getRoleBinding()));
            return CompositeFuture.join(adminApiFutures)
                    .compose(res -> reconcileRoutes(adminApi, adminApi.getRoutes()))
                    .compose(routesHostMap -> {
                        String adminRouteHost = routesHostMap.get(adminApi.getRouteName(Listener.EXTERNAL_TLS_NAME));
                        String adminRouteUri = "https://" + adminRouteHost;
                        updateEndpoints(new EventStreamsEndpoint(EventStreamsEndpoint.ADMIN_KEY, EventStreamsEndpoint.EndpointType.api, adminRouteUri));
                        return Future.succeededFuture(routesHostMap);
                    })
                    .compose(routesHostMap -> reconcileCerts(adminApi, routesHostMap, dateSupplier))
                    .compose(secretResult -> deploymentOperator.createOrUpdate(adminApi.getDeployment(secretResult.resource().getMetadata().getResourceVersion())))
                    .map(this);
        }

        Future<ReconciliationState> createSchemaRegistry(Supplier<Date> dateSupplier) {
            List<Future> schemaRegistryFutures = new ArrayList<>();
            SchemaRegistryModel schemaRegistry = new SchemaRegistryModel(instance, imageConfig);
            if (schemaRegistry.getCustomImage()) {
                customImageCount++;
            }
            if (schemaRegistry.getPersistentVolumeClaim() != null) {
                schemaRegistryFutures.add(pvcOperator.reconcile(namespace, schemaRegistry.getDefaultResourceName(), schemaRegistry.getPersistentVolumeClaim()));

            }
            schemaRegistryFutures.add(serviceAccountOperator.reconcile(namespace, schemaRegistry.getDefaultResourceName(), schemaRegistry.getServiceAccount()));
            schemaRegistryFutures.add(serviceOperator.reconcile(namespace, schemaRegistry.getExternalServiceName(), schemaRegistry.getExternalService()));
            schemaRegistryFutures.add(serviceOperator.reconcile(namespace, schemaRegistry.getInternalServiceName(), schemaRegistry.getInternalService()));
            schemaRegistryFutures.add(networkPolicyOperator.reconcile(namespace, schemaRegistry.getDefaultResourceName(), schemaRegistry.getNetworkPolicy()));

            return CompositeFuture.join(schemaRegistryFutures)
                    .compose(res -> reconcileRoutes(schemaRegistry, schemaRegistry.getRoutes()))
                    .compose(routesHostMap -> {
                        String schemaRouteHost = routesHostMap.get(schemaRegistry.getRouteName(Listener.EXTERNAL_TLS_NAME));
                        String schemaRouteUri = "https://" + schemaRouteHost;
                        updateEndpoints(new EventStreamsEndpoint(EventStreamsEndpoint.SCHEMA_REGISTRY_KEY, EventStreamsEndpoint.EndpointType.api, schemaRouteUri));
                        return Future.succeededFuture(routesHostMap);
                    })
                    .compose(res -> reconcileCerts(schemaRegistry, res, dateSupplier))
                    .compose(secretResult -> {
                        String certGenerationID = null;
                        if (secretResult.resourceOpt().isPresent()) {
                            certGenerationID = secretResult.resource().getMetadata().getResourceVersion();
                        }
                        return deploymentOperator.reconcile(namespace, schemaRegistry.getDefaultResourceName(), schemaRegistry.getDeployment(certGenerationID));
                    }).map(this);
        }

        Future<ReconciliationState> createAdminUI() {
            List<Future> adminUIFutures = new ArrayList<>();
            AdminUIModel ui = new AdminUIModel(instance, imageConfig, pfa.hasRoutes(), icpClusterData);
            if (ui.getCustomImage()) {
                customImageCount++;
            }
            adminUIFutures.add(serviceAccountOperator.reconcile(namespace, ui.getDefaultResourceName(), ui.getServiceAccount()));
            adminUIFutures.add(deploymentOperator.reconcile(namespace, ui.getDefaultResourceName(), ui.getDeployment()));
            adminUIFutures.add(roleBindingOperator.reconcile(namespace, ui.getDefaultResourceName(), ui.getRoleBinding()));
            adminUIFutures.add(serviceOperator.reconcile(namespace, ui.getDefaultResourceName(), ui.getService()));
            adminUIFutures.add(networkPolicyOperator.reconcile(namespace, ui.getDefaultResourceName(), ui.getNetworkPolicy()));

            if (pfa.hasRoutes() && routeOperator != null) {
                adminUIFutures.add(routeOperator.reconcile(namespace, ui.getRouteName(), ui.getRoute()).compose(route -> {
                    if (route.resourceOpt().isPresent()) {
                        String uiRouteHost = route.resource().getSpec().getHost();
                        String uiRouteUri = "https://" + uiRouteHost;

                        status.addToRoutes(AdminUIModel.COMPONENT_NAME, uiRouteHost);
                        status.withNewAdminUiUrl(uiRouteUri);
                        
                        updateEndpoints(new EventStreamsEndpoint("ui", EventStreamsEndpoint.EndpointType.ui, uiRouteUri));
                    }
                    return Future.succeededFuture();
                }));
            }
            return CompositeFuture.join(adminUIFutures)
                    .map(v -> this);
        }

        Future<ReconciliationState> createCollector() {
            List<Future> collectorFutures = new ArrayList<>();
            CollectorModel collector = new CollectorModel(instance, imageConfig);
            if (collector.getCustomImage()) {
                customImageCount++;
            }
            collectorFutures.add(deploymentOperator.reconcile(namespace, collector.getDefaultResourceName(), collector.getDeployment()));
            collectorFutures.add(serviceAccountOperator.reconcile(namespace, collector.getDefaultResourceName(), collector.getServiceAccount()));
            collectorFutures.add(serviceOperator.reconcile(namespace, collector.getDefaultResourceName(), collector.getService()));
            collectorFutures.add(networkPolicyOperator.reconcile(namespace, collector.getDefaultResourceName(), collector.getNetworkPolicy()));
            return CompositeFuture.join(collectorFutures)
                    .map(v -> this);
        }

        Future<ReconciliationState> createOAuthClient() {

            String uiRouteHost = status.getAdminUiUrl();

            log.debug("Found route host '{}'", uiRouteHost);

            if (uiRouteHost != null) {
                ClientModel clientModel = new ClientModel(instance, uiRouteHost);
                Client oidcclient = clientModel.getClient();
                String clientName = oidcclient.getMetadata().getName();

                // Create an operation that can be invoked to retrieve or create the required Custom Resource
                MixedOperation<Client, ClientList, ClientDoneable, Resource<Client, ClientDoneable>> clientcr = client.customResources(com.ibm.eventstreams.api.Crds.getCrd(Client.class), Client.class, ClientList.class, ClientDoneable.class);

                // The OIDC registration requires that we supply an empty client id in the Client when its first created. A watcher
                // process will spot the empty string, generate a clientId and create the associated secret. If we don't check
                // that it exists already and repeat this process each time through the reconsitiation loop we would reset the
                // clientid each time and break the UI. We need to ensure the client is created a single time and not updated. As a
                // result, we have to check if it already exists and only attempt to create it if its not present.
                Resource<Client, ClientDoneable> res = clientcr.inNamespace(namespace).withName(clientName);
                Client existingClient = null;
                if (res != null) {
                    existingClient = res.get();
                }

                if (existingClient == null) {
                    log.info("Creating OAuth client '{}'", clientName);
                    Future<Client> createdClient = toFuture(() -> clientcr
                            .inNamespace(namespace)
                            .createOrReplace(oidcclient));
                    return createdClient.map(v -> this);

                }
            }

            return Future.succeededFuture(this);
        }


        Future<ReconciliationState> updateStatus() {
            status.withCustomImages(customImageCount > 0);
            EventStreamsStatus esStatus = status.build();
            if (instance.getStatus() != esStatus) {
                log.info("Updating status");
                instance.setStatus(esStatus);
                return resourceOperator.createOrUpdate(instance)
                    .map(res -> this);
            }
            return Future.succeededFuture(this);
        }

        /**
         *
         *
         * End of ordered reconcilliation methods.
         * Supporting methods below
         *
         *
         */

        private <T> Future<T> toFuture(Supplier<T> createResource) {
            return Future.future(blockingFuture -> {
                vertx.executeBlocking(blocking -> {
                    try {
                        blocking.complete(createResource.get());
                    } catch (Exception e) {
                        blocking.fail(e);
                    }
                }, blockingFuture);
            });
        }

        /**
         *
         *
         * @param model
         * @param additionalHosts is a map of route names and their corresponding hosts generated by openshift
         * @param dateSupplier
         * @return
         */
        Future<ReconcileResult<Secret>> reconcileCerts(AbstractSecureEndpointModel model, Map<String, String> additionalHosts, Supplier<Date> dateSupplier) {
            log.info("Starting certificate reconciliation for: " + model.getComponentName());
            try {
                boolean regenSecret = false;
                Optional<Secret> certSecret = certificateManager.getSecret(model.getCertSecretName());
                for (Listener listener: model.getTlsListeners()) {

                    String host = listener.isExposed() ? additionalHosts.getOrDefault(model.getRouteName(listener.getName()), "") : "";
                    List<String> hosts = host.isEmpty() ? Collections.emptyList() : Collections.singletonList(host);
                    Service service = listener.isExposed() ? model.getExternalService() : model.getInternalService();
                    Optional<CertAndKeySecretSource> certAndKeySecretSource = listener.getCertOverride(instance.getSpec());

                    // Check for user supplied certificates else check if we need to generate or renew our certificates
                    if (certAndKeySecretSource.isPresent()) {
                        Optional<CertAndKey> providedCertAndKey = certificateManager.certificateAndKey(certAndKeySecretSource.get());
                        if (!providedCertAndKey.isPresent()) {
                            throw new EventStreamsCertificateException("Provided broker cert secret: " + certAndKeySecretSource.get().getSecretName() + " could not be found");
                        }
                        if (certSecret.isPresent()) {
                            CertAndKey currentCertAndKey = certificateManager.certificateAndKey(certSecret.get(), model.getCertSecretCertID(listener.getName()), model.getCertSecretKeyID(listener.getName()));
                            if (certificateManager.sameCertAndKey(currentCertAndKey, providedCertAndKey.get())) {
                                // set current cert for listener but do not set regenSecret to true. We have to set the cert in case a future listener causes a secret regen
                                model.setCertAndKey(listener.getName(), currentCertAndKey);
                            }
                        } else {
                            model.setCertAndKey(listener.getName(), providedCertAndKey.get());
                            regenSecret = true;
                        }
                    } else if (!certSecret.isPresent() || certificateManager.shouldGenerateOrRenewCertificate(certSecret.get(), listener.getName(), dateSupplier, service, hosts)) {
                        CertAndKey certAndKey = certificateManager.generateCertificateAndKey(service, hosts);
                        model.setCertAndKey(listener.getName(), certAndKey);
                        regenSecret = true;
                    }
                }
                // services being null indicates that the secret should be deleted
                if (model.getExternalService() != null || model.getInternalService() != null) {
                    model.createCertificateSecretModelSecret();
                }
                // regen can't be false and the current certSecret not exist
                // get certificate can return n ull and hence delete the secret
                return regenSecret ? secretOperator.reconcile(namespace, model.getCertSecretName(), model.getCertificateSecretModelSecret()) : Future.succeededFuture(ReconcileResult.noop(certSecret.get()));
            } catch (EventStreamsCertificateException e) {
                log.error(e);
                return Future.failedFuture(e);
            }
        }

        private Future<Void> setTrustStoreForReplicator(ReplicatorCredentials replicatorCredentials) {

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
                secretOperator.getAsync(namespace, resourceNameCA).setHandler(getRes -> {

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

        private Future<Void> setClientAuthForReplicator(ReplicatorCredentials replicatorCredentials) {

            Promise<Void> setAuthSet = Promise.promise();


            String resourceName = instance.getMetadata().getName() + "-" + AbstractModel.APP_NAME + "-" + ReplicatorModel.REPLICATOR_CONNECT_USER_NAME;

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
                secretOperator.getAsync(namespace, resourceName).setHandler(getRes -> {
                    if (getRes.succeeded()) {
                        if (getRes.result() != null) {
                            replicatorCredentials.setReplicatorClientAuth(getRes.result());
                            setAuthSet.complete();
                        } else {
                            log.info("Replicator Connect User secret " + resourceName + " doesn't exist");
                            setAuthSet.fail("Replicator Connect User secret " + resourceName + " doesn't exist");
                        }
                    } else {
                        log.error("Failed to query for the Replicator Connect User Secret" + resourceName + " " + getRes.cause().toString());
                        setAuthSet.fail("Failed to query for the Replicator Connect User Secret" + resourceName + " " + getRes.cause().toString());
                    }
                });
            } else {
                setAuthSet.complete();
            }
            return setAuthSet.future();
        }

        private Condition buildErrorCondition(String message) {
            return new ConditionBuilder()
                .withLastTransitionTime(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()))
                .withType("NotReady")
                .withStatus("True")
                .withReason("Errored")
                .withMessage(message)
                .build();
        }

        protected Future<Map<String, String>> reconcileRoutes(AbstractSecureEndpointModel model, Map<String, Route> routes) {
            if (!pfa.hasRoutes() || routeOperator == null) {
                return Future.succeededFuture(Collections.emptyMap());
            }

            List<Future> routeFutures = routes.entrySet()
                    .stream()
                    .map(entry -> routeOperator.reconcile(namespace, entry.getKey(), entry.getValue())
                        .compose(routeResult -> {
                            
                            Map<String, String> map = new HashMap<>(1); // Has to be HashMap for putAll
                            if (!routeResult.resourceOpt().isPresent()) {
                                map.put(entry.getKey(), "");
                                return Future.succeededFuture(map);
                            }
                            String routeHost = routeResult.resource().getSpec().getHost();
                            status.addToRoutes(entry.getKey().replaceFirst(model.getResourcePrefix() + "-", ""), routeHost);
                            map.put(entry.getKey(), routeHost);
                            return Future.succeededFuture(map);
                        })
                    )
                    .collect(Collectors.toList());

            return CompositeFuture.join(routeFutures)
                .compose(ar -> {
                    List<Map<String, String>> allRoutesMaps = ar.list();
                    Map<String, String> routesMap = allRoutesMaps
                        .stream()
                        .reduce((map1, map2) -> {
                            map1.putAll(map2);
                            return map1;
                        })
                        .orElse(Collections.emptyMap());
                    return Future.succeededFuture(routesMap);
                });
        }

        protected void updateEndpoints(EventStreamsEndpoint newEndpoint) {
            // replace any existing endpoint with the same name
            status.removeMatchingFromEndpoints(item -> newEndpoint.getName().equals(item.getName()));
            status.addToEndpoints(newEndpoint);
        }
    }
}
