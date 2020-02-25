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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Base64.Encoder;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import com.ibm.eventstreams.Main;
import com.ibm.eventstreams.api.Labels;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.ReplicatorSpec;

import com.ibm.eventstreams.replicator.ReplicatorCredentials;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRuleBuilder;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2ClusterSpec;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2ClusterSpecBuilder;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2Tls;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.authentication.KafkaClientAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerTls;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.strimzi.api.kafka.model.template.KafkaConnectTemplate;
import io.strimzi.api.kafka.model.template.KafkaConnectTemplateBuilder;
import io.strimzi.api.kafka.model.template.MetadataTemplateBuilder;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2Builder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyEgressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;


public class ReplicatorModel extends AbstractModel {

    public static final String COMPONENT_NAME = "replicator";
    public static final int REPLICATOR_PORT = 8083;

    public static final String REPLICATOR_SECRET_NAME = "replicator-secret";
    public static final String REPLICATOR_CLUSTER_NAME = "replicator-cluster";

    public static final String REPLICATOR_SECRET_KEY_NAME = "georeplicationdestinationclusters";

    protected static final String CONFIG_STORAGE_TOPIC_NAME = "__eventstreams_georeplicator_configs";
    protected static final String OFFSET_STORAGE_TOPIC_NAME = "__eventstreams_georeplicator_offsets";
    protected static final String STATUS_STORAGE_TOPIC_NAME = "__eventstreams_georeplicator_status";
    public static final String REPLICATOR_CONNECT_USER_NAME = "rep-connect-user";
    public static final String REPLICATOR_DESTINATION_CLUSTER_CONNNECTOR_USER_NAME = "rep-target-user";
    public static final String REPLICATOR_SOURCE_CLUSTER_CONNECTOR_USER_NAME = "rep-source-user";

    private final NetworkPolicy networkPolicy;
    private final Secret secret;
    private KafkaMirrorMaker2 kafkaMirrorMaker2;

    private KafkaClientAuthentication clientAuthentication;
    private KafkaMirrorMaker2Tls caCert;

    private static final Logger log = LogManager.getLogger(ReplicatorModel.class.getName());

