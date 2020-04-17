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
import com.ibm.eventstreams.api.TlsVersion;
import com.ibm.eventstreams.api.spec.AdminUISpec;
import com.ibm.eventstreams.api.spec.ComponentSpec;
import com.ibm.eventstreams.api.spec.ComponentTemplate;
import com.ibm.eventstreams.api.spec.ContainerSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.ExternalAccess;
import com.ibm.eventstreams.api.spec.ExternalAccessBuilder;
import com.ibm.eventstreams.api.spec.ImagesSpec;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import com.ibm.eventstreams.api.status.EventStreamsVersions;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import com.ibm.iam.api.model.ClientModel;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HTTPHeaderBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.fabric8.kubernetes.api.model.rbac.SubjectBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.TLSConfig;
import io.fabric8.openshift.api.model.TLSConfigBuilder;
import io.strimzi.api.kafka.model.ContainerEnvVar;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.Logging;
import io.strimzi.api.kafka.model.template.PodTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ibm.eventstreams.api.model.AbstractSecureEndpointsModel.getInternalServiceName;

public class AdminUIModel extends AbstractModel {

    // static variables
    public static final String COMPONENT_NAME = "ui";
    public static final int UI_SERVICE_PORT = 3000;
    public static final String REDIS_CONTAINER_NAME = "redis";
    private static final int DEFAULT_REPLICAS = 1;
    protected static final String TRACE_STATE = "TRACE_STATE";
    private static final String DEFAULT_IBMCOM_UI_IMAGE = "ibmcom/admin-ui:latest";
    private static final String DEFAULT_IBMCOM_REDIS_IMAGE = "ibmcom/redis:latest";
    public static final String ICP_CM_CLUSTER_ADDRESS_KEY = "cluster_address";
    public static final String ICP_CM_CLUSTER_ROUTER_PORT_KEY = "cluster_router_https_port";
    public static final String ICP_CM_CLUSTER_NAME_KEY = "cluster_name";
    public static final String ICP_CM_CLUSTER_PLATFORM_SERVICES_URL = "header_url";


    // deployable objects
    private Deployment deployment;
    private ServiceAccount serviceAccount;
    private Service service;
    private RoleBinding roleBinding;
    private Route route;
    private NetworkPolicy networkPolicy;

    private List<ContainerEnvVar> redisEnvVars;
    private String redisImage;
    private ResourceRequirements redisResourceRequirements;
    private io.strimzi.api.kafka.model.Probe redisLivenessProbe;
    private io.strimzi.api.kafka.model.Probe redisReadinessProbe;
    private String traceString = "ExpressApp;INFO,Simulated;INFO,KubernetesClient;INFO";
    private Map<String, String> icpClusterData;
    private String oidcSecretName;
    private TlsVersion crTlsVersionValue;
    private String enableProducerMetricsPanels;
    private String enableMetricsPanels;

