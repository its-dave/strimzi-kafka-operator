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

import com.ibm.commonservices.CommonServices;
import com.ibm.eventstreams.api.DefaultResourceRequirements;
import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.spec.ComponentSpec;
import com.ibm.eventstreams.api.spec.ComponentTemplate;
import com.ibm.eventstreams.api.spec.ContainerSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.ImagesSpec;
import com.ibm.eventstreams.api.spec.SecurityComponentSpec;
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
import io.strimzi.api.kafka.model.KafkaMirrorMaker2Resources;
import io.strimzi.api.kafka.model.KafkaResources;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.template.PodTemplate;
import io.strimzi.operator.cluster.model.ModelUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AdminApiModel extends AbstractSecureEndpointsModel {

    public static final String COMPONENT_NAME = "admapi";
    public static final String APPLICATION_NAME = "admin-api";
    public static final String ADMIN_API_CONTAINER_NAME = "admin-api";
    public static final int DEFAULT_REPLICAS = 1;
    public static final String DEFAULT_IBMCOM_IMAGE = "ibmcom/admin-api:latest";
    public static final String KAFKA_CONFIGMAP_MOUNT_NAME = "kafka-cm";
    public static final String ADMIN_CLUSTERROLE_NAME = "eventstreams-admin-clusterrole";

    private static final String DEFAULT_TRACE_STRING = "info";
    private String traceString;

    private final String prometheusHost;
    private final String prometheusPort;
    private final CommonServices commonServices;

    private final Deployment deployment;
    private final ServiceAccount serviceAccount;
    private final NetworkPolicy networkPolicy;
    private final RoleBinding roleBinding;
    private final boolean isGeoReplicationEnabled;
    private final String internalKafkaUsername;

    private static final Logger log = LogManager.getLogger(AdminApiModel.class.getName());
    private List<ListenerStatus> kafkaListeners;

    /**
     * This class is used to model all the kube resources required for correct deployment of the Admin Api
     * @param instance
     * @param imageConfig
     * @param kafkaListeners
     * @param commonServices
     */
    public AdminApiModel(EventStreams instance,
                         EventStreamsOperatorConfig.ImageLookup imageConfig,
                         List<ListenerStatus> kafkaListeners,
                         CommonServices commonServices,
                         boolean isGeoReplicationEnabled,
                         String internalKafkaUsername) {
        super(instance, COMPONENT_NAME, APPLICATION_NAME);
        this.kafkaListeners = kafkaListeners != null ? new ArrayList<>(kafkaListeners) : new ArrayList<>();

        this.prometheusHost = commonServices.getConsoleHost();
        this.prometheusPort = commonServices.getConsolePort();
        this.commonServices = commonServices;

        this.isGeoReplicationEnabled = isGeoReplicationEnabled;
        this.internalKafkaUsername = internalKafkaUsername;

        Optional<SecurityComponentSpec> adminApiSpec = Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getAdminApi);

        setOwnerReference(instance);
        setReplicas(adminApiSpec.map(ComponentSpec::getReplicas).orElse(DEFAULT_REPLICAS));
        setResourceRequirements(adminApiSpec.map(ContainerSpec::getResources).orElseGet(ResourceRequirements::new));
        setEnvVars(adminApiSpec.map(ContainerSpec::getEnvVars).orElseGet(ArrayList::new));
        setPodTemplate(adminApiSpec.map(ComponentSpec::getTemplate)
                            .map(ComponentTemplate::getPod)
                            .orElseGet(PodTemplate::new));
        setTlsVersion(getInternalTlsVersion(instance));
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
        traceString = getTraceString(adminApiSpec.map(ComponentSpec::getLogging).orElse(null), DEFAULT_TRACE_STRING, false);

        endpoints = createEndpoints(instance, adminApiSpec.orElse(null));
        deployment = createDeployment(getContainers(instance), getVolumes(instance));
        serviceAccount = createServiceAccount();

        roleBinding = createAdminApiRoleBinding();
        createService(EndpointServiceType.INTERNAL, Collections.emptyMap());
        createService(EndpointServiceType.ROUTE, Collections.emptyMap());
        createService(EndpointServiceType.NODE_PORT, Collections.emptyMap());
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
            .withNewName(GeoReplicatorSecretModel.REPLICATOR_SECRET_NAME)
            .withNewSecret()
                .withNewSecretName(getResourcePrefix() + "-" + GeoReplicatorSecretModel.REPLICATOR_SECRET_NAME)
                .addNewItem().withNewKey(GeoReplicatorSecretModel.REPLICATOR_TARGET_CLUSTERS_SECRET_KEY_NAME)
                .withNewPath(GeoReplicatorSecretModel.REPLICATOR_TARGET_CLUSTERS_SECRET_KEY_NAME)
            .endItem()
            .endSecret()
            .build());

        volumes.addAll(securityVolumes());

        volumes.add(new VolumeBuilder()
            .withNewName(KAFKA_CONFIGMAP_MOUNT_NAME)
            .withNewConfigMap()
                .withNewName(EventStreamsKafkaModel.getKafkaConfigMapName(getInstanceName()))
            .endConfigMap()
            .build());

        if (commonServices.isPresent()) {
            volumes.addAll(commonServices.volumes());
        }

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
                .withNewName(GeoReplicatorSecretModel.REPLICATOR_SECRET_NAME)
                .withMountPath(GeoReplicatorModel.REPLICATOR_SECRET_MOUNT_PATH)
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

        if (commonServices.isPresent()) {
            containerBuilder.addAllToVolumeMounts(commonServices.volumeMounts());
        }

        configureSecurityVolumeMounts(containerBuilder);

        return containerBuilder.build();
    }

    /**
     *
     * @param instance
     * @return A list of default EnvVars for the Admin Api container
     */
    private List<EnvVar> getAdminApiEnvVars(EventStreams instance) {

        String schemaRegistryEndpoint = String.format("%s:%s",
                ModelUtils.serviceDnsNameWithoutClusterDomain(getNamespace(),
                        getInternalServiceName(getInstanceName(), SchemaRegistryModel.COMPONENT_NAME)),
                Endpoint.getPodToPodPort(tlsEnabled()));
        String zookeeperEndpoint = String.format("%s:%s",
                ModelUtils.serviceDnsNameWithoutClusterDomain(getNamespace(),
                        KafkaResources.zookeeperServiceName(EventStreamsKafkaModel.getKafkaInstanceName(getInstanceName()))),
                EventStreamsKafkaModel.ZOOKEEPER_PORT);
        String kafkaConnectRestEndpoint = String.format("http://%s:%s",
                ModelUtils.serviceDnsNameWithoutClusterDomain(getNamespace(),
                        KafkaMirrorMaker2Resources.serviceName(getInstanceName())),
                GeoReplicatorModel.REPLICATOR_PORT);

        ArrayList<EnvVar> envVars = new ArrayList<>(Arrays.asList(
            new EnvVarBuilder().withName("RELEASE").withValue(getInstanceName()).build(),
            new EnvVarBuilder().withName("LICENSE").withValue("accept").build(),
            new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build(),
            new EnvVarBuilder().withName("EVENTSTREAMS_API_GROUP").withValue(EventStreams.RESOURCE_GROUP).build(),
            new EnvVarBuilder().withName("EVENTSTREAMS_API_VERSION").withValue(instance.getApiVersion()).build(),
            new EnvVarBuilder().withName("TRACE_SPEC").withValue(traceString).build(),
            new EnvVarBuilder().withName("SCHEMA_REGISTRY_URL").withValue(schemaRegistryEndpoint).build(),
            new EnvVarBuilder().withName("ZOOKEEPER_CONNECT").withValue(zookeeperEndpoint).build(),
            new EnvVarBuilder().withName("PROMETHEUS_HOST").withValue(prometheusHost).build(),
            new EnvVarBuilder().withName("PROMETHEUS_PORT").withValue(prometheusPort).build(),
            new EnvVarBuilder().withName("KAFKA_STS_NAME").withValue(EventStreamsKafkaModel.getKafkaInstanceName(getInstanceName()) + "-" + EventStreamsKafkaModel.KAFKA_COMPONENT_NAME).build(),
            new EnvVarBuilder().withName("KAFKA_CONNECT_REST_API_ADDRESS").withValue(kafkaConnectRestEndpoint).build(),
            new EnvVarBuilder().withName("KAFKA_PRINCIPAL").withValue(internalKafkaUsername).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_ENABLED").withValue(Boolean.toString(isGeoReplicationEnabled)).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_SECRET_NAME").withValue(getResourcePrefix() + "-" + GeoReplicatorSecretModel.REPLICATOR_SECRET_NAME).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_INTERNAL_CLIENT_AUTH_ENABLED").withValue(Boolean.toString(isReplicatorInternalClientAuthForConnectEnabled(instance))).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_EXTERNAL_CLIENT_AUTH_ENABLED").withValue(Boolean.toString(isReplicatorExternalClientAuthForConnectEnabled(instance))).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_INTERNAL_SERVER_AUTH_ENABLED").withValue(Boolean.toString(isReplicatorInternalServerAuthForConnectEnabled(instance))).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_EXTERNAL_SERVER_AUTH_ENABLED").withValue(Boolean.toString(isReplicatorExternalServerAuthForConnectEnabled(instance))).build(),
            new EnvVarBuilder().withName("GEOREPLICATION_INTERNAL_CLIENT_AUTH_TYPE").withValue(isReplicatorInternalClientAuthForConnectEnabled(instance) ? GeoReplicatorModel.getInternalTlsKafkaListenerAuthentication(instance).getType() : "NONE").build(),
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


        if (commonServices.isPresent()) {
            envVars.addAll(commonServices.envVars());
        }

        // Optionally add the kafka bootstrap URLs if present
        getInternalKafkaBootstrap(kafkaListeners).ifPresent(internalBootstrap ->
            envVars.add(new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(internalBootstrap).build()));

        getRunAsKafkaBootstrap(kafkaListeners).ifPresent(runasBootstrap ->
            envVars.add(new EnvVarBuilder().withName("RUNAS_KAFKA_BOOTSTRAP_SERVERS").withValue(runasBootstrap).build()));

        getInternalPlainKafkaBootstrap(kafkaListeners).ifPresent(kafkaBootstrapInternalPlainUrl ->
            envVars.add(new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_INTERNAL_PLAIN_URL").withValue(kafkaBootstrapInternalPlainUrl).build()));

        getInternalTlsKafkaBootstrap(kafkaListeners).ifPresent(kafkaBootstrapInternalTlsUrl ->
            envVars.add(new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_INTERNAL_TLS_URL").withValue(kafkaBootstrapInternalTlsUrl).build()));

        getExternalKafkaBootstrap(kafkaListeners).ifPresent(kafkaBootstrapExternalUrl ->
            envVars.add(new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_EXTERNAL_URL").withValue(kafkaBootstrapExternalUrl).build()));

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
                .withPeriodSeconds(10)
                .withSuccessThreshold(1)
                .withFailureThreshold(3)
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
                .withPeriodSeconds(10)
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
                            .addToLabels(getLabelOverrides())
                            .addToAnnotations(getAnnotationOverrides())
                        .endMetadata()
                        .editOrNewSpec()
                            .withAffinity(getAffinity())
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
        }
        return deployment;
    }

    @Override
    protected List<Endpoint> createDefaultEndpoints(boolean kafkaAuthenticationEnabled) {
        return new ArrayList<>(Collections.singletonList(Endpoint.createDefaultExternalEndpoint(kafkaAuthenticationEnabled)));
    }

    @Override
    protected List<Endpoint> createP2PEndpoints(EventStreams instance) {
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(Endpoint.createP2PEndpoint(instance, getP2PAuthenticationMechanisms(instance), Collections.singletonList(uniqueInstanceLabels())));
        return endpoints;
    }

    public List<String> getP2PAuthenticationMechanisms(EventStreams instance) {
        return isKafkaAuthenticationEnabled(instance) ? Collections.singletonList(Endpoint.IAM_BEARER_KEY) : Collections.emptyList();
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

        endpoints.forEach(endpoint -> ingressRules.add(createIngressRule(endpoint.getPort(), endpoint.getEndpointIngressLabels())));

        return createNetworkPolicy(createLabelSelector(APPLICATION_NAME), ingressRules, null);
    }
}
