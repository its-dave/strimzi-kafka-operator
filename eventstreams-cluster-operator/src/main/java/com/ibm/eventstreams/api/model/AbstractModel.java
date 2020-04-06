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
import com.ibm.eventstreams.api.Labels;
import com.ibm.eventstreams.api.TlsVersion;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.ExternalAccess;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyBuilder;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyEgressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyEgressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.TLSConfig;
import io.fabric8.openshift.api.model.TLSConfigBuilder;
import io.strimzi.api.kafka.model.ContainerEnvVar;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserBuilder;
import io.strimzi.api.kafka.model.KafkaUserSpec;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternal;
import io.strimzi.api.kafka.model.listener.KafkaListenerTls;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.strimzi.api.kafka.model.status.ListenerAddress;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.template.PodTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"checkstyle:ClassFanOutComplexity", "checkstyle:ClassDataAbstractionCoupling"})
public abstract class AbstractModel {

    public static final String APP_NAME = "ibm-es";
    public static final String APP_NAME_TRUNCATED = "es";
    public static final String DEFAULT_PROMETHEUS_PORT = "8081";
    public static final String DEFAULT_COMPONENT_NAME = "eventstreams";
    public static final int MAX_RESOURCE_NAME_LENGTH = 63;

    public static final String CONFIG_MAP_SUFFIX = "-config";

    protected static final String TLS_VERSION_ENV_KEY = "TLS_VERSION";
    public static final TlsVersion DEFAULT_INTERNAL_TLS = TlsVersion.NONE;
    protected static final TlsVersion DEFAULT_TLS_VERSION = TlsVersion.TLS_V1_2;
    public static final String AUTHENTICATION_LABEL_SEPARATOR = "-";
    public static final String AUTHENTICATION_LABEL_NO_AUTH = "NO-AUTHENTICATION";

    private static final String PRODUCT_ID = "ID";
    private static final String PRODUCT_NAME = "eventstreams";
    private static final String PRODUCT_VERSION = "version";
    private static final String PRODUCT_CLOUDPAK_RATIO_PRODUCTION = "1:1";
    private static final String PRODUCT_CLOUDPAK_RATIO_NON_PRODUCTION = "2:1";
    private static final String PRODUCT_METRIC = "VIRTUAL_PROCESSOR_CORE";
    private static final String CLOUDPAK_ID = "c8b82d189e7545f0892db9ef2731b90d";
    private static final String CLOUDPAK_NAME = "IBM Cloud Pak for Integration";
    private static final String CLOUDPAK_VERSION = "2019.4.1";
    private static final String RUNAS_LISTENER_TYPE = "runas";

    public static final String KAFKA_USER_SECRET_VOLUME_NAME = "kafka-user";
    public static final String CA_CERT = "ca.crt";
    public static final String CA_P12 = "ca.p12";
    public static final String CA_P12_PASS = "ca.password";
    public static final String USER_CERT = "user.crt";
    public static final String USER_KEY = "user.key";
    public static final String USER_P12 = "user.p12";
    public static final String USER_P12_PASS = "user.password";
    public static final String SCRAM_PASSWORD = "password";

    private String kind;
    private String apiVersion;
    private String uid;
    private String componentName;
    private List<ContainerEnvVar> envVars;
    private String instanceName;
    private String namespace;
    private int replicas;
    private ExternalAccess externalAccess;
    private ResourceRequirements resourceRequirements;
    private PodTemplate podTemplate;
    private TlsVersion tlsVersion;

    private List<LocalObjectReference> globalPullSecrets;
    protected String image = "";
    protected boolean customImage;
    private io.strimzi.api.kafka.model.Probe livenessProbe;
    private io.strimzi.api.kafka.model.Probe readinessProbe;

    private static final Logger log = LogManager.getLogger();

    protected AbstractModel(String instanceName, String namespace, String componentName) {
        this.instanceName = instanceName;
        this.namespace = namespace;
        this.componentName = componentName;
    }

    public String getComponentName() {
        return componentName;
    }

    protected List<LocalObjectReference> getGlobalPullSecrets() {
        return globalPullSecrets;
    }

