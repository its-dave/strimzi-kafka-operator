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

import com.ibm.eventstreams.api.DefaultResourceRequirements;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.KafkaMetricsJMXRule;
import com.ibm.eventstreams.api.spec.KafkaMetricsJMXRuleBuilder;
import com.ibm.eventstreams.api.spec.SecuritySpec;

import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.SecurityContext;
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
import io.strimzi.api.kafka.model.KafkaResources;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.TlsSidecar;
import io.strimzi.api.kafka.model.ZookeeperClusterSpec;
import io.strimzi.api.kafka.model.template.ContainerTemplate;
import io.strimzi.api.kafka.model.template.EntityOperatorTemplate;
import io.strimzi.api.kafka.model.template.KafkaClusterTemplate;
import io.strimzi.api.kafka.model.template.PodTemplate;
import io.strimzi.api.kafka.model.template.ZookeeperClusterTemplate;
import io.strimzi.operator.common.model.Labels;

public class EventStreamsKafkaModel extends AbstractModel {

    // Keeping hard coded as currently these fields are protected
    private static final String KAFKA_SERVICE_SELECTOR = "kafka"; // KafkaCluster.APPLICATION_NAME
    private static final String ZOOKEEPER_SERVICE_SELECTOR = "zookeeper"; // ZookeeperCluster.APPLICATION_NAME
    private static final String ENTITY_OPERATOR_SERVICE_SELECTOR = "entity-operator"; // EntityOperator.APPLICATION_NAME

    private static final String STRIMZI_COMPONENT_NAME = "strimzi";
    public static final String KAFKA_COMPONENT_NAME = "kafka";
    public static final String ZOOKEEPER_COMPONENT_NAME = "zookeeper";
    private static final String ENTITY_OPERATOR_COMPONENT_NAME = "entity-operator";
    public static final int KAFKA_PORT = 9092;
    public static final int KAFKA_RUNAS_PORT = 8091;
    public static final int KAFKA_PORT_TLS = 9093;
    public static final int ZOOKEEPER_PORT = 2181;

    private final KafkaClusterSpec kafkaClusterSpec;
    private final ZookeeperClusterSpec zookeeperClusterSpec;
    private final EntityOperatorSpec entityOperatorSpec;

    private final Kafka kafka;

    /**
     * This class is used to model the kafka custom resource used by the strimzi cluster operator
     * @param instance
     */
    @SuppressWarnings({"checkstyle:MethodLength"})
    public EventStreamsKafkaModel(EventStreams instance) {
        super(instance, STRIMZI_COMPONENT_NAME, STRIMZI_COMPONENT_NAME);

        setOwnerReference(instance);
        setTlsVersion(Optional.ofNullable(instance.getSpec())
                            .map(EventStreamsSpec::getSecurity)
                            .map(SecuritySpec::getInternalTls)
                            .orElse(DEFAULT_INTERNAL_TLS));

        // These fields are required in the CRD but kept
        // as optional now to handle any potential null pointers
        Optional<KafkaSpec> strimziOverrides = Optional
            .ofNullable(instance)
            .map(EventStreams::getSpec)
            .map(EventStreamsSpec::getStrimziOverrides);

        kafkaClusterSpec = strimziOverrides.map(KafkaSpec::getKafka)
                .orElseGet(KafkaClusterSpec::new);

        zookeeperClusterSpec = strimziOverrides.map(KafkaSpec::getZookeeper)
                .orElseGet(ZookeeperClusterSpec::new);

        entityOperatorSpec = strimziOverrides.map(KafkaSpec::getEntityOperator)
                .orElseGet(EntityOperatorSpec::new);
        
        
        kafka = createKafka(strimziOverrides.orElseGet(KafkaSpec::new));
    }


    /**
     * 
     * @param strimziOverrides the kafka spec provided in the Eventstreams CR
     * @return the Kafka CR to be created
     */
    @SuppressWarnings({"checkstyle:MethodLength"})
    private Kafka createKafka(KafkaSpec strimziOverrides) {

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
                        .orElseGet(ArrayList::new));

        Labels strimziComponentLabels = labelsWithoutResourceGroup();

        SecurityContext readOnlyFsSecurityContext = getSecurityContext(true);
        SecurityContext writableFsSecurityContext = getSecurityContext(false);

        /**
         * Core Kafka CustomResource
         * only include mandatary component changes
         */

