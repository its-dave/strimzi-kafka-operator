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

import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsReplicator;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.ReplicatorSpec;
import com.ibm.eventstreams.replicator.ReplicatorCredentials;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyEgressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRuleBuilder;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2Builder;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2ClusterSpec;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2ClusterSpecBuilder;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2Spec;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2Tls;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.authentication.KafkaClientAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternal;
import io.strimzi.api.kafka.model.listener.KafkaListenerTls;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.strimzi.api.kafka.model.template.KafkaConnectTemplate;
import io.strimzi.api.kafka.model.template.KafkaConnectTemplateBuilder;
import io.strimzi.operator.cluster.model.KafkaCluster;
import io.strimzi.operator.cluster.model.ModelUtils;
import io.strimzi.operator.common.model.Labels;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ReplicatorModel extends AbstractModel {


    public static final String COMPONENT_NAME = "georep";
    public static final String APPLICATION_NAME = "kafka-mirror-maker-2";
    public static final int REPLICATOR_PORT = 8083;
    public static final String REPLICATOR_CLUSTER_NAME = "georep-cluster";
    protected static final String CONFIG_STORAGE_TOPIC_NAME = "__eventstreams_georeplicator_configs";
    protected static final String OFFSET_STORAGE_TOPIC_NAME = "__eventstreams_georeplicator_offsets";
    protected static final String STATUS_STORAGE_TOPIC_NAME = "__eventstreams_georeplicator_status";
    public static final String BYTE_ARRAY_CONVERTER_NAME = "org.apache.kafka.connect.converters.ByteArrayConverter";
    public static final String REPLICATOR_SECRET_MOUNT_PATH = "/etc/georeplication";
    private static final int DEFAULT_REPLICAS = 0;

    private NetworkPolicy networkPolicy;
    private KafkaMirrorMaker2 kafkaMirrorMaker2;

    private static final Logger log = LogManager.getLogger(ReplicatorModel.class.getName());

    /**
     * This class is used to model a KafkaMirrorMaker2 custom resource used by the strimzi cluster operator,
     * it is also used to create the kube resources required to correctly deploy the geo-replicator
     * @param replicatorInstance
     * @param instance
     * @param replicatorCredentials
     */
    public ReplicatorModel(EventStreamsReplicator replicatorInstance, EventStreams instance, ReplicatorCredentials replicatorCredentials, KafkaMirrorMaker2 mirrorMaker2) {
        //always set the namespace to be that of the owning EventStreams instance
        super(instance, COMPONENT_NAME, APPLICATION_NAME);

        setOwnerReference(replicatorInstance);
        
        if (isReplicatorEnabled(replicatorInstance)) {
            kafkaMirrorMaker2 = createMirrorMaker2(replicatorInstance, instance, replicatorCredentials, mirrorMaker2);
            networkPolicy = createNetworkPolicy();
        } else {
            kafkaMirrorMaker2 = null;
            networkPolicy = null;
        }

    }

    private KafkaMirrorMaker2 createMirrorMaker2(EventStreamsReplicator replicatorInstance, EventStreams instance,  ReplicatorCredentials replicatorCredentials, KafkaMirrorMaker2 mirrorMaker2) {

        Optional<ReplicatorSpec> eventStreamsreplicatorSpec = Optional.ofNullable(replicatorInstance.getSpec());
        KafkaMirrorMaker2Spec mm2Spec = mirrorMaker2 == null || mirrorMaker2.getSpec() == null ? new KafkaMirrorMaker2Spec() : mirrorMaker2.getSpec();

        int replicas = eventStreamsreplicatorSpec.map(ReplicatorSpec::getReplicas).orElse(DEFAULT_REPLICAS);
        mm2Spec.setReplicas(replicas);

        KafkaMirrorMaker2Tls caCert = replicatorCredentials.getReplicatorConnectTrustStore();
        KafkaClientAuthentication clientAuthentication = replicatorCredentials.getReplicatorConnectClientAuth();
        KafkaListenerTls internalTlsKafkaListener = getInternalTlsKafkaListener(instance);

        String connectClusterName = ReplicatorModel.getDefaultReplicatorClusterName(getInstanceName());
        mm2Spec.setConnectCluster(connectClusterName);

        String bootstrap = getDefaultBootstrap(internalTlsKafkaListener);
        int kafkaReplicas = instance.getSpec().getStrimziOverrides().getKafka().getReplicas();

        // Maximum number of replicas for ConnectConfig topics is 3
        int connectConfigTopicReplicas = (kafkaReplicas >= 3) ? 3 : kafkaReplicas;


        Map<String, Object> kafkaMirrorMaker2Config = new HashMap<>();
        //don't overwrite any existing config that's been set for the connectCluster
        if (mm2Spec.getClusters() != null) {
            final Optional<KafkaMirrorMaker2ClusterSpec> existingClusterSpec = Optional.ofNullable(mm2Spec)
                    .map(KafkaMirrorMaker2Spec::getClusters)
                    .filter(list -> !list.isEmpty())
                    .flatMap(populatedList -> populatedList
                            .stream()
                            .filter(item -> connectClusterName.equals(item.getAlias()))
                            .findFirst());

            if (existingClusterSpec.isPresent()) {
                kafkaMirrorMaker2Config = existingClusterSpec.get().getConfig();
            } else {
                addConfigToProperties(kafkaMirrorMaker2Config, connectConfigTopicReplicas);
            }
        } else {
            addConfigToProperties(kafkaMirrorMaker2Config, connectConfigTopicReplicas);
        }

        Labels labels = labelsWithoutResourceGroup();

        // Needs to be KafkaConnectTemplate here, not MM2 equivalent
        KafkaConnectTemplate kafkaMirrorMaker2Template = new KafkaConnectTemplateBuilder()
                .editOrNewConnectContainer()
                    .withSecurityContext(getSecurityContext(false))
                .endConnectContainer()
                .editOrNewPod()
                    .withNewMetadata()
                        .addToAnnotations(getEventStreamsMeteringAnnotations(COMPONENT_NAME))
                        .addToAnnotations(getPrometheusAnnotations(DEFAULT_PROMETHEUS_PORT))
                        .addToLabels(labels.toMap())
                    .endMetadata()
                .endPod()
                .build();
        mm2Spec.setTemplate(kafkaMirrorMaker2Template);

        //if no security set then caCert and clientAuthentication are null and just not set on the connect object
        //if we have an oauth config then the bootstrap will be the TLS port, but the clientAuthentication will be null so the connection will fail (and we log earlier that oauth is unsupported)
        KafkaMirrorMaker2ClusterSpec newKafkaMirrorMaker2ClusterSpec = new KafkaMirrorMaker2ClusterSpecBuilder()
                .withBootstrapServers(bootstrap)
                .withTls(caCert)
                .withAuthentication(clientAuthentication)
                .withAlias(connectClusterName)
                .withConfig(kafkaMirrorMaker2Config)
                .build();

        if (mm2Spec.getClusters() != null) {

            Optional.ofNullable(mm2Spec)
                    .map(KafkaMirrorMaker2Spec::getClusters)
                    .filter(list -> !list.isEmpty())
                    .map(populatedList -> populatedList.removeIf(item -> connectClusterName.equals(item.getAlias())));

            mm2Spec.getClusters().add(newKafkaMirrorMaker2ClusterSpec);

        } else {

            mm2Spec.setClusters(Collections.singletonList(newKafkaMirrorMaker2ClusterSpec));
        }

        return new KafkaMirrorMaker2Builder()
                .withApiVersion(KafkaMirrorMaker2.RESOURCE_GROUP + "/" + KafkaMirrorMaker2.V1ALPHA1)
                .editOrNewMetadata()
                    .withNamespace(getNamespace())
                    .withName(getReplicatorName())
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .addToLabels(labels.toMap())
                .endMetadata()
                .withNewSpecLike(mm2Spec)
                .endSpec()
                .build();
    }

    private void addConfigToProperties(Map<String, Object> kafkaMirrorMaker2Config, int connectConfigTopicReplicas) {

        kafkaMirrorMaker2Config.put("config.storage.replication.factor", connectConfigTopicReplicas);
        kafkaMirrorMaker2Config.put("offset.storage.replication.factor", connectConfigTopicReplicas);
        kafkaMirrorMaker2Config.put("status.storage.replication.factor", connectConfigTopicReplicas);
        kafkaMirrorMaker2Config.put("config.storage.topic", CONFIG_STORAGE_TOPIC_NAME);
        kafkaMirrorMaker2Config.put("offset.storage.topic", OFFSET_STORAGE_TOPIC_NAME);
        kafkaMirrorMaker2Config.put("status.storage.topic", STATUS_STORAGE_TOPIC_NAME);
        kafkaMirrorMaker2Config.put("key.converter", BYTE_ARRAY_CONVERTER_NAME);
        kafkaMirrorMaker2Config.put("value.converter", BYTE_ARRAY_CONVERTER_NAME);
        kafkaMirrorMaker2Config.put("group.id", getDefaultReplicatorClusterName(getInstanceName()));

    }

    private String getDefaultBootstrap(KafkaListenerTls internalTlsKafkaListener) {
        // Check is done on instance.getSpec()... as caCert might be null due to an error, or because oauth is on
        // We use the internal port for connect, so we don't query here on listeners.external
        String bootstrap = ModelUtils.serviceDnsName(getNamespace(), KafkaCluster.serviceName(getInstanceName())) + ":";

        if (internalTlsKafkaListener != null) {
            bootstrap += EventStreamsKafkaModel.KAFKA_PORT_TLS;
        } else {
            //security off
            bootstrap += EventStreamsKafkaModel.KAFKA_PORT;
        }

        return bootstrap;
    }

    public static boolean isReplicatorEnabled(EventStreamsReplicator replicatorInstance) {
        return Optional.ofNullable(replicatorInstance)
                .map(replicator -> replicator.getSpec().getReplicas())
                .orElse(DEFAULT_REPLICAS) > 0;
    }

    public static boolean isValidInstanceForGeoReplication(EventStreams instance) {
        return isSupportedAuthType(getExternalKafkaListenerAuthentication(instance));
    }

    public String getReplicatorName() {
        return getInstanceName();
    }

    private NetworkPolicy createNetworkPolicy() {
        List<NetworkPolicyIngressRule> ingressRules = new ArrayList<>(1);
        ingressRules.add(createCustomReplicatorConnectIngressRule());
        List<NetworkPolicyEgressRule> egressRules = new ArrayList<>(0);
        return super.createNetworkPolicy(createLabelSelector(APPLICATION_NAME), ingressRules, egressRules);
    }


    private NetworkPolicyIngressRule createCustomReplicatorConnectIngressRule() {

        NetworkPolicyIngressRuleBuilder policyBuilder = new NetworkPolicyIngressRuleBuilder()
            .addNewPort().withNewPort(REPLICATOR_PORT).endPort();

        policyBuilder
            .addNewFrom()
                .withNewPodSelector()
                    .addToMatchLabels(Labels.EMPTY.withKubernetesName(AdminApiModel.APPLICATION_NAME).toMap())
                .endPodSelector()
            .endFrom()
            // Strimzi operator needs to Connect
            .addNewFrom()
                .withNewPodSelector()
                    .addToMatchLabels(Labels.STRIMZI_KIND_LABEL, AbstractModel.OPERATOR_NAME)
                .endPodSelector()
                .withNewNamespaceSelector()
                .endNamespaceSelector()
            .endFrom();

        return policyBuilder.build();

    }

    public static KafkaListenerTls getInternalTlsKafkaListener(EventStreams instance) {
        return Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getListeners)
                .map(KafkaListeners::getTls)
                .orElse(null);
    }

    public static KafkaListenerAuthentication getInternalTlsKafkaListenerAuthentication(EventStreams instance) {
        return Optional.ofNullable(getInternalTlsKafkaListener(instance))
                .map(KafkaListenerTls::getAuth)
                .orElse(null);
    }

    public static KafkaListenerExternal getExternalKafkaListener(EventStreams instance) {
        return Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getListeners)
                .map(KafkaListeners::getExternal)
                .orElse(null);
    }

    public static KafkaListenerAuthentication getExternalKafkaListenerAuthentication(EventStreams instance) {
        return Optional.ofNullable(getExternalKafkaListener(instance))
                .map(KafkaListenerExternal::getAuth)
                .orElse(null);
    }

    public static String getDefaultReplicatorClusterName(String instanceName) {
        return instanceName + "-" + ReplicatorModel.REPLICATOR_CLUSTER_NAME;
    }

    /**
     * @return KafkaMirrorMaker2 the current implementation of the geo-replicator
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

}