    protected void setGlobalPullSecrets(List<LocalObjectReference> globalPullSecrets) {
        this.globalPullSecrets = globalPullSecrets;
    }

    protected String getInstanceName() {
        return instanceName;
    }

    protected List<ContainerEnvVar> getEnvVars() {
        return envVars;
    }

    protected void setPodTemplate(PodTemplate podTemplate) {
        this.podTemplate = podTemplate;
    }

    protected void setEnvVars(List<ContainerEnvVar> envVars) {
        this.envVars = envVars;
    }

    protected String getNamespace() {
        return namespace;
    }

    protected void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    protected void setExternalAccess(ExternalAccess externalAccess) {
        this.externalAccess = externalAccess;
    }

    protected void setResourceRequirements(ResourceRequirements resourceRequirements) {
        this.resourceRequirements = resourceRequirements;
    }

    protected void setTlsVersion(TlsVersion tlsVersion) {
        this.tlsVersion = tlsVersion;
    }

    protected TlsVersion getTlsVersion() {
        return tlsVersion;
    }

    protected io.strimzi.api.kafka.model.Probe getLivenessProbe() {
        return livenessProbe;
    }

    protected void setLivenessProbe(io.strimzi.api.kafka.model.Probe livenessProbe) {
        this.livenessProbe = livenessProbe;
    }

    protected io.strimzi.api.kafka.model.Probe getReadinessProbe() {
        return readinessProbe;
    }

    protected void setReadinessProbe(io.strimzi.api.kafka.model.Probe readinessProbe) {
        this.readinessProbe = readinessProbe;
    }

    protected Boolean tlsEnabled() {
        return tlsEnabled(getTlsVersion());
    }

    protected static Boolean tlsEnabled(TlsVersion tlsVersion) {
        switch (tlsVersion) {
            case NONE: return false;
            default: return true;
        }
    }

    protected String getUrlProtocol() {
        return tlsEnabled() ? "https://" : "http://";
    }

    protected String getUrlProtocol(TlsVersion internalTLS) {
        switch (internalTLS) {
            case NONE: return "http://";
            default: return "https://";
        }
    }

    protected String getHealthCheckProtocol() {
        return tlsEnabled() ? "HTTPS" : "HTTP";
    }

    public String getResourcePrefix() {
        return getResourcePrefix(instanceName);
    }

    protected static String getResourcePrefix(String instanceName) {
        return instanceName + "-" + APP_NAME;
    }

