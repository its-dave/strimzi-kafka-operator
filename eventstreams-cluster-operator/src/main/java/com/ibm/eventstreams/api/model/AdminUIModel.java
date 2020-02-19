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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ibm.eventstreams.Main;
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
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import com.ibm.iam.api.model.ClientModel;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HTTPHeaderBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyEgressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.fabric8.kubernetes.api.model.rbac.SubjectBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.TLSConfigBuilder;
import io.strimzi.api.kafka.model.ContainerEnvVar;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.Logging;
import io.strimzi.api.kafka.model.template.PodTemplate;

import java.util.Collections;

public class AdminUIModel extends AbstractModel {

    // static variables
    public static final String COMPONENT_NAME = "admin-ui";
    public static final int UI_SERVICE_PORT = 3000;
    public static final String REDIS_CONTAINER_NAME = "redis";
    private static final int DEFAULT_REPLICAS = 1;
    protected static final String TRACE_STATE = "TRACE_STATE";
    private static final String DEFAULT_IBMCOM_UI_IMAGE = "ibmcom/admin-ui:latest";
    private static final String DEFAULT_IBMCOM_REDIS_IMAGE = "ibmcom/redis:latest";
    public static final String ICP_CM_CLUSTER_ADDRESS_KEY = "cluster_address";
    public static final String ICP_CM_CLUSTER_ROUTER_PORT_KEY = "cluster_router_https_port";
    public static final String ICP_CM_CLUSTER_NAME_KEY = "cluster_name";

    // deployable objects
    private final Deployment deployment;
    private final ServiceAccount serviceAccount;
    private final Service service;
    private final RoleBinding roleBinding;
    private final Route route;
    private final NetworkPolicy networkPolicy;

    private final List<ContainerEnvVar> redisEnvVars;
    private final String redisImage;
    private final ResourceRequirements redisResourceRequirements;
    private io.strimzi.api.kafka.model.Probe redisLivenessProbe;
    private io.strimzi.api.kafka.model.Probe redisReadinessProbe;
    private String traceString = "ExpressApp;INFO,Simulated;INFO,KubernetesClient;INFO";
    private Map<String, String> icpClusterData;
    private String oidcSecretName;
    private SecuritySpec.Encryption crEncryptionValue;
    private String enableProducerMetricsPanels;
    private String enableMetricsPanels;

