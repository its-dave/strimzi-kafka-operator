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
import com.ibm.eventstreams.api.spec.ReplicatorSpec;

import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRuleBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyEgressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.strimzi.api.kafka.model.KafkaConnect;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserBuilder;
import io.strimzi.api.kafka.model.AclOperation;
import io.strimzi.api.kafka.model.AclRuleBuilder;
import io.strimzi.api.kafka.model.AclRule;
import io.strimzi.api.kafka.model.AclResourcePatternType;
import io.strimzi.api.kafka.model.KafkaConnectBuilder;
import io.strimzi.api.kafka.model.template.KafkaConnectTemplate;
import io.strimzi.api.kafka.model.template.KafkaConnectTemplateBuilder;
import io.strimzi.api.kafka.model.template.MetadataTemplateBuilder;

public class ReplicatorModel extends AbstractModel {

    public static final String COMPONENT_NAME = "replicator";
    public static final int REPLICATOR_PORT = 8083;

    public static final String REPLICATOR_SECRET_NAME = "replicator-secret";

    public static final String REPLICATOR_SECRET_KEY_NAME = "georeplicationdestinationclusters";

    protected static final String CONFIG_STORAGE_TOPIC_NAME = "__eventstreams_georeplicator_configs";
    protected static final String OFFSET_STORAGE_TOPIC_NAME = "__eventstreams_georeplicator_offsets";
    protected static final String STATUS_STORAGE_TOPIC_NAME = "__eventstreams_georeplicator_status";
    protected static final String REPLICATOR_CONNECT_USER_NAME = "replicator-connect-user";
    protected static final String REPLICATOR_DESTINATION_CLUSTER_CONNNECTOR_USER_NAME = "replicator-destination-cluster-user";
    protected static final String REPLICATOR_SOURCE_CLUSTER_CONNECTOR_USER_NAME = "replicator-source-cluster-user";

    private final NetworkPolicy networkPolicy;
    private final Secret secret;
    private final KafkaConnect kafkaConnect;

    private KafkaUser replicatorConnectUser;
    private KafkaUser replicatorDestinationConnectorUser;
    private KafkaUser replicatorSourceConnectorUser;

    private static final Logger log = LogManager.getLogger(ReplicatorModel.class.getName());


