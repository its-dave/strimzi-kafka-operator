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
import com.ibm.eventstreams.api.model.AdminProxyModel;
import com.ibm.eventstreams.api.model.AdminUIModel;
import com.ibm.eventstreams.api.model.CollectorModel;
import com.ibm.eventstreams.api.model.EventStreamsKafkaModel;
import com.ibm.eventstreams.api.model.InternalKafkaUserModel;
import com.ibm.eventstreams.api.model.ReplicatorModel;
import com.ibm.eventstreams.api.model.RestProducerModel;
import com.ibm.eventstreams.api.model.SchemaRegistryModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.status.EventStreamsStatus;
import com.ibm.eventstreams.api.status.EventStreamsStatusBuilder;
import com.ibm.eventstreams.controller.certifificates.EventStreamsCertificateException;
import com.ibm.eventstreams.controller.certifificates.EventStreamsCertificateManager;
import com.ibm.eventstreams.rest.NameValidation;
import com.ibm.eventstreams.rest.VersionValidation;
import com.ibm.iam.api.model.ClientModel;
import com.ibm.iam.api.spec.Client;
import com.ibm.iam.api.spec.ClientDoneable;
import com.ibm.iam.api.spec.ClientList;

import io.fabric8.openshift.api.model.Route;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.Crds;
import io.strimzi.api.kafka.model.CertAndKeySecretSource;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaUser;
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
                .compose(state -> state.createKafkaCustomResource())
                .compose(state -> state.waitForKafkaStatus())
                .compose(state -> state.createInternalKafkaUser())
                .compose(state -> state.createAdminProxy())
                .compose(state -> state.createRestProducer(this::dateSupplier))
                .compose(state -> state.createAdminApi(this::dateSupplier))
                .compose(state -> state.createSchemaRegistry(this::dateSupplier))
                .compose(state -> state.createAdminUI())
                .compose(state -> state.createReplicator())
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
        final EventStreamsStatusBuilder status;
        final EventStreamsOperatorConfig.ImageLookup imageConfig;
        final EventStreamsCertificateManager certificateManager;
        Map<String, String> icpClusterData = null;

        ReconciliationState(Reconciliation reconciliation, EventStreams instance, EventStreamsOperatorConfig.ImageLookup imageConfig) {
            this.reconciliation = reconciliation;
            this.instance = instance;

            this.status = instance.getStatus() == null ? new EventStreamsStatusBuilder()
                .withRoutes(new HashMap<>())
                .withConditions()
                .withNewVersions()
                .endVersions()
                : new EventStreamsStatusBuilder(instance.getStatus());

            this.imageConfig = imageConfig;
            this.certificateManager = new EventStreamsCertificateManager(secretOperator, reconciliation.namespace(), EventStreamsKafkaModel.getKafkaInstanceName(reconciliation.name()));
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
                        byte[] clusterCaCertArray = Base64.getDecoder().decode(clusterCaCert);
                        icpClusterData.put("icp_public_cacert", new String(clusterCaCertArray, "US-ASCII"));
                        clusterCaSecretPromise.complete();
                    } catch (Exception e) {
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

        Future<ReconciliationState> createKafkaCustomResource() {
            EventStreamsKafkaModel kafka = new EventStreamsKafkaModel(instance);
            Future<Kafka> createdKafka = toFuture(() -> Crds.kafkaOperation(client)
                .inNamespace(instance.getMetadata().getNamespace())
                .createOrReplace(kafka.getKafka()));
                
            return createdKafka.map(v -> this);
        }

        Future<ReconciliationState> waitForKafkaStatus() {
            String namespace = instance.getMetadata().getNamespace();
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

        Future<ReconciliationState> createInternalKafkaUser() {
            InternalKafkaUserModel internalUserModel = new InternalKafkaUserModel(instance);
            KafkaUser kafkaUser = internalUserModel
                    .getKafkaUser();
            kafkaUser.getMetadata()
                    .getLabels()
                    .putAll(internalUserModel.getComponentLabels());
            Future<KafkaUser> createdKafkaUser = toFuture(() -> Crds
                .kafkaUserOperation(client)
                .inNamespace(instance.getMetadata().getNamespace())
                .createOrReplace(kafkaUser));
            return createdKafkaUser.map(v -> this);
        }

        Future<ReconciliationState> createAdminProxy() {
            List<Future> adminProxyFutures = new ArrayList<>();
            AdminProxyModel adminProxy = new AdminProxyModel(instance, imageConfig, pfa.hasRoutes());
            if (adminProxy.getCustomImage()) {
                customImageCount++;
            }
            adminProxyFutures.add(deploymentOperator.createOrUpdate(adminProxy.getDeployment()));
            adminProxyFutures.add(serviceAccountOperator.createOrUpdate(adminProxy.getServiceAccount()));
            adminProxyFutures.add(configMapOperator.createOrUpdate(adminProxy.getConfigMap()));
            adminProxyFutures.add(serviceOperator.createOrUpdate(adminProxy.getService()));
            adminProxyFutures.add(networkPolicyOperator.createOrUpdate(adminProxy.getNetworkPolicy()));
            if (pfa.hasRoutes() && routeOperator != null) {
                adminProxyFutures.add(routeOperator.createOrUpdate(adminProxy.getRoute()).compose(route -> {
                    status.addToRoutes(AdminProxyModel.COMPONENT_NAME, route.resource().getSpec().getHost());
                    return Future.succeededFuture();
                }));
            }
            return CompositeFuture.join(adminProxyFutures)
                    .map(v -> this);
        }

        Future<ReconciliationState> createRestProducer(Supplier<Date> dateSupplier) {
            Future<ReconcileResult<Deployment>> restProducerFuture = Future.succeededFuture();
            log.info("Starting rest producer reconcile");
            if (instance.getSpec().getRestProducer() != null) {
                List<Future> restProducerFutures = new ArrayList<>();
                RestProducerModel restProducer = new RestProducerModel(instance, imageConfig, status.getKafkaListeners());
                if (restProducer.getCustomImage()) {
                    customImageCount++;
                }
                restProducerFutures.add(serviceAccountOperator.createOrUpdate(restProducer.getServiceAccount()));
                // Keep old service for Route
                restProducerFutures.add(serviceOperator.createOrUpdate(restProducer.getService()));
                restProducerFutures.add(serviceOperator.createOrUpdate(restProducer.getExternalService()));
                restProducerFutures.add(serviceOperator.createOrUpdate(restProducer.getInternalService()));
                restProducerFutures.add(networkPolicyOperator.createOrUpdate(restProducer.getNetworkPolicy()));
                restProducerFuture = CompositeFuture.join(restProducerFutures)
                        .compose(res -> {
                            Map<String, Route> routes = restProducer.getRoutes();
                            routes.put("", restProducer.getRoute());
                            return createOrUpdateRoutes(restProducer, routes);
                        })
                        .compose(res -> reconcileCerts(restProducer, res, dateSupplier))
                        .compose(secretResult -> deploymentOperator.createOrUpdate(restProducer.getDeployment(secretResult.resource().getMetadata().getResourceVersion())));
            }

            return restProducerFuture.map(v -> this);
        }

        Future<ReconciliationState> createAdminApi(Supplier<Date> dateSupplier) {
            List<Future> adminApiFutures = new ArrayList<>();
            AdminApiModel adminApi = new AdminApiModel(instance, imageConfig, status.getKafkaListeners(), icpClusterData);
            if (adminApi.getCustomImage()) {
                customImageCount++;
            }
            adminApiFutures.add(serviceAccountOperator.createOrUpdate(adminApi.getServiceAccount()));
            // Still add the old service for the old rest container
            adminApiFutures.add(serviceOperator.createOrUpdate(adminApi.getService()));
            adminApiFutures.add(serviceOperator.createOrUpdate(adminApi.getExternalService()));
            adminApiFutures.add(serviceOperator.createOrUpdate(adminApi.getInternalService()));
            adminApiFutures.add(networkPolicyOperator.createOrUpdate(adminApi.getNetworkPolicy()));
            adminApiFutures.add(roleBindingOperator.createOrUpdate(adminApi.getRoleBinding()));
            return CompositeFuture.join(adminApiFutures)
                    .compose(res -> {
                        Map<String, Route> routes = adminApi.getRoutes();
                        routes.put("", adminApi.getRoute());
                        return createOrUpdateRoutes(adminApi, routes);
                    })
                    .compose(res -> reconcileCerts(adminApi, res, dateSupplier))
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
                schemaRegistryFutures.add(pvcOperator.createOrUpdate(schemaRegistry.getPersistentVolumeClaim()));
            }
            schemaRegistryFutures.add(serviceAccountOperator.createOrUpdate(schemaRegistry.getServiceAccount()));
            schemaRegistryFutures.add(serviceOperator.createOrUpdate(schemaRegistry.getExternalService()));
            schemaRegistryFutures.add(serviceOperator.createOrUpdate(schemaRegistry.getInternalService()));
            schemaRegistryFutures.add(networkPolicyOperator.createOrUpdate(schemaRegistry.getNetworkPolicy()));
            return CompositeFuture.join(schemaRegistryFutures)
                    .compose(res -> createOrUpdateRoutes(schemaRegistry, schemaRegistry.getRoutes()))
                    .compose(res -> reconcileCerts(schemaRegistry, res, dateSupplier))
                    .compose(secretResult -> deploymentOperator.createOrUpdate(schemaRegistry.getDeployment(secretResult.resource().getMetadata().getResourceVersion())))
                    .map(this);
        }
        
        Future<ReconciliationState> createAdminUI() {
            List<Future> adminUIFutures = new ArrayList<>();
            AdminUIModel ui = new AdminUIModel(instance, imageConfig, pfa.hasRoutes(), icpClusterData);
            if (ui.getCustomImage()) {
                customImageCount++;
            }
            adminUIFutures.add(deploymentOperator.createOrUpdate(ui.getDeployment()));
            adminUIFutures.add(serviceAccountOperator.createOrUpdate(ui.getServiceAccount()));
            adminUIFutures.add(roleBindingOperator.createOrUpdate(ui.getRoleBinding()));
            adminUIFutures.add(serviceOperator.createOrUpdate(ui.getService()));
            adminUIFutures.add(networkPolicyOperator.createOrUpdate(ui.getNetworkPolicy()));
            if (pfa.hasRoutes() && routeOperator != null) {
                adminUIFutures.add(routeOperator.createOrUpdate(ui.getRoute()).compose(route -> {
                    status.addToRoutes(AdminUIModel.COMPONENT_NAME, route.resource().getSpec().getHost());
                    status.withNewAdminUiUrl("https://" + route.resource().getSpec().getHost());
                    return Future.succeededFuture();
                }));
            }
            return CompositeFuture.join(adminUIFutures)
                    .map(v -> this);
        }

        Future<ReconciliationState> createReplicator() {

            List<Future> replicatorFutures = new ArrayList<>();
            ReplicatorModel replicatorModel = new ReplicatorModel(instance);

            replicatorFutures.add(createReplicatorSecretIfRequired(replicatorModel));
            replicatorFutures.add(createReplicatorSourceUser(replicatorModel));

            if (instance.getSpec().getReplicator() != null && instance.getSpec().getReplicator().getReplicas() > 0) {

                replicatorFutures.add(createReplicatorDestinationUsers(replicatorModel));
                replicatorFutures.add(networkPolicyOperator.createOrUpdate(replicatorModel.getNetworkPolicy()));
                replicatorFutures.add(
                    toFuture(() -> Crds.kafkaConnectOperation(client)
                        .inNamespace(instance.getMetadata().getNamespace())
                        .createOrReplace(replicatorModel.getReplicator()))
                );
            }
            return CompositeFuture.join(replicatorFutures)
                    .map(v -> this);
        }

        Future<ReconciliationState> createCollector() {
            List<Future> collectorFutures = new ArrayList<>();
            CollectorModel collector = new CollectorModel(instance, imageConfig);
            if (collector.getCustomImage()) {
                customImageCount++;
            }
            collectorFutures.add(deploymentOperator.createOrUpdate(collector.getDeployment()));
            collectorFutures.add(serviceAccountOperator.createOrUpdate(collector.getServiceAccount()));
            collectorFutures.add(serviceOperator.createOrUpdate(collector.getService()));
            collectorFutures.add(networkPolicyOperator.createOrUpdate(collector.getNetworkPolicy()));
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
                Resource<Client, ClientDoneable> res = clientcr.inNamespace(instance.getMetadata().getNamespace()).withName(clientName);
                Client existingClient = null;
                if (res != null) {
                    existingClient = res.get();
                }

                if (existingClient == null) {
                    log.info("Creating OAuth client '{}'", clientName);
                    Future<Client> createdClient = toFuture(() -> clientcr
                            .inNamespace(instance.getMetadata().getNamespace())
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

        Future<ReconcileResult<Secret>> reconcileCerts(AbstractSecureEndpointModel model, Map<String, String> additionalHosts, Supplier<Date> dateSupplier) {
            log.info("Starting certificate reconciliation for: " + model.getComponentName());
            try {
                boolean regenSecret = false;
                Optional<Secret> certSecret = certificateManager.getSecret(model.getCertSecretName());
                for (Listener listener: model.getTlsListeners()) {
                    String host = listener.isExposed() ? additionalHosts.getOrDefault(listener.getName(), "") : "";
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
                // regen can't be false and the current certSecret not exist
                return regenSecret ? secretOperator.createOrUpdate(model.getCertSecret()) : Future.succeededFuture(ReconcileResult.noop(certSecret.get()));
            } catch (EventStreamsCertificateException e) {
                log.error(e);
                return Future.failedFuture(e);
            }
        }

        CompositeFuture createReplicatorDestinationUsers(ReplicatorModel replicatorModel) {

            KafkaUser replicatorConnectUser = replicatorModel.getReplicatorConnectUser();
            KafkaUser replicatorDestinationConnectorUser = replicatorModel.getReplicatorDestinationConnectorUser();

            Future<KafkaUser> createdReplicatorConnectUser = toFuture(() -> Crds
                    .kafkaUserOperation(client)
                    .inNamespace(instance.getMetadata().getNamespace())
                     .createOrReplace(replicatorConnectUser));

            Future<KafkaUser> createdReplicatorDestinationConnectorUser = toFuture(() -> Crds
                    .kafkaUserOperation(client)
                    .inNamespace(instance.getMetadata().getNamespace())
                    .createOrReplace(replicatorDestinationConnectorUser));

            return CompositeFuture.join(createdReplicatorConnectUser, createdReplicatorDestinationConnectorUser);
        }

        Future<KafkaUser> createReplicatorSourceUser(ReplicatorModel replicatorModel) {
            KafkaUser replicatorSourceConnectorUser = replicatorModel.getReplicatorSourceConnectorUser();

            return toFuture(() -> Crds
                    .kafkaUserOperation(client)
                    .inNamespace(instance.getMetadata().getNamespace())
                    .createOrReplace(replicatorSourceConnectorUser));
        }

        Future<Void> createReplicatorSecretIfRequired(ReplicatorModel replicatorModel) {
            Promise<Void> createSecretPromise = Promise.promise();

            String resourceName = instance.getMetadata().getName() + "-" + AbstractModel.APP_NAME + "-" + ReplicatorModel.REPLICATOR_SECRET_NAME;

            secretOperator.getAsync(instance.getMetadata().getNamespace(), resourceName).setHandler(getRes -> {
                if (getRes.succeeded()) {
                    if (getRes.result() == null) {
                        secretOperator.createOrUpdate(replicatorModel.getSecret()).setHandler(createSecretResult -> {
                            if (createSecretResult.succeeded()) {
                                createSecretPromise.complete();
                            } else {
                                log.error("Failed to create  the Replicator Secret", getRes.cause());
                                createSecretPromise.fail(createSecretResult.cause());
                            }
                        });
                    } else {
                        log.debug("Replicator Secret Exists");
                        createSecretPromise.complete();
                    }
                } else {
                    log.error("Failed to query for the Replicator Secret", getRes.cause());
                    createSecretPromise.fail("Failed to query for the Replicator Secret" + getRes.cause());
                }
            });
            return createSecretPromise.future();
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

        protected Future<Map<String, String>> createOrUpdateRoutes(AbstractSecureEndpointModel model, Map<String, Route> routes) {
            if (!pfa.hasRoutes() || routeOperator == null) {
                return Future.succeededFuture(Collections.emptyMap());
            }
            List<Future> routeFutures = routes.entrySet()
                    .stream()
                    .map(entry -> routeOperator.createOrUpdate(entry.getValue()).compose(routeResult -> {
                        String routeHost = routeResult.resource().getSpec().getHost();
                        status.addToRoutes(model.componentPrefixedName(entry.getKey()), routeHost);
                        Map<String, String> map = new HashMap<>(1); // Has to be HashMap for putAll
                        map.put(entry.getKey(), routeHost);
                        return Future.succeededFuture(map);
                    }))
                    .collect(Collectors.toList());

            return CompositeFuture.join(routeFutures).compose(ar -> {
                List<Map<String, String>> maps = ar.list();
                return Future.succeededFuture(maps.stream().reduce((map1, map2) -> {
                    map1.putAll(map2);
                    return map1;
                }).orElse(Collections.emptyMap()));
            });
        }
    }
}