    protected static String getResourcePrefixTruncatedAppName(String instanceName) {
        return instanceName + "-" + APP_NAME_TRUNCATED;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public boolean getCustomImage() {
        return this.customImage;
    }

    protected void setCustomImage(String defaultIBMComImage, Optional<String> defaultEnvImage) {
        List<String> defaultImages = new ArrayList<>();
        defaultImages.add(defaultIBMComImage);
        defaultImages.add(defaultEnvImage.orElse(""));
        this.customImage = !defaultImages
                .stream()
                .anyMatch(image -> this.image.equals(image));
    }

    public OwnerReference getEventStreamsOwnerReference() {
        return new OwnerReferenceBuilder()
            .withApiVersion(apiVersion)
            .withKind(kind)
            .withName(instanceName)
            .withUid(uid)
            .withBlockOwnerDeletion(false)
            .withController(false)
            .build();
    }

    protected void setOwnerReference(EventStreams instance) {
        this.apiVersion = instance.getApiVersion();
        this.kind = instance.getKind();
        this.uid = instance.getMetadata().getUid();
    }

    private List<LocalObjectReference> getPullSecrets() {

        return Stream.of(Optional.ofNullable(podTemplate).map(PodTemplate::getImagePullSecrets),
                         Optional.ofNullable(globalPullSecrets))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    public String getDefaultResourceName() {
        return getDefaultResourceName(instanceName, componentName);
    }

    public String getComponentNameWithSuffix(String name) {
        return name.isEmpty() ? getComponentName() : getComponentName() + "-" + name;
    }

    public String getDefaultResourceNameWithSuffix(String suffix) {
        return getDefaultResourceNameWithSuffix(suffix, getInstanceName(), getComponentName());
    }

    public static String getDefaultResourceNameWithSuffix(String suffix, String instanceName, String componentName) {
        if (suffix.isEmpty()) {
            return getDefaultResourceName(instanceName, componentName);
        }

        final int minPrefixWithComponentLength = 12;
        // -1 to account for '-'
        int maxPrefixWithComponentLength = MAX_RESOURCE_NAME_LENGTH - suffix.length() - 1;
        final int maxSuffixStringLength = 50;
        final String resourceNameWithoutTruncation = getDefaultResourceName(instanceName, componentName) + "-" + suffix;
        if (resourceNameWithoutTruncation.length() < MAX_RESOURCE_NAME_LENGTH) {
            return resourceNameWithoutTruncation;
        }
        if (maxPrefixWithComponentLength < minPrefixWithComponentLength) {
            // Suffix is at least 51 characters (MAX_RESOURCE_NAME_LENGTH - 12)
            suffix = suffix.substring(0, maxSuffixStringLength - 5) + '-' + getHash(suffix).substring(0, 4);
            maxPrefixWithComponentLength = MAX_RESOURCE_NAME_LENGTH - suffix.length() - 1;
        }
        return getDefaultResourceNameWithTruncation(instanceName, componentName, maxPrefixWithComponentLength) + "-" + suffix;
    }

    public static String getDefaultResourceName(String instanceName, String componentName) {
        return getDefaultResourceNameWithTruncation(instanceName, componentName, MAX_RESOURCE_NAME_LENGTH);
    }

    private static String getDefaultResourceNameWithTruncation(String instanceName, String componentName, int maxPrefixWithComponentLength) {
        String noTruncationResourceName = getResourcePrefix(instanceName) + "-" + componentName;
        if (noTruncationResourceName.length() > maxPrefixWithComponentLength) {
            return getResourcePrefixTruncated(instanceName, componentName, maxPrefixWithComponentLength);
        }
        return noTruncationResourceName;
    }

    private static String getResourcePrefixTruncated(String instanceName, String componentName, int maxPrefixWithComponentLength) {
        // Truncate the App name and avoid further truncation, if possible
        String truncatedAppNamePrefixResourceName = getResourcePrefixTruncatedAppName(instanceName) + "-" + componentName;
        if (truncatedAppNamePrefixResourceName.length() > maxPrefixWithComponentLength) {
            // Hash the Instance name
            int maxInstanceNameLength = maxPrefixWithComponentLength - APP_NAME_TRUNCATED.length() - componentName.length() - 2;
            int charactersOfInstanceNameToKeep = Math.min(maxInstanceNameLength, instanceName.length());
            if (maxInstanceNameLength < 5) {
                // If the number of characters available for the prefix is insufficient, drop the App name
                maxInstanceNameLength = maxPrefixWithComponentLength - componentName.length() - 2;
                if (maxInstanceNameLength > 5) {
                    charactersOfInstanceNameToKeep = Math.max(charactersOfInstanceNameToKeep, maxInstanceNameLength);
                    instanceName = instanceName.substring(0, charactersOfInstanceNameToKeep - 5) + '-' + getHash(instanceName).substring(0, 4);
                    return instanceName + "-" + componentName;
                }
            // Sufficient characters to hash Instance name whilst keeping truncated App name
            } else {
                instanceName = instanceName.substring(0, charactersOfInstanceNameToKeep - 5) + '-' + getHash(instanceName).substring(0, 4);
                return getResourcePrefixTruncatedAppName(instanceName) + "-" + componentName;
            }
        }
        return truncatedAppNamePrefixResourceName;
    }

    /**
     * Hash a given String using SHA-256 converting it to a String using a Base64 encoder.
     * If digest cannot be created due to a NoSuchAlgorithmException, return the original
     * value which will lead to a name which is too long being returned and an error
     * which will be logged
     * @param value to be hashed
     * @return hashed value encoded to String
     */
    private static String getHash(String value) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error whilst encrypting instance name: {}", e);
            return value;
        }
    }

