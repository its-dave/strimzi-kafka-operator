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

import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.model.AbstractSecureEndpointsModel;
import com.ibm.eventstreams.api.model.AdminApiModel;
import com.ibm.eventstreams.api.model.AdminUIModel;
import com.ibm.eventstreams.api.model.ClusterSecretsModel;
import com.ibm.eventstreams.api.model.CollectorModel;
import com.ibm.eventstreams.api.model.EventStreamsKafkaModel;
import com.ibm.eventstreams.api.model.InternalKafkaUserModel;
import com.ibm.eventstreams.api.model.KafkaNetworkPolicyExtensionModel;
import com.ibm.eventstreams.api.model.MessageAuthenticationModel;
import com.ibm.eventstreams.api.model.ReplicatorSecretModel;
import com.ibm.eventstreams.api.model.ReplicatorSourceUsersModel;
import com.ibm.eventstreams.api.model.RestProducerModel;
import com.ibm.eventstreams.api.model.SchemaRegistryModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.status.EventStreamsEndpoint;
import com.ibm.eventstreams.api.status.EventStreamsStatus;
import com.ibm.eventstreams.api.status.EventStreamsStatusBuilder;
import com.ibm.eventstreams.controller.certificates.EventStreamsCertificateException;
import com.ibm.eventstreams.controller.certificates.EventStreamsCertificateManager;
import com.ibm.eventstreams.rest.EndpointValidation;
import com.ibm.eventstreams.rest.LicenseValidation;
import com.ibm.eventstreams.rest.NameValidation;
import com.ibm.eventstreams.rest.PlainListenerValidation;
import com.ibm.eventstreams.rest.VersionValidation;
import com.ibm.eventstreams.rest.ValidationResponsePayload.ValidationResponse;
import com.ibm.iam.api.controller.Cp4iServicesBindingResourceOperator;
import com.ibm.iam.api.model.ClientModel;
import com.ibm.iam.api.model.Cp4iServicesBindingModel;
import com.ibm.iam.api.spec.Client;
import com.ibm.iam.api.spec.ClientDoneable;
import com.ibm.iam.api.spec.ClientList;
import com.ibm.iam.api.spec.Cp4iServicesBinding;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteSpec;
import io.strimzi.api.kafka.Crds;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.api.kafka.model.status.ConditionBuilder;
import io.strimzi.api.kafka.model.status.KafkaStatus;
import io.strimzi.certs.CertAndKey;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.strimzi.operator.cluster.model.ModelUtils;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class EventStreamsOperator extends AbstractOperator<EventStreams, EventStreamsResourceOperator> {

    private static final Logger log = LogManager.getLogger(EventStreamsOperator.class.getName());

    private final KubernetesClient client;
    private final EventStreamsResourceOperator esResourceOperator;
    private final Cp4iServicesBindingResourceOperator cp4iResourceOperator;
    private final KafkaUserOperator kafkaUserOperator;
    private final KafkaMirrorMaker2Operator kafkaMirrorMaker2Operator;
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
    public static final String CP4I_SERVICES_BINDING_NAME = "cp4iservicesbinding.cp4i.ibm.com";

    public EventStreamsOperator(Vertx vertx, KubernetesClient client, String kind, PlatformFeaturesAvailability pfa,
                                EventStreamsResourceOperator esResourceOperator,
                                Cp4iServicesBindingResourceOperator cp4iResourceOperator,
                                EventStreamsOperatorConfig.ImageLookup imageConfig,
                                RouteOperator routeOperator,
                                long kafkaStatusReadyTimeoutMs) {
        super(vertx, kind, esResourceOperator);
        log.info("Creating EventStreamsOperator");
        this.esResourceOperator = esResourceOperator;
        this.cp4iResourceOperator = cp4iResourceOperator;
        this.client = client;
        this.pfa = pfa;
        this.kafkaUserOperator = new KafkaUserOperator(vertx, client);
        this.kafkaMirrorMaker2Operator = new KafkaMirrorMaker2Operator(vertx, client);
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
        customImageCount =  0;

        return reconcileState.validateCustomResource()
                .compose(state -> state.getCloudPakClusterData())
                .compose(state -> state.getCloudPakClusterCert())
                .compose(state -> state.createCloudPakClusterCertSecret())
                .compose(state -> state.createCp4iServicesBinding())
                .compose(state -> state.waitForCp4iServicesBindingStatus())
                .compose(state -> state.createKafkaCustomResource())
                .compose(state -> state.waitForKafkaStatus())
                .compose(state -> state.createKafkaNetworkPolicyExtension())
                .compose(state -> state.createReplicatorUsers()) //needs to be before createReplicator and createAdminAPI
                .compose(state -> state.createInternalKafkaUser())
                .compose(state -> state.createMessageAuthenticationSecret())
                .compose(state -> state.createRestProducer(this::dateSupplier))
                .compose(state -> state.createReplicatorSecret())
                .compose(state -> state.createAdminApi(this::dateSupplier))
                .compose(state -> state.createSchemaRegistry(this::dateSupplier))
                .compose(state -> state.createAdminUI())
                .compose(state -> state.createCollector())
                .compose(state -> state.createOAuthClient())
                .compose(state -> state.checkEventStreamsSpec(this::dateSupplier))
                .onSuccess(state -> state.finalStatusUpdate())
                .onFailure(thr -> reconcileState.recordFailure(thr))
                .map(t -> null);
    }

    @Override
    protected Future<Boolean> delete(Reconciliation reconciliation) {
        return Future.succeededFuture(Boolean.FALSE);
    }

    @SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
    class ReconciliationState {
        final Reconciliation reconciliation;
        final EventStreams instance;
        final String namespace;
        final EventStreamsStatusBuilder status;
        final List<Condition> previousConditions;
        final EventStreamsOperatorConfig.ImageLookup imageConfig;
        final EventStreamsCertificateManager certificateManager;
        Map<String, String> icpClusterData = null;

        ReconciliationState(Reconciliation reconciliation, EventStreams instance, EventStreamsOperatorConfig.ImageLookup imageConfig) {
            this.reconciliation = reconciliation;
            this.instance = instance;
            this.namespace = instance.getMetadata().getNamespace();
            // keep any existing conditions so that the timestamps on them can be preserved
            this.previousConditions = Optional.ofNullable(instance.getStatus()).map(EventStreamsStatus::getConditions).orElse(new ArrayList<>());
            this.status = instance.getStatus() == null ? new EventStreamsStatusBuilder()
                .withRoutes(new HashMap<>())
                .withConditions(new ArrayList<>())
                .withNewVersions()
                .endVersions()
                // clear previous conditions now that a copy is saved
                : new EventStreamsStatusBuilder(instance.getStatus()).withConditions(new ArrayList<>());
            this.imageConfig = imageConfig;
            this.certificateManager = new EventStreamsCertificateManager(secretOperator, reconciliation.namespace(), EventStreamsKafkaModel.getKafkaInstanceName(instance.getMetadata().getName()));
        }

        // there are several checks, but keeping them all in one place is helpful, so overriding the checkstyle warning
        @SuppressWarnings("checkstyle:NPathComplexity")
        Future<ReconciliationState> validateCustomResource() {
            String phase = Optional.ofNullable(status.getPhase()).orElse("Pending");

            // fail straight away if the CR previously had errored conditions
            if (previousConditions.stream().anyMatch(c -> "Errored".equals(c.getReason()))) {
                return Future.failedFuture("Error");
            }

            boolean isValidCR = true;

            if (LicenseValidation.shouldReject(instance)) {
                addNotReadyCondition("LicenseNotAccepted", "Invalid custom resource: EventStreams License not accepted");
                isValidCR = false;
            }
            if (NameValidation.shouldReject(instance)) {
                addNotReadyCondition("InvalidName", "Invalid custom resource: EventStreams metadata name not accepted");
                isValidCR = false;
            }
            if (VersionValidation.shouldReject(instance)) {
                addNotReadyCondition("InvalidVersion", "Invalid custom resource: Unsupported version. Supported versions are " + VersionValidation.VALID_APP_VERSIONS.toString());
                isValidCR = false;
            }
            ValidationResponse response = EndpointValidation.validateEndpoints(instance).getResponse();
            if (response != null) {
                addNotReadyCondition(EndpointValidation.FAILURE_REASON, response.getStatus().getMessage());
                isValidCR = false;
            }
            if (!ReplicatorSourceUsersModel.isValidInstance(instance)) {
                addNotReadyCondition("UnsupportedAuthorization", "Listener client authentication unsupported for Geo Replication. Supported versions are TLS and SCRAM");
                isValidCR = false;
            }
            if (PlainListenerValidation.shouldReject(instance)) {
                addNotReadyCondition("InvalidSecurityConfiguration", PlainListenerValidation.getRejectionReason(instance));
                isValidCR = false;
            }

            boolean adminApiRequested = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getAdminApi).isPresent();
            boolean uiRequested = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getAdminUI).isPresent();
            if (uiRequested && !adminApiRequested) {
                addNotReadyCondition("InvalidUiConfiguration", "adminApi is a required component to enable adminUi");
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

            EventStreamsStatus statusSubresource = status.withPhase(phase).build();
            instance.setStatus(statusSubresource);

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
                return Future.failedFuture("Invalid Event Streams specification: further details in the status conditions");
            }
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
                addNotReadyCondition("DependencyMissing", "Could not retrieve cloud pak resources");

                EventStreamsStatus statusSubresource = status.withPhase("Failed").build();
                instance.setStatus(statusSubresource);

                Promise<ReconciliationState> failReconcile = Promise.promise();
                updateStatus(statusSubresource).onComplete(f -> {
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
                    addNotReadyCondition("DependencyMissing", "could not get secret 'ibmcloud-cluster-ca-cert' in namespace 'kube-public'");

                    EventStreamsStatus statusSubresource = status.withPhase("Failed").build();
                    instance.setStatus(statusSubresource);

                    updateStatus(statusSubresource).onComplete(f -> {
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
                        addNotReadyCondition("FailedCreate", err.getMessage());

                        EventStreamsStatus statusSubresource = status.withPhase("Failed").build();
                        instance.setStatus(statusSubresource);

                        updateStatus(statusSubresource).onComplete(f -> {
                            log.info("Failure to create ICP Cluster CA Cert secret " + f.succeeded());
                            clusterCaSecretPromise.fail(err);
                        });
                    });
            } else {
                addNotReadyCondition("DependencyMissing", "Encoded ICP CA Cert not in ICPClusterData");

                EventStreamsStatus statusSubresource = status.withPhase("Failed").build();
                instance.setStatus(statusSubresource);

                updateStatus(statusSubresource).onComplete(f -> {
                    log.info("Encoded ICP CA Cert not in ICPClusterData: " + f.succeeded());
                    clusterCaSecretPromise.fail("Encoded ICP CA Cert not in ICPClusterData");
                });
            }

            return clusterCaSecretPromise.future().map(v -> this);
        }


        Future<ReconciliationState> createCp4iServicesBinding() {
            if (!isCrdPresent(CP4I_SERVICES_BINDING_NAME)) {
                status.withCp4iPresent(false);
                return Future.succeededFuture(this);
            }
            status.withCp4iPresent(true);

            Cp4iServicesBindingModel cp4iServicesBindingModel = new Cp4iServicesBindingModel(instance);
            Cp4iServicesBinding cp4iServicesBinding = cp4iServicesBindingModel.getCp4iServicesBinding();
            String cp4iServicesBindingName = cp4iServicesBinding.getMetadata().getName();

            // Create an operation that can be invoked to retrieve or create the required Custom Resource
            cp4iResourceOperator.reconcile(namespace, cp4iServicesBindingName, cp4iServicesBinding);

            return Future.succeededFuture(this);
        }

        Future<ReconciliationState> waitForCp4iServicesBindingStatus() {
            if (!status.isCp4iPresent()) {
                log.debug("CP4I is not present");
                return Future.succeededFuture(this);
            }

            String cp4iInstanceName = Cp4iServicesBindingModel.getCp4iInstanceName(instance.getMetadata().getName());

            return cp4iResourceOperator.waitForCp4iServicesBindingStatusAndMaybeGetUrl(namespace, cp4iInstanceName, defaultPollIntervalMs, 10000, reconciliation)
                .setHandler(res -> {
                    if (res.succeeded()) {
                        log.debug("Putting the header URL " + res.result() + " into the Admin UI");
                        icpClusterData.put(AdminUIModel.ICP_CM_CLUSTER_PLATFORM_SERVICES_URL, res.result());
                    } else {
                        log.debug("Putting the header URL (empty string) into the Admin UI");
                        icpClusterData.put(AdminUIModel.ICP_CM_CLUSTER_PLATFORM_SERVICES_URL, "");
                    }
                }).compose(v -> {
                    return Future.succeededFuture(this);
                });
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

            return esResourceOperator.kafkaCRHasStoppedDeploying(namespace, kafkaInstanceName, defaultPollIntervalMs, kafkaStatusReadyTimeoutMs)
                    .compose(v -> {
                        log.debug("Retrieve Kafka instances in namespace : " + namespace);
                        Optional<Kafka> kafkaInstance = esResourceOperator.getKafkaInstance(namespace, kafkaInstanceName);
                        if (kafkaInstance.isPresent()) {
                            log.debug("Found Kafka instance with name : " + kafkaInstance.get().getMetadata().getName());
                            KafkaStatus kafkaStatus = kafkaInstance.get().getStatus();
                            log.debug("{}: kafkaStatus: {}", reconciliation, kafkaStatus);

                            List<Condition> conditions = Optional.ofNullable(kafkaStatus.getConditions()).orElse(Collections.emptyList());

                            conditions.stream()
                                // ignore Kafka readiness conditions as we want to set our own
                                .filter(c -> !"Ready".equals(c.getType()))
                                // copy any warnings or messages from the Kafka CR into the ES CR
                                .forEach(this::addToConditions);

                            status.withKafkaListeners(kafkaStatus.getListeners());

                            if (conditions.stream().noneMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()))) {
                                return Future.failedFuture("Kafka cluster is not ready");
                            }
                        } else {
                            return Future.failedFuture("Failed to retrieve kafkaInstance");
                        }
                        return Future.succeededFuture(this);
                    });
        }

        Future<ReconciliationState> createKafkaNetworkPolicyExtension() {
            KafkaNetworkPolicyExtensionModel kafkaNetworkPolicyExtensionModel = new KafkaNetworkPolicyExtensionModel(instance);
            return networkPolicyOperator
                .reconcile(namespace, kafkaNetworkPolicyExtensionModel.getDefaultResourceName(), kafkaNetworkPolicyExtensionModel.getNetworkPolicy())
                .map(v -> this);
        }

        Future<ReconciliationState> createReplicatorUsers() {

            ReplicatorSourceUsersModel replicatorSourceUsersModel = new ReplicatorSourceUsersModel(instance);

            return kafkaUserOperator.reconcile(namespace,
                    replicatorSourceUsersModel.getSourceConnectorKafkaUserName(),
                    replicatorSourceUsersModel.getSourceConnectorKafkaUser()).map(v -> this);

        }

        Future<ReconciliationState> createReplicatorSecret() {

            ReplicatorSecretModel replicatorSecretModel = new ReplicatorSecretModel(instance);
            String secretName = replicatorSecretModel.getSecretName();

            return secretOperator.getAsync(namespace, secretName)
                    .compose(secret -> {
                        // Secret should only be created once
                        if (secret == null) {
                            log.debug("Creating replicator secret {} as not found", secretName);
                            return secretOperator.reconcile(namespace, secretName, replicatorSecretModel.getSecret());
                        }
                        return Future.succeededFuture(ReconcileResult.noop(secret));
                    }).map(res -> this);

        }

        Future<ReconciliationState> createInternalKafkaUser() {
            InternalKafkaUserModel internalKafkaUserModel = new InternalKafkaUserModel(instance);
            return kafkaUserOperator.reconcile(namespace, internalKafkaUserModel.getInternalKafkaUserName(), internalKafkaUserModel.getKafkaUser())
                    .map(this);
        }

        Future<ReconciliationState> createRestProducer(Supplier<Date> dateSupplier) {
            log.info("Starting rest producer reconcile");
            List<Future> restProducerFutures = new ArrayList<>();
            RestProducerModel restProducer = new RestProducerModel(instance, imageConfig, status.getKafkaListeners(), icpClusterData);
            if (restProducer.getCustomImage()) {
                customImageCount++;
            }
            restProducerFutures.add(serviceAccountOperator.reconcile(namespace, restProducer.getDefaultResourceName(), restProducer.getServiceAccount()));
            for (EndpointServiceType type : EndpointServiceType.values()) {
                restProducerFutures.add(serviceOperator.reconcile(namespace, restProducer.getServiceName(type), restProducer.getSecurityService(type)));
            }
            restProducerFutures.add(networkPolicyOperator.reconcile(namespace, restProducer.getDefaultResourceName(), restProducer.getNetworkPolicy()));
            return CompositeFuture.join(restProducerFutures)
                .compose(v -> reconcileRoutes(restProducer, restProducer.getRoutes()))
                .compose(routesMap -> reconcileCerts(restProducer, routesMap, dateSupplier))
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
            adminApiFutures.add(serviceAccountOperator.reconcile(namespace, adminApi.getDefaultResourceName(), adminApi.getServiceAccount()));

            for (EndpointServiceType type : EndpointServiceType.values()) {
                adminApiFutures.add(serviceOperator.reconcile(namespace, adminApi.getServiceName(type), adminApi.getSecurityService(type)));
            }

            adminApiFutures.add(networkPolicyOperator.createOrUpdate(adminApi.getNetworkPolicy()));
            adminApiFutures.add(roleBindingOperator.createOrUpdate(adminApi.getRoleBinding()));
            return CompositeFuture.join(adminApiFutures)
                    .compose(v -> reconcileRoutes(adminApi, adminApi.getRoutes()))
                    .compose(routesHostMap -> {
                        for (String adminRouteHost : routesHostMap.values()) {
                            String adminRouteUri = "https://" + adminRouteHost;
                            updateEndpoints(new EventStreamsEndpoint(EventStreamsEndpoint.ADMIN_KEY, EventStreamsEndpoint.EndpointType.API, adminRouteUri));
                        }
                        return Future.succeededFuture(routesHostMap);
                    })
                    .compose(routesHostMap -> reconcileCerts(adminApi, routesHostMap, dateSupplier))
                    .compose(secretResult -> {
                        String certGenerationID = secretResult.resourceOpt()
                            .map(Secret::getMetadata)
                            .map(ObjectMeta::getResourceVersion)
                            .orElse(null);
                        return deploymentOperator.reconcile(namespace, adminApi.getDefaultResourceName(), adminApi.getDeployment(certGenerationID));
                    })
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
            for (EndpointServiceType type : EndpointServiceType.values()) {
                schemaRegistryFutures.add(serviceOperator.reconcile(namespace, schemaRegistry.getServiceName(type), schemaRegistry.getSecurityService(type)));
            }
            schemaRegistryFutures.add(networkPolicyOperator.reconcile(namespace, schemaRegistry.getDefaultResourceName(), schemaRegistry.getNetworkPolicy()));

            return CompositeFuture.join(schemaRegistryFutures)
                    .compose(v -> reconcileRoutes(schemaRegistry, schemaRegistry.getRoutes()))
                    .compose(routesHostMap -> {
                        String schemaRouteHost = routesHostMap.get(schemaRegistry.getRouteName(Endpoint.DEFAULT_EXTERNAL_NAME));
                        if (schemaRouteHost != null) {
                            String schemaRouteUri = "https://" + schemaRouteHost;
                            updateEndpoints(new EventStreamsEndpoint(EventStreamsEndpoint.SCHEMA_REGISTRY_KEY,
                                                                     EventStreamsEndpoint.EndpointType.API,
                                                                     schemaRouteUri));
                        }
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

        Future<ReconciliationState> createMessageAuthenticationSecret() {
            log.debug("Creating message authentication secret");
            MessageAuthenticationModel messageAuthenticationModel = new MessageAuthenticationModel(instance);
            return secretOperator.reconcile(instance.getMetadata().getNamespace(), messageAuthenticationModel.getSecretName(instance.getMetadata().getName()), messageAuthenticationModel.getSecret()).map(v -> this);
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

                        if (AdminUIModel.isUIEnabled(instance)) {
                            status.withNewAdminUiUrl(uiRouteUri);
                            updateEndpoints(new EventStreamsEndpoint(EventStreamsEndpoint.UI_KEY,
                                                                     EventStreamsEndpoint.EndpointType.UI,
                                                                     uiRouteUri));
                        }
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

            String uiRoute = status.getRoutes().get(AdminUIModel.COMPONENT_NAME);

            log.debug("Found route '{}'", uiRoute);

            if (uiRoute != null) {
                ClientModel clientModel = new ClientModel(instance, "https://" + uiRoute);
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

        Future<ReconciliationState> checkEventStreamsSpec(Supplier<Date> dateSupplier) {
            EventStreamsSpecChecker checker = new EventStreamsSpecChecker(dateSupplier, instance.getSpec());
            List<Condition> warnings = checker.run();
            for (Condition warning : warnings) {
                addToConditions(warning);
            }
            return Future.succeededFuture(this);
        }


        Future<ReconciliationState> updateStatus(EventStreamsStatus newStatus) {
            Promise<ReconciliationState> updateStatusPromise = Promise.promise();

            esResourceOperator.getAsync(namespace, instance.getMetadata().getName()).setHandler(getRes -> {
                if (getRes.succeeded()) {
                    EventStreams current = getRes.result();
                    if (current != null) {
                        EventStreams updatedStatus = new EventStreamsBuilder(current).withStatus(newStatus).build();
                        esResourceOperator.updateEventStreamsStatus(updatedStatus)
                                .setHandler(updateRes -> {
                                    if (updateRes.succeeded()) {
                                        updateStatusPromise.complete(this);
                                    } else {
                                        log.error("Failed to update status", updateRes.cause());
                                        updateStatusPromise.fail(updateRes.cause());
                                    }
                                });
                    } else {
                        log.error("Event Streams resource not found");
                        updateStatusPromise.fail("Event Streams resource not found");
                    }
                } else {
                    log.error("Event Streams resource not found", getRes.cause());
                    updateStatusPromise.fail(getRes.cause());
                }
            });

            return updateStatusPromise.future();
        }



        Future<ReconciliationState> finalStatusUpdate() {
            status.withCustomImages(customImageCount > 0);
            status.withPhase("Running");
            addReadyCondition();

            log.info("Updating status");
            EventStreamsStatus esStatus = status.build();
            instance.setStatus(esStatus);
            return updateStatus(esStatus);
        }


        void recordFailure(Throwable thr) {
            log.error("Recording reconcile failure", thr);
            addNotReadyCondition("DeploymentFailed", thr.getMessage());

            EventStreamsStatus statusSubresource = status.withPhase("Failed").build();
            instance.setStatus(statusSubresource);
            updateStatus(statusSubresource);
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
        Future<ReconcileResult<Secret>> reconcileCerts(AbstractSecureEndpointsModel model, Map<String, String> additionalHosts, Supplier<Date> dateSupplier) {
            log.info("Starting certificate reconciliation for: " + model.getComponentName());
            try {
                boolean regenSecret = false;
                Optional<Secret> certSecret = certificateManager.getSecret(model.getCertificateSecretName());
                for (Endpoint endpoint : model.getTlsNonP2PEndpoints()) {
                    regenSecret = updateCertAndKeyInModel(certSecret, model, endpoint, additionalHosts, dateSupplier) || regenSecret;
                }
                // Create secret if any services are not null
                List<Service> securityServices = Arrays.stream(EndpointServiceType.values())
                    .map(model::getSecurityService)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

                if (securityServices.size() > 0) {
                    model.createCertificateSecretModelSecret();
                }

                return secretOperator.reconcile(namespace, model.getCertificateSecretName(), model.getCertificateSecretModelSecret());

            } catch (EventStreamsCertificateException e) {
                log.error(e);
                return Future.failedFuture(e);
            }
        }

        /**
         * Helper method which updates the cert and key in the model for a given endpoint. Will always set the key and
         * cert in case a future endpoint requires a regeneration of the secret.
         * @param certSecret Existing secret that a previous reconcile has created
         * @param model the current model to update
         * @param endpoint the current endpoint to configure the cert and key for
         * @param additionalHosts a map of route names to additional hosts
         * @param dateSupplier the date supplier
         * @return a boolean of whether or not we need to regenerate the secret
         * @throws EventStreamsCertificateException
         */
        private boolean updateCertAndKeyInModel(Optional<Secret> certSecret, AbstractSecureEndpointsModel model, Endpoint endpoint, Map<String, String> additionalHosts, Supplier<Date> dateSupplier) throws EventStreamsCertificateException {
            String host = endpoint.isRoute() ? additionalHosts.getOrDefault(model.getRouteName(endpoint.getName()), "") : "";
            List<String> hosts = host.isEmpty() ? Collections.emptyList() : Collections.singletonList(host);
            Service service = endpoint.isRoute() ? null : model.getSecurityService(endpoint.getType());

            if (endpoint.getCertificateAndKeyOverride() != null) {
                Optional<CertAndKey> providedCertAndKey = certificateManager.certificateAndKey(endpoint.getCertificateAndKeyOverride());
                if (!providedCertAndKey.isPresent()) {
                    throw new EventStreamsCertificateException("Provided broker cert secret: " + endpoint.getCertificateAndKeyOverride().getSecretName() + " could not be found");
                }
                if (certSecret.isPresent()) {
                    CertAndKey currentCertAndKey = certificateManager.certificateAndKey(certSecret.get(), model.getCertSecretCertID(endpoint.getName()), model.getCertSecretKeyID(endpoint.getName()));
                    boolean hasCertOverridesChanged = certificateManager.sameCertAndKey(currentCertAndKey, providedCertAndKey.get());
                    model.setCertAndKey(endpoint.getName(), hasCertOverridesChanged ? providedCertAndKey.get() : currentCertAndKey);
                    return hasCertOverridesChanged;
                }
                // The secret hasn't been generated yet so create a new entry with the provided cert and key
                model.setCertAndKey(endpoint.getName(), providedCertAndKey.get());
                return true;
            } else if (!certSecret.isPresent() || certificateManager.shouldGenerateOrRenewCertificate(certSecret.get(), endpoint.getName(), dateSupplier, service, hosts)) {
                CertAndKey certAndKey = certificateManager.generateCertificateAndKey(service, hosts);
                model.setCertAndKey(endpoint.getName(), certAndKey);
                return true;
            }
            // even if we don't need to regenerate the secret, we need to set the key and cert in case of future reconciliation
            CertAndKey currentCertAndKey = certificateManager.certificateAndKey(certSecret.get(), model.getCertSecretCertID(endpoint.getName()), model.getCertSecretKeyID(endpoint.getName()));
            model.setCertAndKey(endpoint.getName(), currentCertAndKey);
            return false;
        }

        protected Future<Map<String, String>> reconcileRoutes(AbstractSecureEndpointsModel model, Map<String, Route> routes) {
            return deleteUnspecifiedRoutes(model, routes)
                .compose(res -> createRoutes(model, routes));
        }

        protected Future<Map<String, String>> createRoutes(AbstractSecureEndpointsModel model, Map<String, Route> routes) {
            if (!pfa.hasRoutes() || routeOperator == null) {
                return Future.succeededFuture(Collections.emptyMap());
            }

            List<Future> routeFutures = routes.entrySet()
                .stream()
                .map(entry -> routeOperator.reconcile(namespace, entry.getKey(), entry.getValue())
                    .compose(routeResult -> {
                        Map<String, String> map = new HashMap<>(1);
                        String routeHost = routeResult.resourceOpt()
                            .map(Route::getSpec)
                            .map(RouteSpec::getHost)
                            .orElse("");

                        if (!routeHost.isEmpty()) {
                            status.addToRoutes(getRouteShortName(model, entry.getKey()), routeHost);
                            map.put(entry.getKey(), routeHost);
                        }
                        return Future.succeededFuture(map);
                    }))
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

        /**
         * Method checks the status for the existing routes and determines if no longer needed they should be deleted
         * @param model the model to check its endpoints
         * @param routes the map of all routes
         * @return A list of futures of whether the routes have been deleted.
         */
        Future<List<ReconcileResult<Route>>> deleteUnspecifiedRoutes(AbstractSecureEndpointsModel model, Map<String, Route> routes) {
            if (!pfa.hasRoutes() || routeOperator == null) {
                return Future.succeededFuture(Collections.emptyList());
            }

            if (status.getRoutes() == null) {
                return Future.succeededFuture(Collections.emptyList());
            }

            List<String> removedKeys = new ArrayList<>();

            // Checking for previously created routes in the status to see if they need to be deleted
            List<Future> routeDeletionFutures = status.getRoutes().keySet().stream()
                .filter(key -> key.contains(model.getComponentName()))
                .filter(key -> !routes.containsKey(status.getRoutes().get(key)))
                .map(key -> {
                    Future<ReconcileResult<Route>> deletion = routeOperator.reconcile(namespace, getRouteLongName(model, key), null);
                    removedKeys.add(key);
                    return deletion;
                })
                .collect(Collectors.toList());

            // Done to avoid ConcurrentModificationException
            removedKeys.forEach(status::removeFromRoutes);

            return CompositeFuture.join(routeDeletionFutures)
                .compose(ar -> Future.succeededFuture(ar.list()));
        }

        private String getRouteShortName(AbstractSecureEndpointsModel model, String routeName) {
            return routeName.replaceFirst(model.getResourcePrefix() + "-", "");
        }

        private String getRouteLongName(AbstractSecureEndpointsModel model, String routeName) {
            return model.getResourcePrefix() + "-" + routeName;
        }

        protected void updateEndpoints(EventStreamsEndpoint newEndpoint) {
            // replace any existing endpoint with the same name
            status.removeMatchingFromEndpoints(item -> newEndpoint.getName().equals(item.getName()));
            status.addToEndpoints(newEndpoint);
        }

        /**
         * Adds the provided condition to the current status.
         *
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
         * Adds a "Ready" condition to the status if there is not already one.
         *  This does nothing if there is already an existing ready condition from
         *  a previous reconcile.
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
         *  Event Streams operand is not ready yet.
         *
         * @param reason A unique, one-word, CamelCase reason for why the operand is not ready.
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
         * Adds a condition to the status, and then sorts the list to ensure
         * that they are maintained in order of timestamp.
         */
        private void addCondition(Condition condition) {
            status.addToConditions(condition);
            status.getConditions().sort(Comparator.comparing(Condition::getLastTransitionTime)
                                            .thenComparing(Condition::getType, Comparator.reverseOrder()));
        }
    }

    Boolean isCrdPresent(String crdName) {
        return Optional.ofNullable(client.customResourceDefinitions().list())
            .map(CustomResourceDefinitionList::getItems)
            .map(list -> list.stream()
                .filter(crd -> crd.getMetadata().getName().equals(crdName))
                .findAny()
                .isPresent())
            .orElse(false);
    }
}
