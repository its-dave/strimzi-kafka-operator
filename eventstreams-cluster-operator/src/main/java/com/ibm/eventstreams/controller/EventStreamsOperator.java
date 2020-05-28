/*
 * IBM Confidential
 * OCO Source Materials
 *
 * 5737-H33
 *
 * (C) Copyright IBM Corp. 2020  All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 */
package com.ibm.eventstreams.controller;

import com.ibm.commonservices.CommonServices;
import com.ibm.commonservices.api.controller.Cp4iServicesBindingResourceOperator;
import com.ibm.commonservices.api.controller.OperandRequestResourceOperator;
import com.ibm.commonservices.api.model.ClientModel;
import com.ibm.commonservices.api.model.Cp4iServicesBindingModel;
import com.ibm.commonservices.api.model.OperandRequestModel;
import com.ibm.commonservices.api.spec.Client;
import com.ibm.commonservices.api.spec.ClientDoneable;
import com.ibm.commonservices.api.spec.ClientList;
import com.ibm.commonservices.api.spec.Cp4iServicesBinding;
import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.model.AbstractModel;
import com.ibm.eventstreams.api.model.AbstractSecureEndpointsModel;
import com.ibm.eventstreams.api.model.AdminApiModel;
import com.ibm.eventstreams.api.model.AdminUIModel;
import com.ibm.eventstreams.api.model.ClusterSecretsModel;
import com.ibm.eventstreams.api.model.CollectorModel;
import com.ibm.eventstreams.api.model.EventStreamsKafkaModel;
import com.ibm.eventstreams.api.model.GeoReplicatorSecretModel;
import com.ibm.eventstreams.api.model.GeoReplicatorSourceUsersModel;
import com.ibm.eventstreams.api.model.InternalKafkaUserModel;
import com.ibm.eventstreams.api.model.KafkaNetworkPolicyExtensionModel;
import com.ibm.eventstreams.api.model.MessageAuthenticationModel;
import com.ibm.eventstreams.api.model.RestProducerModel;
import com.ibm.eventstreams.api.model.SchemaRegistryModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.status.EventStreamsEndpoint;
import com.ibm.eventstreams.api.status.EventStreamsStatus;
import com.ibm.eventstreams.api.status.EventStreamsStatusBuilder;
import com.ibm.eventstreams.controller.certificates.EventStreamsCertificateException;
import com.ibm.eventstreams.controller.certificates.EventStreamsCertificateManager;
import com.ibm.eventstreams.controller.models.ConditionType;
import com.ibm.eventstreams.controller.models.PhaseState;
import com.ibm.eventstreams.controller.models.StatusCondition;
import com.ibm.eventstreams.rest.eventstreams.AuthenticationValidation;
import com.ibm.eventstreams.rest.eventstreams.EndpointValidation;
import com.ibm.eventstreams.rest.eventstreams.GeneralSecurityValidation;
import com.ibm.eventstreams.rest.eventstreams.GeneralValidation;
import com.ibm.eventstreams.rest.eventstreams.LicenseValidation;
import com.ibm.eventstreams.rest.eventstreams.NameValidation;
import com.ibm.eventstreams.rest.eventstreams.UnknownPropertyValidation;
import com.ibm.eventstreams.rest.eventstreams.VersionValidation;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteSpec;
import io.strimzi.api.kafka.Crds;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.api.kafka.model.status.KafkaStatus;
import io.strimzi.api.kafka.model.status.KafkaUserStatus;
import io.strimzi.certs.CertAndKey;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.strimzi.operator.common.AbstractOperator;
import io.strimzi.operator.common.MetricsProvider;
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