    public String getRouteName() {
        return getRouteName("");
    }

    public String getRouteName(String suffix) {
        return getDefaultResourceNameWithSuffix(suffix);
    }

    protected Map<String, String> getEventStreamsMeteringAnnotations() {
        return getEventStreamsMeteringAnnotations("");
    }

    protected Map<String, String> getEventStreamsMeteringAnnotations(String chargedContainers) {
        Map<String, String> annotations = new HashMap<String, String>();

        annotations.put("productID", PRODUCT_ID);
        annotations.put("productName", PRODUCT_NAME);
        annotations.put("productVersion", PRODUCT_VERSION);
        annotations.put("cloudpakId", CLOUDPAK_ID);
        annotations.put("cloudpakName", CLOUDPAK_NAME);
        annotations.put("cloudpakVersion", CLOUDPAK_VERSION);
        annotations.put("productChargedContainers", chargedContainers);
        annotations.put("productCloudpakRatio", PRODUCT_CLOUDPAK_RATIO_PRODUCTION);
        annotations.put("productMetric", PRODUCT_METRIC);

        return annotations;
    }

    protected Map<String, String> getPrometheusAnnotations(String port) {
        Map<String, String> annotations = new HashMap<String, String>();

        annotations.put("prometheus.io/port", port);
        annotations.put("prometheus.io/scheme", "https");
        annotations.put("prometheus.io/scrape", "true");

        return annotations;
    }

    protected Map<String, String> getPrometheusAnnotations() {

        return getPrometheusAnnotations(DEFAULT_PROMETHEUS_PORT);
        
    }

    // If creating a Strimzi Custom Resource please use getComponentLabelsWithoutResourceGroup
    public Map<String, String> getComponentLabels() {
        Map<String, String> labels = getGenericLabels();
        labels.putAll(getResourceGroupLabels());

        return labels;
    }

    // getComponentLabelsWithoutResourceGroup returns the component labels without any of the banned Strimzi namespaced labels
    // Use this function to create labels for Strimzi Custom Resources
    public Map<String, String> getComponentLabelsWithoutResourceGroup() {
        Map<String, String> labels = getGenericLabels();

        return labels;
    }

    public Map<String, String> generateSecurityLabels(boolean isTls, List<String> authenticationMechanisms) {
        Map<String, String> labels = new HashMap<>();
        if (authenticationMechanisms.size() > 0) {
            authenticationMechanisms.forEach(auth -> {
                labels.put(Labels.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + auth, "true");
            });
        } else {
            labels.put(Labels.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + AUTHENTICATION_LABEL_NO_AUTH, "true");
        }
        labels.put(Labels.EVENTSTREAMS_PROTOCOL_LABEL, isTls ? "https" : "http");
        return labels;
    }

    private Map<String, String> getGenericLabels() {
        Map<String, String> labels = new HashMap<>();

        labels.put(Labels.APP_LABEL, APP_NAME);
        labels.put(Labels.COMPONENT_LABEL, this.componentName);
        labels.put(Labels.INSTANCE_LABEL, this.instanceName);
        labels.put(Labels.RELEASE_LABEL, this.instanceName);
        labels.put(Labels.KUBERNETES_PART_OF_LABEL, this.instanceName);
        labels.put(Labels.KUBERNETES_NAME_LABEL, Labels.KUBERNETES_NAME);
        labels.put(Labels.KUBERNETES_INSTANCE_LABEL, this.instanceName);
        labels.put(Labels.KUBERNETES_MANAGED_BY_LABEL, Labels.KUBERNETES_MANAGED_BY);

        return labels;
    }

    private Map<String, String> getResourceGroupLabels() {
        Map<String, String> labels = new HashMap<>();

        labels.put(Labels.NAME_LABEL, getDefaultResourceName());

        return labels;
    }

    public Map<String, String> getServiceSelectorLabel(String serviceSelector) {
        Map<String, String> labels = new HashMap<String, String>();

        labels.put(Labels.SERVICE_SELECTOR_LABEL, serviceSelector);

        return labels;
    }