    public AdminUIModel(EventStreams instance,
                        EventStreamsOperatorConfig.ImageLookup imageConfig,
                        Boolean hasRoutes,
                        Map<String, String> icpClusterData) {

        super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), COMPONENT_NAME);

        this.icpClusterData = icpClusterData;
        this.oidcSecretName = ClientModel.getSecretName(instance);

        Optional<AdminUISpec> userInterfaceSpec = Optional
            .ofNullable(instance.getSpec())
            .map(EventStreamsSpec::getAdminUI);

        setReplicas(userInterfaceSpec.map(ComponentSpec::getReplicas).orElse(DEFAULT_REPLICAS));
        setArchitecture(instance.getSpec().getArchitecture());
        setOwnerReference(instance);
        setEnvVars(userInterfaceSpec.map(ContainerSpec::getEnvVars).orElseGet(ArrayList::new));
        setResourceRequirements(userInterfaceSpec.map(ContainerSpec::getResources).orElseGet(ResourceRequirements::new));
        setPodTemplate(userInterfaceSpec.map(ComponentSpec::getTemplate)
                           .map(ComponentTemplate::getPod)
                           .orElseGet(PodTemplate::new));
        setEncryption(SecuritySpec.Encryption.TLS);
        crEncryptionValue = Optional.ofNullable(instance.getSpec())
            .map(EventStreamsSpec::getSecurity)
            .map(SecuritySpec::getEncryption)
            .orElse(DEFAULT_ENCRYPTION);
        setGlobalPullSecrets(Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getImages).map(
            ImagesSpec::getPullSecrets).orElseGet(ArrayList::new));
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

        deployment = createDeployment(getContainers(), getVolumes());
        serviceAccount = createServiceAccount();
        // create required role ref, subject and cluster role binding so the UI can get a list of pods
        roleBinding = createRoleBinding(
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

        ExternalAccess defaultExternalAccess = new ExternalAccessBuilder()
                .withNewType(hasRoutes ? ExternalAccess.TYPE_ROUTE : ExternalAccess.TYPE_DEFAULT)
                .build();
        setExternalAccess(userInterfaceSpec.map(ComponentSpec::getExternalAccess)
                .orElse(defaultExternalAccess));

        service = createService();

        // AdminUI uses OpenShift-generated certificate with TLM `reencrypt` method.
        // It does not use spec.security.encryption setting from from CR
        route = createRoute(UI_SERVICE_PORT,
                new TLSConfigBuilder()
                        .withNewTermination("reencrypt")
                        .build());
        networkPolicy = createNetworkPolicy();
    }

    public AdminUIModel(EventStreams instance,
                        EventStreamsOperatorConfig.ImageLookup imageConfig,
                        Boolean hasRoutes) {
        this(instance, imageConfig, hasRoutes, null);
    }

    public AdminUIModel(EventStreams instance,
                        EventStreamsOperatorConfig.ImageLookup imageConfig) {
        this(instance, imageConfig, false, null);
    }

    public static String getRouteName(EventStreams instance) {
        return AbstractModel.getDefaultResourceName(instance.getMetadata().getName(), COMPONENT_NAME);
    }

    public String getServiceCeritificateName() {
        return getDefaultResourceName() + "-service-cert";
    }

    private List<Volume> getVolumes() {
        Volume redis = new VolumeBuilder()
            .withName("redis-storage")
            .withNewEmptyDir()
            .endEmptyDir()
            .build();

        return Arrays.asList(redis);

    }

    private Service createService() {
        Service service = createService(UI_SERVICE_PORT);
        service.getMetadata().setAnnotations(Collections.singletonMap("service.beta.openshift.io/serving-cert-secret-name", getServiceCeritificateName()));
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

    private List<Container> getContainers() {
        return Arrays.asList(getUIContainer(), getRedisContainer());
    }

    private Container getUIContainer() {

        String restService = getUrlProtocol(crEncryptionValue) + getDefaultResourceName(getInstanceName(), AdminProxyModel.COMPONENT_NAME) + "." +  getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + AdminProxyModel.SERVICE_PORT;

        List<EnvVar> envVarDefaults = new ArrayList<>();

        envVarDefaults.add(new EnvVarBuilder().withName("LICENSE").withValue("accept").build());
        envVarDefaults.add(
            new EnvVarBuilder()
                .withName("TLS_CERT")
                .withNewValueFrom()
                    .withNewSecretKeyRef()
                        .withName(getServiceCeritificateName())
                        .withKey("tls.crt")
                    .endSecretKeyRef()
                .endValueFrom()
                .build());
        envVarDefaults.add(
            new EnvVarBuilder()
                .withName("TLS_KEY")
                .withNewValueFrom()
                    .withNewSecretKeyRef()
                        .withName(getServiceCeritificateName())
                        .withKey("tls.key")
                    .endSecretKeyRef()
                .endValueFrom()
                .build());
        envVarDefaults.add(new EnvVarBuilder().withName("TLS_ENABLED").withValue(String.valueOf(tlsEnabled())).build());
        envVarDefaults.add(new EnvVarBuilder().withName("ID").withValue(getInstanceName()).build());
        envVarDefaults.add(new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build());
        envVarDefaults.add(new EnvVarBuilder().withName("CONFIGMAP").withValue("releaseConfigMap").build()); // FILL OUT
        envVarDefaults.add(new EnvVarBuilder().withName("EVENT_STREAMS_EDITION").withValue("").build()); // FILL OUT
        envVarDefaults.add(new EnvVarBuilder().withName("EVENT_STREAMS_VERSION").withValue("").build()); // FILL OUT
        envVarDefaults.add(new EnvVarBuilder().withName("KAFKA_VERSION").withValue("").build()); // FILL OUT
        envVarDefaults.add(new EnvVarBuilder().withName("EVENT_STREAMS_CHART_VERSION").withValue("").build()); // FILL OUT
        envVarDefaults.add(new EnvVarBuilder().withName("API_URL").withValue(restService).build());
        envVarDefaults.add(new EnvVarBuilder().withName("PRODUCER_METRICS_ENABLED").withValue(enableProducerMetricsPanels).build());
        envVarDefaults.add(new EnvVarBuilder().withName("METRICS_ENABLED").withValue(enableMetricsPanels).build());

        envVarDefaults.add(new EnvVarBuilder().withName("REDIS_HOST").withValue("127.0.0.1").build());

        envVarDefaults.add(new EnvVarBuilder().withName("CLUSTER_NAME").withValue(Main.CLUSTER_NAME).build());
        envVarDefaults.add(new EnvVarBuilder().withName("GEOREPLICATION_ENABLED").withValue("true").build());
        envVarDefaults.add(
            new EnvVarBuilder()
                .withName(TRACE_STATE)
                .withValue(traceString)
                .build()
        );
        envVarDefaults.add(new EnvVarBuilder().withName("ESFF_SECURITY_AUTHZ").withValue("true").build());
        envVarDefaults.add(new EnvVarBuilder().withName("ESFF_SECURITY_AUTH").withValue("true").build());

        envVarDefaults.add(new EnvVarBuilder().withName("ICP_USER_MGMT_IP").withValue("icp-management-ingress.kube-system").build());
        envVarDefaults.add(new EnvVarBuilder().withName("ICP_USER_MGMT_PORT").withValue("443").build());
        envVarDefaults.add(new EnvVarBuilder().withName("ICP_USER_MGMT_HIGHEST_ROLE_FOR_CRN").withValue("idmgmt/identity/api/v1/teams/highestRole").build());

        if (icpClusterData != null && icpClusterData.size() > 0) {
            String clusterIp = icpClusterData.get(ICP_CM_CLUSTER_ADDRESS_KEY);
            String secureClusterPort = icpClusterData.get(ICP_CM_CLUSTER_ROUTER_PORT_KEY);
            String iamClusterName = icpClusterData.get(ICP_CM_CLUSTER_NAME_KEY);
            envVarDefaults.add(new EnvVarBuilder().withName("CLUSTER_EXTERNAL_IP").withValue(clusterIp).build());
            envVarDefaults.add(new EnvVarBuilder().withName("CLUSTER_EXTERNAL_PORT").withValue(secureClusterPort).build());
            envVarDefaults.add(new EnvVarBuilder().withName("IAM_CLUSTER_NAME").withValue(iamClusterName).build());

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

        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(envVarDefaults);

        ResourceRequirements resourceRequirements = getResourceRequirements(
                new ResourceRequirementsBuilder()
                        .addToRequests("cpu", new Quantity("1000m"))
                        .addToRequests("memory", new Quantity("1Gi"))
                        .addToLimits("cpu", new Quantity("1000m"))
                        .addToLimits("memory", new Quantity("1Gi"))
                        .build()
        );

        return new ContainerBuilder()
            .withName(COMPONENT_NAME)
            .withImage(getImage())
            .withEnv(envVars)
            .withSecurityContext(getSecurityContext(false))
            .withResources(resourceRequirements)
            .addNewPort()
                .withName("uiendpoint")
                .withContainerPort(UI_SERVICE_PORT)
            .endPort()
            .withLivenessProbe(createLivenessProbe())
            .withReadinessProbe(createReadinessProbe())
            .build();
    }

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

    private Container getRedisContainer() {

        List<EnvVar> envVarDefaults = Arrays.asList(
            new EnvVarBuilder().withName("LICENSE").withValue("accept").build()
        );

        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(envVarDefaults, redisEnvVars);

        ResourceRequirements defaultResourceRequirements = new ResourceRequirementsBuilder()
                .addToRequests("cpu", new Quantity("100m"))
                .addToRequests("memory", new Quantity("100Mi"))
                .addToLimits("cpu", new Quantity("100m"))
                .addToLimits("memory", new Quantity("100Mi"))
                .build();

        ResourceRequirements resourceRequirements = getResourceRequirements(redisResourceRequirements,
                                                                            defaultResourceRequirements);

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

    private NetworkPolicy createNetworkPolicy() {
        List<NetworkPolicyIngressRule> ingressRules = new ArrayList<>(1);
        ingressRules.add(createIngressRule(UI_SERVICE_PORT, new HashMap<>()));

        List<NetworkPolicyEgressRule> egressRules = new ArrayList<>(1);
        egressRules.add(createEgressRule(AdminProxyModel.SERVICE_PORT, AdminProxyModel.COMPONENT_NAME));

        return createNetworkPolicy(createLabelSelector(COMPONENT_NAME), ingressRules, egressRules);
    }


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

