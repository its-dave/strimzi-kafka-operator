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

import com.ibm.eventstreams.api.ListenerAuthentication;
import com.ibm.eventstreams.api.ListenerType;
import com.ibm.eventstreams.api.ProductUse;
import com.ibm.eventstreams.api.TlsVersion;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.ExternalAccess;
import com.ibm.eventstreams.api.spec.LicenseSpec;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import io.fabric8.kubernetes.api.model.Affinity;
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
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.TLSConfig;
import io.fabric8.openshift.api.model.TLSConfigBuilder;
import io.strimzi.api.kafka.model.AclRule;
import io.strimzi.api.kafka.model.ContainerEnvVar;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaResources;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserBuilder;
import io.strimzi.api.kafka.model.KafkaUserScramSha512ClientAuthentication;
import io.strimzi.api.kafka.model.KafkaUserSpec;
import io.strimzi.api.kafka.model.KafkaUserSpecBuilder;
import io.strimzi.api.kafka.model.KafkaUserTlsClientAuthentication;
import io.strimzi.api.kafka.model.Logging;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationScramSha512;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationTls;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternal;
import io.strimzi.api.kafka.model.listener.KafkaListenerTls;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.strimzi.api.kafka.model.status.ListenerAddress;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.template.MetadataTemplate;
import io.strimzi.api.kafka.model.template.PodTemplate;
import io.strimzi.operator.cluster.model.ModelUtils;
import io.strimzi.operator.common.model.Labels;
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
    public static final String OPERATOR_NAME = io.strimzi.operator.cluster.model.AbstractModel.STRIMZI_CLUSTER_OPERATOR_NAME;

    public static final String APP_NAME = "ibm-es";
    public static final String APP_NAME_TRUNCATED = "es";
    public static final String DEFAULT_PROMETHEUS_PORT = "9404";
    public static final String DEFAULT_COMPONENT_NAME = "eventstreams";
    public static final int MAX_RESOURCE_NAME_LENGTH = 63;

    public static final String CONFIG_MAP_SUFFIX = "-config";

    protected static final String TLS_VERSION_ENV_KEY = "TLS_VERSION";
    protected static final TlsVersion DEFAULT_TLS_VERSION = TlsVersion.TLS_V1_2;
    public static final TlsVersion DEFAULT_INTERNAL_TLS = DEFAULT_TLS_VERSION;
    public static final String AUTHENTICATION_LABEL_SEPARATOR = "-";
    public static final String AUTHENTICATION_LABEL_NO_AUTH = "no-authentication";

    public static final String PRODUCT_ID_KEY = "productID";
    public static final String PRODUCT_NAME_KEY = "productName";
    public static final String PRODUCT_VERSION_KEY = "productVersion";
    public static final String PRODUCT_METRIC_KEY = "productMetric";
    public static final String PRODUCT_CHARGED_CONTAINERS_KEY = "productChargedContainers";
    protected static final String CLOUDPAK_ID_KEY = "cloudpakId";
    protected static final String CLOUDPAK_NAME_KEY = "cloudpakName";
    protected static final String CLOUDPAK_VERSION_KEY = "cloudpakVersion";
    protected static final String PRODUCT_CLOUDPAK_RATIO_PRODUCTION_KEY = "productCloudpakRatio";

    protected static final String PRODUCT_ID_PRODUCTION = "2cba508800504d0abfa48a0e2c4ecbe2";
    protected static final String PRODUCT_ID_NON_PRODUCTION = "2a79e49111f44ec3acd89608e56138f5";
    protected static final String PRODUCT_NAME_PRODUCTION = "IBM Event Streams";
    protected static final String PRODUCT_NAME_NON_PRODUCTION = "IBM Event Streams for Non Production";
    protected static final String PRODUCT_VERSION = "10.0.0";
    protected static final String PRODUCT_CLOUDPAK_RATIO_PRODUCTION = "1:1";
    protected static final String PRODUCT_CLOUDPAK_RATIO_NON_PRODUCTION = "2:1";
    protected static final String PRODUCT_METRIC = "VIRTUAL_PROCESSOR_CORE";
    protected static final String CLOUDPAK_ID = "c8b82d189e7545f0892db9ef2731b90d";
    protected static final String CLOUDPAK_NAME = "IBM Cloud Pak for Integration";
    protected static final String CLOUDPAK_VERSION = "2020.2.1";
    private static final String RUNAS_LISTENER_TYPE = "runas";
    private static final String TLS_LISTENER_TYPE = "tls";
    private static final String PLAIN_LISTENER_TYPE = "plain";
    private static final String EXTERNAL_LISTENER_TYPE = "external";

    public static final String ENVIRONMENT_PATH = "/env";
    public static final String CERTIFICATE_PATH = "/certs";

    public static final String KAFKA_USER_SECRET_VOLUME_NAME = "kafka-user";
    public static final String CA_CERT = "ca.crt";
    public static final String CA_P12 = "ca.p12";
    public static final String CA_P12_PASS = "ca.password";
    public static final String USER_CERT = "user.crt";
    public static final String USER_KEY = "user.key";
    public static final String USER_P12 = "user.p12";
    public static final String USER_P12_PASS = "user.password";
    public static final String SCRAM_PASSWORD = "password";

    public static final String EVENTSTREAMS_AUTHENTICATION_LABEL = Labels.STRIMZI_DOMAIN + "authentication";
    public static final String EVENTSTREAMS_PROTOCOL_LABEL = Labels.STRIMZI_DOMAIN + "protocol";

    private String kind;
    private String apiVersion;
    private String uid;
    private String componentName;
    private String applicationName;
    private List<ContainerEnvVar> envVars;
    private String instanceName;
    private String namespace;
    private Labels labels;
    private int replicas;
    private ExternalAccess externalAccess;
    private ResourceRequirements resourceRequirements;
    private PodTemplate podTemplate;
    private TlsVersion tlsVersion;
    private ProductUse productUse;

    private List<LocalObjectReference> globalPullSecrets;
    protected String image = "";
    protected boolean customImage;
    private io.strimzi.api.kafka.model.Probe livenessProbe;
    private io.strimzi.api.kafka.model.Probe readinessProbe;

    private static final Logger log = LogManager.getLogger();

    /**
     *
     * @param componentName used to name components and should be max 6 characters
     * @param applicationName used for labeling components, should be descriptive
     */
    protected AbstractModel(EventStreams resource, String componentName, String applicationName) {
        this.instanceName = resource.getMetadata().getName();
        this.namespace = resource.getMetadata().getNamespace();
        this.componentName = componentName;
        this.applicationName = applicationName;

        this.labels = generateDefaultLabels(resource, applicationName, componentName);

        setProductUse(Optional.ofNullable(resource.getSpec())
            .map(EventStreamsSpec::getLicense)
            .map(LicenseSpec::getUse)
            .get());
    }

    public String getComponentName() {
        return componentName;
    }

    public String getApplicationName() {
        return applicationName;
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

    public int getReplicas() {
        return replicas;
    }

    protected void setProductUse(ProductUse productUse) {
        this.productUse = productUse;
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

    public static TlsVersion getInternalTlsVersion(EventStreams instance) {
        return Optional.ofNullable(instance)
                .map(EventStreams::getSpec)
                .map(EventStreamsSpec::getSecurity)
                .map(SecuritySpec::getInternalTls)
                .orElse(DEFAULT_INTERNAL_TLS);
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

    public static Boolean isKafkaAuthenticationEnabled(EventStreams instance) {
        return isListenerAuthenticated(instance, ListenerType.PLAIN) ||
            isListenerAuthenticated(instance, ListenerType.EXTERNAL) ||
            isListenerAuthenticated(instance, ListenerType.TLS);
    }

    public static boolean isKafkaAuthorizationEnabled(EventStreams instance) {
        return Optional.of(instance)
            .map(EventStreams::getSpec)
            .map(EventStreamsSpec::getStrimziOverrides)
            .map(KafkaSpec::getKafka)
            .map(KafkaClusterSpec::getAuthorization)
            .isPresent();
    }

    private static Boolean isListenerAuthenticated(EventStreams instance, ListenerType type) {
        return ListenerAuthentication.getAuthentication(instance, type) != ListenerAuthentication.NONE && ListenerAuthentication.getAuthentication(instance, type) != null;
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

    protected void setOwnerReference(CustomResource instance) {
        this.apiVersion = instance.getApiVersion();
        this.kind = instance.getKind();
        this.uid = instance.getMetadata().getUid();
    }

    public List<LocalObjectReference> getPullSecrets() {

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

    protected Map<String, String> getAnnotationOverrides() {
        return Optional.ofNullable(podTemplate)
            .map(PodTemplate::getMetadata)
            .map(MetadataTemplate::getAnnotations)
            .orElse(Collections.EMPTY_MAP);
    }

    protected Map<String, String> getLabelOverrides() {
        return Optional.ofNullable(podTemplate)
                .map(PodTemplate::getMetadata)
                .map(MetadataTemplate::getLabels)
                .orElse(Collections.EMPTY_MAP);
    }

    protected Affinity getAffinity() {
        return Optional.ofNullable(podTemplate)
                .map(PodTemplate::getAffinity)
                .orElse(null);
    }

    protected Map<String, String> getEventStreamsMeteringAnnotations() {
        return getEventStreamsMeteringAnnotations("");
    }

    protected Map<String, String> getEventStreamsMeteringAnnotations(String chargedContainers) {
        switch (productUse) {
            case CP4I_PRODUCTION:
                return getDefaultEventStreamsMeteringAnnotations(PRODUCT_ID_PRODUCTION, PRODUCT_NAME_PRODUCTION, chargedContainers, PRODUCT_CLOUDPAK_RATIO_PRODUCTION);
            case CP4I_NON_PRODUCTION:
                return getDefaultEventStreamsMeteringAnnotations(PRODUCT_ID_NON_PRODUCTION, PRODUCT_NAME_NON_PRODUCTION, chargedContainers, PRODUCT_CLOUDPAK_RATIO_NON_PRODUCTION);
            case IBM_SUPPORTING_PROGRAM:
                return getEmbeddedEventStreamsMeteringAnnotations();
            default:
                return null;
        }
    }

    private Map<String, String> getDefaultEventStreamsMeteringAnnotations(String productId, String productName, String chargedContainers, String cloudPakRatio) {
        Map<String, String> annotations = new HashMap<String, String>();

        annotations.put(PRODUCT_ID_KEY, productId);
        annotations.put(PRODUCT_NAME_KEY, productName);
        annotations.put(PRODUCT_VERSION_KEY, PRODUCT_VERSION);
        annotations.put(PRODUCT_METRIC_KEY, PRODUCT_METRIC);
        annotations.put(PRODUCT_CHARGED_CONTAINERS_KEY, chargedContainers);
        annotations.put(CLOUDPAK_ID_KEY, CLOUDPAK_ID);
        annotations.put(CLOUDPAK_NAME_KEY, CLOUDPAK_NAME);
        annotations.put(CLOUDPAK_VERSION_KEY, CLOUDPAK_VERSION);
        annotations.put(PRODUCT_CLOUDPAK_RATIO_PRODUCTION_KEY, cloudPakRatio);

        return annotations;
    }

    protected Map<String, String> getEmbeddedEventStreamsMeteringAnnotations() {
        Map<String, String> annotations = new HashMap<String, String>();

        annotations.put(PRODUCT_ID_KEY, PRODUCT_ID_PRODUCTION);
        annotations.put(PRODUCT_NAME_KEY, PRODUCT_NAME_PRODUCTION);
        annotations.put(PRODUCT_VERSION_KEY, PRODUCT_VERSION);
        annotations.put(PRODUCT_METRIC_KEY, PRODUCT_METRIC);

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

    /**
     * NOTE: If creating a Strimzi Custom Resource please use labelsWithoutResourceGroup
     * @return the component labels
     */
    public Labels labels() {
        return this.labels;
    }

    /**
     * Use this function to create labels for Strimzi Custom Resources
     * as they cannot contain Strimzi resource group labels
     * @return the component labels without any of the banned Strimzi resource group labels
     */
    public Labels labelsWithoutResourceGroup() {
        return Labels.fromMap(
                labels().toMap()
                    .entrySet()
                    .stream()
                    .filter(entry -> !entry.getKey().startsWith(Labels.STRIMZI_DOMAIN))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /**
     * Use this function to create a set of labels matching all of the components
     * in the instance
     * @return the component instance and managed-by labels
     */
    protected Labels uniqueInstanceLabels() {
        return Labels.fromMap(
                labels().toMap()
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().equals(Labels.KUBERNETES_INSTANCE_LABEL) || entry.getKey().equals(Labels.KUBERNETES_MANAGED_BY_LABEL))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /**
     * @param isTls boolean on whether the component is using tls
     * @param authenticationMechanisms a list of a mechanisms that are concatenated into a label
     * @return a valid map of labels used for EventStreams security
     */
    public Map<String, String> securityLabels(boolean isTls, List<String> authenticationMechanisms) {
        Map<String, String> labels = new HashMap<>();
        if (authenticationMechanisms.size() > 0) {
            authenticationMechanisms.forEach(auth -> {
                labels.put(EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + auth, "true");
            });
        } else {
            labels.put(EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + AUTHENTICATION_LABEL_NO_AUTH, "true");
        }
        labels.put(EVENTSTREAMS_PROTOCOL_LABEL, isTls ? "https" : "http");
        return labels;
    }

    public static Labels generateDefaultLabels(EventStreams resource, String applicationName, String componentName) {
        Labels labels = Labels.generateDefaultLabels(resource, applicationName, OPERATOR_NAME);
        // If the component is not part of the parent application (eventstreams), then override Strimzi name label
        // to use resource name including the resource prefix (this matches Strimzi convention)
        // This check stops the default resource name prefix being added to eventstreams (e.g. my-es-ibm-es-eventstreams)
        // to match Strimzi convention
        if (!applicationName.equals(Labels.APPLICATION_NAME)) {
            labels = labels.withStrimziName(getDefaultResourceName(resource.getMetadata().getName(), componentName));
        }
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

        Labels labels = labels();
        Labels selectorLabels = labels.strimziSelectorLabels();

        return new DeploymentBuilder()
            .withNewMetadata()
                .withName(getDefaultResourceName())
                .withNamespace(namespace)
                .withOwnerReferences(getEventStreamsOwnerReference())
                .addToAnnotations(getEventStreamsMeteringAnnotations())
                .addToLabels(labels.toMap())
            .endMetadata()
            .withNewSpec()
                .withReplicas(replicas)
                .withNewSelector()
                    .withMatchLabels(selectorLabels.toMap())
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .addToAnnotations(getEventStreamsMeteringAnnotations())
                        .addToAnnotations(getAnnotationOverrides())
                        .addToLabels(labels.toMap())
                        .addToLabels(getLabelOverrides())
                    .endMetadata()
                    .withNewSpec()
                        .withAffinity(getAffinity())
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
        Labels labels = labels();

        return new ServiceAccountBuilder()
                .withNewMetadata()
                    .withName(getDefaultResourceName())
                    .withNamespace(namespace)
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .addToAnnotations(getEventStreamsMeteringAnnotations())
                    .addToLabels(labels.toMap())
                .endMetadata()
                .withImagePullSecrets(getPullSecrets())
                .build();
    }

    protected RoleBinding createRoleBinding(Subject subject, RoleRef role) {
        Labels labels = labels();

        return new RoleBindingBuilder()
                .withNewMetadata()
                    .withName(getDefaultResourceName())
                    .withNamespace(namespace)
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .addToLabels(labels.toMap())
                .endMetadata()
                .withSubjects(subject)
                .withRoleRef(role)
                .build();
    }

    protected String getConfigMapName() {
        return getDefaultResourceName() + CONFIG_MAP_SUFFIX;
    }

    protected ConfigMap createConfigMap(Map<String, String> data) {
        Labels labels = labels();

        return new ConfigMapBuilder()
                .withNewMetadata()
                    .withName(getConfigMapName())
                    .withNamespace(namespace)
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .addToLabels(labels.toMap())
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

    protected Service createService(String name, List<ServicePort> ports, Map<String, String> annotations) {
        String externalAccessType = Optional.ofNullable(externalAccess)
                .map(ExternalAccess::getType)
                .orElse(ExternalAccess.TYPE_DEFAULT);

        String type = ExternalAccess.TYPE_NODEPORT.equals(externalAccessType) ? "NodePort" : "ClusterIP";
        return createService(type, name, ports, annotations);
    }

    protected Service createService(String type, String name, List<ServicePort> ports, Map<String, String> annotations) {
        Labels labels = labels();
        Labels selectorLabels = labels.strimziSelectorLabels();

        return new ServiceBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .addToLabels(labels.toMap())
                    .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                    .withType(type)
                    .addAllToPorts(ports)
                    .withSelector(selectorLabels.toMap())
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
    protected Route createRoute(String name, Optional<String> host, String serviceName, int port, TLSConfig tlsConfig, Map<String, String> additionalLabels) {
        Labels labels = labels();

        RouteBuilder route = new RouteBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .addToLabels(labels.toMap())
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

        host.ifPresent(s -> route.editSpec()
                                .withNewHost(s)
                                .endSpec());
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
        Labels labels = labels();

        return new NetworkPolicyBuilder()
            .withNewMetadata()
                .withName(getDefaultResourceName())
                .withNamespace(namespace)
                .withOwnerReferences(getEventStreamsOwnerReference())
                .addToLabels(labels.toMap())
            .endMetadata()
            .withNewSpec()
                .withPodSelector(labelSelector)
                .withIngress(ingressRules)
                .withEgress(egressRules)
            .endSpec()
            .build();
    }

    protected String getKafkaUserName(String kafkaUserName) {
        return getKafkaUserName(getInstanceName(), kafkaUserName);
    }

    public static String getKafkaUserName(String instanceName, String kafkaUserName) {
        return getDefaultResourceName(instanceName, kafkaUserName);
    }

    protected KafkaUser createKafkaUser(String kafkaUserName, KafkaUserSpec spec) {
        Labels labels = labelsWithoutResourceGroup()
                // label each kafka user with the instance of kafka it belongs to
                .withStrimziCluster(EventStreamsKafkaModel.getKafkaInstanceName(getInstanceName()));

        return new KafkaUserBuilder()
            .withApiVersion(KafkaUser.RESOURCE_GROUP + "/" + KafkaUser.V1BETA1)
            .withNewMetadata()
                .withName(kafkaUserName)
                .withOwnerReferences(getEventStreamsOwnerReference())
                .withNamespace(getNamespace())
                .withLabels(labels.toMap())
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
                .addNewItem().withNewKey(USER_CERT).withNewPath(USER_CERT).endItem()
                .addNewItem().withNewKey(USER_KEY).withNewPath(USER_KEY).endItem()
                .addNewItem().withNewKey(USER_P12).withNewPath(USER_P12).endItem()
                .addNewItem().withNewKey(USER_P12_PASS).withNewPath(USER_P12_PASS).endItem()
            .endSecret()
            .build();
    }

    protected NetworkPolicyIngressRule createIngressRule(int port, List<Labels> matchLabels) {

        NetworkPolicyIngressRuleBuilder policyBuilder = new NetworkPolicyIngressRuleBuilder()
            .addNewPort().withNewPort(port).endPort();

        matchLabels.forEach(labels -> {
            policyBuilder.addNewFrom()
                        .withNewPodSelector()
                            .withMatchLabels(labels.toMap())
                        .endPodSelector()
                    .endFrom();
        });

        return policyBuilder.build();
    }

    protected LabelSelector createLabelSelector(String componentName) {
        return new LabelSelectorBuilder()
                .addToMatchLabels(Labels.EMPTY
                        .withKubernetesName(componentName)
                        .withKubernetesInstance(instanceName)
                        .toMap())
                .build();
    }

    protected Secret createSecret(String name, Map<String, String> data) {
        return createSecret(namespace, name, data, labels(), null);
    }

    protected Secret createSecret(String namespace, String name, Map<String, String> data,
        Labels labels, Map<String, String> annotations) {

        return new SecretBuilder()
            .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(labels.toMap())
                .withAnnotations(annotations)
                .withOwnerReferences(getEventStreamsOwnerReference())
            .endMetadata()
            .withData(data)
            .build();
    }

    protected Optional<String> getInternalKafkaBootstrap(List<ListenerStatus> kafkaListeners) {
        return tlsEnabled() ? getInternalTlsKafkaBootstrap(kafkaListeners) : getInternalPlainKafkaBootstrap(kafkaListeners);
    }

    protected Optional<String> getInternalPlainKafkaBootstrap(List<ListenerStatus> kafkaListeners) {
        return getKafkaBootstrap(kafkaListeners, PLAIN_LISTENER_TYPE);
    }

    protected Optional<String> getInternalTlsKafkaBootstrap(List<ListenerStatus> kafkaListeners) {
        return getKafkaBootstrap(kafkaListeners, TLS_LISTENER_TYPE);
    }

    protected Optional<String> getRunAsKafkaBootstrap(List<ListenerStatus> kafkaListeners) {
        return getKafkaBootstrap(kafkaListeners, RUNAS_LISTENER_TYPE);
    }

    protected Optional<String> getExternalKafkaBootstrap(List<ListenerStatus> kafkaListeners) {
        return getKafkaBootstrap(kafkaListeners, EXTERNAL_LISTENER_TYPE);
    }

    private Optional<String> getKafkaBootstrap(List<ListenerStatus> kafkaListeners, String listenerType) {
        String kafkaBootstrap = null;

        Optional<ListenerAddress> listenerAddress = kafkaListeners
            .stream()
            .filter(listener -> listenerType.equals(listener.getType()))
            .findFirst()
            .map(ListenerStatus::getAddresses)
            .map(addressList -> addressList.get(0));

        if (listenerAddress.isPresent()) {
            kafkaBootstrap = listenerAddress.get().getHost() + ":" + listenerAddress.get().getPort();
        } else if (RUNAS_LISTENER_TYPE.equals(listenerType)) {
            kafkaBootstrap = String.format("%s:%s",
                    ModelUtils.serviceDnsNameWithoutClusterDomain(
                            namespace,
                            KafkaResources.bootstrapServiceName(EventStreamsKafkaModel.getKafkaInstanceName(getInstanceName()))),
                    EventStreamsKafkaModel.KAFKA_RUNAS_PORT);
        }

        return  Optional.ofNullable(kafkaBootstrap);
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

    // Checks whether the presented KafkaListenerAuthentication is one of the supported authentication types
    protected static boolean isSupportedAuthType(KafkaListenerAuthentication auth) {
        return auth instanceof KafkaListenerAuthenticationTls ||
                auth instanceof KafkaListenerAuthenticationScramSha512;
    }

    protected KafkaUser createKafkaUser(List<AclRule> aclList, String kafkaUserName, KafkaListenerAuthentication kafkaAuth) {
        KafkaUserSpecBuilder kafkaUserSpec = new KafkaUserSpecBuilder()
                .withNewKafkaUserAuthorizationSimple()
                .withAcls(aclList)
                .endKafkaUserAuthorizationSimple();

        if (kafkaAuth instanceof KafkaListenerAuthenticationTls) {
            kafkaUserSpec.withAuthentication(new KafkaUserTlsClientAuthentication());
        } else if (kafkaAuth instanceof KafkaListenerAuthenticationScramSha512) {
            kafkaUserSpec.withAuthentication(new KafkaUserScramSha512ClientAuthentication());
        }

        return createKafkaUser(kafkaUserName, kafkaUserSpec.build());
    }

    /**
     * This method is used by the components to get the current logging level from the CR.
     * @param logging The logging object from the spec.
     * @param defaultString The default String to use if no loggers are configured.
     * @param singleLoggingLevel A boolean indicating whether a single logging level should be returned, rather than
     *                           a string of loggers and associated logging levels.
     * @param defaultString The delimiter String that is used to separate the logger and the logging level.
     * @return A string containing the trace string to apply
     */
    protected String getTraceString(Logging logging, String defaultString, boolean singleLoggingLevel) {
        String loggingString = defaultString;
        if (logging != null && InlineLogging.TYPE_INLINE.equals(logging.getType())) {
            Map<String, String> loggers = ((InlineLogging) logging).getLoggers();
            if (loggers != null) {
                if (singleLoggingLevel) {
                    String firstKey = loggers.keySet().stream().findFirst().orElse(null);
                    if (firstKey != null) {
                        loggingString = loggers.get(firstKey);
                    }
                } else {
                    List<String> loggersArray = new ArrayList<>();
                    loggers.forEach((k, v) -> {
                        loggersArray.add(k + ":" + v);
                    });
                    loggingString = String.join(",", loggersArray);
                }
            }
        }
        return loggingString;
    }
}