    /**
     * 
     * @param supplied
     * @param defaults
     * @return the default resource requirements overridden by any user supplied requirements 
     */
    protected ResourceRequirements getResourceRequirements(ResourceRequirements supplied, ResourceRequirements defaults) {
        Map<String, Quantity> requests = Optional.ofNullable(supplied.getRequests()).orElseGet(HashMap::new);
        defaults.getRequests().forEach(requests::putIfAbsent);
        Map<String, Quantity> limits = Optional.ofNullable(supplied.getLimits()).orElseGet(HashMap::new);
        defaults.getLimits().forEach(limits::putIfAbsent);
        return new ResourceRequirementsBuilder()
                .withRequests(requests)
                .withLimits(limits)
                .build();
    }

    /**
     * @param defaults
     * @return the default resource requirements overridden by any user supplied requirements 
     */
    protected ResourceRequirements getResourceRequirements(ResourceRequirements defaults) {
        return getResourceRequirements(resourceRequirements, defaults);
    }

    protected SecurityContext getSecurityContext(boolean readOnlyRootFileSystem) {
        return new SecurityContextBuilder()
                .withNewPrivileged(false)
                .withNewReadOnlyRootFilesystem(readOnlyRootFileSystem)
                .withAllowPrivilegeEscalation(false)
                .withNewRunAsNonRoot(true)
                .withNewCapabilities()
                    .addNewDrop("ALL")
                .endCapabilities()
                .build();
    }

    protected PodSecurityContext getPodSecurityContext() {
        return new PodSecurityContextBuilder()
                .withNewRunAsNonRoot(true)
                .build();
    }

    protected String firstDefinedImage(String defaultImage, Optional<String>... maybeImages) {
        return Arrays
            .stream(maybeImages)
                .filter(image -> image.isPresent())
                .map(Optional::get)
                .findFirst()
                .orElse(defaultImage);
    }

    public Deployment createDeployment(
        List<Container> containers,
        List<Volume> volumes) {

        return new DeploymentBuilder()
            .withNewMetadata()
                .withName(getDefaultResourceName())
                .withNamespace(namespace)
                .withOwnerReferences(getEventStreamsOwnerReference())
                .addToAnnotations(getEventStreamsMeteringAnnotations())
                .addToLabels(getComponentLabels())
                .addToLabels(getServiceSelectorLabel(componentName))
            .endMetadata()
            .withNewSpec()
                .withReplicas(replicas)
                .withNewSelector()
                    .addToMatchLabels(Labels.INSTANCE_LABEL, instanceName)
                    .addToMatchLabels(Labels.COMPONENT_LABEL, componentName)
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .addToAnnotations(getEventStreamsMeteringAnnotations())
                        .addToLabels(getComponentLabels())
                        .addToLabels(getServiceSelectorLabel(componentName))
                    .endMetadata()
                    .withNewSpec()
                        .withContainers(containers)
                        .withVolumes(volumes)
                        .withSecurityContext(getPodSecurityContext())
                        .withNewServiceAccount(getDefaultResourceName())
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();
    }

    protected ServiceAccount createServiceAccount() {
        return new ServiceAccountBuilder()
                .withNewMetadata()
                    .withName(getDefaultResourceName())
                    .withNamespace(namespace)
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .addToAnnotations(getEventStreamsMeteringAnnotations())
                    .addToLabels(getComponentLabels())
                .endMetadata()
                .withImagePullSecrets(getPullSecrets())
                .build();
    }

    protected RoleBinding createRoleBinding(Subject subject, RoleRef role) {
        return new RoleBindingBuilder()
                .withNewMetadata()
                    .withName(getDefaultResourceName())
                    .withNamespace(namespace)
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .addToLabels(getComponentLabels())
                .endMetadata()
                .withSubjects(subject)
                .withRoleRef(role)
                .build();
    }

    protected String getConfigMapName() {
        return getDefaultResourceName() + CONFIG_MAP_SUFFIX;
    }