    public ReplicatorModel(EventStreams instance) {
        super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), COMPONENT_NAME);

        String namespace = instance.getMetadata().getNamespace();

        ReplicatorSpec replicatorSpec = instance.getSpec().getReplicator();
      
        String kafkaInstanceName = EventStreamsKafkaModel.getKafkaInstanceName(getInstanceName());

        setOwnerReference(instance);
        setArchitecture(instance.getSpec().getArchitecture());

        createReplicatorConnectUser();
        createReplicatorDestinationConnectorUser();
        createReplicatorSourceConnectorUser();

        String bootstrap = Optional.ofNullable(replicatorSpec.getBootstrapServers())
            .orElse(kafkaInstanceName + "-kafka-bootstrap." + namespace + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.KAFKA_PORT);

        int numberOfConnectConfigTopicReplicas = (instance.getSpec().getStrimziOverrides().getKafka().getReplicas() >= 3)
                ? 3 : instance.getSpec().getStrimziOverrides().getKafka().getReplicas();

        Map<String, Object> configOfKafkaConnect = new HashMap<>();
        configOfKafkaConnect.put("config.storage.replication.factor", numberOfConnectConfigTopicReplicas);
        configOfKafkaConnect.put("offset.storage.replication.factor", numberOfConnectConfigTopicReplicas);
        configOfKafkaConnect.put("status.storage.replication.factor", numberOfConnectConfigTopicReplicas);
        configOfKafkaConnect.put("config.storage.topic", CONFIG_STORAGE_TOPIC_NAME);
        configOfKafkaConnect.put("offset.storage.topic", OFFSET_STORAGE_TOPIC_NAME);
        configOfKafkaConnect.put("status.storage.topic", STATUS_STORAGE_TOPIC_NAME);
        configOfKafkaConnect.put("key.converter", "org.apache.kafka.connect.converters.ByteArrayConverter");
        configOfKafkaConnect.put("value.converter", "org.apache.kafka.connect.converters.ByteArrayConverter");

        KafkaConnectTemplate kafkaConnectTemplate = new KafkaConnectTemplateBuilder()
            .editOrNewPod()
                .withMetadata(new MetadataTemplateBuilder()
                        .addToAnnotations(getEventStreamsMeteringAnnotations(COMPONENT_NAME))
                        .addToAnnotations(getPrometheusAnnotations(DEFAULT_PROMETHEUS_PORT))
                        .addToLabels(getServiceSelectorLabel(COMPONENT_NAME))
                        .addToLabels(getComponentLabels())
                        .build())
            .endPod()
            .build();

        kafkaConnect = new KafkaConnectBuilder()
            .withApiVersion(KafkaConnect.RESOURCE_GROUP + "/" + KafkaConnect.V1BETA1)  
            .editOrNewMetadata()
                .withNamespace(namespace)
                .withName(getDefaultResourceName(getInstanceName(), COMPONENT_NAME))
                .withOwnerReferences(getEventStreamsOwnerReference())
                .addToLabels(getServiceSelectorLabel(COMPONENT_NAME))
                .addToLabels(getComponentLabels())
            .endMetadata()
            .withNewSpecLike(replicatorSpec)
                .withTemplate(kafkaConnectTemplate)
                .withBootstrapServers(bootstrap)
                .withConfig(configOfKafkaConnect)
            .endSpec()
            .build();
          
        this.networkPolicy = createNetworkPolicy();

        Encoder encoder = Base64.getEncoder();

        Map<String, String> data = Collections.singletonMap(REPLICATOR_SECRET_KEY_NAME, encoder.encodeToString("[]".getBytes(StandardCharsets.UTF_8)));

    
        this.secret = createSecret(namespace, getDefaultResourceName(getInstanceName(),  REPLICATOR_SECRET_NAME), data, getComponentLabels(), 
            getEventStreamsMeteringAnnotations(COMPONENT_NAME));
           
    }

    // Used to store the credentials for the Connect workers connecting to Kafka
    // https://docs.confluent.io/4.1.0/connect/security.html
    private void createReplicatorConnectUser() {

        List<AclRule> connectAclList = new ArrayList<>();

        //Connect needs the ability to read/write to/from the three config topics
        AclRule rule1read = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                    .withName(CONFIG_STORAGE_TOPIC_NAME)
                    .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        AclRule rule1write = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(CONFIG_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.WRITE)
                .withHost("*")
                .build();

        AclRule rule2read = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(OFFSET_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        AclRule rule2write = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(OFFSET_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.WRITE)
                .withHost("*")
                .build();

        AclRule rule3read = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                    .withName(STATUS_STORAGE_TOPIC_NAME)
                    .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        AclRule rule3write = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(STATUS_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.WRITE)
                .withHost("*")
                .build();

        //Connect needs to be able to create the three Connect config topics defined in rules 1-3 + the topics being mirrored to the destination
        AclRule rule4 = new AclRuleBuilder()
                .withNewAclRuleClusterResource()
                .endAclRuleClusterResource()
                .withOperation(AclOperation.CREATE)
                .withHost("*")
                .build();

        //Connect also needs read on group.id
        AclRule rule5 = new AclRuleBuilder()
                .withNewAclRuleGroupResource()
                   .withName("*")
                   .withPatternType(AclResourcePatternType.PREFIX)
                .endAclRuleGroupResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        //Connect writes the data being brought over from the source cluster, it therefore needs write permission to any topic
        //as we don't know the names of these topics at install time
        //We could edit this user each time a new topic is added to the replication but lets keep it simple for now
        AclRule rule6 = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                    .withName("*")
                    .withPatternType(AclResourcePatternType.PREFIX)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.WRITE)
                .withHost("*")
                .build();

        connectAclList.add(rule1read);
        connectAclList.add(rule1write);
        connectAclList.add(rule2read);
        connectAclList.add(rule2write);
        connectAclList.add(rule3read);
        connectAclList.add(rule3write);
        connectAclList.add(rule4);
        connectAclList.add(rule5);
        connectAclList.add(rule6);

        replicatorConnectUser = buildUser(connectAclList, REPLICATOR_CONNECT_USER_NAME);

    }

    //used to allow the mirror maker connector to create destination topics and ACLs
    //This user is only used when this cluster is a destination cluster, but is made in advance ready to use
    private void createReplicatorDestinationConnectorUser() {

        List<AclRule> connectorDestinationAclList = new ArrayList<>();

        //Need the ability to create the destination topics (eg sourceClusterName.topic1)
        AclRule rule1create = new AclRuleBuilder()
                .withNewAclRuleClusterResource()
                .endAclRuleClusterResource()
                .withOperation(AclOperation.CREATE) //createTopicPermission
                .withHost("*")
                .build();

        //Need the ability to create the destination topics (eg sourceClusterName.topic1)
        AclRule rule1alter = new AclRuleBuilder()
                .withNewAclRuleClusterResource()
                .endAclRuleClusterResource()
                .withOperation(AclOperation.ALTER)  //createAclPermission
                .withHost("*")
                .build();

        connectorDestinationAclList.add(rule1create);
        connectorDestinationAclList.add(rule1alter);

        replicatorDestinationConnectorUser = buildUser(connectorDestinationAclList, REPLICATOR_DESTINATION_CLUSTER_CONNNECTOR_USER_NAME);

    }

    //Used to allow the mirror maker connector to create topics on the source cluster, read and write this topic and read from the source topic
    //This user is only used when the cluster is a source cluster but is made in advance ready to use
    private void createReplicatorSourceConnectorUser() {

        //need the ability to read from the source topics (don't know the names of these at this point)
        List<AclRule> connectorSourceAclList = new ArrayList<>();
        AclRule rule1 = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                    .withName("*")
                    .withPatternType(AclResourcePatternType.PREFIX)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();


        //Need the ability to create the source offset topic
        AclRule rule2create = new AclRuleBuilder()
                .withNewAclRuleClusterResource()
                .endAclRuleClusterResource()
                .withOperation(AclOperation.CREATE) //createTopicPermission
                .withHost("*")
                .build();

        //Need the ability to create the source offset topic
        AclRule rule2read = new AclRuleBuilder()
                .withNewAclRuleClusterResource()
                .endAclRuleClusterResource()
                .withOperation(AclOperation.READ)  //readAclPermission
                .withHost("*")
                .build();

        //Need the ability to write to the source side offset syncs topic which is called mm2-offset-syncs." + targetClusterAlias() + ".internal
        //We don't know the full name so using the prefix
        AclRule rule3 = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                    .withName("mm2-offset-syncs.*")
                    .withPatternType(AclResourcePatternType.PREFIX)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.WRITE)
                .withHost("*")
                .build();

        connectorSourceAclList.add(rule1);
        connectorSourceAclList.add(rule2create);
        connectorSourceAclList.add(rule2read);
        connectorSourceAclList.add(rule3);

        replicatorSourceConnectorUser = buildUser(connectorSourceAclList, REPLICATOR_SOURCE_CLUSTER_CONNECTOR_USER_NAME);

    }


    private KafkaUser buildUser(List<AclRule> aclList, String kafkaUserName) {

        Map<String, String> labels = new HashMap<>();
        labels.put("strimzi.io/cluster", getResourcePrefix());

        return new KafkaUserBuilder()
                .withApiVersion(KafkaUser.RESOURCE_GROUP + "/" + KafkaUser.V1BETA1)
                .withNewMetadata()
                    .withName(getDefaultResourceName(getInstanceName(),  kafkaUserName))
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .withNamespace(getNamespace())
                    .withLabels(labels)
                    .withLabels(getComponentLabels())
                .endMetadata()
                .withNewSpec()
                    .withNewKafkaUserTlsClientAuthentication()
                    .endKafkaUserTlsClientAuthentication()
                    .withNewKafkaUserAuthorizationSimple()
                        .withAcls(aclList)
                    .endKafkaUserAuthorizationSimple()
                .endSpec()
                .build();
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

    public static String getKafkaInstanceName(String instanceName) {
        return getResourcePrefix(instanceName);
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

    /**
     * @return KafkaConnect return the replicator 
     */
    public KafkaConnect getReplicator() {
        return this.kafkaConnect;
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

    /**
     * @return KafkaUser return the KafkaUser used to connect KafkaConnect worker to Kafka
     */
    public KafkaUser getReplicatorConnectUser() {
        return replicatorConnectUser;
    }

    /**
     * @return KafkaUser return the KafkaUser used to connect MirrorMaker connector to source Kafka
     */
    public KafkaUser getReplicatorSourceConnectorUser() {
        return replicatorSourceConnectorUser;
    }

    /**
     * @return KafkaUser return the KafkaUser used to connect MirrorMaker connector to destination Kafka
     */
    public KafkaUser getReplicatorDestinationConnectorUser() {
        return replicatorDestinationConnectorUser;
    }

}
