/*
 * IBM Confidential
 * OCO Source Materials
 *
 * 5737-H33
 *
 * (C) Copyright IBM Corp. 2020  All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 */

package com.ibm.eventstreams.api.model;

import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import io.strimzi.api.kafka.model.AclOperation;
import io.strimzi.api.kafka.model.AclResourcePatternType;
import io.strimzi.api.kafka.model.AclRule;
import io.strimzi.api.kafka.model.AclRuleBuilder;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserBuilder;
import io.strimzi.api.kafka.model.KafkaUserScramSha512ClientAuthentication;
import io.strimzi.api.kafka.model.KafkaUserTlsClientAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationScramSha512;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationTls;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternal;
import io.strimzi.api.kafka.model.listener.KafkaListenerTls;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.strimzi.operator.common.model.Labels;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings({"checkstyle:MethodLength"})
public class ReplicatorUsersModel extends AbstractModel {

    private KafkaUser replicatorConnectUser;
    private KafkaUser replicatorDestinationConnectorUser;
    private KafkaUser replicatorSourceConnectorUser;

    EventStreams instance;

    private static final Logger log = LogManager.getLogger(ReplicatorModel.class.getName());

    public ReplicatorUsersModel(EventStreams instance) {
        super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), ReplicatorModel.COMPONENT_NAME);

        this.instance = instance;

        createReplicatorConnectUser();
        createReplicatorDestinationConnectorUser();
        createReplicatorSourceConnectorUser();

    }


    // Used to store the credentials for the Connect workers connecting to Kafka
    // https://docs.confluent.io/4.1.0/connect/security.html
    private void createReplicatorConnectUser() {

        List<AclRule> connectAclList = new ArrayList<>();

        //Kakfa Connect needs the ability to read/write to/from the three configuration topics
        AclRule configStorageTopicRead = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(ReplicatorModel.CONFIG_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        AclRule configStorageTopicWrite = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(ReplicatorModel.CONFIG_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.WRITE)
                .withHost("*")
                .build();

        AclRule configStorageTopicDescribe = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(ReplicatorModel.CONFIG_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBE)
                .withHost("*")
                .build();

        AclRule configStorageTopicDescribeConfigs = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(ReplicatorModel.CONFIG_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBECONFIGS)
                .withHost("*")
                .build();

        AclRule offsetStorageTopicRead = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(ReplicatorModel.OFFSET_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        AclRule offsetStorageTopicWrite = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(ReplicatorModel.OFFSET_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.WRITE)
                .withHost("*")
                .build();

        AclRule offsetStorageTopicDescribe = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(ReplicatorModel.OFFSET_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBE)
                .withHost("*")
                .build();

        AclRule offsetStorageTopicDescribeConfigs = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(ReplicatorModel.OFFSET_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBECONFIGS)
                .withHost("*")
                .build();

        AclRule statusStorageTopicRead = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(ReplicatorModel.STATUS_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        AclRule statusStorageTopicWrite = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(ReplicatorModel.STATUS_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.WRITE)
                .withHost("*")
                .build();

        AclRule statusStorageTopicDescribe = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(ReplicatorModel.STATUS_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBE)
                .withHost("*")
                .build();

        AclRule statusStorageTopicDescribeConfigs = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(ReplicatorModel.STATUS_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBECONFIGS)
                .withHost("*")
                .build();

        //Connect needs to be able to create the three Connect config topics defined in rules 1-3 + the topics being mirrored to the destination
        AclRule clusterResourceCreate = new AclRuleBuilder()
                .withNewAclRuleClusterResource()
                .endAclRuleClusterResource()
                .withOperation(AclOperation.CREATE)
                .withHost("*")
                .build();

        //Connect needs to be able to create the three Connect config topics defined in rules 1-3 + the topics being mirrored to the destination
        AclRule clusterResourceDescribeConfigs = new AclRuleBuilder()
                .withNewAclRuleClusterResource()
                .endAclRuleClusterResource()
                .withOperation(AclOperation.DESCRIBECONFIGS)
                .withHost("*")
                .build();

        //Connect also needs read on group.id
        AclRule connectClusterGroupRead = new AclRuleBuilder()
                .withNewAclRuleGroupResource()
                .withName("connect-cluster")
                .withPatternType(AclResourcePatternType.PREFIX)
                .endAclRuleGroupResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        //Connect also needs describe on group.id
        AclRule connectClusterGroupDescribe = new AclRuleBuilder()
                .withNewAclRuleGroupResource()
                .withName("connect-cluster")
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleGroupResource()
                .withOperation(AclOperation.DESCRIBE)
                .withHost("*")
                .build();


        //Connect writes the data being brought over from the source cluster, it therefore needs write permission to any topic
        //as we don't know the names of these topics at install time
        //We could edit this user each time a new topic is added to the replication but lets keep it simple for now
        AclRule writeAllTopics = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName("*")
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.WRITE)
                .withHost("*")
                .build();

        AclRule consumerOffsetsRead = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName("__consumer_offsets")
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        AclRule consumerOffsetsDescribe = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName("__consumer_offsets")
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBE)
                .withHost("*")
                .build();

        connectAclList.add(configStorageTopicRead);
        connectAclList.add(configStorageTopicDescribe);
        connectAclList.add(configStorageTopicWrite);
        connectAclList.add(offsetStorageTopicRead);
        connectAclList.add(offsetStorageTopicDescribe);
        connectAclList.add(offsetStorageTopicWrite);
        connectAclList.add(statusStorageTopicRead);
        connectAclList.add(statusStorageTopicDescribe);
        connectAclList.add(statusStorageTopicWrite);
        connectAclList.add(clusterResourceCreate);
        connectAclList.add(clusterResourceDescribeConfigs);
        connectAclList.add(connectClusterGroupRead);
        connectAclList.add(connectClusterGroupDescribe);
        connectAclList.add(writeAllTopics);
        connectAclList.add(consumerOffsetsRead);
        connectAclList.add(consumerOffsetsDescribe);
        connectAclList.add(configStorageTopicDescribeConfigs);
        connectAclList.add(offsetStorageTopicDescribeConfigs);
        connectAclList.add(statusStorageTopicDescribeConfigs);

        KafkaListenerAuthentication kafkaAuth = Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getListeners)
                .map(KafkaListeners::getTls)
                .map(KafkaListenerTls::getAuth).orElse(null);

        if (kafkaAuth != null) {
            replicatorConnectUser = createKafkaUser(connectAclList, ReplicatorModel.REPLICATOR_CONNECT_USER_NAME, kafkaAuth);
        }
    }

    //A User to allow the mirror maker connector to create destination topics and ACLs
    //Only created if the cluster is destination cluster
    private void createReplicatorDestinationConnectorUser() {

        List<AclRule> connectorDestinationAclList = new ArrayList<>();

        //Need the ability to create the destination topics (eg sourceClusterName.topic1)
        AclRule clusterResourceCreate = new AclRuleBuilder()
                .withNewAclRuleClusterResource()
                .endAclRuleClusterResource()
                .withOperation(AclOperation.CREATE) //createTopicPermission
                .withHost("*")
                .build();

        //Need the ability to create the destination topics (eg sourceClusterName.topic1)
        AclRule clusterResourceAlter = new AclRuleBuilder()
                .withNewAclRuleClusterResource()
                .endAclRuleClusterResource()
                .withOperation(AclOperation.ALTER)  //createAclPermission
                .withHost("*")
                .build();

        connectorDestinationAclList.add(clusterResourceCreate);
        connectorDestinationAclList.add(clusterResourceAlter);

        KafkaListenerAuthentication kafkaAuth = Optional.ofNullable(instance.getSpec())
            .map(EventStreamsSpec::getStrimziOverrides)
            .map(KafkaSpec::getKafka)
            .map(KafkaClusterSpec::getListeners)
            .map(KafkaListeners::getTls)
            .map(KafkaListenerTls::getAuth).orElse(null);

        if (kafkaAuth != null) {
            replicatorDestinationConnectorUser = createKafkaUser(connectorDestinationAclList, ReplicatorModel.REPLICATOR_DESTINATION_CLUSTER_CONNNECTOR_USER_NAME, kafkaAuth);
        }
    }

    //Used to allow the mirror maker connector to create topics on the source cluster, read and write this topic and read from the source topic
    //This user is only used when the cluster is a source cluster but is made in advance ready to use
    private void createReplicatorSourceConnectorUser() {

        //need the ability to read from the source topics (don't know the names of these at this point)
        List<AclRule> connectorSourceAclList = new ArrayList<>();
        AclRule allTopicsRead = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName("*")
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        AclRule allTopicDescribe = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName("*")
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBE)
                .withHost("*")
                .build();


        //Need the ability to create the source offset topic
        AclRule clusterResourceCreate = new AclRuleBuilder()
                .withNewAclRuleClusterResource()
                .endAclRuleClusterResource()
                .withOperation(AclOperation.CREATE) //createTopicPermission
                .withHost("*")
                .build();

        //Need the ability to read the source offset topic
        AclRule clusterResourceRead = new AclRuleBuilder()
                .withNewAclRuleClusterResource()
                .endAclRuleClusterResource()
                .withOperation(AclOperation.READ)  //readAclPermission
                .withHost("*")
                .build();

        //Need the ability to read the source offset topic
        AclRule clusterResourceDescribe = new AclRuleBuilder()
                .withNewAclRuleClusterResource()
                .endAclRuleClusterResource()
                .withOperation(AclOperation.DESCRIBE)  //readAclPermission
                .withHost("*")
                .build();

        //Need the ability to write to the source side offset syncs topic which is called mm2-offset-syncs." + targetClusterAlias() + ".internal
        //We don't know the full name so using the prefix
        AclRule offsetTopicWrite = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName("mm2-offset-syncs.*")
                .withPatternType(AclResourcePatternType.PREFIX)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.WRITE)
                .withHost("*")
                .build();

        connectorSourceAclList.add(allTopicsRead);
        connectorSourceAclList.add(allTopicDescribe);
        connectorSourceAclList.add(clusterResourceCreate);
        connectorSourceAclList.add(clusterResourceRead);
        connectorSourceAclList.add(clusterResourceDescribe);
        connectorSourceAclList.add(offsetTopicWrite);

        KafkaListenerAuthentication kafkaAuth = Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getListeners)
                .map(KafkaListeners::getExternal)
                .map(KafkaListenerExternal::getAuth)
                .orElse(null);

        if (kafkaAuth != null) {
            replicatorSourceConnectorUser = createKafkaUser(connectorSourceAclList, ReplicatorModel.REPLICATOR_SOURCE_CLUSTER_CONNECTOR_USER_NAME, kafkaAuth);
        }
    }

    private KafkaUser createKafkaUser(List<AclRule> aclList, String kafkaUserName, KafkaListenerAuthentication kafkaAuth) {

        setOwnerReference(instance);
        setArchitecture(instance.getSpec().getArchitecture());

        Map<String, String> labels = new HashMap<>();
        labels.put(Labels.STRIMZI_CLUSTER_LABEL, getInstanceName());

        KafkaUser createdUser = null;

        createdUser = new KafkaUserBuilder()
            .withApiVersion(KafkaUser.RESOURCE_GROUP + "/" + KafkaUser.V1BETA1)
            .withNewMetadata()
                .withName(getDefaultResourceName(getInstanceName(), kafkaUserName))
                .withOwnerReferences(getEventStreamsOwnerReference())
                .withNamespace(getNamespace())
                .withLabels(labels)
            .endMetadata()
            .withNewSpec()
               .withNewKafkaUserAuthorizationSimple()
                   .withAcls(aclList)
               .endKafkaUserAuthorizationSimple()
            .endSpec()
            .build();

        if (kafkaAuth instanceof KafkaListenerAuthenticationTls) {

            createdUser.getSpec().setAuthentication(new KafkaUserTlsClientAuthentication());

        } else if (kafkaAuth instanceof KafkaListenerAuthenticationScramSha512) {

            createdUser.getSpec().setAuthentication(new KafkaUserScramSha512ClientAuthentication());

        }

        return createdUser;

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
