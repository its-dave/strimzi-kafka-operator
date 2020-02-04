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
import com.ibm.eventstreams.api.Listener;
import com.ibm.eventstreams.api.spec.ComponentSpec;
import com.ibm.eventstreams.api.spec.ComponentTemplate;
import com.ibm.eventstreams.api.spec.ContainerSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.ImagesSpec;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;

import io.fabric8.kubernetes.api.model.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyEgressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyEgressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.fabric8.kubernetes.api.model.rbac.SubjectBuilder;
import io.fabric8.openshift.api.model.Route;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.Logging;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.template.PodTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdminApiModel extends AbstractSecureEndpointModel {

    public static final String COMPONENT_NAME = "admin-api";
    protected static final String FRONTEND_REST_CONTAINER_NAME = "frontend-rest";
    public static final String ADMIN_API_CONTAINER_NAME = "admin-api";
    public static final String FRONTEND_REST_IMAGE = "hyc-qp-stable-docker-local.artifactory.swg-devops.com/eventstreams-rest-icp-linux-amd64:2020-01-17-16.07.02-2c5fca9-exp";
    // This should be 9443 if we're enabling TLS, or 9080 if we're not
    // Other classes should use the getServicePort method to get the port
    private static final int SERVICE_PORT = 9080;
    private static final int SERVICE_PORT_TLS = 9443;
    public static final int DEFAULT_REPLICAS = 1;
    public static final String DEFAULT_IBMCOM_IMAGE = "ibmcom/admin-api:latest";

    private static final String CLUSTER_CA_VOLUME_MOUNT_NAME = "cluster-ca";
    private static final String CERTS_VOLUME_MOUNT_NAME = "certs";
    public static final String ADMIN_CLUSTERROLE_NAME = "eventstreams-admin-clusterrole";

    private static final String CERTIFICATE_PATH = "/opt/ibm/adminapi";
    private static final String KAFKA_USER_CERTIFICATE_PATH = CERTIFICATE_PATH + File.separator + "user";
    private static final String CLUSTER_CERTIFICATE_PATH = CERTIFICATE_PATH + File.separator + "cluster";

    private String traceString = "*=info";
    private final String prometheusHost;
    private final String prometheusPort;
    private final String clusterCaCert;
    private final String icpClusterName;

    private final Deployment deployment;
    private final ServiceAccount serviceAccount;
    private final Service service;
    private final Route route;
    private final NetworkPolicy networkPolicy;
    private final RoleBinding roleBinding;
    
    private static final Logger log = LogManager.getLogger(AdminApiModel.class.getName());
    private List<ListenerStatus> kafkaListeners;

    public AdminApiModel(EventStreams instance,
                         EventStreamsOperatorConfig.ImageLookup imageConfig,
                         List<ListenerStatus> kafkaListeners,
                         Map<String, String> icpClusterData) {
        super(instance, instance.getMetadata().getNamespace(), COMPONENT_NAME);
        this.kafkaListeners = kafkaListeners != null ? new ArrayList<>(kafkaListeners) : new ArrayList<>();
        
        this.prometheusHost = icpClusterData.getOrDefault("cluster_address", "null");
        this.prometheusPort = icpClusterData.getOrDefault("cluster_router_https_port", "null");
        this.clusterCaCert = icpClusterData.getOrDefault("icp_public_cacert", "null");
        this.icpClusterName = icpClusterData.getOrDefault("cluster_name", "null");

        Optional<ComponentSpec> adminApiSpec = Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getAdminApi);

        setOwnerReference(instance);
        setArchitecture(instance.getSpec().getArchitecture());
        setReplicas(adminApiSpec.map(ComponentSpec::getReplicas).orElse(DEFAULT_REPLICAS));
        setResourceRequirements(adminApiSpec.map(ContainerSpec::getResources).orElseGet(ResourceRequirements::new));
        setEnvVars(adminApiSpec.map(ContainerSpec::getEnvVars).orElseGet(ArrayList::new));
        setPodTemplate(adminApiSpec.map(ComponentSpec::getTemplate)
                            .map(ComponentTemplate::getPod)
                            .orElseGet(PodTemplate::new));
        setEncryption(Optional.ofNullable(instance.getSpec())
                            .map(EventStreamsSpec::getSecurity)
                            .map(SecuritySpec::getEncryption)
                            .orElse(DEFAULT_ENCRYPTION));
        setGlobalPullSecrets(Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getImages).map(
            ImagesSpec::getPullSecrets).orElseGet(ArrayList::new));
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

        deployment = createDeployment(getContainers(), getVolumes());
        serviceAccount = createServiceAccount();
        
        roleBinding = createRoleBinding(
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

        service = createService(getServicePort(tlsEnabled()));
        createServices();
        createRoutes();
        route = createRoute(getServicePort(tlsEnabled()));
        networkPolicy = createNetworkPolicy();
    }

    private List<Volume> getVolumes() {

        Volume certsVolume = new VolumeBuilder()
                .withNewName(CERTS_VOLUME_MOUNT_NAME)
                .withNewSecret()
                    .withNewSecretName(getCertSecretName()) //mount everything in the secret into this volume
                .endSecret()
                .build();

        Volume clusterCa = new VolumeBuilder()
            .withNewName(CLUSTER_CA_VOLUME_MOUNT_NAME)
            .withNewSecret()
                .withNewSecretName(getResourcePrefix() + "-cluster-ca-cert")
                .addNewItem().withNewKey(CA_CERT).withNewPath("podtls.crt").endItem()
                .addNewItem().withNewKey(CA_P12).withNewPath("podtls.p12").endItem()
            .endSecret()
            .build();

        Volume replicatorSecret = new VolumeBuilder()
            .withNewName(ReplicatorModel.REPLICATOR_SECRET_NAME)
            .withNewSecret()
                .withNewSecretName(getResourcePrefix() + "-" + ReplicatorModel.REPLICATOR_SECRET_NAME)
                .addNewItem().withNewKey(ReplicatorModel.REPLICATOR_SECRET_KEY_NAME)
                .withNewPath(ReplicatorModel.REPLICATOR_SECRET_KEY_NAME)
            .endItem()
            .endSecret()
            .build();


        return Arrays.asList(createKafkaUserCertVolume(), clusterCa, replicatorSecret, certsVolume);
    }

    private List<Container> getContainers() {
        return Arrays.asList(getRestContainer(), getAdminApiContainer());
    }

    private Container getAdminApiContainer() {
        String runAsKafkaBootstrap = getRunAsKafkaBootstrap(kafkaListeners);

        List<EnvVar> adminApiEnvVars = getAdminApiEnvVars(runAsKafkaBootstrap);

        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(adminApiEnvVars);

        ResourceRequirements resourceRequirements = getResourceRequirements(
            new ResourceRequirementsBuilder()
                    .addToRequests("cpu", new Quantity("500m"))
                    .addToRequests("memory", new Quantity("1Gi"))
                    .addToLimits("cpu", new Quantity("4000m"))
                    .addToLimits("memory", new Quantity("1Gi"))
                    .build()
        );

        return new ContainerBuilder()
            .withName(ADMIN_API_CONTAINER_NAME)
            .withImage(getImage())
            .withEnv(envVars)
            .withSecurityContext(getSecurityContext(false))
            .addNewVolumeMount()
                .withNewName(KAFKA_USER_SECRET_VOLUME_NAME)
                .withMountPath("/certs/p2p")
                .withNewReadOnly(true)
            .endVolumeMount()
                .addNewVolumeMount()
                .withNewName(CERTS_VOLUME_MOUNT_NAME)
                .withMountPath("/certs")
                .withNewReadOnly(true)
            .endVolumeMount()
            .addNewVolumeMount()
                .withNewName(CLUSTER_CA_VOLUME_MOUNT_NAME)
                .withMountPath("/opt/ibm/adminapi/cluster")
                .withNewReadOnly(true)
            .endVolumeMount()
            .addNewVolumeMount()
                .withNewName(ReplicatorModel.REPLICATOR_SECRET_NAME)
                .withMountPath("/etc/georeplication")
                .withNewReadOnly(true)
            .endVolumeMount()
            .withResources(resourceRequirements)
            .withLivenessProbe(createLivenessProbe(Listener.podToPodListener(tlsEnabled()).getPort()))
            .withReadinessProbe(createReadinessProbe(Listener.podToPodListener(tlsEnabled()).getPort()))
            .build();
    }

    private Container getRestContainer() {
        String kafkaConnectBootstrapServers = getResourcePrefix() + "-replicator-connect-api." + getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + ReplicatorModel.REPLICATOR_PORT;
        String temporaryKafkaConnectExternalBootstrap = getResourcePrefix() + "-kafka-bootstrap." + getNamespace() + ".svc." + Main.CLUSTER_NAME + ":9092";
        String kafkaBootstrap = getInternalKafkaBootstrap(kafkaListeners);
        String externalBootstrap = getExternalKafkaBootstrap(kafkaListeners);

        List<EnvVar> defaultEnvVars = getDefaultEnvVars(kafkaConnectBootstrapServers, temporaryKafkaConnectExternalBootstrap, kafkaBootstrap, externalBootstrap);

        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(defaultEnvVars);

        ResourceRequirements resourceRequirements = getResourceRequirements(
            new ResourceRequirementsBuilder()
                    .addToRequests("cpu", new Quantity("500m"))
                    .addToRequests("memory", new Quantity("1Gi"))
                    .addToLimits("cpu", new Quantity("4000m"))
                    .addToLimits("memory", new Quantity("1Gi"))
                    .build()
        );

        return new ContainerBuilder()
            .withName(FRONTEND_REST_CONTAINER_NAME)
            .withImage(FRONTEND_REST_IMAGE)
            .withEnv(envVars)
            .withSecurityContext(getSecurityContext(false))
            .addNewVolumeMount()
                .withNewName(KAFKA_USER_SECRET_VOLUME_NAME)
                .withMountPath("/opt/ibm/wlp/usr/shared/resources/user")
                .withNewReadOnly(true)
            .endVolumeMount()
            .addNewVolumeMount()
                .withNewName(CLUSTER_CA_VOLUME_MOUNT_NAME)
                .withMountPath("/opt/ibm/wlp/usr/shared/resources/cluster")
                .withNewReadOnly(true)
            .endVolumeMount()
            .addNewVolumeMount()
                .withNewName(ReplicatorModel.REPLICATOR_SECRET_NAME)
                .withMountPath("/etc/georeplication")
                .withNewReadOnly(true)
            .endVolumeMount()
            .withResources(resourceRequirements)
            .withLivenessProbe(createLivenessProbe(getServicePort(tlsEnabled())))
            .withReadinessProbe(createReadinessProbe(getServicePort(tlsEnabled())))
            .build();
    }

    private List<EnvVar> getDefaultEnvVars(String kafkaConnectBootstrapServers, String temporaryKafkaConnectExternalBootstrap, String kafkaBootstrap, String externalBootstrap) {
        return Arrays.asList(
                new EnvVarBuilder().withName("KAFKA_STS_NAME").withValue(getResourcePrefix() + "-kafka-config").build(),
                new EnvVarBuilder().withName("RELEASE").withValue(getInstanceName()).build(),
                new EnvVarBuilder().withName("LICENSE").withValue("accept").build(),
                new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build(),
                new EnvVarBuilder().withName("CONFIGMAP").withValue(getResourcePrefix() + "-kafka-config").build(),
                new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_URL").withValue(kafkaBootstrap).build(),
                new EnvVarBuilder().withName("KAFKA_ADVERTISED_LISTENER_BOOTSTRAP_ADDRESS").withValue(externalBootstrap).build(),
                new EnvVarBuilder().withName("TRACE_SPEC").withValue(traceString).build(),
                new EnvVarBuilder().withName("ESFF_SECURITY_AUTHZ").withValue("false").build(),
                new EnvVarBuilder().withName("ESFF_SECURITY_KAFKA_TLS").withValue(String.valueOf(tlsEnabled())).build(),
                new EnvVarBuilder().withName("GEOREPLICATION_ENABLED").withValue("true").build(),
                new EnvVarBuilder().withName("KAFKA_CONNECT_BOOTSTRAP_SERVERS").withValue(kafkaConnectBootstrapServers).build(),
                new EnvVarBuilder().withName("TEMPORARY_CONNECT_EXTERNAL_BOOTSTRAP").withValue(temporaryKafkaConnectExternalBootstrap).build(),
                new EnvVarBuilder().withName("REPLICATORKEYS_SECRET_NAME").withValue(getResourcePrefix() + "-" + ReplicatorModel.REPLICATOR_SECRET_NAME).build(),
                new EnvVarBuilder()
                        .withName("CLUSTER_P12_PASSWORD")
                        .withNewValueFrom()
                        .withNewSecretKeyRef()
                        .withName(getResourcePrefix() + "-cluster-ca-cert")
                        .withKey(CA_P12_PASS)
                        .withOptional(true)
                        .endSecretKeyRef()
                        .endValueFrom()
                        .build(),
                new EnvVarBuilder()
                        .withName("USER_P12_PASSWORD")
                        .withNewValueFrom()
                        .withNewSecretKeyRef()
                        .withName(getInternalKafkaUserSecretName())
                        .withKey(USER_P12_PASS)
                        .withOptional(true)
                        .endSecretKeyRef()
                        .endValueFrom()
                        .build(),

                new EnvVarBuilder()
                        .withName("ES_CACERT")
                        .withNewValueFrom()
                        .withNewSecretKeyRef()
                        .withName(getResourcePrefix() + "-cluster-ca-cert")
                        .withKey("ca.crt")
                        .withOptional(true)
                        .endSecretKeyRef()
                        .endValueFrom()
                        .build(),

                new EnvVarBuilder().withName("PROMETHEUS_HOST").withValue(prometheusHost).build(),
                new EnvVarBuilder().withName("PROMETHEUS_PORT").withValue(prometheusPort).build(),
                new EnvVarBuilder().withName("CLUSTER_CACERT").withValue(clusterCaCert).build()
        );
    }

    private List<EnvVar> getAdminApiEnvVars(String runasKafkaBootstrap) {
        List<Listener> listeners = getListeners();
        listeners.add(Listener.podToPodListener(tlsEnabled()));
        return Arrays.asList(
            new EnvVarBuilder().withName("ENDPOINTS").withValue(Listener.createEndpointsString(listeners)).build(),
            new EnvVarBuilder().withName("RELEASE").withValue(getInstanceName()).build(),
            new EnvVarBuilder().withName("LICENSE").withValue("accept").build(),
            new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build(),
            new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_URL").withValue(runasKafkaBootstrap).build(),
            new EnvVarBuilder().withName("IAM_CLUSTER_NAME").withValue(icpClusterName).build(),
            new EnvVarBuilder().withName("SSL_TRUSTSTORE_PATH").withValue(CLUSTER_CERTIFICATE_PATH + File.separator + CA_P12).build(),
            new EnvVarBuilder()
                .withName("SSL_TRUSTSTORE_PASSWORD")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(getInternalKafkaUserSecretName())
                .withKey(CA_P12_PASS)
                .withOptional(true)
                .endSecretKeyRef()
                .endValueFrom()
                .build(),
            new EnvVarBuilder().withName("SSL_KEYSTORE_PATH").withValue(KAFKA_USER_CERTIFICATE_PATH + File.separator + USER_P12).build(),
            new EnvVarBuilder()
                .withName("SSL_KEYSTORE_PASSWORD")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(getResourcePrefix() + "-cluster-ca-cert")
                .withKey(CA_P12_PASS)
                .withOptional(true)
                .endSecretKeyRef()
                .endValueFrom()
                .build()
        );
    }

    public static int getServicePort(boolean tlsEnabled) {
        if (tlsEnabled) {
            return SERVICE_PORT_TLS;
        }
        return SERVICE_PORT;
    }

    protected Probe createLivenessProbe(int port) {
        Probe defaultLivenessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/liveness")
                .withNewPort(port)
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

    protected Probe createReadinessProbe(int port) {
        Probe defaultReadinessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/liveness")
                .withNewPort(port)
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
     * @return Deployment return the deployment with the specified generation id this is used
     * to control rolling updates, for example when the cert secret changes.
     */
    public Deployment getDeployment(String certGenerationID) {
        deployment.getMetadata().getLabels().put(CERT_GENERATION_KEY, certGenerationID);
        deployment.getSpec().getTemplate().getMetadata().getLabels().put(CERT_GENERATION_KEY, certGenerationID);
        return deployment;
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
     * @return RoleBinding return the roleBinding
     */
    public RoleBinding getRoleBinding() {
        return this.roleBinding;
    }


    private NetworkPolicy createNetworkPolicy() {
        List<NetworkPolicyIngressRule> ingressRules = new ArrayList<>(1);
        ingressRules.add(createIngressRule(getServicePort(tlsEnabled()), new HashMap<>()));
        List<Listener> listeners = getListeners();
        listeners.add(Listener.podToPodListener(tlsEnabled()));
        listeners.forEach(listener -> {
            ingressRules.add(createIngressRule(listener.getPort(), new HashMap<>()));
        });

        List<NetworkPolicyEgressRule> egressRules = new ArrayList<>(4);  // TODO up this if add index
        egressRules.add(createEgressRule(EventStreamsKafkaModel.KAFKA_PORT, EventStreamsKafkaModel.KAFKA_COMPONENT_NAME));
        egressRules.add(createEgressRule(Listener.podToPodListener(tlsEnabled()).getPort(), SchemaRegistryModel.COMPONENT_NAME));
        egressRules.add(createEgressRule(EventStreamsKafkaModel.ZOOKEEPER_PORT, EventStreamsKafkaModel.ZOOKEEPER_COMPONENT_NAME));
        egressRules.add(createEgressRule(ReplicatorModel.REPLICATOR_PORT, ReplicatorModel.COMPONENT_NAME));

        // TODO Egress rule -> index manager

        // Egress rule -> 8443 and 443
        egressRules.add(new NetworkPolicyEgressRuleBuilder()
            .addNewPort().withNewPort(8443).endPort()
            .addNewPort().withNewPort(443).endPort()
            .build());

        // Egress rule -> 53 UDP
        egressRules.add(new NetworkPolicyEgressRuleBuilder()
            .addNewPort().withNewPort(53).withProtocol("UDP").endPort()
            .build());

        return createNetworkPolicy(createLabelSelector(COMPONENT_NAME), ingressRules, egressRules);
    }

    private void setTraceString(Logging logging) {
        if (logging != null && InlineLogging.TYPE_INLINE.equals(logging.getType())) {
            Map<String, String> loggers = ((InlineLogging) logging).getLoggers();
            List<String> loggersArray = new ArrayList<>();
            loggers.forEach((k, v) -> {
                loggersArray.add(k + "=" + v);
            });
            this.traceString = String.join(",", loggersArray);
        }
    }
}