    protected ConfigMap createConfigMap(Map<String, String> data) {
        return new ConfigMapBuilder()
                .withNewMetadata()
                    .withName(getConfigMapName())
                    .withNamespace(namespace)
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .addToLabels(getComponentLabels())
                .endMetadata()
                .withData(data)
                .build();
    }

    protected ServicePort createServicePort(int port) {
        return new ServicePortBuilder()
                .withNewName(getComponentNameWithSuffix("http"))
                .withNewProtocol("TCP")
                .withPort(port)
                .build();
    }

    protected Service createService(int port) {
        return createService(getDefaultResourceName(), Collections.singletonList(createServicePort(port)), Collections.emptyMap());
    }

    protected Service createService(String name, List<ServicePort> ports, Map<String, String> annotations) {
        String externalAccessType = Optional.ofNullable(externalAccess)
                .map(ExternalAccess::getType)
                .orElse(ExternalAccess.TYPE_DEFAULT);

        String type = ExternalAccess.TYPE_NODEPORT.equals(externalAccessType) ? "NodePort" : "ClusterIP";
        return createService(type, name, ports, annotations);
    }

    protected Service createService(String type, String name, List<ServicePort> ports, Map<String, String> annotations) {
        return new ServiceBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .addToLabels(getComponentLabels())
                    .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                    .withType(type)
                    .addAllToPorts(ports)
                    .addToSelector(Labels.INSTANCE_LABEL, instanceName)
                    .addToSelector(Labels.COMPONENT_LABEL, componentName)
                .endSpec()
                .build();
    }

    /**
     *
     * @param name the name of the route to be created
     * @param serviceName the name of the service associated with the route
     * @param port the port to route to on the service
     * @param tlsConfig the TLSConfig to set in the route, if null it is not set
     * @param additionalLabels map of security labels
     * @return a configured Route
     */
    protected Route createRoute(String name, String serviceName, int port, TLSConfig tlsConfig, Map<String, String> additionalLabels) {
        RouteBuilder route = new RouteBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .addToLabels(getComponentLabels())
                    .addToLabels(additionalLabels)
                .endMetadata()
                .withNewSpec()
                    .withNewSubdomain("")
                    .withNewTo()
                        .withNewKind("Service")
                        .withNewName(serviceName)
                        .withWeight(100)
                    .endTo()
                    .withNewPort()
                        .withNewTargetPort(port)
                    .endPort()
                    .withNewWildcardPolicy("None")
                .endSpec();

        if (tlsConfig != null) {
            route.editSpec()
                    .withTls(tlsConfig)
                .endSpec();
        }

        return route.build();
    }

    /**
     *
     * @return the default TLSConfig, most often used for configuring a route
     */
    protected TLSConfig getDefaultTlsConfig() {
        return new TLSConfigBuilder()
                .withTermination("passthrough")
                .withInsecureEdgeTerminationPolicy("None")
                .build();
    }

    protected NetworkPolicy createNetworkPolicy(LabelSelector labelSelector,
                                                List<NetworkPolicyIngressRule> ingressRules,
                                                List<NetworkPolicyEgressRule> egressRules) {
        return new NetworkPolicyBuilder()
            .withNewMetadata()
                .withName(getDefaultResourceName())
                .withNamespace(namespace)
                .withOwnerReferences(getEventStreamsOwnerReference())
                .addToLabels(getComponentLabels())
            .endMetadata()
            .withNewSpec()
                .withPodSelector(labelSelector)
                .withIngress(ingressRules)
                .withEgress(egressRules)
            .endSpec()
            .build();
    }

    protected String getKafkaUserName(String kafkaUserSuffix) {
        return getKafkaUserName(getInstanceName(), kafkaUserSuffix);
    }

    public static String getKafkaUserName(String instanceName, String kafkaUserName) {
        return getDefaultResourceName(instanceName, kafkaUserName);
    }