        KafkaBuilder builder = new KafkaBuilder()
            .withApiVersion(Kafka.RESOURCE_GROUP + "/" + Kafka.V1BETA1)
            .editOrNewMetadata()
                .withNamespace(getNamespace())
                .withName(getKafkaInstanceName(getInstanceName()))
                .withOwnerReferences(getEventStreamsOwnerReference())
                .addToLabels(strimziComponentLabels.toMap())
            .endMetadata()
            .withNewSpecLike(strimziOverrides)
                .editOrNewKafka()
                    .addToMetrics(getKafkaMetricsConfig().isEmpty() ? null : getKafkaMetricsConfig())
                    .withResources(getKafkaResources())
                    .editOrNewTlsSidecar()
                        .withResources(getKafkaTlsSidecarResources())
                    .endTlsSidecar()
                    .editOrNewTemplate()
                        .editOrNewKafkaContainer()
                            .withEnv(createContainerEnvVarList(kafkaEnvVars))
                            .withSecurityContext(writableFsSecurityContext)
                        .endKafkaContainer()
                        .editOrNewInitContainer()
                            .withSecurityContext(writableFsSecurityContext)
                        .endInitContainer()
                        .editOrNewTlsSidecarContainer()
                            .withSecurityContext(writableFsSecurityContext)
                        .endTlsSidecarContainer()
                        .editOrNewPod()
                            .editOrNewMetadata()
                                .addToAnnotations(getEventStreamsMeteringAnnotations("kafka"))
                                .addToAnnotations(getPrometheusAnnotations())
                                .addToLabels(strimziComponentLabels.toMap())
                            .endMetadata()
                            .withSecurityContext(getPodSecurityContext())
                            // Equivalent to editOrNew
                            .withAffinity(new AffinityBuilder(getKafkaAffinity())
                                .editOrNewPodAntiAffinity()
                                    .addToPreferredDuringSchedulingIgnoredDuringExecution(preferredWeightedPodAntiAffinityTermForSelector(KAFKA_SERVICE_SELECTOR, 10))
                                    .addToPreferredDuringSchedulingIgnoredDuringExecution(preferredWeightedPodAntiAffinityTermForSelector(ZOOKEEPER_SERVICE_SELECTOR, 5))
                                .endPodAntiAffinity()
                                .build())
                        .endPod()
                    .endTemplate()
                .endKafka()
                .editOrNewZookeeper()
                    .addToMetrics(getZookeeperMetricsConfig().isEmpty() ? null : getZookeeperMetricsConfig())
                    .withResources(getZookeeperResources())
                    .editOrNewTlsSidecar()
                        .withResources(getZookeeperTlsResources())
                    .endTlsSidecar()
                    .editOrNewTemplate()
                        .editOrNewZookeeperContainer()
                            .withSecurityContext(writableFsSecurityContext)
                        .endZookeeperContainer()
                        .editOrNewTlsSidecarContainer()
                            .withSecurityContext(writableFsSecurityContext)
                        .endTlsSidecarContainer()
                        .editOrNewPod()
                            .editOrNewMetadata()
                                .addToAnnotations(getEventStreamsMeteringAnnotations())
                                .addToLabels(strimziComponentLabels.toMap())
                            .endMetadata()
                            .withSecurityContext(getPodSecurityContext())
                            .withAffinity(new AffinityBuilder(getZookeeperAffinity())
                                .editOrNewPodAntiAffinity()
                                    .addToPreferredDuringSchedulingIgnoredDuringExecution(preferredWeightedPodAntiAffinityTermForSelector(ZOOKEEPER_SERVICE_SELECTOR, 10))
                                    .addToPreferredDuringSchedulingIgnoredDuringExecution(preferredWeightedPodAntiAffinityTermForSelector(KAFKA_SERVICE_SELECTOR, 5))
                                .endPodAntiAffinity()
                                .build())
                        .endPod()
                    .endTemplate()
                .endZookeeper()
                .editOrNewEntityOperator()
                    // topic operator is optional, so this is added
                    //  to the builder if needed below
                    .editOrNewUserOperator()
                        .withResources(getEntityUserOperatorResources())
                    .endUserOperator()
                    .editOrNewTlsSidecar()
                        .withResources(getEntityOperatorTlsResources())
                    .endTlsSidecar()
                    .editOrNewTemplate()
                        .editOrNewUserOperatorContainer()
                            .withSecurityContext(writableFsSecurityContext)
                        .endUserOperatorContainer()
                        .editOrNewTlsSidecarContainer()
                            .withSecurityContext(writableFsSecurityContext)
                        .endTlsSidecarContainer()
                        .editOrNewPod()
                            .editOrNewMetadata()
                                .addToAnnotations(getEventStreamsMeteringAnnotations())
                                .addToLabels(strimziComponentLabels.toMap())
                            .endMetadata()
                        .withAffinity(new AffinityBuilder(getEntityOperatorAffinity())
                                .build())
                        .endPod()
                    .endTemplate()
                .endEntityOperator()
            .endSpec();


