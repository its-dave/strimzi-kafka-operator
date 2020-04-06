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
package com.ibm.eventstreams.api.model;

import com.ibm.eventstreams.Main;
import com.ibm.eventstreams.api.DefaultResourceRequirements;
import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.spec.ComponentSpec;
import com.ibm.eventstreams.api.spec.ComponentTemplate;
import com.ibm.eventstreams.api.spec.ContainerSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.ImagesSpec;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HTTPHeaderBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.fabric8.kubernetes.api.model.rbac.SubjectBuilder;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.Logging;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.template.PodTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdminApiModel extends AbstractSecureEndpointsModel {

    public static final String COMPONENT_NAME = "admin-api";
    public static final String ADMIN_API_CONTAINER_NAME = "admin-api";
    public static final int DEFAULT_REPLICAS = 1;
    public static final String DEFAULT_IBMCOM_IMAGE = "ibmcom/admin-api:latest";

    public static final String CLUSTER_CA_VOLUME_MOUNT_NAME = "cluster-ca";
    public static final String CERTS_VOLUME_MOUNT_NAME = "certs";
    public static final String IBMCLOUD_CA_VOLUME_MOUNT_NAME = "ibmcloud";
    public static final String KAFKA_CONFIGMAP_MOUNT_NAME = "kafka-cm";
    public static final String CLIENT_CA_VOLUME_MOUNT_NAME = "client-ca";

    public static final String ADMIN_CLUSTERROLE_NAME = "eventstreams-admin-clusterrole";

    public static final String CERTIFICATE_PATH = "/certs";
    public static final String KAFKA_USER_CERTIFICATE_PATH = CERTIFICATE_PATH + File.separator + "p2p";
    public static final String CLUSTER_CERTIFICATE_PATH = CERTIFICATE_PATH + File.separator + "cluster";
    public static final String CLIENT_CA_CERTIFICATE_PATH = CERTIFICATE_PATH + File.separator + "client";
    public static final String IBMCLOUD_CA_CERTIFICATE_PATH = CERTIFICATE_PATH + File.separator + "ibmcloud";

    private static final String CLIENT_ID_KEY = "CLIENT_ID";
    private static final String CLIENT_SECRET_KEY = "CLIENT_SECRET";

    private String traceString = "info";
    private final String prometheusHost;
    private final String prometheusPort;
    private final String clusterCaCert;
    private final String icpClusterName;
    private final String iamServerURL;
    private final String ibmcloudCASecretName;

    private final Deployment deployment;
    private final ServiceAccount serviceAccount;
    private final NetworkPolicy networkPolicy;
    private final RoleBinding roleBinding;

    private static final Logger log = LogManager.getLogger(AdminApiModel.class.getName());
    private List<ListenerStatus> kafkaListeners;

    /**
     * This class is used to model all the kube resources required for correct deployment of the Admin Api
     * @param instance
     * @param imageConfig
     * @param kafkaListeners
     * @param icpClusterData
     */
    public AdminApiModel(EventStreams instance,
                         EventStreamsOperatorConfig.ImageLookup imageConfig,
                         List<ListenerStatus> kafkaListeners,
                         Map<String, String> icpClusterData) {
        super(instance, instance.getSpec().getAdminApi(), COMPONENT_NAME);
        this.kafkaListeners = kafkaListeners != null ? new ArrayList<>(kafkaListeners) : new ArrayList<>();

        this.prometheusHost = icpClusterData.getOrDefault("cluster_address", "null");
        this.prometheusPort = icpClusterData.getOrDefault("cluster_router_https_port", "null");
        this.clusterCaCert = icpClusterData.getOrDefault("icp_public_cacert", "null");
        this.icpClusterName = icpClusterData.getOrDefault("cluster_name", "null");
        this.iamServerURL = icpClusterData.getOrDefault("cluster_endpoint", "null");

        ibmcloudCASecretName = ClusterSecretsModel.getIBMCloudSecretName(getInstanceName());

        Optional<ComponentSpec> adminApiSpec = Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getAdminApi);

        setOwnerReference(instance);
        setReplicas(adminApiSpec.map(ComponentSpec::getReplicas).orElse(DEFAULT_REPLICAS));
        setResourceRequirements(adminApiSpec.map(ContainerSpec::getResources).orElseGet(ResourceRequirements::new));
        setEnvVars(adminApiSpec.map(ContainerSpec::getEnvVars).orElseGet(ArrayList::new));
        setPodTemplate(adminApiSpec.map(ComponentSpec::getTemplate)
                            .map(ComponentTemplate::getPod)
                            .orElseGet(PodTemplate::new));
        setTlsVersion(Optional.ofNullable(instance.getSpec())
                            .map(EventStreamsSpec::getSecurity)
                            .map(SecuritySpec::getInternalTls)
                            .orElse(DEFAULT_INTERNAL_TLS));
        setGlobalPullSecrets(Optional.ofNullable(instance.getSpec())
                                .map(EventStreamsSpec::getImages)
                                .map(ImagesSpec::getPullSecrets)
                                .orElseGet(imageConfig::getPullSecrets));
        setImage(
            firstDefinedImage(
                    DEFAULT_IBMCOM_IMAGE,
                adminApiSpec.map(ComponentSpec::getImage),
                imageConfig.getAdminApiImage()));
        setCustomImage(DEFAULT_IBMCOM_IMAGE, imageConfig.getAdminApiImage());
        setLivenessProbe(adminApiSpec.map(ComponentSpec::getLivenessProbe)
                .orElseGet(io.strimzi.api.kafka.model.Probe::new));
        setReadinessProbe(adminApiSpec.map(ComponentSpec::getReadinessProbe)
                .orElseGet(io.strimzi.api.kafka.model.Probe::new));
        setTraceString(adminApiSpec.map(ComponentSpec::getLogging).orElse(null));

        deployment = createDeployment(getContainers(instance), getVolumes(instance));
        serviceAccount = createServiceAccount();

        roleBinding = createAdminApiRoleBinding();
        createService(EndpointServiceType.INTERNAL);
        createService(EndpointServiceType.ROUTE);
        createService(EndpointServiceType.NODE_PORT);
        routes = createRoutesFromEndpoints();

        networkPolicy = createNetworkPolicy();
    }

    /**
     *
     * @param instance
     * @return A list of volumes to be put into the deployment
     */
    private List<Volume> getVolumes(EventStreams instance) {

        List<Volume> volumes = new ArrayList<>();
        volumes.add(new VolumeBuilder()
            .withNewName(ReplicatorModel.REPLICATOR_SECRET_NAME)
            .withNewSecret()
                .withNewSecretName(getResourcePrefix() + "-" + ReplicatorModel.REPLICATOR_SECRET_NAME)
                .addNewItem().withNewKey(ReplicatorModel.REPLICATOR_TARGET_CLUSTERS_SECRET_KEY_NAME)
                .withNewPath(ReplicatorModel.REPLICATOR_TARGET_CLUSTERS_SECRET_KEY_NAME)
            .endItem()
            .endSecret()
            .build());

        if (isReplicatorExternalClientAuthForConnectEnabled(instance)) {
            volumes.add(new VolumeBuilder()
                    .withNewName(ReplicatorUsersModel.SOURCE_CONNECTOR_KAFKA_USER_NAME)
                    .withNewSecret()
                    .withNewSecretName(ReplicatorUsersModel.getSourceConnectorKafkaUserName(getInstanceName())) //mount everything in the secret into this volume
                    .endSecret()
                    .build());
        }

        if (isReplicatorInternalClientAuthForConnectEnabled(instance) && ReplicatorModel.isReplicatorEnabled(instance)) {
            volumes.add(new VolumeBuilder()
                    .withNewName(ReplicatorUsersModel.CONNECT_KAFKA_USER_NAME)
                    .withNewSecret()
                    .withNewSecretName(ReplicatorUsersModel.getConnectKafkaUserName(getInstanceName())) //mount everything in the secret into this volume
                    .endSecret()
                    .build());
            volumes.add(new VolumeBuilder()
                    .withNewName(ReplicatorUsersModel.TARGET_CONNECTOR_KAFKA_USER_NAME)
                    .withNewSecret()
                    .withNewSecretName(ReplicatorUsersModel.getTargetConnectorKafkaUserName(getInstanceName())) //mount everything in the secret into this volume
                    .endSecret()
                    .build());
        }

        volumes.addAll(getSecurityVolumes());

        volumes.add(new VolumeBuilder()
            .withNewName(KAFKA_CONFIGMAP_MOUNT_NAME)
            .withNewConfigMap()
                .withNewName(EventStreamsKafkaModel.getKafkaConfigMapName(getInstanceName()))
            .endConfigMap()
            .build());

        // Add The IAM Specific Volumes.  If we need to build without IAM Support we can put a variable check
        // here.
        volumes.add(new VolumeBuilder()
            .withNewName(IBMCLOUD_CA_VOLUME_MOUNT_NAME)
            .withNewSecret()
            .withNewSecretName(ibmcloudCASecretName)
                .addNewItem().withNewKey(CA_CERT).withNewPath(CA_CERT).endItem()
            .endSecret()
            .build());

        return volumes;
    }

    /**
     *
     * @param instance
     * @return The list of containers in the Admin Api pod
     */
    private List<Container> getContainers(EventStreams instance) {
        return Arrays.asList(getAdminApiContainer(instance));
    }

    /**
     *
     * @param instance
     * @return The Admin Api Container
     */
    private Container getAdminApiContainer(EventStreams instance) {

        List<EnvVar> adminApiDefaultEnvVars = getAdminApiEnvVars(instance);
        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(adminApiDefaultEnvVars);

        ContainerBuilder containerBuilder = new ContainerBuilder()
            .withName(ADMIN_API_CONTAINER_NAME)
            .withImage(getImage())
            .withEnv(envVars)
            .withSecurityContext(getSecurityContext(false))
            .addNewVolumeMount()
                .withNewName(ReplicatorModel.REPLICATOR_SECRET_NAME)
                .withMountPath(ReplicatorModel.REPLICATOR_SECRET_MOUNT_PATH)
                .withNewReadOnly(true)
            .endVolumeMount()
            .addNewVolumeMount()
                .withNewName(KAFKA_CONFIGMAP_MOUNT_NAME)
                .withMountPath("/etc/kafka-cm")
                .withNewReadOnly(true)
            .endVolumeMount()

            .withResources(getResourceRequirements(DefaultResourceRequirements.ADMIN_API))
            .withLivenessProbe(createLivenessProbe())
            .withReadinessProbe(createReadinessProbe());

        //only add the replicator secret volume mounts if client auth enabled
        if (isReplicatorExternalClientAuthForConnectEnabled(instance)) {
            containerBuilder.addNewVolumeMount()
                .withNewName(ReplicatorUsersModel.SOURCE_CONNECTOR_KAFKA_USER_NAME)
                    .withMountPath(ReplicatorModel.SOURCE_CONNECTOR_SECRET_MOUNT_PATH)
                    .withNewReadOnly(true)
                .endVolumeMount();
        }
        if (isReplicatorInternalClientAuthForConnectEnabled(instance) && ReplicatorModel.isReplicatorEnabled(instance)) {
            containerBuilder.addNewVolumeMount()
                .withNewName(ReplicatorUsersModel.CONNECT_KAFKA_USER_NAME)
                .withMountPath(ReplicatorModel.CONNECT_SECRET_MOUNT_PATH)
                .withNewReadOnly(true)
            .endVolumeMount()
            .addNewVolumeMount()
                .withNewName(ReplicatorUsersModel.TARGET_CONNECTOR_KAFKA_USER_NAME)
                .withMountPath(ReplicatorModel.TARGET_CONNECTOR_SECRET_MOUNT_PATH)
                .withNewReadOnly(true)
                .endVolumeMount();
        }

        // Add The IAM Specific Volume mount. If we need to build without IAM Support we can put a variable check
        // here.
        containerBuilder.addNewVolumeMount()
            .withNewName(IBMCLOUD_CA_VOLUME_MOUNT_NAME)
            .withMountPath(IBMCLOUD_CA_CERTIFICATE_PATH)
            .withNewReadOnly(true)
            .endVolumeMount();

        configureSecurityVolumeMounts(containerBuilder);

        return containerBuilder.build();
    }

    /**
     *
     * @param instance
     * @return A list of default EnvVars for the Admin Api container
     */
    private List<EnvVar> getAdminApiEnvVars(EventStreams instance) {

        String internalBootstrap = getInternalKafkaBootstrap(kafkaListeners);
        String runasBootstrap = getRunAsKafkaBootstrap(kafkaListeners);
        String kafkaBootstrapInternalPlainUrl = getInternalPlainKafkaBootstrap(kafkaListeners);
        String kafkaBootstrapInternalTlsUrl = getInternalTlsKafkaBootstrap(kafkaListeners);
        String kafkaBootstrapExternalUrl = getExternalKafkaBootstrap(kafkaListeners);
        String schemaRegistryEndpoint =  getInternalServiceName(getInstanceName(), SchemaRegistryModel.COMPONENT_NAME) + "." +  getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + Endpoint.getPodToPodPort(tlsEnabled());
        String zookeeperEndpoint = EventStreamsKafkaModel.getKafkaInstanceName(getInstanceName()) + "-" + EventStreamsKafkaModel.ZOOKEEPER_COMPONENT_NAME + "-client." + getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.ZOOKEEPER_PORT;
        String kafkaConnectRestEndpoint = "http://" + getResourcePrefix() + "-" + ReplicatorModel.COMPONENT_NAME + "-mirrormaker2-api." + getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + ReplicatorModel.REPLICATOR_PORT;

        ArrayList envVars = new ArrayList<>(Arrays.asList(
            new EnvVarBuilder().withName("RELEASE").withValue(getInstanceName()).build(),
            new EnvVarBuilder().withName("LICENSE").withValue("accept").build(),
            new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build(),
            new EnvVarBuilder().withName("TRACE_SPEC").withValue(traceString).build(),
            new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(internalBootstrap).build(),
            new EnvVarBuilder().withName("RUNAS_KAFKA_BOOTSTRAP_SERVERS").withValue(runasBootstrap).build(),
            new EnvVarBuilder().withName("SSL_ENABLED").withValue(tlsEnabled().toString()).build(),
            new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_INTERNAL_PLAIN_URL").withValue(kafkaBootstrapInternalPlainUrl).build(),
            new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_INTERNAL_TLS_URL").withValue(kafkaBootstrapInternalTlsUrl).build(),
            new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_EXTERNAL_URL").withValue(kafkaBootstrapExternalUrl).build(),
            new EnvVarBuilder().withName("SSL_TRUSTSTORE_PATH").withValue(CLUSTER_CERTIFICATE_PATH + File.separator + CA_P12).build(),
            new EnvVarBuilder().withName("CLIENT_CA_PATH").withValue(CLIENT_CA_CERTIFICATE_PATH + File.separator + CA_CERT).build(),
            new EnvVarBuilder().withName("SCHEMA_REGISTRY_ENABLED").withValue(Boolean.toString(SchemaRegistryModel.isSchemaRegistryEnabled(instance))).build(),
            new EnvVarBuilder().withName("SCHEMA_REGISTRY_URL").withValue(schemaRegistryEndpoint).build(),
            new EnvVarBuilder().withName("ZOOKEEPER_CONNECT").withValue(zookeeperEndpoint).build(),
            new EnvVarBuilder().withName("PROMETHEUS_HOST").withValue(prometheusHost).build(),
            new EnvVarBuilder().withName("PROMETHEUS_PORT").withValue(prometheusPort).build(),
            new EnvVarBuilder().withName("CLUSTER_CACERT").withValue(clusterCaCert).build(),
            new EnvVarBuilder().withName("KAFKA_STS_NAME").withValue(EventStreamsKafkaModel.getKafkaInstanceName(getInstanceName()) + "-" + EventStreamsKafkaModel.KAFKA_COMPONENT_NAME).build(),
            new EnvVarBuilder().withName("KAFKA_CONNECT_REST_API_ADDRESS").withValue(kafkaConnectRestEndpoint).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_ENABLED").withValue(Boolean.toString(ReplicatorModel.isReplicatorEnabled(instance))).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_SECRET_NAME").withValue(getResourcePrefix() + "-" + ReplicatorModel.REPLICATOR_SECRET_NAME).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_INTERNAL_CLIENT_AUTH_ENABLED").withValue(Boolean.toString(isReplicatorInternalClientAuthForConnectEnabled(instance))).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_EXTERNAL_CLIENT_AUTH_ENABLED").withValue(Boolean.toString(isReplicatorExternalClientAuthForConnectEnabled(instance))).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_INTERNAL_SERVER_AUTH_ENABLED").withValue(Boolean.toString(isReplicatorInternalServerAuthForConnectEnabled(instance))).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_EXTERNAL_SERVER_AUTH_ENABLED").withValue(Boolean.toString(isReplicatorExternalServerAuthForConnectEnabled(instance))).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_INTERNAL_CLIENT_AUTH_TYPE").withValue(isReplicatorInternalClientAuthForConnectEnabled(instance) ? ReplicatorModel.getInternalTlsKafkaListenerAuthentication(instance).getType() : "NONE").build(),
            new EnvVarBuilder()
                .withName("SSL_TRUSTSTORE_PASSWORD")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(EventStreamsKafkaModel.getKafkaClusterCaCertName(getInstanceName()))
                .withKey(CA_P12_PASS)
                .endSecretKeyRef()
                .endValueFrom()
                .build(),
            new EnvVarBuilder()
                .withName("SSL_KEYSTORE_PATH")
                .withValue(KAFKA_USER_CERTIFICATE_PATH + File.separator + "podtls.p12")
                .build(),
            new EnvVarBuilder()
                .withName("SSL_KEYSTORE_PASSWORD")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(InternalKafkaUserModel.getInternalKafkaUserSecretName(getInstanceName()))
                .withKey(USER_P12_PASS)
                .endSecretKeyRef()
                .endValueFrom()
                .build(),
            new EnvVarBuilder()
                .withName("CLIENT_P12_PASSWORD")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(EventStreamsKafkaModel.getKafkaClientCaCertName(getInstanceName()))
                .withKey(CA_P12_PASS)
                .endSecretKeyRef()
                .endValueFrom()
                .build(),

        // Add The IAM Specific Envars.  If we need to build without IAM Support we can put a variable check
        // here.
            new EnvVarBuilder().withName("IAM_CLUSTER_NAME").withValue(icpClusterName).build(),
            new EnvVarBuilder().withName("IAM_SERVER_URL").withValue(iamServerURL).build(),
            new EnvVarBuilder().withName("IAM_SERVER_CA_CERT").withValue(IBMCLOUD_CA_CERTIFICATE_PATH + File.separator + CA_CERT).build(),
            new EnvVarBuilder()
                .withName("CLIENT_ID")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(getResourcePrefix() + "-oidc-secret")
                .withKey(CLIENT_ID_KEY)
                .endSecretKeyRef()
                .endValueFrom()
                .build(),
            new EnvVarBuilder()
                .withName("CLIENT_SECRET")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(getResourcePrefix() + "-oidc-secret")
                .withKey(CLIENT_SECRET_KEY)
                .endSecretKeyRef()
                .endValueFrom()
                .build(),
            new EnvVarBuilder()
               .withName("ES_CACERT")
               .withNewValueFrom()
               .withNewSecretKeyRef()
               .withName(EventStreamsKafkaModel.getKafkaClusterCaCertName(getInstanceName()))
               .withKey("ca.crt")
               .withOptional(true)
               .endSecretKeyRef()
               .endValueFrom()
               .build()
            ));

        configureSecurityEnvVars(envVars);

        return envVars;
    }

    /**
     *
     * @return The liveness probe
     */
    protected Probe createLivenessProbe() {
        Probe defaultLivenessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/liveness")
                .withNewPort(Endpoint.getPodToPodPort(tlsEnabled()))
                .withScheme(getHealthCheckProtocol())
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(240)
                .withPeriodSeconds(30)
                .withTimeoutSeconds(10)
                .withSuccessThreshold(1)
                .withFailureThreshold(10)
                .build();
        return combineProbeDefinitions(defaultLivenessProbe, super.getLivenessProbe());
    }

    /**
     *
     * @return The readiness probe
     */
    protected Probe createReadinessProbe() {
        Probe defaultReadinessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/liveness")
                .withNewPort(Endpoint.getPodToPodPort(tlsEnabled()))
                .withScheme(getHealthCheckProtocol())
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(60)
                .withPeriodSeconds(10)
                .withTimeoutSeconds(10)
                .withSuccessThreshold(1)
                .withFailureThreshold(3)
                .build();
        return combineProbeDefinitions(defaultReadinessProbe, super.getReadinessProbe());
    }

    /**
     *
     * @return The rolebinding for the Admin Api
     */
    private RoleBinding createAdminApiRoleBinding() {
        return createRoleBinding(
            new SubjectBuilder()
                .withKind("ServiceAccount")
                .withName(getDefaultResourceName())
                .withNamespace(getNamespace())
                .build(),
            new RoleRefBuilder()
                .withKind("ClusterRole")
                .withName(ADMIN_CLUSTERROLE_NAME)
                .withApiGroup("rbac.authorization.k8s.io")
                .build());
    }

    /**
     * @return Deployment return the deployment with the specified generation id this is used
     * to control rolling updates, for example when the cert secret changes.
     */
    public Deployment getDeployment(String certGenerationID) {
        if (certGenerationID != null && deployment != null) {
            return new DeploymentBuilder(deployment)
                .editOrNewMetadata()
                .addToLabels(CERT_GENERATION_KEY, certGenerationID)
                .endMetadata()
                .editOrNewSpec()
                .editOrNewTemplate()
                .editOrNewMetadata()
                .addToLabels(CERT_GENERATION_KEY, certGenerationID)
                .endMetadata()
                .endTemplate()
                .endSpec()
                .build();
        }
        return deployment;
    }

    @Override
    public List<String> getP2PAuthenticationMechanisms() {
        return Collections.singletonList("IAM-BEARER");
    }

    /**
     * @return Deployment return the deployment with an empty generation id
     */
    public Deployment getDeployment() {
        return getDeployment("");
    }

    /**
     * @return getServiceAccount return the service account
     */
    public ServiceAccount getServiceAccount() {
        return this.serviceAccount;
    }

    /**
     * @return NetworkPolicy return the network policy
     */
    public NetworkPolicy getNetworkPolicy() {
        return this.networkPolicy;
    }

    /**
     * @return RoleBinding return the roleBinding
     */
    public RoleBinding getRoleBinding() {
        return this.roleBinding;
    }

    private NetworkPolicy createNetworkPolicy() {
        List<NetworkPolicyIngressRule> ingressRules = new ArrayList<>();

        endpoints.forEach(endpoint -> ingressRules.add(createIngressRule(endpoint.getPort(), new HashMap<>())));

        return createNetworkPolicy(createLabelSelector(COMPONENT_NAME), ingressRules, null);
    }

    private void setTraceString(Logging logging) {
        if (logging != null && InlineLogging.TYPE_INLINE.equals(logging.getType())) {
            Map<String, String> loggers = ((InlineLogging) logging).getLoggers();
            loggers.keySet().stream().findFirst().ifPresent(firstKey -> {
                String loggingLevel = loggers.get(firstKey);
                this.traceString = loggingLevel;
                log.debug("API Admin logging level set to {}", loggingLevel);
            });
        }
    }

}
