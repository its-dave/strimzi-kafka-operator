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

import com.ibm.eventstreams.api.DefaultResourceRequirements;
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
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.Logging;
import io.strimzi.api.kafka.model.template.PodTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CollectorModel extends AbstractModel {

    public static final String COMPONENT_NAME = "metrics";
    public static final String APPLICATION_NAME = "metrics";
    public static final int METRICS_PORT = 8888; // no tls for prometheus
    public static final int API_PORT = 6888; // optionally tls secured for internal communication
    public static final int DEFAULT_REPLICAS = 1;
    private static final String DEFAULT_IBMCOM_IMAGE = "ibmcom/collector:latest";
    private String traceLevel = "0";

    public static final String DEFAULT_CIPHER_SUITES_NODE = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_RSA_WITH_AES_128_GCM_SHA256";

    private ServiceAccount serviceAccount;
    private Deployment deployment;
    private Service service;
    private NetworkPolicy networkPolicy;

    /**
     * This class is used to model all the kube resources required for correct deployment of the Collector component
     * @param instance
     * @param imageConfig
     */
    public CollectorModel(EventStreams instance,
                          EventStreamsOperatorConfig.ImageLookup imageConfig) {
        super(instance, COMPONENT_NAME, APPLICATION_NAME);
        Optional<ComponentSpec> collectorSpec = Optional.ofNullable(instance.getSpec())
            .map(EventStreamsSpec::getCollector);
        
        if (collectorSpec.isPresent()) {

            setOwnerReference(instance);
            setReplicas(collectorSpec.map(ComponentSpec::getReplicas).orElse(DEFAULT_REPLICAS));
            setResourceRequirements(collectorSpec.map(ComponentSpec::getResources).orElseGet(ResourceRequirements::new));
            setEnvVars(collectorSpec.map(ContainerSpec::getEnvVars).orElseGet(ArrayList::new));
            setPodTemplate(collectorSpec.map(ComponentSpec::getTemplate)
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
            setImage(firstDefinedImage(
                DEFAULT_IBMCOM_IMAGE,
                            collectorSpec.map(ComponentSpec::getImage),
                            imageConfig.getCollectorImage()));
            setCustomImage(DEFAULT_IBMCOM_IMAGE, imageConfig.getCollectorImage());
            setLivenessProbe(collectorSpec.map(ComponentSpec::getLivenessProbe)
                    .orElseGet(io.strimzi.api.kafka.model.Probe::new));
            setReadinessProbe(collectorSpec.map(ComponentSpec::getReadinessProbe)
                    .orElseGet(io.strimzi.api.kafka.model.Probe::new));
            setTraceLevel(collectorSpec.map(ComponentSpec::getLogging).orElse(null));

            Boolean enableProducerMetrics = Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getConfig)
                .filter(map -> map.containsKey("interceptor.class.names"))
                .isPresent();
                                
            deployment = createDeployment(getContainers(instance), getVolumes());
            service = createService(enableProducerMetrics);
            networkPolicy = createNetworkPolicy();
            serviceAccount = createServiceAccount();
        }
    }


    /**
     * 
     * @return The list of volumes to put into the Collector pod
     */
    private List<Volume> getVolumes() {
        return Arrays.asList(createKafkaUserCertVolume());
    }

    /**
     * 
     * @return The list of containers to put into the Collector pod
     */
    private List<Container> getContainers(EventStreams instance) {
        return Arrays.asList(getCollectorContainer(instance));
    }

    /**
     * 
     * @return The Collector container
     */
    private Container getCollectorContainer(EventStreams instance) {
        List<EnvVar> envVarDefaults = Arrays.asList(
            new EnvVarBuilder().withName("TRACE_LEVEL").withValue(traceLevel).build(),
            new EnvVarBuilder().withName("API_PORT").withValue(Integer.toString(API_PORT)).build(),
            new EnvVarBuilder().withName("METRICS_PORT").withValue(Integer.toString(METRICS_PORT)).build(),
            new EnvVarBuilder().withName("TLS_ENABLED").withValue(String.valueOf(tlsEnabled())).build(),
            new EnvVarBuilder().withName("TLS_CERT").withValue("/etc/ssl/certs/podtls.crt").build(),
            new EnvVarBuilder().withName("TLS_KEY").withValue("/etc/ssl/certs/podtls.key").build(),
            new EnvVarBuilder().withName("LICENSE").withValue("accept").build(),
            new EnvVarBuilder().withName("CIPHER_SUITES").withValue(DEFAULT_CIPHER_SUITES_NODE).build(),
            new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build(),
            new EnvVarBuilder().withName(TLS_VERSION_ENV_KEY).withValue(getTlsVersionEnvValue(instance)).build()
        );

        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(envVarDefaults);

        return new ContainerBuilder()
            .withName(COMPONENT_NAME)
            .withImage(getImage())
            .withEnv(envVars)
            .withSecurityContext(getSecurityContext(false))
            .withResources(getResourceRequirements(DefaultResourceRequirements.COLLECTOR))
            .addNewVolumeMount()
                .withNewName(KAFKA_USER_SECRET_VOLUME_NAME)
                .withMountPath("/etc/ssl/certs")
                .withNewReadOnly(true)
            .endVolumeMount()
            .addNewPort()
                .withName("metrics")
                .withContainerPort(METRICS_PORT)
            .endPort()
            .addNewPort()
                .withName("api")
                .withContainerPort(API_PORT)
            .endPort()
            .withLivenessProbe(createLivenessProbe())
            .withReadinessProbe(createReadinessProbe())
            .build();
    }

    /**
     * 
     * @return The liveness probe for the Collector container
     */
    protected Probe createLivenessProbe() {
        Probe defaultLivenessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/health")
                .withNewPort(API_PORT)
                .withScheme(getHealthCheckProtocol())
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(20)
                .withPeriodSeconds(20)
                .withTimeoutSeconds(10)
                .build();
        return combineProbeDefinitions(defaultLivenessProbe, super.getLivenessProbe());
    }

    /**
     * 
     * @return The readiness probe for the Collector container
     */
    protected Probe createReadinessProbe() {
        Probe defaultReadinessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/health")
                .withNewPort(API_PORT)
                .withScheme(getHealthCheckProtocol())
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(20)
                .withPeriodSeconds(20)
                .withTimeoutSeconds(10)
                .withFailureThreshold(2)
                .withSuccessThreshold(1)
                .build();
        return combineProbeDefinitions(defaultReadinessProbe, super.getReadinessProbe());
    }

    /**
     * 
     * @return The network policy for the Collector pod
     */
    private NetworkPolicy createNetworkPolicy() {
        List<NetworkPolicyIngressRule> ingressRules = new ArrayList<>(1);
        ingressRules.add(new NetworkPolicyIngressRule());

        return createNetworkPolicy(createLabelSelector(COMPONENT_NAME), ingressRules, null);
    }

    /**
     * 
     * @param enabledProducerMetrics if true the service will be created with a metrics port
     * and annotated with the prometheus annotations
     * @return the service associated with the Collector pod
     */
    private Service createService(boolean enabledProducerMetrics) {
        List<ServicePort> svcPorts = new ArrayList<>();
        Map<String, String> annotations = new HashMap<>();
        svcPorts.add(createServicePort(API_PORT));
        if (enabledProducerMetrics) {
            svcPorts.add(new ServicePortBuilder()
                    .withPort(METRICS_PORT)
                    .withNewName("metrics")
                    .withNewProtocol("TCP")
                    .build());
            annotations.put("prometheus.io/scrape", "true");
            annotations.put("prometheus.io/port", String.valueOf(METRICS_PORT));
            annotations.put("prometheus.io/path", "/metrics");
        }
        return createService(getDefaultResourceName(), svcPorts, annotations);
    }

    /**
     * @return Deployment return the deployment
     */
    public Deployment getDeployment() {
        return this.deployment;
    }

    /**
     * @return Service return the service
     */
    public Service getService() {
        return this.service;
    }

    /**
     * @return NetworkPolicy return the network policy
     */
    public NetworkPolicy getNetworkPolicy() {
        return this.networkPolicy;
    }

    /**
     * @return getServiceAccount return the service account
     */
    public ServiceAccount getServiceAccount() {
        return this.serviceAccount;
    }

    private void setTraceLevel(Logging logging) {
        if (logging != null && InlineLogging.TYPE_INLINE.equals(logging.getType())) {
            Map<String, String> loggers = ((InlineLogging) logging).getLoggers();
            String firstKey = loggers.keySet().stream().findFirst().orElse(null);
            if (firstKey != null) {
                traceLevel = loggers.get(firstKey);
            }
        }
    }
}