        /**
         * Add optional elements to the spec before building
         */


        /**
         * Topic Operator
         */
        if (Optional.ofNullable(entityOperatorSpec).map(EntityOperatorSpec::getTopicOperator).isPresent()) {
            builder.editSpec()
                    .editEntityOperator()
                        .editOrNewTopicOperator()
                            .withResources(getEntityTopicOperatorResources())
                        .endTopicOperator()
                        .editOrNewTemplate()
                            .editOrNewTopicOperatorContainer()
                                .withSecurityContext(writableFsSecurityContext)
                            .endTopicOperatorContainer()
                        .endTemplate()
                    .endEntityOperator()
                .endSpec();
        }

        /**
         * JmxTrans
         */
        if (Optional.ofNullable(strimziOverrides).map(KafkaSpec::getJmxTrans).isPresent()) {
            builder.editSpec()
                    .editJmxTrans()
                         // These need to be defined
//                        .withResources(getJmxTransResources())
                        .editOrNewTemplate()
                            .editOrNewContainer()
                                .withSecurityContext(readOnlyFsSecurityContext)
                            .endContainer()
                        .endTemplate()
                    .endJmxTrans()
                    .endSpec();
        }

        return builder.build();
    }

    /**
     * getKafkaInstanceName returns the name of the Kafka instance for the given EventStreams instance name
     * Do not use this method when not referencing Kafka resources
     * @param instanceName
     * @return the name of the kafka instance
     */
    public static String getKafkaInstanceName(String instanceName) {
        return instanceName;
    }

    /**
     * 
     * @return The kafka affinity from the Eventstreams CR or a new affinity
     */
    private Affinity getKafkaAffinity() {
        return Optional.ofNullable(kafkaClusterSpec.getTemplate())
            .map(KafkaClusterTemplate::getPod)
            .map(PodTemplate::getAffinity)
            .orElseGet(Affinity::new);
    }

    /**
     * 
     * @return The zookeeper affinity from the Eventstreams CR or a new affinity
     */
    private Affinity getZookeeperAffinity() {
        return Optional.ofNullable(zookeeperClusterSpec.getTemplate())
            .map(ZookeeperClusterTemplate::getPod)
            .map(PodTemplate::getAffinity)
            .orElseGet(Affinity::new);
    }

    /**
     * 
     * @return The entity operator affinity from the Eventstreams CR or a new affinity
     */
    private Affinity getEntityOperatorAffinity() {
        return Optional.ofNullable(entityOperatorSpec.getTemplate())
            .map(EntityOperatorTemplate::getPod)
            .map(PodTemplate::getAffinity)
            .orElseGet(Affinity::new);
    }


    private WeightedPodAffinityTerm preferredWeightedPodAntiAffinityTermForSelector(String kubernetesName, Integer affinityWeight) {

        return new WeightedPodAffinityTermBuilder()
            .withWeight(affinityWeight)
            .withNewPodAffinityTerm()
                .withTopologyKey("kubernetes.io/hostname")
                .withNewLabelSelector()
                    .addNewMatchExpression()
                        .withKey(Labels.KUBERNETES_INSTANCE_LABEL)
                        .withNewOperator("In")
                        .withValues(getInstanceName())
                    .endMatchExpression()
                    .addNewMatchExpression()
                        .withKey(Labels.KUBERNETES_NAME_LABEL)
                        .withNewOperator("In")
                        .withValues(kubernetesName)
                    .endMatchExpression()
                .endLabelSelector()
            .endPodAffinityTerm()
            .build();
    }

    public static String getKafkaClusterCaCertName(String instanceName) {
        return KafkaResources.clusterCaCertificateSecretName(instanceName);
    }

    public static String getKafkaClientCaCertName(String instanceName) {
        return KafkaResources.clientsCaCertificateSecretName(instanceName);
    }

    public static String getKafkaBrokersSecretName(String instanceName) {
        return getKafkaInstanceName(instanceName) + "-kafka-brokers";
    }

    public static String getKafkaConfigMapName(final String instanceName) {
        return getKafkaInstanceName(instanceName) + "-kafka-config";
    }

    /**
     * 
     * @return The kafka resource requirements
     */
    private ResourceRequirements getKafkaResources() {
        ResourceRequirements kafkaResources = Optional.ofNullable(kafkaClusterSpec.getResources())
                .orElseGet(ResourceRequirements::new);
        return getResourceRequirements(kafkaResources, DefaultResourceRequirements.KAFKA);
    }

    /**
     * 
     * @return The kafka tls sidecar resource requirements
     */
    private ResourceRequirements getKafkaTlsSidecarResources() {
        ResourceRequirements tlsSidecarResources = Optional.ofNullable(kafkaClusterSpec.getTlsSidecar())
                .map(TlsSidecar::getResources)
                .orElseGet(ResourceRequirements::new);
        return getResourceRequirements(tlsSidecarResources, DefaultResourceRequirements.TLS_SIDECAR);
    }

    /**
     * 
     * @return The zookeeper resource requirements
     */
    private ResourceRequirements getZookeeperResources() {
        ResourceRequirements zookeeperResources = Optional.ofNullable(zookeeperClusterSpec.getResources())
                .orElseGet(ResourceRequirements::new);
        return getResourceRequirements(zookeeperResources, DefaultResourceRequirements.ZOOKEEPER);
    }

    /**
     * 
     * @return The zookeeper tls sidecar resource requirements
     */
    private ResourceRequirements getZookeeperTlsResources() {
        ResourceRequirements zkTlsSidecarResources = Optional.ofNullable(zookeeperClusterSpec.getTlsSidecar())
                .map(TlsSidecar::getResources)
                .orElseGet(ResourceRequirements::new);
        return getResourceRequirements(zkTlsSidecarResources, DefaultResourceRequirements.TLS_SIDECAR);
    }


    /**
     * 
     * @return The entity topic operator resource requirements
     */
    private ResourceRequirements getEntityTopicOperatorResources() {
        ResourceRequirements topicOperatorResources = Optional.ofNullable(entityOperatorSpec.getTopicOperator())
                .map(EntityTopicOperatorSpec::getResources)
                .orElseGet(ResourceRequirements::new);
        return getResourceRequirements(topicOperatorResources, DefaultResourceRequirements.ENTITY_OPERATOR);
    }

    /**
     * 
     * @return The entity user operator resource requirements
     */
    private ResourceRequirements getEntityUserOperatorResources() {
        ResourceRequirements userOperatorResources = Optional.ofNullable(entityOperatorSpec.getUserOperator())
                .map(EntityUserOperatorSpec::getResources)
                .orElseGet(ResourceRequirements::new);
        return getResourceRequirements(userOperatorResources, DefaultResourceRequirements.ENTITY_OPERATOR);
    }

    /**
     * 
     * @return The entity operator tls sidecar resources
     */
    private ResourceRequirements getEntityOperatorTlsResources() {
        ResourceRequirements entityOperatorTlsResources = Optional.ofNullable(entityOperatorSpec.getTlsSidecar())
                .map(TlsSidecar::getResources)
                .orElseGet(ResourceRequirements::new);
        return getResourceRequirements(entityOperatorTlsResources, DefaultResourceRequirements.TLS_SIDECAR);
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

    /**
     * 
     * @return The kafka custom resource
     */
    public Kafka getKafka() {
        return this.kafka;
    }

    /**
     * 
     * @return The kafka metrics map of config options
     */
    private Map<String, Object> getKafkaMetricsConfig() {
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
        return kafkaMetrics;
    }

    /**
     * 
     * @return The zookeeper Metrics map of config options
     */
    private Map<String, Object> getZookeeperMetricsConfig() {
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
        return zookeeperMetrics;
    }

    /**
     * 
     * @return A list of the default kafka JMX metric rules that are necessary to provide supported metrics data to the Admin UI
     */
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
