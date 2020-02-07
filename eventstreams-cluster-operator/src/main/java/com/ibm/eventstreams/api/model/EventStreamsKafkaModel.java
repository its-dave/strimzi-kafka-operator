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
import java.util.regex.Pattern;

import com.ibm.eventstreams.api.Labels;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.KafkaMetricsJMXRule;
import com.ibm.eventstreams.api.spec.KafkaMetricsJMXRuleBuilder;
import com.ibm.eventstreams.api.spec.SecuritySpec;

import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTermBuilder;
import io.strimzi.api.kafka.model.ContainerEnvVar;
import io.strimzi.api.kafka.model.ContainerEnvVarBuilder;
import io.strimzi.api.kafka.model.EntityOperatorSpec;
import io.strimzi.api.kafka.model.EntityTopicOperatorSpec;
import io.strimzi.api.kafka.model.EntityUserOperatorSpec;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaBuilder;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.TlsSidecar;
import io.strimzi.api.kafka.model.ZookeeperClusterSpec;
import io.strimzi.api.kafka.model.template.ContainerTemplate;
import io.strimzi.api.kafka.model.template.EntityOperatorTemplate;
import io.strimzi.api.kafka.model.template.KafkaClusterTemplate;
import io.strimzi.api.kafka.model.template.PodTemplate;
import io.strimzi.api.kafka.model.template.ZookeeperClusterTemplate;

public class EventStreamsKafkaModel extends AbstractModel {

    private static final String KAFKA_SERVICE_SELECTOR = "kafka-sts";
    private static final String ZOOKEEPER_SERVICE_SELECTOR = "zookeeper-sts";
    private static final String ENTITY_OPERATOR_SERVICE_SELECTOR = "entity-operator";

    private static final String STRIMZI_COMPONENT_NAME = "strimzi";
    public static final String KAFKA_COMPONENT_NAME = "kafka";
    public static final String ZOOKEEPER_COMPONENT_NAME = "zookeeper";
    private static final String ENTITY_OPERATOR_COMPONENT = "entity-operator";
    public static final int KAFKA_PORT = 9092;
    public static final int KAFKA_PORT_TLS = 9093;
    public static final int ZOOKEEPER_PORT = 2181;

    private final Kafka kafka;

