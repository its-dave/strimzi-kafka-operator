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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.startsWith;

import java.util.HashMap;
import java.util.Map;
import java.util.Base64.Encoder;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import com.ibm.eventstreams.Main;
import com.ibm.eventstreams.api.model.utils.ModelUtils;

import com.ibm.eventstreams.api.Labels;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.ReplicatorSpecBuilder;

import com.ibm.eventstreams.replicator.ReplicatorCredentials;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.Secret;

public class ReplicatorModelTest {

    private final String instanceName = "test";
    private final String componentPrefix = instanceName + "-" + AbstractModel.APP_NAME + "-" + ReplicatorModel.COMPONENT_NAME;
    private final int defaultReplicas = 1;
    private final String  kafkaInstanceName = EventStreamsKafkaModel.getKafkaInstanceName(instanceName);
    private final String namespace = "myproject";
    private final String bootstrap = kafkaInstanceName + "-kafka-bootstrap." + namespace + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.KAFKA_PORT_TLS;
    private final int numberOfConnectorTopics = 3;

    private ReplicatorCredentials replicatorCredentials;


    private KafkaMirrorMaker2 createDefaultReplicator() {

        return  createDefaultReplicatorModel().getReplicator();

    }

    private EventStreamsBuilder createDefaultEventStreams() {

        return ModelUtils.createDefaultEventStreams(instanceName)
            .withMetadata(new ObjectMetaBuilder()
                .withNewName(instanceName)
                .withNewNamespace(namespace)
                .build())
            .editSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                    .withNewKafka()
                        .withReplicas(3)
                        .withNewListeners()
                            .withNewTls()
                                .withNewKafkaListenerAuthenticationTlsAuth()
                                .endKafkaListenerAuthenticationTlsAuth()
                            .endTls()
                        .endListeners()
                        .withNewTemplate()
                            .withNewPod()
                                .withNewMetadata()
                                .endMetadata()
                            .endPod()
                        .endTemplate()
                    .endKafka()
                    .build()
                )
                .withNewReplicator()
                    .withReplicas(defaultReplicas)
                .endReplicator()
            .endSpec();
    }

    private ReplicatorModel createDefaultReplicatorModel() {
        EventStreams instance = createDefaultEventStreams().build();
        replicatorCredentials = new ReplicatorCredentials(instance);
        return new ReplicatorModel(instance, replicatorCredentials);
    }

    @Test
    public void testDefaultReplicatorIsCreated() {
        KafkaMirrorMaker2 replicator = createDefaultReplicator();

        assertThat(replicator.getKind(), is("KafkaMirrorMaker2"));
        assertThat(replicator.getApiVersion(), is("eventstreams.ibm.com/v1alpha1"));

        assertThat(replicator.getMetadata().getName(), startsWith(componentPrefix));

        Map<String, String> labels = replicator.getMetadata().getLabels();
        for (Map.Entry<String, String> label : labels.entrySet()) {
            assertThat(label.getKey(), not(containsString(io.strimzi.operator.common.model.Labels.STRIMZI_DOMAIN)));
        }

        assertThat(replicator.getSpec().getReplicas(), is(defaultReplicas));
        assertThat(replicator.getSpec().getClusters().get(0).getBootstrapServers(), is(bootstrap));
        assertThat(replicator.getSpec().getConfig().get("config.storage.replication.factor"), is(numberOfConnectorTopics));
        assertThat(replicator.getSpec().getConfig().get("offset.storage.replication.factor"), is(numberOfConnectorTopics));
        assertThat(replicator.getSpec().getConfig().get("status.storage.replication.factor"), is(numberOfConnectorTopics));
    }

    @Test
    public void testDefaultReplicatorHasRequiredLabels() {
        KafkaMirrorMaker2 replicator = createDefaultReplicator();

        Map<String, String> replicatorPodLabels = replicator.getSpec()
                .getTemplate()
                .getPod()
                .getMetadata()
                .getLabels();

        assertThat(replicatorPodLabels.get(Labels.APP_LABEL),  is(AbstractModel.APP_NAME));
        assertThat(replicatorPodLabels.get(Labels.SERVICE_SELECTOR_LABEL),  is(ReplicatorModel.COMPONENT_NAME));
        assertThat(replicatorPodLabels.get(Labels.INSTANCE_LABEL),  is(instanceName));
        assertThat(replicatorPodLabels.get(Labels.RELEASE_LABEL),  is(instanceName));
    }


    @Test
    public void testDefaultReplicatorHasRequiredMeteringAnnotations() {
        KafkaMirrorMaker2 replicator = createDefaultReplicator();

        Map<String, String> replicatorPodAnnotations = replicator.getSpec().getTemplate().getPod()
                .getMetadata().getAnnotations();

        assertThat(replicatorPodAnnotations.get("productID"),  is("ID"));
        assertThat(replicatorPodAnnotations.get("cloudpakId"),  is("c8b82d189e7545f0892db9ef2731b90d"));
        assertThat(replicatorPodAnnotations.get("productChargedContainers"),  is("replicator"));
        assertThat(replicatorPodAnnotations.get("prometheus.io/port"),  is("8081"));

    }


    @Test
    public void testReplicatorWithCustomLabels() {
        String customBootstrap = "custom-bootstrap";
        int customReplicas = 2;
        Map<String, Object> mirrorMaker2Config = new HashMap<>();
        mirrorMaker2Config.put("config.storage.replication.factor", 2);
        mirrorMaker2Config.put("offset.storage.replication.factor", 2);
        mirrorMaker2Config.put("status.storage.replication.factor", 2);
        mirrorMaker2Config.put("key.converter2", "org.apache.kafka.connect.converters.ByteArrayConverter2");
        mirrorMaker2Config.put("value.converter2", "org.apache.kafka.connect.converters.ByteArrayConverter2");



        EventStreams instance = createDefaultEventStreams()
                .editSpec()
                .withReplicator(new ReplicatorSpecBuilder()
                        .withBootstrapServers(customBootstrap)
                        .withReplicas(customReplicas)
                        .build())
                .endSpec().build();


        assertThat(instance.getSpec().getReplicator().getBootstrapServers(),  is(customBootstrap));
        assertThat(instance.getSpec().getReplicator().getReplicas(), is(customReplicas));

    }


    @Test
    public void testDefaultReplicatorNetworkPolicy() {

        ReplicatorModel replicator = createDefaultReplicatorModel();
        NetworkPolicy networkPolicy = replicator.getNetworkPolicy();

        String expectedNetworkPolicyName = componentPrefix + "-network-policy";

        String expectedClusterOperatorName = "cluster-operator";

        assertThat(networkPolicy.getMetadata().getName(), is(expectedNetworkPolicyName));
        assertThat(networkPolicy.getKind(), is("NetworkPolicy"));

        assertThat(networkPolicy.getSpec().getEgress().size(), is(0));
        assertThat(networkPolicy.getSpec().getIngress().size(), is(1));

        assertThat(networkPolicy.getSpec().getIngress().get(0).getPorts().size(), is(1));
        assertThat(networkPolicy.getSpec().getIngress().get(0).getPorts().get(0).getPort().getIntVal(), is(ReplicatorModel.REPLICATOR_PORT));
        assertThat(networkPolicy.getSpec().getIngress().get(0).getFrom().size(), is(2));
        assertThat(networkPolicy.getSpec().getIngress().get(0).getFrom().get(0).getPodSelector().getMatchLabels().size(), is(1));

        assertThat(networkPolicy.getSpec().getIngress().get(0).getFrom().get(0).getPodSelector().getMatchLabels().get(Labels.COMPONENT_LABEL), is(AdminApiModel.COMPONENT_NAME));
        assertThat(networkPolicy.getSpec().getIngress().get(0).getFrom().get(1).getPodSelector().getMatchLabels().size(), is(1));

        assertThat(networkPolicy.getSpec().getIngress().get(0).getFrom().get(1).getPodSelector().getMatchLabels().get(io.strimzi.operator.common.model.Labels.STRIMZI_KIND_LABEL), is(expectedClusterOperatorName));
        assertThat(networkPolicy.getSpec().getIngress().get(0).getFrom().get(1).getNamespaceSelector().getMatchExpressions().size(), is(0));

    }

    @Test
    public void testDefaultReplicatorSecret() {

        ReplicatorModel replicator = createDefaultReplicatorModel();
        Secret replicatorSecret = replicator.getSecret();

        assertThat(replicatorSecret.getMetadata().getName(), is(instanceName + "-" + AbstractModel.APP_NAME + "-"  + ReplicatorModel.REPLICATOR_SECRET_NAME));
        assertThat(replicatorSecret.getKind(), is("Secret"));
        assertThat(replicatorSecret.getMetadata().getNamespace(), is(namespace));

        Encoder encoder = Base64.getEncoder();
        assertThat(replicatorSecret.getData().get(ReplicatorModel.REPLICATOR_SECRET_KEY_NAME), is(encoder.encodeToString("[]".getBytes(StandardCharsets.UTF_8))));

        Map<String, String> replicatorSecretLabels = replicator.getSecret().getMetadata().getLabels();

        assertThat(replicatorSecretLabels.get(Labels.APP_LABEL),  is(AbstractModel.APP_NAME));
        assertThat(replicatorSecretLabels.get(Labels.INSTANCE_LABEL),  is(instanceName));
        assertThat(replicatorSecretLabels.get(Labels.RELEASE_LABEL),  is(instanceName));

    }

}