    protected KafkaUser createKafkaUser(String kafkaUserName, KafkaUserSpec spec) {
        Map<String, String> labels = getComponentLabelsWithoutResourceGroup();
        labels.put(io.strimzi.operator.common.model.Labels.STRIMZI_CLUSTER_LABEL, EventStreamsKafkaModel.getKafkaInstanceName(getInstanceName()));

        return new KafkaUserBuilder()
            .withApiVersion(KafkaUser.RESOURCE_GROUP + "/" + KafkaUser.V1BETA1)
            .withNewMetadata()
                .withName(kafkaUserName)
                .withOwnerReferences(getEventStreamsOwnerReference())
                .withNamespace(getNamespace())
                .withLabels(labels)
            .endMetadata()
            .withSpec(spec)
            .build();
    }
    
    protected List<EnvVar> combineEnvVarListsNoDuplicateKeys(List<EnvVar> envVarDefaults) {
        return combineEnvVarListsNoDuplicateKeys(envVarDefaults, envVars);
    }

    protected List<EnvVar> combineEnvVarListsNoDuplicateKeys(List<EnvVar> initialEnvVars, List<ContainerEnvVar> overrides) {
        Map<String, EnvVar> envVarMap = new HashMap<>();
        for (EnvVar env: initialEnvVars) {
            envVarMap.put(env.getName(), env);
        }

        if (overrides != null) {
            for (ContainerEnvVar envVarOverride: overrides) {
                envVarMap.put(envVarOverride.getName(),
                        new EnvVarBuilder()
                            .withNewName(envVarOverride.getName())
                            .withNewValue(envVarOverride.getValue())
                            .build()
                );
            }
        }
        return new ArrayList<>(envVarMap.values());
    }

    protected Probe combineProbeDefinitions(Probe probe, io.strimzi.api.kafka.model.Probe overrides) {
        return combineProbeDefinitions(probe, overrides, overrides.getInitialDelaySeconds(), overrides.getTimeoutSeconds());
    }

    protected Probe combineProbeDefinitions(Probe probe, io.strimzi.api.kafka.model.Probe overrides, int initialDelaySeconds, int timeoutSeconds) {
        return new ProbeBuilder(probe)
                .withPeriodSeconds(overrides.getPeriodSeconds() != null ? overrides.getPeriodSeconds() : probe.getPeriodSeconds())
                .withSuccessThreshold(overrides.getSuccessThreshold() != null ? overrides.getSuccessThreshold() : probe.getSuccessThreshold())
                .withFailureThreshold(overrides.getFailureThreshold() != null ? overrides.getFailureThreshold() : probe.getFailureThreshold())
                // Use Strimzi Defaults
                .withInitialDelaySeconds(initialDelaySeconds)
                .withTimeoutSeconds(timeoutSeconds)
                .build();
    }

    protected Volume createKafkaUserCertVolume() {
        return new VolumeBuilder()
            .withNewName(KAFKA_USER_SECRET_VOLUME_NAME)
            .withNewSecret()
                .withNewSecretName(InternalKafkaUserModel.getInternalKafkaUserSecretName(getInstanceName()))
                .addNewItem().withNewKey(USER_CERT).withNewPath("podtls.crt").endItem()
                .addNewItem().withNewKey(USER_KEY).withNewPath("podtls.key").endItem()
                .addNewItem().withNewKey(USER_P12).withNewPath("podtls.p12").endItem()
            .endSecret()
            .build();
    }

    protected NetworkPolicyIngressRule createIngressRule(int port, Map<String, String> componentNames) {
 
        NetworkPolicyIngressRuleBuilder policyBuilder = new NetworkPolicyIngressRuleBuilder()
            .addNewPort().withNewPort(port).endPort();

        componentNames.forEach((k, v) -> policyBuilder.addNewFrom()
            .withNewPodSelector()
                .addToMatchLabels(k, v)
            .endPodSelector()
            .endFrom());

        return policyBuilder.build();
    }

    protected NetworkPolicyEgressRule createEgressRule(int port, String componentName) {
        return new NetworkPolicyEgressRuleBuilder()
                .addNewPort().withNewPort(port).endPort()
                .addNewTo()
                    .withNewPodSelector()
                        .addToMatchLabels(Labels.COMPONENT_LABEL, componentName)
                    .endPodSelector()
                .endTo()
                .build();
    }

