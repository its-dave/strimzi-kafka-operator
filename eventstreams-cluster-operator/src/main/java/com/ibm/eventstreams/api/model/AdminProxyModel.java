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
import com.ibm.eventstreams.api.spec.ExternalAccess;
import com.ibm.eventstreams.api.spec.ExternalAccessBuilder;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import io.fabric8.kubernetes.api.model.ConfigMap;
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
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.TLSConfig;
import io.strimzi.api.kafka.model.template.PodTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdminProxyModel extends AbstractModel {

    public static final String COMPONENT_NAME = "admin-proxy";
    public static final int SERVICE_PORT = 9443;
    public static final int HEALTH_PORT = 32010;
    public static final int DEFAULT_REPLICAS = 1;

    private static final String CONFIG_VOLUME_MOUNT_NAME = "config";
    private static final String KAFKA_BROKERS_VOLUME_MOUNT_NAME = "kafka-brokers";
    private static final String DEFAULT_IBMCOM_IMAGE = "ibmcom/admin-proxy:latest";

    private Deployment deployment;
    private ServiceAccount serviceAccount;
    private ConfigMap configmap;
    private Service service;
    private Route route;
    private NetworkPolicy networkPolicy;

    public AdminProxyModel(EventStreams instance,
                           EventStreamsOperatorConfig.ImageLookup imageConfig,
                           Boolean hasRoutes) {
        super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), COMPONENT_NAME);

        Optional<ComponentSpec> adminProxySpec = Optional.ofNullable(instance.getSpec())
            .map(EventStreamsSpec::getAdminProxy);

        setOwnerReference(instance);
        setArchitecture(instance.getSpec().getArchitecture());
        setReplicas(adminProxySpec.map(ComponentSpec::getReplicas).orElse(DEFAULT_REPLICAS));
        setResourceRequirements(adminProxySpec.map(ComponentSpec::getResources).orElseGet(ResourceRequirements::new));
        setEnvVars(adminProxySpec.map(ContainerSpec::getEnvVars).orElseGet(ArrayList::new));
        setPodTemplate(adminProxySpec.map(ComponentSpec::getTemplate)
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
                DEFAULT_IBMCOM_IMAGE, adminProxySpec.map(ComponentSpec::getImage),
                imageConfig.getAdminProxyImage()));
        setCustomImage(DEFAULT_IBMCOM_IMAGE, imageConfig.getAdminProxyImage());
        setLivenessProbe(adminProxySpec.map(ComponentSpec::getLivenessProbe)
                .orElseGet(io.strimzi.api.kafka.model.Probe::new));
        setReadinessProbe(adminProxySpec.map(ComponentSpec::getReadinessProbe)
                .orElseGet(io.strimzi.api.kafka.model.Probe::new));

        ExternalAccess defaultExternalAccess = new ExternalAccessBuilder()
                .withNewType(hasRoutes ? ExternalAccess.TYPE_ROUTE : ExternalAccess.TYPE_DEFAULT)
                .build();
        setExternalAccess(adminProxySpec.map(ComponentSpec::getExternalAccess)
                .orElse(defaultExternalAccess));

        deployment = createDeployment(getContainers(), getVolumes());
        configmap = createConfigMap(getProxyConfigMap());
        service = createService(SERVICE_PORT);
        serviceAccount = createServiceAccount();
        networkPolicy = createNetworkPolicy();

        TLSConfig tlsConfig = tlsEnabled() ? getDefaultTlsConfig() : null;
        route = createRoute(getRouteName(), getDefaultResourceName(), SERVICE_PORT, tlsConfig);
    }

    public AdminProxyModel(EventStreams instance,
                           EventStreamsOperatorConfig.ImageLookup imageConfig) {
        this(instance, imageConfig, false);
    }

    private Map<String, String> getProxyConfigMap() {

        String restAdminURL = getUrlProtocol() + AbstractSecureEndpointModel.getInternalServiceName(getInstanceName(), AdminApiModel.COMPONENT_NAME) + "." + getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" +  Listener.podToPodListener(tlsEnabled()).getPort();
        String restProducerURL = getUrlProtocol() + AbstractSecureEndpointModel.getInternalServiceName(getInstanceName(), RestProducerModel.COMPONENT_NAME) + "." + getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + Listener.podToPodListener(tlsEnabled()).getPort();
        String schemaRegistryURL = getUrlProtocol() + AbstractSecureEndpointModel.getInternalServiceName(getInstanceName(), SchemaRegistryModel.COMPONENT_NAME) + "." + getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + Listener.podToPodListener(tlsEnabled()).getPort();
        String config = new StringBuilder()
                .append("# Config File\n")
                .append("daemon off;\n")
                .append("worker_processes  5;\n")
                .append("pid /var/run/nginx.pid;\n")
                .append("events {\n")
                .append("  worker_connections 1024;\n")
                .append("}\n")
                .append("http {\n")
                .append("  include           /etc/nginx/mime.types;\n")
                .append("  default_type      application/octet-stream;\n")
                .append("  server_tokens     off;\n")
                .append("  add_header        Strict-Transport-Security \\\"max-age=31536000\\\" always;\n")
                .append("  map $http_content_type $producer_content_type {\n")
                .append("    default \"text/plain\";\n")
                .append("    \"~application/json\" \"application/json\";\n")
                .append("  }\n")
                .append("  map $http_accept $producer_accept {\n")
                .append("    default \"text/plain\";\n")
                .append("    \"~application/json\" \"application/json\";\n")
                .append("    \"*/*\" \"*/*\";\n")
                .append("  }\n")
                .append(addNewServer(Main.CLUSTER_NAME))
                .append(addListener(SERVICE_PORT))
                .append(addLocation("/", restAdminURL, "/"))
                .append(addLocation("/topics", restProducerURL, "/topics"))
                .append(addLocation("/files/schemas", schemaRegistryURL, "/files/schemas"))
                .append(addLocation("/files/ca-certificates", restAdminURL, "/admin/certificates"))
                .append(addLocation("/schemas", schemaRegistryURL, "/api/schemas"))
                .append(addLocation("/schemas/ids", schemaRegistryURL, "/compatibility/schemas/ids"))
                .append(addLocation("/subjects", schemaRegistryURL, "/compatibility/subjects"))
                .append(addStaticFilesLocation())
                .append(endServer())
                .append(addNewServer("healthcheck"))
                .append(addListener(HEALTH_PORT))
                .append(addHealthCheckLocation())
                .append(endServer())
                .append("}\n")
                .toString();

        Map<String, String> data = new HashMap<String, String>();
        data.put("nginx.conf", config);
        return data;
    }

    private String addNewServer(String serverName) {
        return new StringBuilder()
            .append("  server {\n")
            .append("    server_name " + serverName + ";\n")
            .toString();
    }
    private String endServer() {
        return "  }\n";
    }
    private String addHealthCheckLocation() {
        return new StringBuilder()
        .append("    location /live {\n")
        .append("      return 200;\n")
        .append("    }\n")
        .append("    location /ready {\n")
        .append("      return 200;\n")
        .append("    }\n")
        .toString();
    }
    private String addStaticFilesLocation() {
        return new StringBuilder()
        .append("    location /files/ {\n")
        .append("      root /etc/nginx/static;\n")
        .append("    }\n")
        .toString();
    }

    private String addListener(int port) {
        StringBuilder sb = new StringBuilder();
        if (tlsEnabled()) {
            sb.append("    listen " + port + " ssl;\n")
                .append("    ssl_certificate /etc/nginx/ssl/external/tls.crt;\n")
                .append("    ssl_certificate_key /etc/nginx/ssl/external/tls.key;\n")
                .append("    ssl_ciphers EECDH+AESGCM:EDH+AESGCM:AES256+EECDH:AES256+EDH;\n")
                .append("    ssl_prefer_server_ciphers on;\n")
                .append("    ssl_protocols TLSv1.2;\n");
        } else {
            sb.append("    listen " + port + ";\n");
        }
        return sb.toString();
    }

    private String addLocation(String location, String url, String path) {
        StringBuilder sb = new StringBuilder()
                .append("    location " + location + " {\n")
                .append("      proxy_pass " + url + path + ";\n")
                .append("      proxy_pass_request_headers on;\n")
                .append("      proxy_pass_request_body    on;\n");

        if (tlsEnabled()) {
            sb.append("      proxy_ssl_certificate         /etc/nginx/ssl/internal/podtls.crt;\n")
                .append("      proxy_ssl_certificate_key     /etc/nginx/ssl/internal/podtls.key;\n")
                .append("      proxy_ssl_protocols     TLSv1.2;\n");
        }
        if (location.equals("/topics")) {
            sb.append("      proxy_set_header           Content-Type $producer_content_type;\n")
                .append("      proxy_set_header           Accept $producer_accept;\n");
        }
        sb.append("    }\n");

        return sb.toString();
    }

    private List<Volume> getVolumes() {
        Volume configVolume = new VolumeBuilder()
            .withNewName(CONFIG_VOLUME_MOUNT_NAME)
            .withNewConfigMap()
                .withNewName(getConfigMapName())
            .endConfigMap()
            .build();

        String kafkaInstanceName = EventStreamsKafkaModel.getKafkaInstanceName(getInstanceName());

        Volume kafkaBrokersVolume = new VolumeBuilder()
            .withNewName(KAFKA_BROKERS_VOLUME_MOUNT_NAME)
            .withNewSecret()
                .withNewSecretName(EventStreamsKafkaModel.getKafkaBrokersSecretName(getInstanceName()))
                .addNewItem().withNewKey(kafkaInstanceName + "-kafka-0.crt").withNewPath("tls.crt").endItem()
                .addNewItem().withNewKey(kafkaInstanceName + "-kafka-0.key").withNewPath("tls.key").endItem()
            .endSecret()
            .build();

        return Arrays.asList(configVolume, kafkaBrokersVolume, createKafkaUserCertVolume());
    }

    private List<Container> getContainers() {
        return Arrays.asList(getAdminProxyContainer());
    }

    private Container getAdminProxyContainer() {

        List<EnvVar> envVarDefaults = Arrays.asList(
            new EnvVarBuilder().withName("CONFIGMAP").withValue(getDefaultResourceName()).build(),
            new EnvVarBuilder().withName("LICENSE").withValue("accept").build(),
            new EnvVarBuilder().withName("TLS_ENABLED").withValue(String.valueOf(tlsEnabled())).build(),
            new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build()
        );

        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(envVarDefaults);

        ResourceRequirements resourceRequirements = getResourceRequirements(
                new ResourceRequirementsBuilder()
                        .addToRequests("cpu", new Quantity("500m"))
                        .addToRequests("memory", new Quantity("250Mi"))
                        .addToLimits("cpu", new Quantity("500m"))
                        .addToLimits("memory", new Quantity("250Mi"))
                        .build()
        );

        return new ContainerBuilder()
            .addNewPort()
                .withNewName(COMPONENT_NAME)
                .withContainerPort(SERVICE_PORT)
            .endPort()
            .withName(COMPONENT_NAME)
            .withImage(getImage())
            .withEnv(envVars)
            .addNewVolumeMount()
                .withName(CONFIG_VOLUME_MOUNT_NAME)
                .withMountPath("/nginx.conf")
                .withNewSubPath("nginx.conf")
            .endVolumeMount()
            .addNewVolumeMount()
                .withName(KAFKA_USER_SECRET_VOLUME_NAME)
                .withMountPath("/etc/nginx/ssl/internal")
            .endVolumeMount()
            .addNewVolumeMount()
                .withName(KAFKA_BROKERS_VOLUME_MOUNT_NAME)
                .withMountPath("/etc/nginx/ssl/external")
            .endVolumeMount()
            .withSecurityContext(getSecurityContext(false))
            .withResources(resourceRequirements)
            .withLivenessProbe(createLivenessProbe())
            .withReadinessProbe(createReadinessProbe())
            .build();
    }

    protected Probe createLivenessProbe() {
        Probe defaultLivenessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/live")
                .withNewPort(HEALTH_PORT)
                .withScheme(getHealthCheckProtocol())
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(15)
                .withPeriodSeconds(15)
                .withTimeoutSeconds(15)
                .withSuccessThreshold(1)
                .withFailureThreshold(6)
                .build();
        return combineProbeDefinitions(defaultLivenessProbe, super.getLivenessProbe());
    }

    protected Probe createReadinessProbe() {
        Probe defaultReadinessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/ready")
                .withNewPort(HEALTH_PORT)
                .withScheme(getHealthCheckProtocol())
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(1)
                .withPeriodSeconds(5)
                .withTimeoutSeconds(5)
                .withSuccessThreshold(1)
                .withFailureThreshold(3)
                .build();
        return combineProbeDefinitions(defaultReadinessProbe, super.getReadinessProbe());
    }

    private NetworkPolicy createNetworkPolicy() {
        List<NetworkPolicyEgressRule> egressRules = new ArrayList<>(3);
        egressRules.add(createEgressRule(Listener.podToPodListener(tlsEnabled()).getPort(), AdminApiModel.COMPONENT_NAME));
        egressRules.add(createEgressRule(Listener.podToPodListener(tlsEnabled()).getPort(), RestProducerModel.COMPONENT_NAME));
        egressRules.add(createEgressRule(Listener.podToPodListener(tlsEnabled()).getPort(), SchemaRegistryModel.COMPONENT_NAME));
        List<NetworkPolicyIngressRule> ingressRules = new ArrayList<>(1);
        ingressRules.add(new NetworkPolicyIngressRule());
        return createNetworkPolicy(createLabelSelector(COMPONENT_NAME), ingressRules, egressRules);
    }
    public static String getRouteName(EventStreams instance) {
        return getDefaultResourceName(instance.getMetadata().getName(), COMPONENT_NAME);
    }
    /**
     * @return getDeployment returns the deployment
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
     * @return getConfigMap returns the deployment
     */
    public ConfigMap getConfigMap() {
        return this.configmap;
    }
    /**
     * @return getService returns the service
     */
    public Service getService() {
        return this.service;
    }

    /**
     * @return getRoute returns the route
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
}
