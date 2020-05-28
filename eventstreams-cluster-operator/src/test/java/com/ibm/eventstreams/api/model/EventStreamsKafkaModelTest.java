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

import static com.ibm.eventstreams.api.model.AbstractModel.CLOUDPAK_ID;
import static com.ibm.eventstreams.api.model.AbstractModel.CLOUDPAK_ID_KEY;
import static com.ibm.eventstreams.api.model.AbstractModel.PRODUCT_CHARGED_CONTAINERS_KEY;
import static com.ibm.eventstreams.api.model.AbstractModel.PRODUCT_ID_KEY;
import static com.ibm.eventstreams.api.model.AbstractModel.PRODUCT_ID_PRODUCTION;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.KafkaMetricsJMXRule;
import com.ibm.eventstreams.api.spec.KafkaMetricsJMXRuleBuilder;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.NodeSelectorTerm;
import io.fabric8.kubernetes.api.model.NodeSelectorTermBuilder;
import io.fabric8.kubernetes.api.model.PodAntiAffinity;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.strimzi.api.kafka.model.EntityOperatorSpec;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.ZookeeperClusterSpec;

public class EventStreamsKafkaModelTest {

    private final String instanceName = "test-instance";

    private EventStreamsBuilder createDefaultEventStreams() {
        return ModelUtils.createDefaultEventStreams(instanceName);
    }

    private EventStreamsKafkaModel createDefaultKafkaModel() {
        EventStreams eventStreamsResource = createDefaultEventStreams()
                    .editOrNewSpec()
                .endSpec()
                .build();
        return new EventStreamsKafkaModel(eventStreamsResource);
    }

    @Test
    public void testDefaultKafkaIsCreated() {
        Kafka kafka = createDefaultKafkaModel().getKafka();

        assertThat(kafka.getKind(), is("Kafka"));
        assertThat(kafka.getApiVersion(), is("eventstreams.ibm.com/v1beta1"));
    }

    @Test
    public void testDefaultKafkaHasRequiredMeteringAnnotations() {
        Kafka kafka = createDefaultKafkaModel().getKafka();

        Map<String, String> kafkaPodAnnotations = kafka.getSpec().getKafka().getTemplate().getPod()
                .getMetadata().getAnnotations();
        Map<String, String> zookeeperPodAnnotations = kafka.getSpec().getZookeeper().getTemplate().getPod()
                .getMetadata().getAnnotations();

        assertThat(kafkaPodAnnotations.get(PRODUCT_ID_KEY),  is(PRODUCT_ID_PRODUCTION));
        assertThat(kafkaPodAnnotations.get(CLOUDPAK_ID_KEY),  is(CLOUDPAK_ID));
        assertThat(kafkaPodAnnotations.get(PRODUCT_CHARGED_CONTAINERS_KEY),  is("kafka"));
        assertThat(kafkaPodAnnotations.get("prometheus.io/port"),  is(AbstractModel.DEFAULT_PROMETHEUS_PORT));

        assertThat(zookeeperPodAnnotations.get(PRODUCT_ID_KEY),  is(PRODUCT_ID_PRODUCTION));
        assertThat(zookeeperPodAnnotations.get(CLOUDPAK_ID_KEY),  is(CLOUDPAK_ID));
        assertThat(zookeeperPodAnnotations.get(PRODUCT_CHARGED_CONTAINERS_KEY),  is(""));
    }

    @Test
    public void testKafkaWithCustomLabels() {
        String customLabelKey = "custom-label-key";
        String customLabelValue = "custom-label-value";

        EventStreams instance = createDefaultEventStreams()
            .editSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                            .withNewTemplate()
                                .withNewPod()
                                    .withNewMetadata()
                                        .addToLabels(customLabelKey, customLabelValue)
                                    .endMetadata()
                                .endPod()
                            .endTemplate()
                        .endKafka()
                        .build()
                )
            .endSpec()
            .build();

        EventStreamsKafkaModel kafka = new EventStreamsKafkaModel(instance);

