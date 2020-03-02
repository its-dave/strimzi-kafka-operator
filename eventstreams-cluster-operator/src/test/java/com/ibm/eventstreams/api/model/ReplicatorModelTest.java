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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Base64.Encoder;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import io.strimzi.api.kafka.model.KafkaMirrorMaker2ClusterSpecBuilder;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2Spec;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2SpecBuilder;
import io.strimzi.operator.cluster.model.Ca;


import com.ibm.eventstreams.Main;
import com.ibm.eventstreams.api.model.utils.ModelUtils;

import com.ibm.eventstreams.api.Labels;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.ReplicatorSpec;
import com.ibm.eventstreams.api.spec.ReplicatorSpecBuilder;

import com.ibm.eventstreams.replicator.ReplicatorCredentials;
import io.fabric8.kubernetes.api.model.SecretBuilder;

import io.strimzi.api.kafka.model.KafkaMirrorMaker2;

import io.strimzi.api.kafka.model.KafkaMirrorMaker2ClusterSpec;

import io.strimzi.api.kafka.model.KafkaMirrorMaker2Tls;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2TlsBuilder;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;

import io.strimzi.api.kafka.model.authentication.KafkaClientAuthentication;
import io.strimzi.api.kafka.model.authentication.KafkaClientAuthenticationTlsBuilder;




import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.Secret;

public class ReplicatorModelTest {

    private final String instanceName = "test";
    private final String componentPrefix = instanceName + "-" + AbstractModel.APP_NAME + "-" + ReplicatorModel.COMPONENT_NAME;
    private final int defaultReplicas = 1;
    private final int nonDefaultReplicas = 2;
    private final int defaultKafkaReplicas = 3;
    private final int nonDefaultKafkaReplicas = 1;
    private final String nonDefaultConnectClusterName = "nonDefaultConnectClusterName";
    private final String nonDefaultClusterAlias = "nonDefaultClusterAlias";
    private final String kafkaInstanceName = EventStreamsKafkaModel.getKafkaInstanceName(instanceName);
    private final String namespace = "myproject";
    private final String bootstrap = kafkaInstanceName + "-kafka-bootstrap." + namespace + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.KAFKA_PORT_TLS;
    private final String nonDefaultBootstrap = "nonDefaultBootstrap";

    private ReplicatorCredentials replicatorCredentials;