    /**
     * This class is used to model all the kube resources required for correct deployment of the Admin UI
     * @param instance
     * @param imageConfig
     * @param hasRoutes
     * @param icpClusterData
     */
    public AdminUIModel(EventStreams instance,
                        EventStreamsOperatorConfig.ImageLookup imageConfig,
                        Boolean hasRoutes,
                        Map<String, String> icpClusterData) {

        super(instance, COMPONENT_NAME);

        this.icpClusterData = icpClusterData;
        this.oidcSecretName = ClientModel.getSecretName(instance);

        Optional<AdminUISpec> userInterfaceSpec = Optional
            .ofNullable(instance.getSpec())
            .map(EventStreamsSpec::getAdminUI);

        // needed to support the route created below
        setOwnerReference(instance);

        if (userInterfaceSpec.isPresent()) {
            setReplicas(userInterfaceSpec.map(ComponentSpec::getReplicas).orElse(DEFAULT_REPLICAS));
            setEnvVars(userInterfaceSpec.map(ContainerSpec::getEnvVars).orElseGet(ArrayList::new));
            setResourceRequirements(userInterfaceSpec.map(ContainerSpec::getResources).orElseGet(ResourceRequirements::new));
            setPodTemplate(userInterfaceSpec.map(ComponentSpec::getTemplate)
                            .map(ComponentTemplate::getPod)
                            .orElseGet(PodTemplate::new));
            setTlsVersion(TlsVersion.TLS_V1_2);
            crTlsVersionValue = Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getSecurity)
                .map(SecuritySpec::getInternalTls)
                .orElse(DEFAULT_INTERNAL_TLS);
            setGlobalPullSecrets(Optional.ofNullable(instance.getSpec())
                                    .map(EventStreamsSpec::getImages)
                                    .map(ImagesSpec::getPullSecrets)
                                    .orElseGet(imageConfig::getPullSecrets));
            setImage(firstDefinedImage(
                DEFAULT_IBMCOM_UI_IMAGE, userInterfaceSpec.map(ComponentSpec::getImage),
                            imageConfig.getAdminUIImage()));

            setLivenessProbe(userInterfaceSpec.map(ComponentSpec::getLivenessProbe)
                    .orElseGet(io.strimzi.api.kafka.model.Probe::new));
            setReadinessProbe(userInterfaceSpec.map(ComponentSpec::getReadinessProbe)
                    .orElseGet(io.strimzi.api.kafka.model.Probe::new));
            setTraceString(userInterfaceSpec.map(ComponentSpec::getLogging).orElse(null));
            
            enableProducerMetricsPanels = Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getConfig)
                .filter(map -> map.containsKey("interceptor.class.names"))
                .orElse(null) == null ? "false" : "true"; 
            enableMetricsPanels = Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getMetrics)
                .isPresent() ? "true" : "false";
                
            Optional<ContainerSpec> redisSpec = userInterfaceSpec.map(AdminUISpec::getRedis);

            redisEnvVars = redisSpec.map(ContainerSpec::getEnvVars).orElseGet(ArrayList::new);
            redisImage = firstDefinedImage(
                    DEFAULT_IBMCOM_REDIS_IMAGE, redisSpec.map(ContainerSpec::getImage),
                            imageConfig.getAdminUIRedisImage());
            redisResourceRequirements = redisSpec.map(ContainerSpec::getResources).orElseGet(ResourceRequirements::new);
            redisLivenessProbe = redisSpec.map(ContainerSpec::getLivenessProbe)
                    .orElseGet(io.strimzi.api.kafka.model.Probe::new);
            redisReadinessProbe = redisSpec.map(ContainerSpec::getLivenessProbe)
                    .orElseGet(io.strimzi.api.kafka.model.Probe::new);

            setCustomImages(imageConfig.getAdminUIImage(), imageConfig.getAdminUIRedisImage());

            ExternalAccess defaultExternalAccess = new ExternalAccessBuilder()
                    .withNewType(hasRoutes ? ExternalAccess.TYPE_ROUTE : ExternalAccess.TYPE_DEFAULT)
                    .build();
            setExternalAccess(userInterfaceSpec.map(ComponentSpec::getExternalAccess)
                    .orElse(defaultExternalAccess));

            deployment = createDeployment(getContainers(instance), getVolumes());
            serviceAccount = createServiceAccount();
            roleBinding = createAdminUIRoleBinding();

            service = createService();
            networkPolicy = createNetworkPolicy();
        }

        // The route is created regardless of whether a UI deployment is created,
        //  because it is required for OIDC registration. (The OIDC client that
        //  creates is required for other components, like admin-api).

        TLSConfig tlsConfig = new TLSConfigBuilder()
                .withNewTermination("reencrypt")
                .build();
        route = createRoute(getRouteName(), getDefaultResourceName(), UI_SERVICE_PORT, tlsConfig, new HashMap<>());
    }

    /**
     * 
     * @return A list of volumes to put in the Admin UI pod
     */
    private List<Volume> getVolumes() {
        Volume redis = new VolumeBuilder()
            .withName("redis-storage")
            .withNewEmptyDir()
            .endEmptyDir()
            .build();

        return Collections.singletonList(redis);

    }

    /**
     * 
     * @return The service associated with the Admin UI pod
     */
    private Service createService() {
        Service service = createService(UI_SERVICE_PORT);
        service.getMetadata().setAnnotations(Collections.singletonMap("service.beta.openshift.io/serving-cert-secret-name", getServiceCertificateName()));
        return service;
    }

    protected void setCustomImages(Optional<String> defaultEnvUiImage, Optional<String> defaultEnvRedisImage) {
        List<String> defaultUiImages = new ArrayList<>();
        defaultUiImages.add(DEFAULT_IBMCOM_UI_IMAGE);
        defaultUiImages.add(defaultEnvUiImage.isPresent() ? defaultEnvUiImage.get() : "");
        boolean uiCustomImage = defaultUiImages
                .stream()
                .filter(image -> this.image.equals(image))
                .findFirst().isPresent() ? false : true;

        List<String> defaultRedisImages = new ArrayList<>();
        defaultRedisImages.add(DEFAULT_IBMCOM_REDIS_IMAGE);
        defaultRedisImages.add(defaultEnvRedisImage.isPresent() ? defaultEnvRedisImage.get() : "");
        boolean redisCustomImage = defaultRedisImages
                .stream()
                .filter(image -> redisImage.equals(image))
                .findFirst().isPresent() ? false : true;

        this.customImage = uiCustomImage || redisCustomImage;
    }

    /**
     * 
     * @return A list of containers to put in the Admin UI pod
     */
    private List<Container> getContainers(EventStreams instance) {
        return Arrays.asList(getUIContainer(instance), getRedisContainer());
    }

    /**
     * 
     * @return The Admin UI container
     */
    private Container getUIContainer(EventStreams instance) {
        boolean isSecurityEnabled = !Optional.ofNullable(instance)
            .map(EventStreams::getSpec)
            .map(EventStreamsSpec::getSecurity)
            .map(SecuritySpec::getInternalTls)
            .orElse(AbstractModel.DEFAULT_INTERNAL_TLS)
            .equals(TlsVersion.NONE);

        String adminApiService = getUrlProtocol(crTlsVersionValue) + getInternalServiceName(getInstanceName(), AdminApiModel.COMPONENT_NAME) + "." +  getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + Endpoint.getPodToPodPort(isSecurityEnabled);
        String schemaRegistryService = getUrlProtocol(crTlsVersionValue) + getInternalServiceName(getInstanceName(), SchemaRegistryModel.COMPONENT_NAME) + "." +  getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + Endpoint.getPodToPodPort(isSecurityEnabled);

        List<EnvVar> envVarDefaults = new ArrayList<>();

        envVarDefaults.add(new EnvVarBuilder().withName("LICENSE").withValue("accept").build());
        envVarDefaults.add(
            new EnvVarBuilder()
                .withName("TLS_CERT")
                .withNewValueFrom()
                    .withNewSecretKeyRef()
                        .withName(getServiceCertificateName())
                        .withKey("tls.crt")
                    .endSecretKeyRef()
                .endValueFrom()
                .build());
        envVarDefaults.add(
            new EnvVarBuilder()
                .withName("TLS_KEY")
                .withNewValueFrom()
                    .withNewSecretKeyRef()
                        .withName(getServiceCertificateName())
                        .withKey("tls.key")
                    .endSecretKeyRef()
                .endValueFrom()
                .build());
        envVarDefaults.add(new EnvVarBuilder().withName("TLS_ENABLED").withValue(String.valueOf(tlsEnabled())).build());
        envVarDefaults.add(new EnvVarBuilder().withName("ID").withValue(getInstanceName()).build());
        envVarDefaults.add(new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build());
        envVarDefaults.add(
                new EnvVarBuilder()
                        .withName("EVENT_STREAMS_VERSION")
                        .withValue(Optional.ofNullable(instance.getSpec())
                                .map(EventStreamsSpec::getVersion)
                                .orElse(EventStreamsVersions.OPERAND_VERSION))
                        .build());
        envVarDefaults.add(
                new EnvVarBuilder()
                        .withName("KAFKA_VERSION")
                        .withValue(Optional.ofNullable(instance.getSpec())
                                .map(EventStreamsSpec::getStrimziOverrides)
                                .map(KafkaSpec::getKafka)
                                .map(KafkaClusterSpec::getVersion)
                                .orElse("2.4.0"))
                        .build());
        envVarDefaults.add(
                new EnvVarBuilder()
                        .withName("EVENT_STREAMS_CHART_VERSION")
                        .withValue(Optional.ofNullable(instance.getSpec())
                                .map(EventStreamsSpec::getVersion)
                                .orElse(EventStreamsVersions.OPERAND_VERSION))
                        .build());
        envVarDefaults.add(new EnvVarBuilder().withName("API_URL").withValue(adminApiService).build());
        envVarDefaults.add(new EnvVarBuilder().withName("PRODUCER_METRICS_ENABLED").withValue(enableProducerMetricsPanels).build());
        envVarDefaults.add(new EnvVarBuilder().withName("METRICS_ENABLED").withValue(enableMetricsPanels).build());
        envVarDefaults.add(new EnvVarBuilder().withName(TLS_VERSION_ENV_KEY).withValue(getTlsVersionEnvValue(instance)).build());

        envVarDefaults.add(new EnvVarBuilder().withName("REDIS_HOST").withValue("127.0.0.1").build());

        envVarDefaults.add(new EnvVarBuilder().withName("CLUSTER_NAME").withValue(Main.CLUSTER_NAME).build());
        envVarDefaults.add(new EnvVarBuilder().withName("GEOREPLICATION_ENABLED").withValue("true").build());
        envVarDefaults.add(new EnvVarBuilder().withName("SCHEMA_REGISTRY_ENABLED").withValue(Boolean.toString(SchemaRegistryModel.isSchemaRegistryEnabled(instance))).build());
        envVarDefaults.add(new EnvVarBuilder().withName("SCHEMA_REGISTRY_URL").withValue(schemaRegistryService).build());
        envVarDefaults.add(
            new EnvVarBuilder()
                .withName(TRACE_STATE)
                .withValue(traceString)
                .build()
        );

        envVarDefaults.add(new EnvVarBuilder().withName("ICP_USER_MGMT_IP").withValue("icp-management-ingress.kube-system").build());
        envVarDefaults.add(new EnvVarBuilder().withName("ICP_USER_MGMT_PORT").withValue("443").build());
        envVarDefaults.add(new EnvVarBuilder().withName("ICP_USER_MGMT_HIGHEST_ROLE_FOR_CRN").withValue("idmgmt/identity/api/v1/teams/highestRole").build());

        if (icpClusterData != null && icpClusterData.size() > 0) {
            String clusterIp = icpClusterData.get(ICP_CM_CLUSTER_ADDRESS_KEY);
            String secureClusterPort = icpClusterData.get(ICP_CM_CLUSTER_ROUTER_PORT_KEY);
            String iamClusterName = icpClusterData.get(ICP_CM_CLUSTER_NAME_KEY);
            String platformServicesUrl = icpClusterData.get(ICP_CM_CLUSTER_PLATFORM_SERVICES_URL);
            envVarDefaults.add(new EnvVarBuilder().withName("CLUSTER_EXTERNAL_IP").withValue(clusterIp).build());
            envVarDefaults.add(new EnvVarBuilder().withName("CLUSTER_EXTERNAL_PORT").withValue(secureClusterPort).build());
            envVarDefaults.add(new EnvVarBuilder().withName("IAM_CLUSTER_NAME").withValue(iamClusterName).build());
            envVarDefaults.add(new EnvVarBuilder().withName("ICP4I_PLATFORM_SERVICES_URL").withValue(platformServicesUrl).build());

            envVarDefaults.add(
                new EnvVarBuilder()
                    .withName("CLIENT_ID")
                    .withNewValueFrom()
                    .withNewSecretKeyRef()
                    .withName(oidcSecretName)
                    .withKey("CLIENT_ID")
                    .endSecretKeyRef()
                    .endValueFrom()
                    .build()
            );
            envVarDefaults.add(
                new EnvVarBuilder()
                    .withName("CLIENT_SECRET")
                    .withNewValueFrom()
                    .withNewSecretKeyRef()
                    .withName(oidcSecretName)
                    .withKey("CLIENT_SECRET")
                    .endSecretKeyRef()
                    .endValueFrom()
                    .build()
            );
        } else {
            envVarDefaults.add(new EnvVarBuilder().withName("CLUSTER_EXTERNAL_IP").withValue("").build());
            envVarDefaults.add(new EnvVarBuilder().withName("CLUSTER_EXTERNAL_PORT").withValue("").build());
        }

        envVarDefaults.add(new EnvVarBuilder().withName("ESFF_SECURITY_AUTH").withValue(authEnabled(instance).toString()).build());
        envVarDefaults.add(new EnvVarBuilder().withName("ESFF_SECURITY_AUTHZ").withValue(authEnabled(instance).toString()).build());

        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(envVarDefaults);

        return new ContainerBuilder()
            .withName(COMPONENT_NAME)
            .withImage(getImage())
            .withEnv(envVars)
            .withSecurityContext(getSecurityContext(false))
            .withResources(getResourceRequirements(DefaultResourceRequirements.ADMIN_UI))
            .addNewPort()
                .withName("uiendpoint")
                .withContainerPort(UI_SERVICE_PORT)
            .endPort()
            .withLivenessProbe(createLivenessProbe())
            .withReadinessProbe(createReadinessProbe())
            .build();
    }

    /**
     * 
     * @return The liveness probe for the Admin UI container
     */
    protected Probe createLivenessProbe() {
        Probe defaultLivenessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/status")
                .withNewPort(UI_SERVICE_PORT)
                .withScheme(getHealthCheckProtocol())
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(15)
                .withPeriodSeconds(10)
                .withTimeoutSeconds(10)
                .withSuccessThreshold(1)
                .withFailureThreshold(6)
                .build();
        return combineProbeDefinitions(defaultLivenessProbe, super.getLivenessProbe());
    }

    /**
     * 
     * @return The readiness probe for the Admin UI container
     */
    protected Probe createReadinessProbe() {
        Probe defaultReadinessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/status")
                .withNewPort(UI_SERVICE_PORT)
                .withScheme(getHealthCheckProtocol())
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(15)
                .withPeriodSeconds(10)
                .withTimeoutSeconds(10)
                .withFailureThreshold(3)
                .build();
        return combineProbeDefinitions(defaultReadinessProbe, super.getReadinessProbe());
    }

    /**
     * 
     * @return The Redis container
     */
    private Container getRedisContainer() {

        // TODO what do we do with License env vars
        List<EnvVar> envVarDefaults = Arrays.asList(
            new EnvVarBuilder().withName("LICENSE").withValue("accept").build()
        );

        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(envVarDefaults, redisEnvVars);

        ResourceRequirements resourceRequirements = getResourceRequirements(redisResourceRequirements,
                                                                            DefaultResourceRequirements.ADMIN_UI_REDIS);

        return new ContainerBuilder()
            .withName(REDIS_CONTAINER_NAME)
            .withImage(redisImage)
            .withEnv(envVars)
            .withSecurityContext(getSecurityContext(true))
            .withResources(resourceRequirements)
            .addNewVolumeMount()
                .withName("redis-storage")
                .withMountPath("/data")
            .endVolumeMount()
            .withLivenessProbe(createRedisLivenessProbe())
            .withReadinessProbe(createRedisReadinessProbe())
            .build();
    }

    /**
     * 
     * @return The liveness probe for the redis container
     */
    protected Probe createRedisLivenessProbe() {
        Probe defaultLivenessProbe = new ProbeBuilder()
                .withNewExec()
                    .withCommand("sh", "-c", "/usr/local/bin/redis-cli -h $(hostname) ping")
                .endExec()
                .withFailureThreshold(2)
                .withInitialDelaySeconds(30)
                .withPeriodSeconds(10)
                .withTimeoutSeconds(10)
                .withSuccessThreshold(1)
                .build();
        return combineProbeDefinitions(defaultLivenessProbe, redisLivenessProbe);
    }

    /**
     * 
     * @return The readiness probe for the redis container
     */
    protected Probe createRedisReadinessProbe() {
        Probe defaultReadinessProbe = new ProbeBuilder()
                .withNewExec()
                .withCommand("sh", "-c", "/usr/local/bin/redis-cli -h $(hostname) ping")
                .endExec()
                .withFailureThreshold(2)
                .withInitialDelaySeconds(10)
                .withPeriodSeconds(10)
                .withSuccessThreshold(1)
                .withTimeoutSeconds(10)
                .build();
        return combineProbeDefinitions(defaultReadinessProbe, redisReadinessProbe);
    }

    /**
     * 
     * @return The rolebinding for the Admin UI
     */
    private RoleBinding createAdminUIRoleBinding() {
        return createRoleBinding(
            new SubjectBuilder()
                .withKind("ServiceAccount")
                .withName(getDefaultResourceName())
                .withNamespace(getNamespace())
                .build(),
            new RoleRefBuilder()
                .withKind("ClusterRole")
                .withName("eventstreams-ui-clusterrole")
                .withApiGroup("rbac.authorization.k8s.io")
                .build());
    }

    public static boolean isUIEnabled(EventStreams instance) {
        return Optional.ofNullable(instance.getSpec().getAdminUI())
                .map(AdminUISpec::getReplicas)
                .map(replicas -> replicas > 0)
                .orElse(false);
    }

    /**
     * @return Deployment return the deployment
     */
    public Deployment getDeployment() {
        return this.deployment;
    }

    /**
     * @return getServiceAccount return the service account
     */
    public ServiceAccount getServiceAccount() {
        return this.serviceAccount;
    }

    /**
     * @return Service return the service
     */
    public Service getService() {
        return this.service;
    }

    /**
     * @return Route return the route
     */
    public Route getRoute() {
        return this.route;
    }

    /**
     * @return NetworkPolicy return the network policy
     */
    public NetworkPolicy getNetworkPolicy() {
        return this.networkPolicy;
    }

    /**
     * 
     * @return The network policy for the Admin UI
     */
    private NetworkPolicy createNetworkPolicy() {
        List<NetworkPolicyIngressRule> ingressRules = new ArrayList<>(1);
        ingressRules.add(createIngressRule(UI_SERVICE_PORT, new HashMap<>()));

        return createNetworkPolicy(createLabelSelector(COMPONENT_NAME), ingressRules, null);
    }

    /**
     * The default resource name with a suffix "service-cert"
     * @return Then service certificate name
     */
    private String getServiceCertificateName() {
        return getDefaultResourceNameWithSuffix("service-cert");
    }

    /**
     * 
     * @return The Admin UI rolebinding
     */
    public RoleBinding getRoleBinding() {
        return this.roleBinding;
    }

    private void setTraceString(Logging logging) {
        if (logging != null && InlineLogging.TYPE_INLINE.equals(logging.getType())) {
            Map<String, String> loggers = ((InlineLogging) logging).getLoggers();
            List<String> loggersArray = new ArrayList();
            loggers.forEach((k, v) -> {
                loggersArray.add(k + ";" + v);
            });
            traceString = String.join(",", loggersArray);
        }
    }

}