        Map<String, String> kafkaPodLabels = kafka.getKafka().getSpec().getKafka().getTemplate().getPod().getMetadata().getLabels();
        assertThat(kafkaPodLabels, hasEntry(customLabelKey, customLabelValue));
    }

    @Test
    public void testKafkaWithCustomAnnotations() {
        String customAnnotationKey = "custom-annotation-key";
        String customAnnotationValue = "custom-annotation-value";

        EventStreams instance = createDefaultEventStreams()
            .editSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                            .withNewTemplate()
                                .withNewPod()
                                    .withNewMetadata()
                                        .addToAnnotations(customAnnotationKey, customAnnotationValue)
                                    .endMetadata()
                                .endPod()
                            .endTemplate()
                        .endKafka()
                        .build()
            )
            .endSpec()
            .build();

        EventStreamsKafkaModel kafka = new EventStreamsKafkaModel(instance);
        Map<String, String> kafkaPodAnnotations = kafka.getKafka().getSpec().getKafka().getTemplate().getPod().getMetadata().getAnnotations();
        Map<String, String> zookeeperPodAnnotations = kafka.getKafka().getSpec().getZookeeper().getTemplate().getPod().getMetadata().getAnnotations();

        assertThat(kafkaPodAnnotations.get(PRODUCT_ID_KEY),  is(PRODUCT_ID_PRODUCTION));
        assertThat(kafkaPodAnnotations.get(CLOUDPAK_ID_KEY),  is(CLOUDPAK_ID));
        assertThat(kafkaPodAnnotations.get(PRODUCT_CHARGED_CONTAINERS_KEY),  is("kafka"));
        assertThat(kafkaPodAnnotations.get("prometheus.io/port"),  is(AbstractModel.DEFAULT_PROMETHEUS_PORT));
        assertThat(kafkaPodAnnotations.get(customAnnotationKey),  is(customAnnotationValue));

        assertThat(zookeeperPodAnnotations.get(PRODUCT_ID_KEY),  is(PRODUCT_ID_PRODUCTION));
        assertThat(zookeeperPodAnnotations.get(CLOUDPAK_ID_KEY),  is(CLOUDPAK_ID));
        assertThat(zookeeperPodAnnotations.get(PRODUCT_CHARGED_CONTAINERS_KEY),  is(""));
    }


    @Test
    public void testDefaultKafkaHasRequiredPodAntiAffinity() {
        Kafka kafkaInstance = createDefaultKafkaModel().getKafka();

        PodAntiAffinity actualPodAntiAffinity = kafkaInstance.getSpec()
                .getKafka()
                .getTemplate()
                .getPod()
                .getAffinity()
                .getPodAntiAffinity();
        assertThat(actualPodAntiAffinity
                .getPreferredDuringSchedulingIgnoredDuringExecution()
                .size(),  is(2));
        assertThat(actualPodAntiAffinity
                .getPreferredDuringSchedulingIgnoredDuringExecution()
                .size(),  is(2));
    }

    @Test
    public void testKafkaWithCustomNodeAffinity() {
        NodeSelectorTerm customNodeSelector = new NodeSelectorTermBuilder()
            .addNewMatchExpression()
                .withNewKey("custom-key")
                .withNewOperator("custom-operator")
                .addNewValue("custom-value")
            .endMatchExpression()
            .build();

        EventStreams instance = createDefaultEventStreams()
            .editSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                            .withNewTemplate()
                                .withNewPod()
                                    .withAffinity(new AffinityBuilder()
                                            .withNewNodeAffinity()
                                                .withNewRequiredDuringSchedulingIgnoredDuringExecution()
                                                    .addToNodeSelectorTerms(customNodeSelector)
                                                .endRequiredDuringSchedulingIgnoredDuringExecution()
                                            .endNodeAffinity()
                                            .build())
                                .endPod()
                            .endTemplate()
                        .endKafka()
                        .build()
                )
            .endSpec()
            .build();

        List<NodeSelectorTerm> nodeSelectorTerms = new EventStreamsKafkaModel(instance)
                .getKafka()
                .getSpec()
                .getKafka()
                .getTemplate()
                .getPod()
                .getAffinity()
                .getNodeAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .getNodeSelectorTerms();
        assertThat(nodeSelectorTerms, hasItem(customNodeSelector));
    }

    @Test
    public void testDefaultKafkaResources() {
        Kafka kafka = createDefaultKafkaModel().getKafka();

        ResourceRequirements resources = kafka.getSpec().getKafka().getResources();
        assertThat(resources.getRequests().get("cpu"), is(new Quantity("100m")));
        assertThat(resources.getLimits().get("cpu"), is(new Quantity("1000m")));
        assertThat(resources.getRequests().get("memory"), is(new Quantity("128Mi")));
        assertThat(resources.getLimits().get("memory"), is(new Quantity("2Gi")));
    }

    @Test
    public void testKafkaResourcesWithOverrides() {
        ResourceRequirements customResources = new ResourceRequirementsBuilder()
                .addToLimits("cpu", new Quantity("200m"))
                .addToRequests("memory", new Quantity("1Gi"))
                .build();

        EventStreams instance = createDefaultEventStreams()
            .editSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .editOrNewKafka()
                            .withResources(customResources)
                        .endKafka()
                        .build())
            .endSpec()
            .build();
        Kafka kafka = new EventStreamsKafkaModel(instance).getKafka();

        ResourceRequirements actualResources = kafka.getSpec().getKafka().getResources();
        assertThat(actualResources.getRequests().get("cpu"), is(new Quantity("100m")));
        assertThat(actualResources.getLimits().get("cpu"), is(new Quantity("200m")));
        assertThat(actualResources.getRequests().get("memory"), is(new Quantity("1Gi")));
        assertThat(actualResources.getLimits().get("memory"), is(new Quantity("2Gi")));
    }

    @Test
    public void testDefaultKafkaTlsSidecarResources() {
        Kafka kafka = createDefaultKafkaModel().getKafka();

        ResourceRequirements resources = kafka.getSpec().getKafka().getTlsSidecar().getResources();
        assertThat(resources.getRequests().get("cpu"), is(new Quantity("10m")));
        assertThat(resources.getLimits().get("cpu"), is(new Quantity("100m")));
        assertThat(resources.getRequests().get("memory"), is(new Quantity("128Mi")));
        assertThat(resources.getLimits().get("memory"), is(new Quantity("128Mi")));
    }

    @Test
    public void testKafkaTlsSidecarResourcesWithOverrides() {
        ResourceRequirements customResources = new ResourceRequirementsBuilder()
                .addToRequests("cpu", new Quantity("20m"))
                .addToLimits("memory", new Quantity("1Gi"))
                .build();

        EventStreams instance = createDefaultEventStreams()
            .editSpec()
            .withStrimziOverrides(new KafkaSpecBuilder()
                    .editOrNewKafka()
                        .editOrNewTlsSidecar()
                            .withResources(customResources)
                        .endTlsSidecar()
                    .endKafka()
                    .build())
            .endSpec()
            .build();
        Kafka kafka = new EventStreamsKafkaModel(instance).getKafka();

        ResourceRequirements actualResources = kafka.getSpec().getKafka().getTlsSidecar().getResources();
        assertThat(actualResources.getRequests().get("cpu"), is(new Quantity("20m")));
        assertThat(actualResources.getLimits().get("cpu"), is(new Quantity("100m")));
        assertThat(actualResources.getRequests().get("memory"), is(new Quantity("128Mi")));
        assertThat(actualResources.getLimits().get("memory"), is(new Quantity("1Gi")));
    }

    @Test
    public void testDefaultJmxTransResources() {
        EventStreams instance = createDefaultEventStreams()
            .editSpec()
            .withStrimziOverrides(new KafkaSpecBuilder()
                    .editOrNewJmxTrans()
                        .editOrNewTemplate()
                            .editOrNewPod()
                                .editOrNewMetadata()
                                    .addToLabels("foo", "bar")
                                .endMetadata()
                            .endPod()
                        .endTemplate()
                    .endJmxTrans()
                    .build())
            .endSpec()
            .build();
        Kafka kafka = new EventStreamsKafkaModel(instance).getKafka();

        ResourceRequirements resources = kafka.getSpec().getJmxTrans().getResources();
        assertThat(resources.getRequests().get("cpu"), is(new Quantity("250m")));
        assertThat(resources.getLimits().get("cpu"), is(new Quantity("1000m")));
        assertThat(resources.getRequests().get("memory"), is(new Quantity("1Gi")));
        assertThat(resources.getLimits().get("memory"), is(new Quantity("1Gi")));
    }

    @Test
    public void testJmxTransResourcesWithOverrides() {
        ResourceRequirements customResources = new ResourceRequirementsBuilder()
                .addToRequests("cpu", new Quantity("400m"))
                .addToLimits("memory", new Quantity("500Mi"))
                .build();

        EventStreams instance = createDefaultEventStreams()
            .editSpec()
            .withStrimziOverrides(new KafkaSpecBuilder()
                    .editOrNewJmxTrans()
                        .withResources(customResources)
                    .endJmxTrans()
                    .build())
            .endSpec()
            .build();
        Kafka kafka = new EventStreamsKafkaModel(instance).getKafka();

        ResourceRequirements actualResources = kafka.getSpec().getJmxTrans().getResources();
        assertThat(actualResources.getRequests().get("cpu"), is(new Quantity("400m")));
        assertThat(actualResources.getLimits().get("cpu"), is(new Quantity("1000m")));
        assertThat(actualResources.getRequests().get("memory"), is(new Quantity("1Gi")));
        assertThat(actualResources.getLimits().get("memory"), is(new Quantity("500Mi")));
    }

    @Test
    public void testDefaultZookeeperResources() {
        ZookeeperClusterSpec zookeeper = createDefaultKafkaModel().getKafka().getSpec().getZookeeper();

        ResourceRequirements resources = zookeeper.getResources();
        assertThat(resources.getRequests().get("cpu"), is(new Quantity("100m")));
        assertThat(resources.getLimits().get("cpu"), is(new Quantity("1000m")));
        assertThat(resources.getRequests().get("memory"), is(new Quantity("128Mi")));
        assertThat(resources.getLimits().get("memory"), is(new Quantity("1Gi")));
    }

    @Test
    public void testZookeeperResourcesWithOverrides() {
        ResourceRequirements customResources = new ResourceRequirementsBuilder()
                .addToRequests("cpu", new Quantity("20m"))
                .addToLimits("memory", new Quantity("1Gi"))
                .build();

        EventStreams instance = createDefaultEventStreams()
            .editSpec()
            .withStrimziOverrides(new KafkaSpecBuilder()
                    .editOrNewZookeeper()
                        .withResources(customResources)
                    .endZookeeper()
                    .build())
            .endSpec()
            .build();
        Kafka kafka = new EventStreamsKafkaModel(instance).getKafka();

        ResourceRequirements actualResources = kafka.getSpec().getZookeeper().getResources();
        assertThat(actualResources.getRequests().get("cpu"), is(new Quantity("20m")));
        assertThat(actualResources.getLimits().get("cpu"), is(new Quantity("1000m")));
        assertThat(actualResources.getRequests().get("memory"), is(new Quantity("128Mi")));
        assertThat(actualResources.getLimits().get("memory"), is(new Quantity("1Gi")));
    }

    @Test
    public void testTopicOperatorOmittedByDefault() {
        EntityOperatorSpec entityOperator = createDefaultKafkaModel().getKafka().getSpec().getEntityOperator();

        assertThat(entityOperator.getTopicOperator(), is(nullValue()));
    }

    @Test
    public void testTopicOperatorDefaultResources() {
        EventStreams instance = createDefaultEventStreams()
                .editSpec()
                    .withStrimziOverrides(new KafkaSpecBuilder()
                        .editOrNewEntityOperator()
                            .editOrNewTopicOperator()
                            .endTopicOperator()
                        .endEntityOperator()
                    .build())
                .endSpec()
                .build();
        Kafka kafka = new EventStreamsKafkaModel(instance).getKafka();

        ResourceRequirements resources = kafka.getSpec().getEntityOperator().getTopicOperator().getResources();
        assertThat(resources.getRequests().get("cpu"), is(new Quantity("10m")));
        assertThat(resources.getLimits().get("cpu"), is(new Quantity("1000m")));
        assertThat(resources.getRequests().get("memory"), is(new Quantity("1Gi")));
        assertThat(resources.getLimits().get("memory"), is(new Quantity("1Gi")));
    }

    @Test
    public void testTopicOperatorResourcesWithOverrides() {
        ResourceRequirements customResources = new ResourceRequirementsBuilder()
                .addToRequests("cpu", new Quantity("20m"))
                .addToLimits("memory", new Quantity("2Gi"))
                .build();
        EventStreams instance = createDefaultEventStreams()
            .editSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .editOrNewEntityOperator()
                            .editOrNewTopicOperator()
                                .withResources(customResources)
                            .endTopicOperator()
                        .endEntityOperator()
                        .build())
            .endSpec()
            .build();
        Kafka kafka = new EventStreamsKafkaModel(instance).getKafka();

        ResourceRequirements actualResources = kafka.getSpec().getEntityOperator().getTopicOperator().getResources();
        assertThat(actualResources.getRequests().get("cpu"), is(new Quantity("20m")));
        assertThat(actualResources.getLimits().get("cpu"), is(new Quantity("1000m")));
        assertThat(actualResources.getRequests().get("memory"), is(new Quantity("1Gi")));
        assertThat(actualResources.getLimits().get("memory"), is(new Quantity("2Gi")));
    }

    @Test
    public void testUserOperatorDefaultResources() {
        EntityOperatorSpec entityOperator = createDefaultKafkaModel().getKafka().getSpec().getEntityOperator();

        ResourceRequirements resources = entityOperator.getUserOperator().getResources();
        assertThat(resources.getRequests().get("cpu"), is(new Quantity("10m")));
        assertThat(resources.getLimits().get("cpu"), is(new Quantity("1000m")));
        assertThat(resources.getRequests().get("memory"), is(new Quantity("1Gi")));
        assertThat(resources.getLimits().get("memory"), is(new Quantity("1Gi")));
    }

    @Test
    public void testUserOperatorResourcesWithOverrides() {
        ResourceRequirements resources = new ResourceRequirementsBuilder()
                .addToRequests("cpu", new Quantity("20m"))
                .addToLimits("memory", new Quantity("2Gi"))
                .build();
        EventStreams eventStreamsInstance = createDefaultEventStreams()
                .editSpec()
                    .withStrimziOverrides(new KafkaSpecBuilder()
                            .withNewEntityOperator()
                                .withNewUserOperator()
                                    .withResources(resources)
                                .endUserOperator()
                            .endEntityOperator()
                            .build())
                .endSpec()
                .build();
        Kafka kafka = new EventStreamsKafkaModel(eventStreamsInstance).getKafka();

        ResourceRequirements actualResources = kafka.getSpec().getEntityOperator().getUserOperator().getResources();
        assertThat(actualResources.getRequests().get("cpu"), is(new Quantity("20m")));
        assertThat(actualResources.getLimits().get("cpu"), is(new Quantity("1000m")));
        assertThat(actualResources.getRequests().get("memory"), is(new Quantity("1Gi")));
        assertThat(actualResources.getLimits().get("memory"), is(new Quantity("2Gi")));
    }

    @Test
    public void testEntityOperatorTlsDefaultResources() {
        EntityOperatorSpec entityOperator = createDefaultKafkaModel().getKafka().getSpec().getEntityOperator();

        ResourceRequirements resources = entityOperator.getTlsSidecar().getResources();
        assertThat(resources.getRequests().get("cpu"), is(new Quantity("10m")));
        assertThat(resources.getLimits().get("cpu"), is(new Quantity("100m")));
        assertThat(resources.getRequests().get("memory"), is(new Quantity("128Mi")));
        assertThat(resources.getLimits().get("memory"), is(new Quantity("128Mi")));
    }

    @Test
    public void testEntityOperatorTlsResourcesWithOverrides() {
        ResourceRequirements resources = new ResourceRequirementsBuilder()
                .addToRequests("cpu", new Quantity("20m"))
                .addToLimits("memory", new Quantity("2Gi"))
                .build();
        EventStreams eventStreamsInstance = createDefaultEventStreams()
                .editOrNewSpec()
                    .withStrimziOverrides(new KafkaSpecBuilder()
                            .withNewEntityOperator()
                                .withNewTlsSidecar()
                                    .withResources(resources)
                                .endTlsSidecar()
                            .endEntityOperator()
                            .build())
                .endSpec()
                .build();
        Kafka kafka = new EventStreamsKafkaModel(eventStreamsInstance).getKafka();

        ResourceRequirements actualResources = kafka.getSpec().getEntityOperator().getTlsSidecar().getResources();
        assertThat(actualResources.getRequests().get("cpu"), is(new Quantity("20m")));
        assertThat(actualResources.getLimits().get("cpu"), is(new Quantity("100m")));
        assertThat(actualResources.getRequests().get("memory"), is(new Quantity("128Mi")));
        assertThat(actualResources.getLimits().get("memory"), is(new Quantity("2Gi")));
    }

    @Test
    public void testMetricsNotPresentByDefault() {

        EventStreams eventStreamsInstance = createDefaultEventStreams().build();
        Kafka kafka = new EventStreamsKafkaModel(eventStreamsInstance).getKafka();

        assertThat(kafka.getSpec().getKafka().getMetrics(), is(nullValue()));
    }

    @Test
    public void testMetricsPresentWithOverrides() {

        List<KafkaMetricsJMXRule> rules = new ArrayList<>();
        rules.add(new KafkaMetricsJMXRuleBuilder()
            .withName("metric_to_append_to_default")
            .withPattern(Pattern.compile("thisisapattern"))
            .build());

        Map<String, Object> metricsMap = new HashMap<>();
        metricsMap.put("lowercaseOutputName", true);
        metricsMap.put("rules", rules);

        EventStreams eventStreamsInstance = createDefaultEventStreams()
            .editOrNewSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                            .addToMetrics(metricsMap)
                        .endKafka()
                        .build())
            .endSpec()
            .build();
        
        Kafka kafka = new EventStreamsKafkaModel(eventStreamsInstance).getKafka();
        // add default metrics for assertion
        rules.addAll(EventStreamsKafkaModel.getDefaultKafkaJMXMetricRules());
        metricsMap.put("rules", rules);
        // need to compare toString as they are different objects 
        // but the to string should map the yaml result to the same thing
        assertThat(kafka.getSpec().getKafka().getMetrics().toString(), is(metricsMap.toString()));
    }
}