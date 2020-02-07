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
import com.ibm.eventstreams.api.Listener;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.ExternalAccess;
import com.ibm.eventstreams.api.spec.SecuritySpec.Encryption;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.NodeSelectorTerm;
import io.fabric8.kubernetes.api.model.NodeSelectorTermBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
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
import io.strimzi.api.kafka.model.status.ListenerAddress;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorage;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorageOverride;
import io.strimzi.api.kafka.model.template.PodTemplate;

import java.util.ArrayList;
import java.util.Arrays;
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
    public static final String DEFAULT_PROMETHEUS_PORT = "8081";

    public static final String CONFIG_MAP_SUFFIX = "-config";
    public static final String NETWORK_POLICY_SUFFIX = "-network-policy";

    private static final String KUBERNETES_ARCHITECTURE_LABEL_NAME = "beta.kubernetes.io/arch";
    private static final String DEFAULT_ARCHITECTURE = "amd64";
    protected static final Encryption DEFAULT_ENCRYPTION = Encryption.NONE;

    private static final long EVENTSTREAMS_USER_ID = 65534L;
    private static final String PRODUCT_ID = "ID";
    private static final String PRODUCT_NAME = "eventstreams";
    private static final String PRODUCT_VERSION = "version";
    private static final String PRODUCT_CLOUDPAK_RATIO_PRODUCTION = "1:1";
    private static final String PRODUCT_CLOUDPAK_RATIO_NON_PRODUCTION = "2:1";
    private static final String PRODUCT_METRIC = "VIRTUAL_PROCESSOR_CORE";
    private static final String CLOUDPAK_ID = "c8b82d189e7545f0892db9ef2731b90d";
    private static final String CLOUDPAK_NAME = "IBM Cloud Pak for Integration";
    private static final String CLOUDPAK_VERSION = "2019.4.1";

    public static final String KAFKA_USER_SECRET_VOLUME_NAME = "kafka-user";
    public static final String CA_CERT = "ca.crt";
    public static final String CA_P12 = "ca.p12";
    public static final String CA_P12_PASS = "ca.password";
    public static final String USER_CERT = "user.crt";
    public static final String USER_KEY = "user.key";
    public static final String USER_P12 = "user.p12";
    public static final String USER_P12_PASS = "user.password";

    private String kind;
    private String apiVersion;
    private String uid;
    private String componentName;
    private List<ContainerEnvVar> envVars;
    private String instanceName;
    private String namespace;
    private int replicas;
    private ExternalAccess externalAccess;
    private String architecture;
    private ResourceRequirements resourceRequirements;
    private PodTemplate podTemplate;
    private Encryption encryption;

    private List<LocalObjectReference> globalPullSecrets;
    protected String image = "";
    protected boolean customImage;
    private io.strimzi.api.kafka.model.Probe livenessProbe;
    private io.strimzi.api.kafka.model.Probe readinessProbe;

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

    protected void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public String getArchitecture() {
        return Optional.ofNullable(architecture).orElse(DEFAULT_ARCHITECTURE);
    }

    protected void setResourceRequirements(ResourceRequirements resourceRequirements) {
        this.resourceRequirements = resourceRequirements;
    }

    protected void setEncryption(Encryption encryption) {
        this.encryption = encryption;
    }

    protected Encryption getEncryption() {
        return encryption;
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
        switch (getEncryption()) {
            case TLS: return true;
            default: return false;
        }
    }
    protected String getUrlProtocol() {
        return tlsEnabled() ? "https://" : "http://";
    }
    protected String getUrlProtocol(Encryption encryption) {
        switch (encryption) {
            case TLS: return "https://";
            default: return "http://";
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

    public String componentPrefixedName(String name) {
        return name.isEmpty() ? getComponentName() : getComponentName() + "-" + name;
    }

    public String fullPrefixedName(String name) {
        return name.isEmpty() ? getDefaultResourceName() : getDefaultResourceName() + "-" + name;
    }

    public static String getDefaultResourceName(String instanceName, String componentName) {
        return getResourcePrefix(instanceName) + "-" + componentName;
    }

    protected String getInternalKafkaUserSecretName() {
        return getDefaultResourceName(instanceName, InternalKafkaUserModel.COMPONENT_NAME);
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

    public Map<String, String> getComponentLabels() {
        Map<String, String> labels = new HashMap<String, String>();

        labels.put(Labels.APP_LABEL, APP_NAME);
        labels.put(Labels.COMPONENT_LABEL, this.componentName);
        labels.put(Labels.INSTANCE_LABEL, this.instanceName);
        labels.put(Labels.RELEASE_LABEL, this.instanceName);
        labels.put(Labels.KUBERNETES_NAME_LABEL, Labels.KUBERNETES_NAME);
        labels.put(Labels.KUBERNETES_INSTANCE_LABEL, this.instanceName);
        labels.put(Labels.KUBERNETES_MANAGED_BY_LABEL, Labels.KUBERNETES_MANAGED_BY);

        return labels;
    }

    public Map<String, String> getServiceSelectorLabel(String serviceSelector) {
        Map<String, String> labels = new HashMap<String, String>();

        labels.put(Labels.SERVICE_SELECTOR_LABEL, serviceSelector);

        return labels;
    }

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

    protected ResourceRequirements getResourceRequirements(ResourceRequirements defaultResources) {
        return getResourceRequirements(resourceRequirements, defaultResources);
    }

    protected SecurityContext getSecurityContext(boolean readOnlyRootFileSystem) {
        return new SecurityContextBuilder()
                .withNewPrivileged(false)
                .withNewReadOnlyRootFilesystem(readOnlyRootFileSystem)
                .withAllowPrivilegeEscalation(false)
                .withNewRunAsNonRoot(true)
                .withRunAsUser(EVENTSTREAMS_USER_ID)
                .withNewCapabilities()
                    .addNewDrop("ALL")
                .endCapabilities()
                .build();
    }

    protected PodSecurityContext getPodSecurityContext() {
        return new PodSecurityContextBuilder()
                .withNewRunAsNonRoot(true)
                .withRunAsUser(EVENTSTREAMS_USER_ID)
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
                        .withNewAffinity()
                            .editOrNewNodeAffinity()
                                .editOrNewRequiredDuringSchedulingIgnoredDuringExecution()
                                    .addToNodeSelectorTerms(getNodeSelectorTermForArchitecture())
                                .endRequiredDuringSchedulingIgnoredDuringExecution()
                            .endNodeAffinity()
                        .endAffinity()
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

    protected ServicePort createServicePort(Listener listener) {
        return new ServicePortBuilder()
                .withNewName(componentPrefixedName(listener.getName()))
                .withNewProtocol("TCP")
                .withPort(listener.getPort())
                .build();
    }

    protected ServicePort createServicePort(int port) {
        return new ServicePortBuilder()
                .withNewName(componentPrefixedName("http"))
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

    // will go when remove old routes
    protected Route createRoute(int port) {
        if (tlsEnabled()) {
            TLSConfig tlsConfig = new TLSConfigBuilder()
                    .withTermination("passthrough")
                    .withInsecureEdgeTerminationPolicy("None")
                    .build();
            return createRoute(port, tlsConfig);
        }
        RouteBuilder routeBuilder = createDefaultRouteBuilder(getDefaultResourceName(), getDefaultResourceName(), port);
        return routeBuilder.build();
    }

    // Used by UI
    protected Route createRoute(int port, TLSConfig tlsConfig) {
        RouteBuilder routeBuilder = createDefaultRouteBuilder(getDefaultResourceName(), getDefaultResourceName(), port);
        routeBuilder
                .editSpec()
                    .withTls(tlsConfig)
                .endSpec();
        return routeBuilder.build();
    }

    protected Route createRoute(String serviceName, Listener listener) {
        String name = fullPrefixedName(listener.getName());
        RouteBuilder routeBuilder = createDefaultRouteBuilder(name, serviceName, listener.getPort());
        if (listener.isTls()) {
            routeBuilder
                    .editSpec()
                    .withNewTls()
                        .withTermination("passthrough")
                        .withInsecureEdgeTerminationPolicy("None")
                    .endTls()
                    .endSpec();
        }
        return routeBuilder.build();
    }

    protected RouteBuilder createDefaultRouteBuilder(String name, String serviceName, int port) {
        return new RouteBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .addToLabels(getComponentLabels())
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
    }

    protected NetworkPolicy createNetworkPolicy(LabelSelector labelSelector,
                                                List<NetworkPolicyIngressRule> ingressRules,
                                                List<NetworkPolicyEgressRule> egressRules) {
        return new NetworkPolicyBuilder()
            .withNewMetadata()
                .withName(getDefaultResourceName() + NETWORK_POLICY_SUFFIX)
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

    protected PersistentVolumeClaim createPersistentVolumeClaim(String name, PersistentClaimStorage storage) {
        Map<String, Quantity> requests = new HashMap<>();
        requests.put("storage", new Quantity(Optional.ofNullable(storage.getSize()).orElse("1Gi"), null));

        LabelSelector selector = null;
        if (storage.getSelector() != null && !storage.getSelector().isEmpty()) {
            selector = new LabelSelector(null, storage.getSelector());
        }

        String storageClass = Optional.ofNullable(storage.getStorageClass()).orElse("");

        String accessMode = Optional.ofNullable(storage.getOverrides()).orElseGet(Collections::emptyList)
                .stream()
                .filter(override -> override != null && override.getAdditionalProperties() != null)
                .map(PersistentClaimStorageOverride::getAdditionalProperties)
                .map(override -> override.get("accessMode").toString())
                .findFirst()
                .orElse("ReadWriteOnce");

        PersistentVolumeClaimBuilder pvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(getComponentLabels())
                .endMetadata()
                .withNewSpec()
                    .withAccessModes(accessMode)
                    .withNewResources()
                        .addToRequests(requests)
                    .endResources()
                    .withStorageClassName(storageClass)
                    .withSelector(selector)
                .endSpec();

        if (storage.isDeleteClaim()) {
            pvc = pvc.editMetadata()
                    .withOwnerReferences(getEventStreamsOwnerReference())
                .endMetadata();
        }

        return pvc.build();
    }

    protected Volume createKafkaUserCertVolume() {
        return new VolumeBuilder()
            .withNewName(KAFKA_USER_SECRET_VOLUME_NAME)
            .withNewSecret()
                .withNewSecretName(getInternalKafkaUserSecretName())
                .addNewItem().withNewKey(USER_CERT).withNewPath("podtls.crt").endItem()
                .addNewItem().withNewKey(USER_KEY).withNewPath("podtls.key").endItem()
                .addNewItem().withNewKey(USER_P12).withNewPath("podtls.p12").endItem()
            .endSecret()
            .build();
    }

    protected NodeSelectorTerm getNodeSelectorTermForArchitecture() {

        return new NodeSelectorTermBuilder()
                .addNewMatchExpression()
                    .withNewKey(KUBERNETES_ARCHITECTURE_LABEL_NAME)
                    .withNewOperator("In")
                    .withValues(getArchitecture())
                .endMatchExpression()
                .build();
    }

    protected NetworkPolicyIngressRule createIngressRule(int port, String componentName) {

        return createIngressRule(port, Collections.singletonMap(Labels.COMPONENT_LABEL, componentName));
    
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
        String listenerType = tlsEnabled() ? "tls" : "plain";
        return getKafkaBootstrap(kafkaListeners, listenerType);
    }

    protected String getRunAsKafkaBootstrap(List<ListenerStatus> kafkaListeners) {
        String listenerType = "runas";
        return getKafkaBootstrap(kafkaListeners, listenerType);
    }

    protected String getExternalKafkaBootstrap(List<ListenerStatus> kafkaListeners) {
        return getKafkaBootstrap(kafkaListeners, "external");
    }

    private String getKafkaBootstrap(List<ListenerStatus> kafkaListeners, String listenerType) {
        String kafkaBootstrap = getResourcePrefix() + "-kafka-bootstrap." + getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + (tlsEnabled() ? EventStreamsKafkaModel.KAFKA_PORT_TLS : EventStreamsKafkaModel.KAFKA_PORT);

        Optional<ListenerAddress> listenerAddress = kafkaListeners
                .stream()
                .filter(listener -> listenerType.equals(listener.getType()))
                .findFirst()
                .map(ListenerStatus::getAddresses)
                .map(addressList -> addressList.get(0));

        if (listenerAddress.isPresent()) {
            kafkaBootstrap = listenerAddress.get().getHost() + ":" + listenerAddress.get().getPort();
        }

        return kafkaBootstrap;
    }

}