import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class EventStreamsOperator extends AbstractOperator<EventStreams, EventStreamsResourceOperator> {

    private static final Logger log = LogManager.getLogger(EventStreamsOperator.class.getName());

    private final KubernetesClient client;
    private final EventStreamsResourceOperator esResourceOperator;
    private final OperandRequestResourceOperator operandRequestResourceOperator;
    private final Cp4iServicesBindingResourceOperator cp4iResourceOperator;
    private final EventStreamsGeoReplicatorResourceOperator replicatorResourceOperator;
    private final KafkaUserOperator kafkaUserOperator;
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

    private final MetricsProvider metricsProvider;

    private final String operatorNamespace;

    private long defaultPollIntervalMs = 1000;
    private long kafkaStatusReadyTimeoutMs;

    private int customImageCount = 0;

    public static final String COMMON_SERVICES_NOT_FOUND_REASON = "CommonServicesNotFound";
    public static final String COMMON_SERVICES_CERTIFICATE_NOT_FOUND_REASON = "CommonServicesCertificateNotFound";
    public static final String EVENTSTREAMS_CREATING_REASON = "Creating";

    @SuppressWarnings("checkstyle:ParameterNumber")
    public EventStreamsOperator(Vertx vertx, KubernetesClient client, String kind, PlatformFeaturesAvailability pfa,
                                EventStreamsResourceOperator esResourceOperator,
                                OperandRequestResourceOperator operandRequestResourceOperator,
                                Cp4iServicesBindingResourceOperator cp4iResourceOperator,
                                EventStreamsGeoReplicatorResourceOperator replicatorResourceOperator,
                                KafkaUserOperator kafkaUserOperator,
                                EventStreamsOperatorConfig.ImageLookup imageConfig,
                                RouteOperator routeOperator,
                                MetricsProvider metricsProvider,
                                String operatorNamespace,
                                long kafkaStatusReadyTimeoutMs) {
        super(vertx, kind, esResourceOperator, metricsProvider);
        log.traceEntry(() -> vertx, () -> client, () -> kind, () -> pfa, () -> esResourceOperator,
            () -> cp4iResourceOperator, () -> imageConfig, () -> routeOperator,
            () -> kafkaStatusReadyTimeoutMs);
        log.info("Creating EventStreamsOperator");
        this.esResourceOperator = esResourceOperator;
        this.kafkaUserOperator = kafkaUserOperator;
        this.operandRequestResourceOperator = operandRequestResourceOperator;
        this.cp4iResourceOperator = cp4iResourceOperator;
        this.replicatorResourceOperator = replicatorResourceOperator;
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
        this.metricsProvider = metricsProvider;

        this.operatorNamespace = operatorNamespace;

        this.kafkaStatusReadyTimeoutMs = kafkaStatusReadyTimeoutMs;

        log.traceExit();
    }

    @Override
    protected Future<Void> createOrUpdate(Reconciliation reconciliation, EventStreams instance) {
        log.traceEntry(() -> reconciliation, () -> instance);
        ReconciliationState reconcileState = new ReconciliationState(reconciliation, instance, imageConfig);

        return log.traceExit(reconcile(reconcileState));
    }

    Date dateSupplier() {
        return new Date();
    }

    private Future<Void> reconcile(ReconciliationState reconcileState) {
        log.traceEntry(() -> reconcileState);
        customImageCount =  0;

        return log.traceExit(reconcileState.validateCustomResource()
                .compose(state -> state.createCommonServicesOperandRequest())
//                .compose(state -> state.waitForCommonServicesOperandRequest())
                .compose(state -> state.getCommonServices())
                .compose(state -> state.getCommonServicesClusterCert())
                .compose(state -> state.createCloudPakClusterCertSecret())
                .compose(state -> state.createCp4iServicesBinding())
                .compose(state -> state.waitForCp4iServicesBindingStatus())
                .compose(state -> state.createKafkaCustomResource())
                .compose(state -> state.waitForKafkaStatus())
                .compose(state -> state.createKafkaNetworkPolicyExtension())
                .compose(state -> state.createReplicatorUsers()) //needs to be before createReplicator and createAdminAPI
                .compose(state -> state.createInternalKafkaUser())
                .compose(state -> state.waitForKafkaUserStatus())
                .compose(state -> state.createMessageAuthenticationSecret())
                .compose(state -> state.createRestProducer(this::dateSupplier))
                .compose(state -> state.createReplicatorSecret())
                .compose(state -> state.createAdminApi(this::dateSupplier))
                .compose(state -> state.createSchemaRegistry(this::dateSupplier))
                .compose(state -> state.createAdminUI())
                .compose(state -> state.createCollector(this::dateSupplier))
                .compose(state -> state.createOAuthClient())
                .onSuccess(state -> state.finalStatusUpdate())
                .onFailure(thr -> reconcileState.recordFailure(thr))
                .map(t -> null));
    }

    @Override
    protected Future<Boolean> delete(Reconciliation reconciliation) {
        log.traceEntry(() -> reconciliation);
        return log.traceExit(Future.succeededFuture(Boolean.FALSE));
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
        CommonServices commonServices = null;
        String cloudPakHeaderURL = "";
        String kafkaPrincipal;
        boolean cp4iPresent = false;

        ReconciliationState(Reconciliation reconciliation, EventStreams instance, EventStreamsOperatorConfig.ImageLookup imageConfig) {
            log.traceEntry(() -> reconciliation, () -> instance, () -> imageConfig);
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
            log.traceExit();
        }

        // there are several checks, but keeping them all in one place is helpful, so overriding the checkstyle warning
        @SuppressWarnings("checkstyle:NPathComplexity")
        Future<ReconciliationState> validateCustomResource() {
            log.traceEntry();
            PhaseState phase = Optional.ofNullable(status.getPhase()).orElse(PhaseState.PENDING);

            AtomicBoolean isValidCR = new AtomicBoolean(true);
            List<StatusCondition> conditions = new ArrayList<>();
            conditions.addAll(new LicenseValidation().validateCr(instance));
            conditions.addAll(new NameValidation().validateCr(instance));
            conditions.addAll(new VersionValidation().validateCr(instance));
            conditions.addAll(new EndpointValidation().validateCr(instance));
            conditions.addAll(GeoReplicatorSourceUsersModel.validateCr(instance));
            conditions.addAll(new AuthenticationValidation().validateCr(instance));
            conditions.addAll(new GeneralValidation().validateCr(instance));
            conditions.addAll(new GeneralSecurityValidation().validateCr(instance));
            conditions.addAll(new UnknownPropertyValidation().validateCr(instance));

            conditions.forEach(condition -> {
                addToConditions(condition.toCondition());
                if (condition.getType().equals(ConditionType.ERROR)) {
                    isValidCR.set(false);
                }
            });

            if (isValidCR.get()) {
                addCondition(previousConditions
                        .stream()
                        // restore any previous readiness condition if this was set, so
                        // that timestamps remain consistent
                        .filter(c -> c.getType().equals(PhaseState.PENDING.toValue()))
                        .findFirst()
                        // otherwise set a new condition saying that the reconcile loop is running
                        .orElse(StatusCondition.createPendingCondition(EVENTSTREAMS_CREATING_REASON, "Event Streams is being deployed.").toCondition()));
            } else {
                phase = PhaseState.FAILED;
            }

            EventStreamsStatus statusSubresource = status.withPhase(phase).build();
            instance.setStatus(statusSubresource);

            // Update if we need to notify the user of an error, otherwise
            //  on the first run only, otherwise the user will see status
            //  warning conditions flicker in and out of the list
            if (!isValidCR.get() || previousConditions.isEmpty()) {
                updateStatus(statusSubresource);
            }

            if (isValidCR.get()) {
                return log.traceExit(Future.succeededFuture(this));
            } else {
                // we don't want the reconcile loop to continue any further if the CR is not valid
                log.debug("Invalid Event Streams specification: further details in the status conditions");
                return log.traceExit(Future.failedFuture("Invalid Event Streams specification: further details in the status conditions"));
            }
        }

        Future<ReconciliationState> createCommonServicesOperandRequest() {
            log.traceEntry();
            OperandRequestModel operandRequestModel = new OperandRequestModel(instance);
            return log.traceExit(
                    operandRequestResourceOperator.reconcile(namespace,
                        operandRequestModel.operandRequestName(instance.getMetadata().getName()),
                        operandRequestModel.getOperandRequest())
                    .map(rs -> this));
        }

        Future<ReconciliationState> getCommonServices() {
            log.traceEntry();
            return log.traceExit(
                configMapOperator.getAsync(operatorNamespace, CommonServices.INGRESS_CM_NAME)
                .compose(ingressCM -> ingressCM == null ?
                    Future.failedFuture("ConfigMap for Common Services Management Ingress missing.") :
                    Future.succeededFuture(ingressCM))
                .onSuccess(ingressCM -> {
                    Map<String, String> ingressConfigMapData = ingressCM.getData();
                    commonServices = new CommonServices(instance.getMetadata().getName(), ingressConfigMapData);
                })
                .onFailure(cause -> {
                    log.atError().withThrowable(cause).log("Failed to get Common Services Management Ingress ConfigMap");
                    updateStatusWithCommonServicesMissing();
                })
                .map(v -> this)
            );
        }

        Future<ReconciliationState> getCommonServicesClusterCert() {
            log.traceEntry();
            // get common services info for prometheus metrics
            return log.traceExit(secretOperator.getAsync(operatorNamespace, CommonServices.CA_CERT_SECRET_NAME)
                .compose(clusterCert -> clusterCert == null ?
                    Future.failedFuture("Secret for Common Services Management Ingress missing.") :
                    Future.succeededFuture(clusterCert))
                .compose(clusterCert -> {
                    try {
                        String clusterCaCert = clusterCert.getData().get("ca.crt");
                        byte[] clusterCaCertArray = Base64.getDecoder().decode(clusterCaCert);
                        commonServices.setCaCerts(clusterCaCert, new String(clusterCaCertArray, StandardCharsets.US_ASCII));
                        return Future.succeededFuture();
                    } catch (IllegalArgumentException exception) {
                        log.atError().withThrowable(exception).log("Unable to decode CA certificate");
                        return Future.failedFuture(exception);
                    }
                })
                .onFailure(cause -> {
                    log.atError().withThrowable(cause).log("Failed to get Common Services Management Ingress CA Secret");
                    updateStatusWithCommonServicesMissing();
                })
                .map(v -> this)
            );
        }

        Future<ReconciliationState> updateStatusWithCommonServicesMissing() {
            log.traceEntry();

            String failureMessage = "Common Services is required by Event Streams, but the Event Streams Operator could not find the Common Services CA certificate. "
                    + "Contact IBM Support for assistance in diagnosing the cause.";
            addToConditions(StatusCondition.createErrorCondition(COMMON_SERVICES_NOT_FOUND_REASON, failureMessage).toCondition());

            EventStreamsStatus statusSubresource = status.withPhase(PhaseState.FAILED).build();
            instance.setStatus(statusSubresource);

            return log.traceExit(
                updateStatus(statusSubresource)
                    .onFailure(cause -> log.atError().withThrowable(cause).log("Failed to update status with Common Services resources missing"))
            );
        }

        Future<ReconciliationState> createCloudPakClusterCertSecret() {
            log.traceEntry();
            Promise<Void> clusterCaSecretPromise = Promise.promise();
            ClusterSecretsModel clusterSecrets = new ClusterSecretsModel(instance, secretOperator);
            // get common services info for prometheus metrics
            String clusterCert = commonServices.getEncodedCaCert();
            if (clusterCert != null) {
                clusterSecrets.createIBMCloudCASecret(clusterCert)
                    .onSuccess(ar -> clusterCaSecretPromise.complete())
                    .onFailure(clusterCaSecretPromise::fail);
            } else {
                String failureMessage = "Common Services is required by Event Streams, but the Event Streams Operator could not find the Common Services CA certificate. "
                    + "Contact IBM Support for assistance in diagnosing the cause.";
                addToConditions(StatusCondition.createErrorCondition(COMMON_SERVICES_CERTIFICATE_NOT_FOUND_REASON, failureMessage).toCondition());

                EventStreamsStatus statusSubresource = status.withPhase(PhaseState.FAILED).build();
                instance.setStatus(statusSubresource);

                updateStatus(statusSubresource).onComplete(f -> {
                    log.info("Encoded ICP CA Cert not in ICPClusterData: {}", f.succeeded());
                    clusterCaSecretPromise.fail("Encoded ICP CA Cert not in ICPClusterData");
                });
            }

            return log.traceExit(clusterCaSecretPromise.future().map(v -> this));
        }


        Future<ReconciliationState> createCp4iServicesBinding() {
            log.traceEntry();
            Boolean cp4iBindingCrdPresent = Optional.ofNullable(client.customResourceDefinitions().withName(Cp4iServicesBinding.CRD_NAME).get())
                    .isPresent();
            if (!cp4iBindingCrdPresent) {
                cp4iPresent = false;
                log.info("CP4I Services Binding CRD is not present, binding will not be created");
                return log.traceExit(Future.succeededFuture(this));
            }

            Cp4iServicesBindingModel cp4iServicesBindingModel = new Cp4iServicesBindingModel(instance);
            Cp4iServicesBinding cp4iServicesBinding = cp4iServicesBindingModel.getCp4iServicesBinding();
            String cp4iServicesBindingName = cp4iServicesBinding.getMetadata().getName();

            Promise<ReconciliationState> createCp4iServicesBinding = Promise.promise();
            cp4iResourceOperator.reconcile(namespace, cp4iServicesBindingName, cp4iServicesBinding)
                    .onComplete(res -> {
                        if (res.succeeded()) {
                            log.debug("Successfully created CP4I Services Binding");
                            cp4iPresent = true;
                        } else {
                            log.error("Failed to create CP4I Services Binding: {}", res.cause());
                            cp4iPresent = false;
                        }
                        createCp4iServicesBinding.complete(this);
                    });
            return log.traceExit(createCp4iServicesBinding.future());
        }

        Future<ReconciliationState> waitForCp4iServicesBindingStatus() {
            log.traceEntry();
            if (!cp4iPresent) {
                log.debug("CP4I is not present, no longer waiting for CP4I Services Binding Status");
                return log.traceExit(Future.succeededFuture(this));
            }

            String cp4iInstanceName = Cp4iServicesBindingModel.getCp4iInstanceName(instance.getMetadata().getName());

            Promise<ReconciliationState> waitForCp4iServicesBindingStatus = Promise.promise();
            cp4iResourceOperator.waitForCp4iServicesBindingStatusAndMaybeGetUrl(namespace, cp4iInstanceName, defaultPollIntervalMs, 10000)
                .onComplete(res -> {
                    if (res.succeeded()) {
                        String cp4iHeaderUrl = cp4iResourceOperator.getCp4iHeaderUrl(namespace, cp4iInstanceName).orElseGet(() -> {
                            log.warn("{}: No header URL present in CP4I binding {}", reconciliation, cp4iInstanceName);
                            return "";
                        });
                        log.debug("Putting the header URL " + cp4iHeaderUrl + " into the Admin UI");
                        cloudPakHeaderURL = cp4iHeaderUrl;
                    } else {
                        log.debug("Putting the header URL (empty string) into the Admin UI");
                        cloudPakHeaderURL = "";
                    }
                    waitForCp4iServicesBindingStatus.complete(this);
                });
            return log.traceExit(waitForCp4iServicesBindingStatus.future());
        }

        Future<ReconciliationState> createKafkaCustomResource() {
            log.traceEntry();
            EventStreamsKafkaModel kafka = new EventStreamsKafkaModel(instance);

            List<Future> kafkaFutures = new ArrayList<>();
            kafkaFutures.add(toFuture(() -> Crds.kafkaOperation(client)
                    .inNamespace(namespace)
                    .createOrReplace(kafka.getKafka())));

            return log.traceExit(CompositeFuture.join(kafkaFutures)
                    .map(this));
        }

        Future<ReconciliationState> waitForKafkaStatus() {
            log.traceEntry();
            String kafkaInstanceName = EventStreamsKafkaModel.getKafkaInstanceName(instance.getMetadata().getName());

            return log.traceExit(esResourceOperator.kafkaCRHasStoppedDeploying(namespace, kafkaInstanceName, defaultPollIntervalMs, kafkaStatusReadyTimeoutMs)
                    .compose(v -> {
                        log.debug("Retrieve Kafka instances in namespace: {}", namespace);
                        Optional<Kafka> kafkaInstance = esResourceOperator.getKafkaInstance(namespace, kafkaInstanceName);
                        if (kafkaInstance.isPresent()) {
                            log.debug("Found Kafka instance with name: {}", kafkaInstance.get().getMetadata().getName());
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
                            return log.traceExit(Future.failedFuture("Failed to retrieve kafkaInstance"));
                        }
                        return log.traceExit(Future.succeededFuture(this));
                    }));
        }

        Future<ReconciliationState> createKafkaNetworkPolicyExtension() {
            log.traceEntry();
            KafkaNetworkPolicyExtensionModel kafkaNetworkPolicyExtensionModel = new KafkaNetworkPolicyExtensionModel(instance);
            return log.traceExit(networkPolicyOperator
                .reconcile(namespace, kafkaNetworkPolicyExtensionModel.getDefaultResourceName(), kafkaNetworkPolicyExtensionModel.getNetworkPolicy())
                .map(v -> this));
        }

        Future<ReconciliationState> createReplicatorUsers() {
            log.traceEntry();
            GeoReplicatorSourceUsersModel geoReplicatorSourceUsersModel = new GeoReplicatorSourceUsersModel(instance);
            return log.traceExit(kafkaUserOperator.reconcile(namespace,
                    geoReplicatorSourceUsersModel.getSourceConnectorKafkaUserName(),
                    geoReplicatorSourceUsersModel.getSourceConnectorKafkaUser()).map(v -> this));
        }

        Future<ReconciliationState> createReplicatorSecret() {
            log.traceEntry();
            GeoReplicatorSecretModel geoReplicatorSecretModel = new GeoReplicatorSecretModel(instance);
            String secretName = geoReplicatorSecretModel.getSecretName();

            return log.traceExit(secretOperator.getAsync(namespace, secretName)
                    .compose(secret -> {
                        // Secret should only be created once
                        if (secret == null) {
                            log.debug("Creating replicator secret {} as not found", secretName);
                            return secretOperator.reconcile(namespace, secretName, geoReplicatorSecretModel.getSecret());
                        }
                        return Future.succeededFuture(ReconcileResult.noop(secret));
                    }).map(res -> this));
        }

        Future<ReconciliationState> createInternalKafkaUser() {
            log.traceEntry();
            InternalKafkaUserModel internalKafkaUserModel = new InternalKafkaUserModel(instance);
            return log.traceExit(kafkaUserOperator.reconcile(namespace, internalKafkaUserModel.getInternalKafkaUserName(), internalKafkaUserModel.getKafkaUser())
                    .map(this));
        }

        Future<ReconciliationState> waitForKafkaUserStatus() {
            log.traceEntry();
            String internalKafkaUsername = InternalKafkaUserModel.getInternalKafkaUserName(instance.getMetadata().getName());

            return log.traceExit(kafkaUserOperator.kafkaUserHasStoppedDeploying(namespace, internalKafkaUsername, defaultPollIntervalMs, kafkaStatusReadyTimeoutMs)
                .compose(v -> {
                    log.debug("Retrieve Kafka user instances in namespace: {}", namespace);
                    Optional<KafkaUser> kafkaUser = kafkaUserOperator.getKafkaUser(namespace, internalKafkaUsername);
                    if (kafkaUser.isPresent()) {
                        log.debug("Found Kafka user instance with name: {}", kafkaUser.get().getMetadata().getName());
                        KafkaUserStatus kafkaUserStatus = kafkaUser.get().getStatus();
                        log.debug("{}: kafkaUserStatus: {}", reconciliation, kafkaUserStatus);

                        kafkaPrincipal = kafkaUserStatus.getUsername();
                        List<Condition> conditions = Optional.ofNullable(kafkaUserStatus.getConditions()).orElse(Collections.emptyList());
                        conditions.stream()
                            // ignore Kafka user readiness conditions as we want to set our own
                            .filter(c -> !"Ready".equals(c.getType()))
                            // copy any warnings or messages from the Kafka user into the ES CR
                            .forEach(this::addToConditions);

                        if (conditions.stream().noneMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()))) {
                            return Future.failedFuture("Kafka user is not ready");
                        }
                    } else {
                        return log.traceExit(Future.failedFuture("Failed to retrieve kafka user"));
                    }
                    return log.traceExit(Future.succeededFuture(this));
                }));
        }

        Future<ReconciliationState> createRestProducer(Supplier<Date> dateSupplier) {
            log.traceEntry(() -> dateSupplier);
            List<Future> restProducerFutures = new ArrayList<>();
            RestProducerModel restProducer = new RestProducerModel(instance, imageConfig, status.getKafkaListeners(), commonServices);
            if (restProducer.getCustomImage()) {
                customImageCount++;
            }
            restProducerFutures.add(serviceAccountOperator.reconcile(namespace, restProducer.getDefaultResourceName(), restProducer.getServiceAccount()));
            for (EndpointServiceType type : EndpointServiceType.values()) {
                restProducerFutures.add(serviceOperator.reconcile(namespace, restProducer.getServiceName(type), restProducer.getSecurityService(type)));
            }
            restProducerFutures.add(networkPolicyOperator.reconcile(namespace, restProducer.getDefaultResourceName(), restProducer.getNetworkPolicy()));
            if (RestProducerModel.isRestProducerEnabled(instance)) {
                restProducerFutures.add(checkPullSecrets(restProducer));
            }
            return log.traceExit(CompositeFuture.join(restProducerFutures)
                .compose(v -> reconcileRoutes(restProducer, restProducer.getRoutes()))
                .compose(routesMap -> {
                    for (Route restProducerRoute : routesMap.values()) {
                        addEndpointToStatus(EventStreamsEndpoint.REST_PRODUCER_KEY, restProducerRoute);
                    }
                    return Future.succeededFuture(routesMap);
                })
                .compose(routesMap -> reconcileCerts(restProducer, routesMap, dateSupplier))
                .compose(secretResult -> {
                    String certGenerationID = null;
                    if (secretResult.resourceOpt().isPresent()) {
                        certGenerationID = secretResult.resource().getMetadata().getResourceVersion();
                    }
                    return deploymentOperator.reconcile(namespace, restProducer.getDefaultResourceName(), restProducer.getDeployment(certGenerationID));
                }).map(this));
        }

        Future<ReconciliationState> createAdminApi(Supplier<Date> dateSupplier) {
            log.traceEntry(() -> dateSupplier);
            List<Future> adminApiFutures = new ArrayList<>();
            AdminApiModel adminApi = new AdminApiModel(instance, imageConfig, status.getKafkaListeners(), commonServices, kafkaPrincipal);
            if (adminApi.getCustomImage()) {
                customImageCount++;
            }
            adminApiFutures.add(serviceAccountOperator.reconcile(namespace, adminApi.getDefaultResourceName(), adminApi.getServiceAccount()));

            for (EndpointServiceType type : EndpointServiceType.values()) {
                adminApiFutures.add(serviceOperator.reconcile(namespace, adminApi.getServiceName(type), adminApi.getSecurityService(type)));
            }

            adminApiFutures.add(networkPolicyOperator.createOrUpdate(adminApi.getNetworkPolicy()));
            adminApiFutures.add(roleBindingOperator.createOrUpdate(adminApi.getRoleBinding()));
            adminApiFutures.add(checkPullSecrets(adminApi));
            return log.traceExit(CompositeFuture.join(adminApiFutures)
                    .compose(v -> reconcileRoutes(adminApi, adminApi.getRoutes()))
                    .compose(routesMap -> {
                        for (Route adminRoute : routesMap.values()) {
                            addEndpointToStatus(EventStreamsEndpoint.ADMIN_KEY, adminRoute);
                        }
                        return Future.succeededFuture(routesMap);
                    })
                    .compose(routesMap -> reconcileCerts(adminApi, routesMap, dateSupplier))
                    .compose(secretResult -> {
                        String certGenerationID = secretResult.resourceOpt()
                            .map(Secret::getMetadata)
                            .map(ObjectMeta::getResourceVersion)
                            .orElse(null);
                        return deploymentOperator.reconcile(namespace, adminApi.getDefaultResourceName(), adminApi.getDeployment(certGenerationID));
                    })
                    .map(this));
        }

        Future<ReconciliationState> createSchemaRegistry(Supplier<Date> dateSupplier) {
            log.traceEntry(() -> dateSupplier);
            List<Future> schemaRegistryFutures = new ArrayList<>();
            SchemaRegistryModel schemaRegistry = new SchemaRegistryModel(instance, imageConfig, status.getKafkaListeners(), commonServices, kafkaPrincipal);
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
            if (SchemaRegistryModel.isSchemaRegistryEnabled(instance)) {
                schemaRegistryFutures.add(checkPullSecrets(schemaRegistry));
            }

            return log.traceExit(CompositeFuture.join(schemaRegistryFutures)
                    .compose(v -> reconcileRoutes(schemaRegistry, schemaRegistry.getRoutes()))
                    .compose(routesMap -> {
                        for (Route schemaRoute : routesMap.values()) {
                            addEndpointToStatus(EventStreamsEndpoint.SCHEMA_REGISTRY_KEY, schemaRoute);
                        }
                        return Future.succeededFuture(routesMap);
                    })
                    .compose(routesMap -> reconcileCerts(schemaRegistry, routesMap, dateSupplier))
                    .compose(secretResult -> {
                        String certGenerationID = null;
                        if (secretResult.resourceOpt().isPresent()) {
                            certGenerationID = secretResult.resource().getMetadata().getResourceVersion();
                        }
                        return deploymentOperator.reconcile(namespace, schemaRegistry.getDefaultResourceName(), schemaRegistry.generateDeployment(certGenerationID, instance));
                    }).map(this));
        }

        Future<ReconciliationState> createMessageAuthenticationSecret() {
            log.traceEntry();
            MessageAuthenticationModel messageAuthenticationModel = new MessageAuthenticationModel(instance);
            String secretName = messageAuthenticationModel.getSecretName(instance.getMetadata().getName());
            Future<ReconcileResult<Secret>> maybeReconcileSecret = secretOperator.getAsync(namespace, secretName)
                .compose(secret -> {
                    // If secret does not exist or has been modified to be invalid, create/patch it
                    // otherwise do nothing
                    if (!messageAuthenticationModel.isValidHmacSecret(secret)) {
                        return secretOperator.reconcile(namespace,
                                secretName,
                                messageAuthenticationModel.getSecret());
                    }
                    return Future.succeededFuture();
                });

            return log.traceExit(maybeReconcileSecret.map(v -> this));

        }

        Future<ReconciliationState> createAdminUI() {
            log.traceEntry();
            List<Future> adminUIFutures = new ArrayList<>();
            AdminUIModel ui = new AdminUIModel(instance, imageConfig, pfa.hasRoutes(), commonServices, cloudPakHeaderURL);
            if (ui.getCustomImage()) {
                customImageCount++;
            }
            adminUIFutures.add(serviceAccountOperator.reconcile(namespace, ui.getDefaultResourceName(), ui.getServiceAccount()));
            adminUIFutures.add(deploymentOperator.reconcile(namespace, ui.getDefaultResourceName(), ui.getDeployment()));
            adminUIFutures.add(roleBindingOperator.reconcile(namespace, ui.getDefaultResourceName(), ui.getRoleBinding()));
            adminUIFutures.add(serviceOperator.reconcile(namespace, ui.getDefaultResourceName(), ui.getService()));
            adminUIFutures.add(networkPolicyOperator.reconcile(namespace, ui.getDefaultResourceName(), ui.getNetworkPolicy()));
            if (AdminUIModel.isUIEnabled(instance)) {
                adminUIFutures.add(checkPullSecrets(ui));
            }

            if (pfa.hasRoutes() && routeOperator != null) {
                adminUIFutures.add(routeOperator.reconcile(namespace, ui.getRouteName(), ui.getRoute()).compose(route -> {
                    if (route.resourceOpt().isPresent()) {
                        String uiRouteHost = route.resource().getSpec().getHost();
                        String uiRouteUri = "https://" + uiRouteHost;
                        log.info("uiRouteHost: {} uiRouteUri: {}", uiRouteHost, uiRouteUri);
                        status.addToRoutes(AdminUIModel.COMPONENT_NAME, uiRouteHost);

                        if (AdminUIModel.isUIEnabled(instance)) {
                            status.withNewAdminUiUrl(uiRouteUri);
                            updateEndpoints(new EventStreamsEndpoint(EventStreamsEndpoint.UI_KEY,
                                                                     EventStreamsEndpoint.EndpointType.UI,
                                                                     uiRouteUri));
                        }
                    }
                    return log.traceExit(Future.succeededFuture());
                }));
            }
            return log.traceExit(CompositeFuture.join(adminUIFutures)
                    .map(v -> this));
        }

        Future<ReconciliationState> createCollector(Supplier<Date> dateSupplier) {
            log.traceEntry();
            List<Future> collectorFutures = new ArrayList<>();
            CollectorModel collector = new CollectorModel(instance, imageConfig);
            if (collector.getCustomImage()) {
                customImageCount++;
            }
            collectorFutures.add(serviceAccountOperator.reconcile(namespace, collector.getDefaultResourceName(), collector.getServiceAccount()));
            collectorFutures.add(serviceOperator.reconcile(namespace, collector.getServiceName(EndpointServiceType.INTERNAL), collector.getSecurityService(EndpointServiceType.INTERNAL)));
            collectorFutures.add(networkPolicyOperator.reconcile(namespace, collector.getDefaultResourceName(), collector.getNetworkPolicy()));
            if (CollectorModel.isCollectorEnabled(instance)) {
                collectorFutures.add(checkPullSecrets(collector));
            }
            return log.traceExit(CompositeFuture.join(collectorFutures)
                    .compose(res -> reconcileCerts(collector, Collections.emptyMap(), dateSupplier))
                    .compose(secretResult -> {
                        String certGenerationID = null;
                        if (secretResult.resourceOpt().isPresent()) {
                            certGenerationID = secretResult.resource().getMetadata().getResourceVersion();
                        }
                        return deploymentOperator.reconcile(namespace, collector.getDefaultResourceName(), collector.getDeployment(certGenerationID));
                    }).map(this));
        }

        Future<ReconciliationState> createOAuthClient() {
            log.traceEntry();

            String uiRoute = status.getRoutes().get(AdminUIModel.COMPONENT_NAME);
            log.debug("Found route '{}'", uiRoute);

            if (uiRoute != null) {
                ClientModel clientModel = new ClientModel(instance, "https://" + uiRoute);
                Client oidcclient = clientModel.getClient();
                String clientName = oidcclient.getMetadata().getName();

                // Create an operation that can be invoked to retrieve or create the required Custom Resource
                MixedOperation<Client, ClientList, ClientDoneable, Resource<Client, ClientDoneable>> clientcr = client.customResources(
                        com.ibm.eventstreams.api.Crds.getCrd(Client.class),
                        Client.class,
                        ClientList.class,
                        ClientDoneable.class);

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
                    return log.traceExit(createdClient.map(v -> this));

                }
            }

            return log.traceExit(Future.succeededFuture(this));
        }

        Future<ReconciliationState> updateStatus(EventStreamsStatus newStatus) {
            log.traceEntry(() -> newStatus);
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

            return log.traceExit(updateStatusPromise.future());
        }

        Future<ReconciliationState> finalStatusUpdate() {
            log.traceEntry();
            status.withCustomImages(customImageCount > 0);
            status.withPhase(PhaseState.READY);
            status.withConditions(status.getConditions().stream().filter(condition -> !EVENTSTREAMS_CREATING_REASON.equals(condition.getReason())).collect(Collectors.toList()));

            log.info("Updating status");
            EventStreamsStatus esStatus = status.build();
            instance.setStatus(esStatus);
            return log.traceExit(updateStatus(esStatus));
        }

        void recordFailure(Throwable thr) {
            log.traceEntry(() -> thr);
            log.error("Recording reconcile failure", thr);
            addToConditions(StatusCondition.createErrorCondition("DeploymentFailed",
                String.format("An unexpected exception was encountered: %s. More detail can be found in the Event Streams Operator log.", thr.getMessage())).toCondition());
            EventStreamsStatus statusSubresource = status.withPhase(PhaseState.FAILED).build();
            instance.setStatus(statusSubresource);
            updateStatus(statusSubresource);
            log.traceExit();
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
            log.traceEntry(() -> createResource);
            return log.traceExit(Future.future(blockingFuture -> {
                vertx.executeBlocking(blocking -> {
                    try {
                        blocking.complete(createResource.get());
                    } catch (Exception e) {
                        blocking.fail(e);
                    }
                }, blockingFuture);
            }));
        }

        /**
         *
         *
         * @param model
         * @param additionalHosts is a map of route names and their corresponding hosts generated by openshift
         * @param dateSupplier
         * @return
         */
        Future<ReconcileResult<Secret>> reconcileCerts(AbstractSecureEndpointsModel model, Map<String, Route> additionalHosts, Supplier<Date> dateSupplier) {
            log.traceEntry(() -> model, () -> additionalHosts, () -> dateSupplier);
            try {
                boolean regenSecret = false;
                Optional<Secret> certSecret = certificateManager.getSecret(model.getCertificateSecretName());
                for (Endpoint endpoint : model.getTlsEndpoints()) {
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

                return log.traceExit(secretOperator.reconcile(namespace, model.getCertificateSecretName(), model.getCertificateSecretModelSecret()));

            } catch (EventStreamsCertificateException e) {
                log.error("Error whilst doing certificate reconciliation for {}:", model.getComponentName(), e);
                return log.traceExit(Future.failedFuture(e));
            }
        }

        /**
         * Helper method which verifies if the pull secrets that a component is configured
         * with actually exist. If the secrets are missing, a warning is added to the
         * instance status, as an explanation to the user if component(s) fail to start.
         *
         * All of the secrets don't need to exist for an image to be pulled, so this only
         * creates a warning if *all* configured secrets (operator env var, CR-level secret,
         * component-level override) do not exist.
         *
         * Because pull secrets can be configured separately for each individual component,
         * the checking (and creation of warnings) is done on a per-component basis.
         *
         * This is only a warning and not an error state as the images may be pulled from
         * a public registry, or a private registry that does not require authentication.
         */
        Future<Void> checkPullSecrets(AbstractModel component) {
            log.traceEntry(() -> component.getComponentName());
            Promise<Void> checkSecretsPromise = Promise.promise();

            List<Future> getPullSecrets = component.getPullSecrets()
                    .stream()
                    .map(secret -> secretOperator.getAsync(namespace, secret.getName()))
                    .collect(Collectors.toList());

            CompositeFuture.all(getPullSecrets).setHandler(res -> {
                if (res.succeeded()) {
                    boolean allPullSecretsMissing = getPullSecrets.stream()
                            .allMatch(future -> future.result() == null);

                    if (allPullSecretsMissing) {
                        String errorMessage = String.format("The image pull secrets specified for the %s component do not exist.", component.getApplicationName())
                            + "This can prevent images being pulled from the entitled registry. "
                            + "Check your entitlement to see if you have access to Event Streams. "
                            + "You can request an entitlement key from https://myibm.ibm.com/products-services/containerlibrary. "
                            + "Create an image pull secret called 'ibm-entitlement-key' using your entitlement key as the password, cp as the username, and cp.icr.io as the docker server.";
                        addToConditions(StatusCondition.createWarningCondition("MissingPullSecret", errorMessage).toCondition());
                    }

                    checkSecretsPromise.complete();
                } else {
                    checkSecretsPromise.fail(res.cause());
                }
            });
            return log.traceExit(checkSecretsPromise.future());
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
        private boolean updateCertAndKeyInModel(Optional<Secret> certSecret, AbstractSecureEndpointsModel model, Endpoint endpoint, Map<String, Route> additionalHosts, Supplier<Date> dateSupplier) throws EventStreamsCertificateException {
            log.traceEntry(() -> certSecret, () -> model, () -> endpoint, () -> additionalHosts, () -> dateSupplier);
            Route route = additionalHosts.get(model.getRouteName(endpoint.getName()));
            String host = endpoint.isRoute() ? Optional.ofNullable(route).map(Route::getSpec).map(RouteSpec::getHost).orElse("") : "";
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
                    return log.traceExit(hasCertOverridesChanged);
                }
                // The secret hasn't been generated yet so create a new entry with the provided cert and key
                model.setCertAndKey(endpoint.getName(), providedCertAndKey.get());
                log.debug("Finished Updating Cert and Key in Model for {}, secret needs to be generated", model.getComponentName());
                return log.traceExit(true);
            } else if (!certSecret.isPresent() || certificateManager.shouldGenerateOrRenewCertificate(certSecret.get(), endpoint.getName(), dateSupplier, service, hosts, model.getComponentName())) {
                CertAndKey certAndKey = certificateManager.generateCertificateAndKey(service, hosts, model.getComponentName());
                model.setCertAndKey(endpoint.getName(), certAndKey);
                log.debug("Finished Updating Cert and Key in Model for {}, secret needs to be regenerated", model.getComponentName());
                return log.traceExit(true);
            }
            // even if we don't need to regenerate the secret, we need to set the key and cert in case of future reconciliation
            CertAndKey currentCertAndKey = certificateManager.certificateAndKey(certSecret.get(), model.getCertSecretCertID(endpoint.getName()), model.getCertSecretKeyID(endpoint.getName()));
            model.setCertAndKey(endpoint.getName(), currentCertAndKey);
            log.info("Finished Updating Cert and Key in Model for {}, secret does not need to be regenerated", model.getComponentName());
            return log.traceExit(false);
        }

        protected Future<Map<String, Route>> reconcileRoutes(AbstractSecureEndpointsModel model, Map<String, Route> routes) {
            log.traceEntry(() -> model, () -> routes);
            return log.traceExit(deleteUnspecifiedRoutes(model, routes)
                .compose(res -> createRoutes(model, routes)));
        }

        protected Future<Map<String, Route>> createRoutes(AbstractSecureEndpointsModel model, Map<String, Route> routes) {
            log.traceEntry(() -> model, () -> routes);
            if (!pfa.hasRoutes() || routeOperator == null) {
                log.debug("Finished attempting to create Routes for {}, no Routes to create", model.getComponentName());
                return log.traceExit(Future.succeededFuture(Collections.emptyMap()));
            }

            List<Future> routeFutures = routes.entrySet()
                .stream()
                .map(entry -> routeOperator.reconcile(namespace, entry.getKey(), entry.getValue())
                    .compose(routeResult -> {
                        Route route = routeResult
                            .resourceOpt()
                            .orElse(null);
                        String routeHost = Optional.ofNullable(route)
                            .map(Route::getSpec)
                            .map(RouteSpec::getHost)
                            .orElse("");

                        status.addToRoutes(getRouteShortName(model, entry.getKey()), routeHost);
                        return Future.succeededFuture(Collections.singletonMap(entry.getKey(), route));
                    }))
                .collect(Collectors.toList());

            return log.traceExit(CompositeFuture.join(routeFutures)
                .compose(ar -> {
                    List<Map<String, Route>> allRoutesMaps = ar.list();
                    Map<String, Route> routesMap = allRoutesMaps
                        .stream()
                        .reduce(new HashMap<>(), (map1, map2) -> {
                            map1.putAll(map2);
                            return map1;
                        });
                    return Future.succeededFuture(routesMap);
                }));
        }

        /**
         * Method checks the status for the existing routes and determines if no longer needed they should be deleted
         * @param model the model to check its endpoints
         * @param routes the map of all routes
         * @return A list of futures of whether the routes have been deleted.
         */
        Future<List<ReconcileResult<Route>>> deleteUnspecifiedRoutes(AbstractSecureEndpointsModel model, Map<String, Route> routes) {
            log.traceEntry(() -> model, () -> routes);
            if (!pfa.hasRoutes() || routeOperator == null) {
                log.debug("Finished Attempting to delete unspecified Routes for {}, no routes to delete", model.getComponentName());
                return log.traceExit(Future.succeededFuture(Collections.emptyList()));
            }

            if (status.getRoutes() == null) {
                log.debug("Finished Attempting to delete unspecified Routes for {}, no routes to delete", model.getComponentName());
                return log.traceExit(Future.succeededFuture(Collections.emptyList()));
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

            return log.traceExit(CompositeFuture.join(routeDeletionFutures)
                .compose(ar -> Future.succeededFuture(ar.list())));
        }

        private String getRouteShortName(AbstractSecureEndpointsModel model, String routeName) {
            log.traceEntry(() -> model, () -> routeName);
            return log.traceExit(routeName.replaceFirst(model.getResourcePrefix() + "-", ""));
        }

        private String getRouteLongName(AbstractSecureEndpointsModel model, String routeName) {
            log.traceEntry(() -> model, () -> routeName);
            return log.traceExit(model.getResourcePrefix() + "-" + routeName);
        }

        protected void updateEndpoints(EventStreamsEndpoint newEndpoint) {
            log.traceEntry(() -> newEndpoint);
            // replace any existing endpoint with the same name
            status.removeMatchingFromEndpoints(item -> newEndpoint.getName().equals(item.getName()));
            status.addToEndpoints(newEndpoint);
            log.traceExit();
        }

        /**
         * Adds the provided condition to the current status.
         *
         * This will check to see if a matching condition was previously seen, and if so
         * that will be reused (allowing for timestamps to remain consistent). If not,
         * the provided condition will be added as-is.
         *
         * @param condition StatusCondition to add to the status conditions list.
         */
        private void addToConditions(Condition condition) {
            log.traceEntry(() -> condition);
            // restore the equivalent previous condition if found, otherwise
            //  add the new condition to the status
            addCondition(previousConditions
                    .stream()
                    .filter(c -> condition.getReason() != null &&
                                 condition.getReason().equals(c.getReason()) &&
                                 condition.getMessage().equals(c.getMessage()))
                    .findFirst()
                    .orElse(condition));
            log.traceExit();
        }

        /**
         * Adds a condition to the status, and then sorts the list to ensure
         * that they are maintained in order of timestamp.
         */
        private void addCondition(Condition condition) {
            log.traceEntry(() -> condition);
            status.addToConditions(condition);
            status.getConditions().sort(Comparator.comparing(Condition::getLastTransitionTime)
                                            .thenComparing(Condition::getType, Comparator.reverseOrder()));
            log.traceExit();
        }

        private void addEndpointToStatus(String key, Route route) {
            log.traceEntry();
            String routeHost = Optional.ofNullable(route)
                .map(Route::getSpec)
                .map(RouteSpec::getHost)
                .orElse("");
            boolean isTls = Optional.of(route)
                .map(Route::getSpec)
                .map(RouteSpec::getTls)
                .isPresent();
            String protocol = isTls ? "https://" : "http://";
            String routeUri = protocol + routeHost;
            log.info("{}: Host: {} Uri: {}", key, routeHost, routeUri);
            updateEndpoints(new EventStreamsEndpoint(key,
                EventStreamsEndpoint.EndpointType.API,
                routeUri));
            log.traceExit();
        }
    }
}