    // Suppress until we refactor if appropriate
    @SuppressWarnings({"checkstyle:MethodLength"})
    public EventStreamsKafkaModel(EventStreams instance) {
        super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), STRIMZI_COMPONENT_NAME);

        setOwnerReference(instance);
        setArchitecture(instance.getSpec().getArchitecture());
        setEncryption(Optional.ofNullable(instance.getSpec())
                            .map(EventStreamsSpec::getSecurity)
                            .map(SecuritySpec::getEncryption)
                            .orElse(DEFAULT_ENCRYPTION));

        Optional<KafkaSpec> strimziOverrides = Optional
            .ofNullable(instance)
            .map(EventStreams::getSpec)
            .map(EventStreamsSpec::getStrimziOverrides);

        KafkaClusterSpec kafkaClusterSpec = strimziOverrides.map(KafkaSpec::getKafka)
                .orElseGet(KafkaClusterSpec::new);

        ZookeeperClusterSpec zookeeperClusterSpec = strimziOverrides.map(KafkaSpec::getZookeeper)
                .orElseGet(ZookeeperClusterSpec::new);

        EntityOperatorSpec entityOperatorSpec = strimziOverrides.map(KafkaSpec::getEntityOperator)
                .orElseGet(EntityOperatorSpec::new);


        // must give the metrics maps a value if they have been defined
        // otherwise they will not be present in the kafka cr created
        Map<String, Object> kafkaMetrics = new HashMap<>();
        if (kafkaClusterSpec.getMetrics() != null) {
            kafkaMetrics.putAll(kafkaClusterSpec.getMetrics());
            kafkaMetrics.putIfAbsent("lowercaseOutputName", true);
            List<KafkaMetricsJMXRule> rules = new ArrayList<>(); 
            if (kafkaMetrics.get("rules") != null) {
                rules.addAll((List<KafkaMetricsJMXRule>) kafkaMetrics.get("rules"));
            }
            rules.addAll(getDefaultKafkaJMXMetricRules());
            kafkaMetrics.put("rules", rules);
        }

        Map<String, Object> zookeeperMetrics = new HashMap<>();
        if (zookeeperClusterSpec.getMetrics() != null) {
            zookeeperMetrics.putAll(zookeeperClusterSpec.getMetrics());
            zookeeperMetrics.putIfAbsent("lowercaseOutputName", true);
            List<KafkaMetricsJMXRule> rules = new ArrayList<>(); 
            if (zookeeperMetrics.get("rules") != null) {
                rules.addAll((List<KafkaMetricsJMXRule>) zookeeperMetrics.get("rules"));
            }
            zookeeperMetrics.put("rules", rules);
        }

        Affinity kafkaAffinity = Optional.ofNullable(kafkaClusterSpec.getTemplate())
                .map(KafkaClusterTemplate::getPod)
                .map(PodTemplate::getAffinity)
                .orElseGet(Affinity::new);

        Affinity zookeeperAffinity = Optional.ofNullable(zookeeperClusterSpec.getTemplate())
                .map(ZookeeperClusterTemplate::getPod)
                .map(PodTemplate::getAffinity)
                .orElseGet(Affinity::new);

        Affinity entityOperatorAffinity = Optional.ofNullable(entityOperatorSpec.getTemplate())
                .map(EntityOperatorTemplate::getPod)
                .map(PodTemplate::getAffinity)
                .orElseGet(Affinity::new);

        List<EnvVar> kafkaEnvVars = Arrays.asList(
                new EnvVarBuilder().withName("COLLECTOR_PORT").withValue(Integer.toString(CollectorModel.API_PORT)).build(),
                new EnvVarBuilder().withName("COLLECTOR_HOST").withValue(getResourcePrefix() + "-" + CollectorModel.COMPONENT_NAME).build(),
                new EnvVarBuilder().withName("COLLECTOR_TLS_ENABLED").withValue(String.valueOf(tlsEnabled())).build(),
                new EnvVarBuilder().withName("COLLECTOR_HOSTNAME_VERIFICATION").withValue("false").build()
        );

        kafkaEnvVars = combineEnvVarListsNoDuplicateKeys(
                kafkaEnvVars,
                Optional.ofNullable(kafkaClusterSpec.getTemplate())
                        .map(KafkaClusterTemplate::getKafkaContainer)
                        .map(ContainerTemplate::getEnv)
                        .orElse(new ArrayList<>()));

        ResourceRequirements kafkaResourceRequirements = getKafkaResources(kafkaClusterSpec);
        ResourceRequirements kafkaTlsSidecarResources = getKafkaTlsSidecarResources(kafkaClusterSpec);
        ResourceRequirements zookeeperResources = getZookeeperResources(zookeeperClusterSpec);
        ResourceRequirements zookeeperTlsResources = getZookeeperTlsResources(zookeeperClusterSpec);
        ResourceRequirements topicOperatorResources = getTopicOperatorResources(entityOperatorSpec);
        ResourceRequirements userOperatorResources = getUserTopicResources(entityOperatorSpec);
        ResourceRequirements entityOperatorTlsResources = getEntityOperatorTlsResources(entityOperatorSpec);

        this.kafka = new KafkaBuilder()
            .withApiVersion(Kafka.RESOURCE_GROUP + "/" + Kafka.V1BETA1)
            .editOrNewMetadata()
                .withNamespace(getNamespace())
                .withName(getKafkaInstanceName(getInstanceName()))
                .withOwnerReferences(getEventStreamsOwnerReference())
                .addToLabels(getComponentLabels())
            .endMetadata()
            .withNewSpecLike(strimziOverrides.orElseGet(KafkaSpec::new))
                .editOrNewKafka()
                    .addToMetrics(kafkaMetrics.isEmpty() ? null : kafkaMetrics)
                    .withResources(kafkaResourceRequirements)
                    .editOrNewTlsSidecar()
                        .withResources(kafkaTlsSidecarResources)
                    .endTlsSidecar()
                    .editOrNewTemplate()
                        .editOrNewKafkaContainer()
                            .withEnv(createContainerEnvVarList(kafkaEnvVars))
                        .endKafkaContainer()
                        .editOrNewPod()
                            .editOrNewMetadata()
                                .addToAnnotations(getEventStreamsMeteringAnnotations("kafka"))
                                .addToAnnotations(getPrometheusAnnotations())
                                .addToLabels(getComponentLabels())
                                .addToLabels(Labels.COMPONENT_LABEL, KAFKA_COMPONENT_NAME)
                                .addToLabels(getServiceSelectorLabel(KAFKA_SERVICE_SELECTOR))
                            .endMetadata()
                            .withSecurityContext(getPodSecurityContext())
                            // Equivalent to editOrNew
                            .withAffinity(new AffinityBuilder(kafkaAffinity)
                                .editOrNewNodeAffinity()
                                    .editOrNewRequiredDuringSchedulingIgnoredDuringExecution()
                                        .addToNodeSelectorTerms(getNodeSelectorTermForArchitecture())
                                    .endRequiredDuringSchedulingIgnoredDuringExecution()
                                .endNodeAffinity()
                                .editOrNewPodAntiAffinity()
                                    .addToPreferredDuringSchedulingIgnoredDuringExecution(preferredWeightedPodAntiAffinityTermForSelector(KAFKA_SERVICE_SELECTOR, 10))
                                    .addToPreferredDuringSchedulingIgnoredDuringExecution(preferredWeightedPodAntiAffinityTermForSelector(ZOOKEEPER_SERVICE_SELECTOR, 5))
                                .endPodAntiAffinity()
                                .build())
                        .endPod()
                    .endTemplate()
                .endKafka()
                .editOrNewZookeeper()
                    .addToMetrics(zookeeperMetrics.isEmpty() ? null : zookeeperMetrics)
                    .withResources(zookeeperResources)
                    .editOrNewTlsSidecar()
                        .withResources(zookeeperTlsResources)
                    .endTlsSidecar()
                    .editOrNewTemplate()
                        .editOrNewPod()
                            .editOrNewMetadata()
                                .addToAnnotations(getEventStreamsMeteringAnnotations())
                                .addToLabels(getComponentLabels())
                                .addToLabels(Labels.COMPONENT_LABEL, ZOOKEEPER_COMPONENT_NAME)
                                .addToLabels(getServiceSelectorLabel(ZOOKEEPER_SERVICE_SELECTOR))
                            .endMetadata()
                            .withSecurityContext(getPodSecurityContext())
                            .withAffinity(new AffinityBuilder(zookeeperAffinity)
                                .editOrNewNodeAffinity()
                                    .editOrNewRequiredDuringSchedulingIgnoredDuringExecution()
                                        .addToNodeSelectorTerms(getNodeSelectorTermForArchitecture())
                                    .endRequiredDuringSchedulingIgnoredDuringExecution()
                                .endNodeAffinity()
                                .editOrNewPodAntiAffinity()
                                    .addToPreferredDuringSchedulingIgnoredDuringExecution(preferredWeightedPodAntiAffinityTermForSelector(ZOOKEEPER_SERVICE_SELECTOR, 10))
                                    .addToPreferredDuringSchedulingIgnoredDuringExecution(preferredWeightedPodAntiAffinityTermForSelector(KAFKA_SERVICE_SELECTOR, 5))
                                .endPodAntiAffinity()
                                .build())
                        .endPod()
                    .endTemplate()
                .endZookeeper()
                .editOrNewEntityOperator()
                    .editOrNewTopicOperator()
                        .withResources(topicOperatorResources)
                    .endTopicOperator()
                    .editOrNewUserOperator()
                        .withResources(userOperatorResources)
                    .endUserOperator()
                    .editOrNewTlsSidecar()
                        .withResources(entityOperatorTlsResources)
                    .endTlsSidecar()
                    .editOrNewTemplate()
                        .editOrNewPod()
                            .editOrNewMetadata()
                                .addToAnnotations(getEventStreamsMeteringAnnotations())
                                .addToLabels(getComponentLabels())
                                .addToLabels(getServiceSelectorLabel(ENTITY_OPERATOR_SERVICE_SELECTOR))
                            .endMetadata()
                        .withAffinity(new AffinityBuilder(entityOperatorAffinity)
                                .editOrNewNodeAffinity()
                                    .editOrNewRequiredDuringSchedulingIgnoredDuringExecution()
                                        .addToNodeSelectorTerms(getNodeSelectorTermForArchitecture())
                                    .endRequiredDuringSchedulingIgnoredDuringExecution()
                                .endNodeAffinity()
                                .build())
                        .endPod()
                    .endTemplate()
                .endEntityOperator()
            .endSpec()
            .build();
    }

    /**
     * getKafkaInstanceName returns the name of the Kafka instance for the given EventStreams instance name
     * Do not use this method when not referencing Kafka resources
     */
    public static String getKafkaInstanceName(String instanceName) {
        return getResourcePrefix(instanceName);
    }

    private WeightedPodAffinityTerm preferredWeightedPodAntiAffinityTermForSelector(String serviceSelector, Integer affinityWeight) {

        return new WeightedPodAffinityTermBuilder()
            .withWeight(affinityWeight)
            .withNewPodAffinityTerm()
                .withTopologyKey("kubernetes.io/hostname")
                .withNewLabelSelector()
                    .addNewMatchExpression()
                        .withKey(Labels.INSTANCE_LABEL)
                        .withNewOperator("In")
                        .withValues(getInstanceName())
                    .endMatchExpression()
                    .addNewMatchExpression()
                        .withKey(Labels.SERVICE_SELECTOR_LABEL)
                        .withNewOperator("In")
                        .withValues(serviceSelector)
                    .endMatchExpression()
                .endLabelSelector()
            .endPodAffinityTerm()
            .build();
    }

    private ResourceRequirements getKafkaResources(KafkaClusterSpec kafkaClusterSpec) {
        ResourceRequirements initialKafkaResources = Optional.ofNullable(kafkaClusterSpec.getResources())
                .orElseGet(ResourceRequirements::new);
        Map<String, Quantity> requests = new HashMap<>();
        requests.put("cpu", new Quantity("100m"));
        requests.put("memory", new Quantity("100Mi"));

        Map<String, Quantity> limits = new HashMap<>();
        limits.put("cpu", new Quantity("1000m"));
        limits.put("memory", new Quantity("2Gi"));

        ResourceRequirements defaultKafkaResources = buildNewResourceRequirements(requests, limits);
        ResourceRequirements kafkaResourceRequirements = getResourceRequirements(initialKafkaResources, defaultKafkaResources);
        return kafkaResourceRequirements;
    }

    private ResourceRequirements defaultTlsSidecarResources() {
        Map<String, Quantity> requests = new HashMap<>();
        requests.put("cpu", new Quantity("10m"));
        requests.put("memory", new Quantity("10Mi"));

        Map<String, Quantity> limits = new HashMap<>();
        limits.put("cpu", new Quantity("100m"));
        limits.put("memory", new Quantity("100Mi"));

        return buildNewResourceRequirements(requests, limits);
    }

    private ResourceRequirements getKafkaTlsSidecarResources(KafkaClusterSpec kafkaClusterSpec) {
        ResourceRequirements initialKafkaTlsSidecarResources = Optional.ofNullable(kafkaClusterSpec.getTlsSidecar())
                .map(TlsSidecar::getResources)
                .orElseGet(ResourceRequirements::new);
        return getResourceRequirements(initialKafkaTlsSidecarResources, defaultTlsSidecarResources());
    }

    private ResourceRequirements getZookeeperTlsResources(ZookeeperClusterSpec zookeeperClusterSpec) {
        ResourceRequirements initialZookeeperTlsResources = Optional.ofNullable(zookeeperClusterSpec.getTlsSidecar())
                .map(TlsSidecar::getResources)
                .orElseGet(ResourceRequirements::new);
        return getResourceRequirements(initialZookeeperTlsResources, defaultTlsSidecarResources());
    }

    private ResourceRequirements getZookeeperResources(ZookeeperClusterSpec zookeeperClusterSpec) {
        ResourceRequirements initialZookeeperResources = Optional.ofNullable(zookeeperClusterSpec.getResources())
                .orElseGet(ResourceRequirements::new);
        Map<String, Quantity> requests = new HashMap<>();
        requests.put("cpu", new Quantity("100m"));
        requests.put("memory", new Quantity("100Mi"));

        Map<String, Quantity> limits = new HashMap<>();
        limits.put("cpu", new Quantity("1000m"));
        limits.put("memory", new Quantity("1Gi"));

        ResourceRequirements defaultZookeeperResources = buildNewResourceRequirements(requests, limits);
        return getResourceRequirements(initialZookeeperResources, defaultZookeeperResources);
    }

    private ResourceRequirements defaultOperatorResources() {
        Map<String, Quantity> requests = new HashMap<>();
        requests.put("cpu", new Quantity("10m"));
        requests.put("memory", new Quantity("50Mi"));

        Map<String, Quantity> limits = new HashMap<>();
        limits.put("cpu", new Quantity("1000m"));
        limits.put("memory", new Quantity("500Mi"));

        return buildNewResourceRequirements(requests, limits);
    }

    private ResourceRequirements getTopicOperatorResources(EntityOperatorSpec entityOperatorSpec) {
        ResourceRequirements initialTopicOperatorResources = Optional.ofNullable(entityOperatorSpec.getTopicOperator())
                .map(EntityTopicOperatorSpec::getResources)
                .orElseGet(ResourceRequirements::new);
        return getResourceRequirements(initialTopicOperatorResources, defaultOperatorResources());
    }

    private ResourceRequirements getUserTopicResources(EntityOperatorSpec entityOperatorSpec) {
        ResourceRequirements initialUserOperatorResources = Optional.ofNullable(entityOperatorSpec.getUserOperator())
                .map(EntityUserOperatorSpec::getResources)
                .orElseGet(ResourceRequirements::new);
        return getResourceRequirements(initialUserOperatorResources, defaultOperatorResources());
    }

    private ResourceRequirements getEntityOperatorTlsResources(EntityOperatorSpec entityOperatorSpec) {
        ResourceRequirements initialEntityOperatorTlsResources = Optional.ofNullable(entityOperatorSpec.getTlsSidecar())
                .map(TlsSidecar::getResources)
                .orElseGet(ResourceRequirements::new);
        return getResourceRequirements(initialEntityOperatorTlsResources, defaultTlsSidecarResources());
    }

    private ResourceRequirements buildNewResourceRequirements(Map<String, Quantity> requests, Map<String, Quantity> limits) {
        ResourceRequirements resources = new ResourceRequirementsBuilder()
                .withRequests(requests)
                .withLimits(limits)
                .build();
        return resources;
    }

    private List<ContainerEnvVar> createContainerEnvVarList(List<EnvVar> envVars) {
        List<ContainerEnvVar> containerEnvVarsList = new ArrayList<>();
        for (EnvVar envVar: envVars) {
            containerEnvVarsList.add(new ContainerEnvVarBuilder()
                .withNewName(envVar.getName())
                .withNewValue(envVar.getValue())
                .build());
        }
        return containerEnvVarsList;
    }

    public Kafka getKafka() {
        return this.kafka;
    }

    public static List<KafkaMetricsJMXRule> getDefaultKafkaJMXMetricRules() {
        List<KafkaMetricsJMXRule> rules = new ArrayList<>();
        rules.add(new KafkaMetricsJMXRuleBuilder()
            .withName("kafka_controller_$1_$2_$3")
            .withPattern(Pattern.compile("kafka.controller<type=(\\w+), name=(\\w+)><>(Count|Value|Mean)"))
            .build());
        rules.add(new KafkaMetricsJMXRuleBuilder()
            .withName("kafka_server_BrokerTopicMetrics_$1_$2")
            .withPattern(Pattern.compile("kafka.server<type=BrokerTopicMetrics, name=(BytesInPerSec|BytesOutPerSec)><>(Count)"))
            .build());
        rules.add(new KafkaMetricsJMXRuleBuilder()
            .withName("kafka_server_BrokerTopicMetrics_$1__alltopics_$2")
            .withPattern(Pattern.compile("kafka.server<type=BrokerTopicMetrics, name=(BytesInPerSec|BytesOutPerSec)><>(OneMinuteRate)"))
            .build());
        rules.add(new KafkaMetricsJMXRuleBuilder()
            .withName("kafka_server_ReplicaManager_$1_$2")
            .withPattern(Pattern.compile("kafka.server<type=ReplicaManager, name=(\\w+)><>(Value)"))
            .build());
        return rules;
    }
}