    @Test
    public void testDefaultReplicatorIsCreated() {
        KafkaMirrorMaker2 replicator = createDefaultReplicator();

        assertThat(replicator.getKind(), is("KafkaMirrorMaker2"));
        assertThat(replicator.getApiVersion(), is("eventstreams.ibm.com/v1alpha1"));

        assertThat(replicator.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(replicator.getMetadata().getNamespace(), is(namespace));

        Map<String, String> labels = replicator.getMetadata().getLabels();
        for (Map.Entry<String, String> label : labels.entrySet()) {
            assertThat(label.getKey(), not(containsString(io.strimzi.operator.common.model.Labels.STRIMZI_DOMAIN)));
        }


        assertThat(replicator.getSpec().getReplicas(), is(defaultReplicas));
        assertThat(replicator.getSpec().getConnectCluster(), is(ReplicatorModel.getDefaultReplicatorClusterName(instanceName)));

        KafkaMirrorMaker2ClusterSpec clusterSpec = replicator.getSpec().getClusters().get(0);

        assertThat(clusterSpec.getAlias(), is(ReplicatorModel.getDefaultReplicatorClusterName(instanceName)));
        assertThat(clusterSpec.getBootstrapServers(), is(bootstrap));
        assertThat(clusterSpec.getAuthentication().getType(), is("tls"));
        assertThat(clusterSpec.getTls().getTrustedCertificates().size(), is(1));
        assertThat(clusterSpec.getConfig().get("config.storage.replication.factor"), is(defaultKafkaReplicas));
        assertThat(clusterSpec.getConfig().get("offset.storage.replication.factor"), is(defaultKafkaReplicas));
        assertThat(clusterSpec.getConfig().get("status.storage.replication.factor"), is(defaultKafkaReplicas));
        assertThat(clusterSpec.getConfig().get("config.storage.topic"), is(ReplicatorModel.CONFIG_STORAGE_TOPIC_NAME));
        assertThat(clusterSpec.getConfig().get("offset.storage.topic"), is(ReplicatorModel.OFFSET_STORAGE_TOPIC_NAME));
        assertThat(clusterSpec.getConfig().get("status.storage.topic"), is(ReplicatorModel.STATUS_STORAGE_TOPIC_NAME));
        assertThat(clusterSpec.getConfig().get("key.converter"), is(ReplicatorModel.BYTE_ARRAY_CONVERTER_NAME));
        assertThat(clusterSpec.getConfig().get("value.converter"), is(ReplicatorModel.BYTE_ARRAY_CONVERTER_NAME));
        assertThat(clusterSpec.getConfig().get("group.id"), is(ReplicatorModel.getDefaultReplicatorClusterName(instanceName)));

    }

    @Test
    public void testNonDefaultReplicatorIsCreated() {
        KafkaMirrorMaker2 replicator = createNonDefaultReplicator();

        assertThat(replicator.getKind(), is("KafkaMirrorMaker2"));
        assertThat(replicator.getApiVersion(), is("eventstreams.ibm.com/v1alpha1"));

        assertThat(replicator.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(replicator.getMetadata().getNamespace(), is(namespace));

        Map<String, String> labels = replicator.getMetadata().getLabels();
        for (Map.Entry<String, String> label : labels.entrySet()) {
            assertThat(label.getKey(), not(containsString(io.strimzi.operator.common.model.Labels.STRIMZI_DOMAIN)));
        }

        assertThat(replicator.getSpec().getReplicas(), is(nonDefaultReplicas));

        KafkaMirrorMaker2ClusterSpec clusterSpec = replicator.getSpec().getClusters().get(0);

        assertThat(clusterSpec.getConfig().get("config.storage.replication.factor"), is(nonDefaultKafkaReplicas));
        assertThat(clusterSpec.getConfig().get("offset.storage.replication.factor"), is(nonDefaultKafkaReplicas));
        assertThat(clusterSpec.getConfig().get("status.storage.replication.factor"), is(nonDefaultKafkaReplicas));

        assertThat(clusterSpec.getConfig().get("config.storage.topic"), is(ReplicatorModel.CONFIG_STORAGE_TOPIC_NAME));
        assertThat(clusterSpec.getConfig().get("offset.storage.topic"), is(ReplicatorModel.OFFSET_STORAGE_TOPIC_NAME));
        assertThat(clusterSpec.getConfig().get("status.storage.topic"), is(ReplicatorModel.STATUS_STORAGE_TOPIC_NAME));
        assertThat(clusterSpec.getConfig().get("key.converter"), is(ReplicatorModel.BYTE_ARRAY_CONVERTER_NAME));
        assertThat(clusterSpec.getConfig().get("value.converter"), is(ReplicatorModel.BYTE_ARRAY_CONVERTER_NAME));

        assertThat(replicator.getSpec().getConnectCluster(), is(ReplicatorModel.getDefaultReplicatorClusterName(nonDefaultConnectClusterName)));

        assertThat(clusterSpec.getAlias(), is(ReplicatorModel.getDefaultReplicatorClusterName(nonDefaultConnectClusterName)));
        assertThat(clusterSpec.getBootstrapServers(), is(nonDefaultBootstrap));
        assertThat(clusterSpec.getAuthentication().getType(), is("tls"));
        assertThat(clusterSpec.getTls().getTrustedCertificates().size(), is(1));

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
                        .withNewListenersLike(ModelUtils.getMutualTLSOnBothInternalAndExternalListenerSpec())
                        .endListeners()
                        .endKafka()
                        .build())
                .withNewReplicator()
                .withReplicas(defaultReplicas)
                .endReplicator()
                .endSpec();
    }


    private EventStreamsBuilder createNonDefaultEventStreams() {

        KafkaClientAuthentication replicatorConnectClientAuth = new KafkaClientAuthenticationTlsBuilder()
                .build();

        KafkaMirrorMaker2Tls serverCert = new KafkaMirrorMaker2TlsBuilder().build();

        List<KafkaMirrorMaker2ClusterSpec> clusterSpecs = new ArrayList<>();
        KafkaMirrorMaker2ClusterSpec mm2ClusterSpec = new KafkaMirrorMaker2ClusterSpecBuilder()
                .withAlias(nonDefaultClusterAlias)
                .withAuthentication(replicatorConnectClientAuth)
                .withTls(serverCert)
                .withBootstrapServers(nonDefaultBootstrap)
                .build();
        clusterSpecs.add(mm2ClusterSpec);

        KafkaMirrorMaker2Spec mm2Spec = new KafkaMirrorMaker2SpecBuilder()
                .withClusters(clusterSpecs)
                .withNewConnectCluster(ReplicatorModel.getDefaultReplicatorClusterName(nonDefaultConnectClusterName))
                .build();

        ReplicatorSpec replicatorSpec = new ReplicatorSpecBuilder()
                .withReplicas(nonDefaultReplicas)
                .withMirrorMaker2Spec(mm2Spec)
                .build();

        return ModelUtils.createDefaultEventStreams(instanceName)
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(instanceName)
                        .withNewNamespace(namespace)
                        .build())
                .editSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                        .withReplicas(nonDefaultKafkaReplicas)
                        .withListeners(ModelUtils.getMutualTLSOnBothInternalAndExternalListenerSpec())
                        .endKafka()
                        .build())
                .withNewReplicatorLike(replicatorSpec)
                    .withReplicas(nonDefaultReplicas)
                .endReplicator()
                .endSpec();
    }

    private KafkaMirrorMaker2 createDefaultReplicator() {
        return createDefaultReplicatorModel().getReplicator();
    }

    private KafkaMirrorMaker2 createNonDefaultReplicator() {
        return createReplicatorModel(false).getReplicator();
    }

    private ReplicatorModel createDefaultReplicatorModel() {
        return createReplicatorModel(true);
    }

    private ReplicatorModel createReplicatorModel(boolean defaults) {
        EventStreams instance = defaults ? createDefaultEventStreams().build() : createNonDefaultEventStreams().build();

        replicatorCredentials = new ReplicatorCredentials(instance);

        Secret replicatorConnectSecret = new SecretBuilder()
                .withNewMetadata()
                .withName(instanceName + "-ibm-es-" + ReplicatorModel.REPLICATOR_CONNECT_USER_NAME)
                .withNamespace(namespace)
                .addToAnnotations(Ca.ANNO_STRIMZI_IO_CA_KEY_GENERATION, "0")
                .endMetadata()
                .addToData("user.key", ModelUtils.Keys.CLUSTER_CA_KEY.toString())
                .addToData("user.crt", ModelUtils.Certificates.CLUSTER_CA.toString())
                .addToData("user.password", "password")
                .build();

        replicatorCredentials.setReplicatorClientAuth(replicatorConnectSecret);
        replicatorCredentials.setReplicatorTrustStore(replicatorConnectSecret);
        return new ReplicatorModel(instance, replicatorCredentials);
    }


}
