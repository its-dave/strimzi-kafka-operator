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
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyEgressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.fabric8.openshift.api.model.Route;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.Logging;
import io.strimzi.api.kafka.model.template.PodTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RestProducerModel extends AbstractSecureEndpointModel {

    public static final String COMPONENT_NAME = "rest-producer";
    public static final int SERVICE_PORT = 8080;
    public static final int DEFAULT_REPLICAS = 1;
    private static final String DEFAULT_IBMCOM_IMAGE = "ibmcom/rest-producer:latest";

    private String loggingLevel = "INFO";

    private Deployment deployment;
    private final ServiceAccount serviceAccount;
    private Service service;
    private NetworkPolicy networkPolicy;
    private List<ListenerStatus> kafkaListeners;
    private final Route route;

    public RestProducerModel(EventStreams instance,
                             EventStreamsOperatorConfig.ImageLookup imageConfig,
                             List<ListenerStatus> kafkaListeners) {
        super(instance, instance.getMetadata().getNamespace(), COMPONENT_NAME);
        this.kafkaListeners = kafkaListeners != null ? new ArrayList<>(kafkaListeners) : new ArrayList<>();

        Optional<ComponentSpec> restProducerSpec = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getRestProducer);

        setOwnerReference(instance);
        setArchitecture(instance.getSpec().getArchitecture());
        setReplicas(restProducerSpec.map(ComponentSpec::getReplicas).orElse(DEFAULT_REPLICAS));
        setEnvVars(restProducerSpec.map(ContainerSpec::getEnvVars).orElseGet(ArrayList::new));
        setResourceRequirements(restProducerSpec.map(ComponentSpec::getResources).orElseGet(ResourceRequirements::new));
        setPodTemplate(restProducerSpec.map(ComponentSpec::getTemplate)
                           .map(ComponentTemplate::getPod)
                           .orElseGet(PodTemplate::new));
        setEncryption(Optional.ofNullable(instance.getSpec())
                           .map(EventStreamsSpec::getSecurity)
                           .map(SecuritySpec::getEncryption)
                           .orElse(DEFAULT_ENCRYPTION));
        setGlobalPullSecrets(Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getImages).map(
            ImagesSpec::getPullSecrets).orElseGet(ArrayList::new));
        setImage(firstDefinedImage(
                DEFAULT_IBMCOM_IMAGE,
                        restProducerSpec.map(ContainerSpec::getImage),
                        imageConfig.getRestProducerImage()));
        setCustomImage(DEFAULT_IBMCOM_IMAGE, imageConfig.getRestProducerImage());
        setLivenessProbe(restProducerSpec.map(ComponentSpec::getLivenessProbe)
                .orElseGet(io.strimzi.api.kafka.model.Probe::new));
        setReadinessProbe(restProducerSpec.map(ComponentSpec::getReadinessProbe)
                .orElseGet(io.strimzi.api.kafka.model.Probe::new));
        setLoggingLevel(restProducerSpec.map(ComponentSpec::getLogging).orElse(null));

        deployment = createDeployment(getContainers(), getVolumes());
        serviceAccount = createServiceAccount();
        service = createService(SERVICE_PORT);
        createServices();
        createRoutes();
        networkPolicy = createNetworkPolicy();
        route = createRoute(SERVICE_PORT);
    }

    private List<Volume> getVolumes() {
        return Arrays.asList(createKafkaUserCertVolume());
    }

    private List<Container> getContainers() {
        return Arrays.asList(getRestProducerContainer());
    }

    private Container getRestProducerContainer() {
        String kafkaBootstrap = getInternalKafkaBootstrap(kafkaListeners);

        List<EnvVar> envVarDefaults = Arrays.asList(
            new EnvVarBuilder().withName("DEPLOYMENT_MODE").withValue("onprem").build(),
            new EnvVarBuilder().withName("LOGGING_LEVEL").withValue(loggingLevel).build(),
            new EnvVarBuilder().withName("LOG_FORMAT").withValue("basic").build(),
            new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_URL").withValue(kafkaBootstrap).build(),
            new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build(),
            new EnvVarBuilder().withName("CONFIGMAP").withValue("configmap").build(),
            new EnvVarBuilder().withName("LICENSE").withValue("accept").build(),
            new EnvVarBuilder().withName("SECURITY_PROTOCOL").withValue(tlsEnabled() ? "ssl" : "none").build(),
            new EnvVarBuilder().withName("USE_TLS_API").withValue(String.valueOf(tlsEnabled())).build(),
            new EnvVarBuilder().withName("TLS_CERT").withValue("/etc/ssl/certs/podtls.crt").build(),
            new EnvVarBuilder().withName("TLS_PRIKEY").withValue("/etc/ssl/certs/podtls.key").build()
        );

        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(envVarDefaults);

        ResourceRequirements resourceRequirements = getResourceRequirements(
                new ResourceRequirementsBuilder()
                        .addToRequests("cpu", new Quantity("500m"))
                        .addToRequests("memory", new Quantity("1Gi"))
                        .addToLimits("cpu", new Quantity("4000m"))
                        .addToLimits("memory", new Quantity("2Gi"))
                        .build()
        );

        return new ContainerBuilder()
            .withName(COMPONENT_NAME)
            .withImage(getImage())
            .withEnv(envVars)
            .withSecurityContext(getSecurityContext(true))
            .withResources(resourceRequirements)
            .addNewVolumeMount()
                .withNewName(KAFKA_USER_SECRET_VOLUME_NAME)
                .withMountPath("/etc/ssl/certs")
                .withNewReadOnly(true)
            .endVolumeMount()
            .withLivenessProbe(createLivenessProbe())
            .withReadinessProbe(createReadinessProbe())
            .build();
    }

    protected Probe createLivenessProbe() {
        Probe defaultLivenessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/alive")
                .withNewPort(SERVICE_PORT)
                .withScheme(getHealthCheckProtocol())
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(60)
                .withPeriodSeconds(20)
                .withTimeoutSeconds(10)
                .withSuccessThreshold(1)
                .withFailureThreshold(6)
                .build();
        return combineProbeDefinitions(defaultLivenessProbe, super.getLivenessProbe());
    }

    protected Probe createReadinessProbe() {
        Probe defaultReadinessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/alive")
                .withNewPort(SERVICE_PORT)
                .withScheme(getHealthCheckProtocol())
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(30)
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

    private NetworkPolicy createNetworkPolicy() {

        List<NetworkPolicyIngressRule> ingressRules = new ArrayList<>(1);
        ingressRules.add(createIngressRule(SERVICE_PORT, new HashMap<>()));

        List<NetworkPolicyEgressRule> egressRules = new ArrayList<>(2);
        egressRules.add(createEgressRule(EventStreamsKafkaModel.KAFKA_PORT, EventStreamsKafkaModel.KAFKA_COMPONENT_NAME));
        egressRules.add(createEgressRule(AdminProxyModel.SERVICE_PORT, AdminProxyModel.COMPONENT_NAME));

        return createNetworkPolicy(createLabelSelector(COMPONENT_NAME), ingressRules, egressRules);
    }

    private void setLoggingLevel(Logging logging) {
        if (logging != null && InlineLogging.TYPE_INLINE.equals(logging.getType())) {
            Map<String, String> loggers = ((InlineLogging) logging).getLoggers();
            String firstKey = loggers.keySet().stream().findFirst().orElse(null);
            if (firstKey != null) {
                loggingLevel = loggers.get(firstKey);
            }
        }
    }

}