    protected LabelSelector createLabelSelector(String componentName) {
        return new LabelSelectorBuilder()
                .addToMatchLabels(Labels.COMPONENT_LABEL, componentName)
                .build();
    }

    protected Secret createSecret(String name, Map<String, String> data) {
        return createSecret(namespace, name, data, getComponentLabels(), null);
    }

    protected Secret createSecret(String namespace, String name, Map<String, String> data,
        Map<String, String> labels, Map<String, String> annotations) {

        return new SecretBuilder()
            .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(labels)
                .withAnnotations(annotations)
                .withOwnerReferences(getEventStreamsOwnerReference())
            .endMetadata()
            .withData(data)
            .build();
    }
    
    protected String getInternalKafkaBootstrap(List<ListenerStatus> kafkaListeners) {
        return tlsEnabled() ? getInternalTlsKafkaBootstrap(kafkaListeners) : getInternalPlainKafkaBootstrap(kafkaListeners);
    }

    protected String getInternalPlainKafkaBootstrap(List<ListenerStatus> kafkaListeners) {
        return getKafkaBootstrap(kafkaListeners, "plain");
    }

    protected String getInternalTlsKafkaBootstrap(List<ListenerStatus> kafkaListeners) {
        return getKafkaBootstrap(kafkaListeners, "tls");
    }

    protected String getRunAsKafkaBootstrap(List<ListenerStatus> kafkaListeners) {
        return getKafkaBootstrap(kafkaListeners, RUNAS_LISTENER_TYPE);
    }

    protected String getExternalKafkaBootstrap(List<ListenerStatus> kafkaListeners) {
        return getKafkaBootstrap(kafkaListeners, "external");
    }

    private String getKafkaBootstrap(List<ListenerStatus> kafkaListeners, String listenerType) {
        String kafkaBootstrap = EventStreamsKafkaModel.getKafkaInstanceName(getInstanceName()) + "-kafka-bootstrap." + getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + (tlsEnabled() ? EventStreamsKafkaModel.KAFKA_PORT_TLS : EventStreamsKafkaModel.KAFKA_PORT);

        Optional<ListenerAddress> listenerAddress = kafkaListeners
                .stream()
                .filter(listener -> listenerType.equals(listener.getType()))
                .findFirst()
                .map(ListenerStatus::getAddresses)
                .map(addressList -> addressList.get(0));

        if (listenerAddress.isPresent()) {
            kafkaBootstrap = listenerAddress.get().getHost() + ":" + listenerAddress.get().getPort();
        } else if (RUNAS_LISTENER_TYPE.equals(listenerType)) {
            kafkaBootstrap = EventStreamsKafkaModel.getKafkaInstanceName(getInstanceName()) + "-kafka-bootstrap." + getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.KAFKA_RUNAS_PORT;
        }

        return kafkaBootstrap;
    }

    public boolean isReplicatorInternalClientAuthForConnectEnabled(EventStreams instance) {
        return Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getListeners)
                .map(KafkaListeners::getTls)
                .map(KafkaListenerTls::getAuth)
                .isPresent();
    }

    public boolean isReplicatorInternalServerAuthForConnectEnabled(EventStreams instance) {
        return Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getListeners)
                .map(KafkaListeners::getTls)
                .isPresent();
    }

    public boolean isReplicatorExternalClientAuthForConnectEnabled(EventStreams instance) {
        return Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getListeners)
                .map(KafkaListeners::getExternal)
                .map(KafkaListenerExternal::getAuth)
                .map(KafkaListenerAuthentication::getType)
                .isPresent();
    }

    public boolean isReplicatorExternalServerAuthForConnectEnabled(EventStreams instance) {
        return Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getListeners)
                .map(KafkaListeners::getExternal)
                .map(KafkaListenerExternal::getAuth)
                .isPresent();
    }

    public String getTlsVersionEnvValue(EventStreams instance) {
        return Optional.of(instance)
            .map(EventStreams::getSpec)
            .map(EventStreamsSpec::getSecurity)
            .map(SecuritySpec::getInternalTls)
            .map(TlsVersion::toValue)
            .orElse(DEFAULT_TLS_VERSION.toValue());
    }
}