    public ReplicatorModel(EventStreams instance) {
        super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), COMPONENT_NAME);

        String namespace = getNamespace();
        setOwnerReference(instance);
        setArchitecture(instance.getSpec().getArchitecture());

        Encoder encoder = Base64.getEncoder();
        Map<String, String> data = Collections.singletonMap(REPLICATOR_SECRET_KEY_NAME, encoder.encodeToString("[]".getBytes(StandardCharsets.UTF_8)));

        this.secret = createSecret(namespace, getDefaultResourceName(getInstanceName(),  REPLICATOR_SECRET_NAME), data, getComponentLabels(),
                getEventStreamsMeteringAnnotations(COMPONENT_NAME));

        this.networkPolicy = createNetworkPolicy();

    }

    public ReplicatorModel(EventStreams instance, ReplicatorCredentials replicatorCredentials) {
        super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), COMPONENT_NAME);

        Optional<ReplicatorSpec> replicatorSpec = Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getReplicator);

        setOwnerReference(instance);
        setArchitecture(instance.getSpec().getArchitecture());

        this.caCert = replicatorCredentials.getReplicatorConnectTrustStore();
        this.clientAuthentication = replicatorCredentials.getReplicatorConnectClientAuth();

        String connectCluster = Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getReplicator)
                .map(ReplicatorSpec::getConnectCluster).orElse(ReplicatorModel.getReplicatorClusterName(getInstanceName()));

        Optional<KafkaListenerTls> internalSecurityEnabled = Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getListeners)
                .map(KafkaListeners::getTls);

        String bootstrap;


        // TODO This was edited to cope with changes in KafkaMirrorMaker2 - needs reviewing
        //We use the internal port for connect, so we don't query here on listeners.external
        if (internalSecurityEnabled.isPresent()) {
            bootstrap = getInstanceName() + "-kafka-bootstrap." + getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.KAFKA_PORT_TLS;
        } else {
            //security off
            bootstrap = getInstanceName() + "-kafka-bootstrap." + getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.KAFKA_PORT;
        }

        int numberOfConnectConfigTopicReplicas = (instance.getSpec().getStrimziOverrides().getKafka().getReplicas() >= 3)
                ? 3 : instance.getSpec().getStrimziOverrides().getKafka().getReplicas();

        Map<String, Object> kafkaMirrorMaker2Config = new HashMap<>();
        kafkaMirrorMaker2Config.put("config.storage.replication.factor", numberOfConnectConfigTopicReplicas);
        kafkaMirrorMaker2Config.put("offset.storage.replication.factor", numberOfConnectConfigTopicReplicas);
        kafkaMirrorMaker2Config.put("status.storage.replication.factor", numberOfConnectConfigTopicReplicas);
        kafkaMirrorMaker2Config.put("config.storage.topic", CONFIG_STORAGE_TOPIC_NAME);
        kafkaMirrorMaker2Config.put("offset.storage.topic", OFFSET_STORAGE_TOPIC_NAME);
        kafkaMirrorMaker2Config.put("status.storage.topic", STATUS_STORAGE_TOPIC_NAME);
        kafkaMirrorMaker2Config.put("key.converter", "org.apache.kafka.connect.converters.ByteArrayConverter");
        kafkaMirrorMaker2Config.put("value.converter", "org.apache.kafka.connect.converters.ByteArrayConverter");
        kafkaMirrorMaker2Config.put("group.id", getReplicatorClusterName(getInstanceName()));

        Map<String, String> labels = getComponentLabelsWithoutResourceGroup();

        //needs to be KafkaConnectTemplate here, not MM2 equivalent
        KafkaConnectTemplate kafkaMirrorMaker2Template = new KafkaConnectTemplateBuilder()
            .editOrNewPod()
                .withMetadata(new MetadataTemplateBuilder()
                    .addToAnnotations(getEventStreamsMeteringAnnotations(COMPONENT_NAME))
                    .addToAnnotations(getPrometheusAnnotations(DEFAULT_PROMETHEUS_PORT))
                    .addToLabels(getServiceSelectorLabel(COMPONENT_NAME))
                    .addToLabels(labels)
                    .build())
            .endPod()
            .build();

        //if no security set then caCert and clientAuthentication are null and just not set on the connect object
        //if we have an oauth config then the bootstrap will be the TLS port, but the clientAuthentication will be null so the connection will fail (and we log earlier that oauth is unsupported)
        KafkaMirrorMaker2ClusterSpec kafkaMirrorMaker2ClusterSpec = new KafkaMirrorMaker2ClusterSpecBuilder()
                .withBootstrapServers(bootstrap)
                .withTls(caCert)
                .withAuthentication(clientAuthentication)
                .withAlias(connectCluster)
                .withConfig(kafkaMirrorMaker2Config)
                .build();

        kafkaMirrorMaker2 = new KafkaMirrorMaker2Builder()
            .withApiVersion(KafkaMirrorMaker2.RESOURCE_GROUP + "/" + KafkaMirrorMaker2.V1ALPHA1)
            .editOrNewMetadata()
                .withNamespace(getNamespace())
                .withName(getDefaultResourceName(getInstanceName(), COMPONENT_NAME))
                .withOwnerReferences(getEventStreamsOwnerReference())
                .addToLabels(labels)
            .endMetadata()
            .withNewSpecLike(replicatorSpec.get())
                .withTemplate(kafkaMirrorMaker2Template)
                .withConnectCluster(connectCluster)
                .withClusters(kafkaMirrorMaker2ClusterSpec)
            .endSpec()
            .build();

        this.networkPolicy = createNetworkPolicy();

        Encoder encoder = Base64.getEncoder();
        Map<String, String> data = Collections.singletonMap(REPLICATOR_SECRET_KEY_NAME, encoder.encodeToString("[]".getBytes(StandardCharsets.UTF_8)));

        this.secret = createSecret(getDefaultResourceName(getInstanceName(),  REPLICATOR_SECRET_NAME), data);
    }

    private NetworkPolicy createNetworkPolicy() {
        List<NetworkPolicyIngressRule> ingressRules = new ArrayList<>(1);

        //TODO need to add in promethus port when we enable metrics issue 4493
        // ingressRules.add(new NetworkPolicyIngressRuleBuilder()
        //     .addNewPort().withNewPort(Integer.parseInt(ReplicatorModel.DEFAULT_PROMETHEUS_PORT)).endPort()
        //     .build());

        ingressRules.add(createCustomReplicatorConnectIngressRule());

        List<NetworkPolicyEgressRule> egressRules = new ArrayList<>(0);

        return super.createNetworkPolicy(createLabelSelector(COMPONENT_NAME), ingressRules, egressRules);
    }


    private NetworkPolicyIngressRule createCustomReplicatorConnectIngressRule() {

        NetworkPolicyIngressRuleBuilder policyBuilder = new NetworkPolicyIngressRuleBuilder()
            .addNewPort().withNewPort(REPLICATOR_PORT).endPort();

        policyBuilder
            .addNewFrom()
                .withNewPodSelector()
                    .addToMatchLabels(Labels.COMPONENT_LABEL, AdminApiModel.COMPONENT_NAME)
                .endPodSelector()
            .endFrom()
            .addNewFrom()
                .withNewPodSelector()
                    .addToMatchLabels(io.strimzi.operator.common.model.Labels.STRIMZI_KIND_LABEL, "cluster-operator")
                .endPodSelector()
                .withNewNamespaceSelector()
                .endNamespaceSelector()
            .endFrom();

        return policyBuilder.build();

    }

    public static String getReplicatorClusterName(String instanceName) {
        return instanceName + "-" + ReplicatorModel.REPLICATOR_CLUSTER_NAME;
    }


    /**
     * @return KafkaMirrorMaker2 the current implementation of the replicator
     */
    public KafkaMirrorMaker2 getReplicator() {
        return this.kafkaMirrorMaker2;
    }

    /**
     * @return NetworkPolicy return the network policy
     */
    public NetworkPolicy getNetworkPolicy() {
        return this.networkPolicy;
    }

    /**
     * @return Secret return the replicators secret
     */
    public Secret getSecret() {
        return this.secret;
    }


}
