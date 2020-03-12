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

import io.strimzi.api.kafka.model.AclOperation;
import io.strimzi.api.kafka.model.AclResourcePatternType;
import io.strimzi.api.kafka.model.AclRule;
import io.strimzi.api.kafka.model.AclRuleBuilder;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserScramSha512ClientAuthentication;
import io.strimzi.api.kafka.model.KafkaUserSpecBuilder;
import io.strimzi.api.kafka.model.KafkaUserTlsClientAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationScramSha512;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationTls;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"checkstyle:MethodLength"})
public class ReplicatorUsersModel extends AbstractModel {

    public static final String CONNECT_KAFKA_USER_NAME = "rep-connect-user";
    public static final String SOURCE_CONNECTOR_KAFKA_USER_NAME = "rep-source-user";
    public static final String TARGET_CONNECTOR_KAFKA_USER_NAME = "rep-target-user";

    private KafkaUser connectKafkaUser;
    private KafkaUser sourceConnectorKafkaUser;
    private KafkaUser targetConnectorKafkaUser;

    private static final Logger log = LogManager.getLogger(ReplicatorModel.class.getName());

    public ReplicatorUsersModel(EventStreams instance) {
        super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), ReplicatorModel.COMPONENT_NAME);

        setOwnerReference(instance);

        KafkaListenerAuthentication internalClientAuth = ReplicatorModel.getInternalTlsKafkaListenerAuthentication(instance);
        KafkaListenerAuthentication externalClientAuth = ReplicatorModel.getExternalKafkaListenerAuthentication(instance);

        createConnectKafkaUser(instance, internalClientAuth);
        createSourceConnectorKafkaUser(instance, externalClientAuth);
        createTargetConnectorKafkaUser(instance, internalClientAuth);
    }

    // Checks whether the presented KafkaListenerAuthentication is one of the supported authentication types of the replicator
    private static boolean isSupportedAuthType(KafkaListenerAuthentication auth) {
        return auth instanceof KafkaListenerAuthenticationTls ||
                auth instanceof KafkaListenerAuthenticationScramSha512;

    }

    // Used to store the credentials for the Connect workers connecting to Kafka
    // https://docs.confluent.io/4.1.0/connect/security.html
    private void createConnectKafkaUser(EventStreams instance, KafkaListenerAuthentication internalClientAuth) {

        // connectKafkaUser is not needed if the internalClientAuth isn't set as no special permissions are needed
        //  to write to Kafka topics
        if (ReplicatorModel.isReplicatorEnabled(instance) && internalClientAuth != null) {
            List<AclRule> connectAclList = new ArrayList<>();

            // Kakfa Connect needs the ability to read/write to/from the three configuration topics
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

            //Connect needs to be able to create the three Connect config topics defined in rules 1-3 + the topics being mirrored to the target
            AclRule clusterResourceCreate = new AclRuleBuilder()
                    .withNewAclRuleClusterResource()
                    .endAclRuleClusterResource()
                    .withOperation(AclOperation.CREATE)
                    .withHost("*")
                    .build();

            //Connect needs to be able to create the three Connect config topics defined in rules 1-3 + the topics being mirrored to the target
            AclRule clusterResourceDescribeConfigs = new AclRuleBuilder()
                    .withNewAclRuleClusterResource()
                    .endAclRuleClusterResource()
                    .withOperation(AclOperation.DESCRIBECONFIGS)
                    .withHost("*")
                    .build();

            //Connect also needs read on group.id
            AclRule connectClusterGroupRead = new AclRuleBuilder()
                    .withNewAclRuleGroupResource()
                    .withName(ReplicatorModel.getDefaultReplicatorClusterName(getInstanceName()))
                    .withPatternType(AclResourcePatternType.PREFIX)
                    .endAclRuleGroupResource()
                    .withOperation(AclOperation.READ)
                    .withHost("*")
                    .build();

            //Connect also needs describe on group.id
            AclRule connectClusterGroupDescribe = new AclRuleBuilder()
                    .withNewAclRuleGroupResource()
                    .withName(ReplicatorModel.getDefaultReplicatorClusterName(getInstanceName()))
                    .withPatternType(AclResourcePatternType.LITERAL)
                    .endAclRuleGroupResource()
                    .withOperation(AclOperation.DESCRIBE)
                    .withHost("*")
                    .build();


            // Connect writes the data being brought over from the source cluster, it therefore needs write permission to any topic
            //  as we don't know the names of these topics at install time
            // We could edit this user each time a new topic is added to the replication but lets keep it simple for now
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

            connectKafkaUser = createKafkaUser(connectAclList, getConnectKafkaUserName(), internalClientAuth);
        } else {
            connectKafkaUser = null;
        }
    }

    // A User to allow the mirror maker connector to create target topics and ACLs
    //  Only created if the cluster is a target cluster
    private void createTargetConnectorKafkaUser(EventStreams instance, KafkaListenerAuthentication internalClientAuth) {

        // targetConnectorKafkaUser is not needed if the internalClientAuth isn't set as no special permissions are needed
        //  to write to Kafka topics
        if (ReplicatorModel.isReplicatorEnabled(instance) && internalClientAuth != null) {
            List<AclRule> connectorTargetAcls = new ArrayList<>();

            // Need the ability to create the target topics (eg sourceClusterName.topic1)
            AclRule clusterResourceCreate = new AclRuleBuilder()
                    .withNewAclRuleClusterResource()
                    .endAclRuleClusterResource()
                    .withOperation(AclOperation.CREATE) //createTopicPermission
                    .withHost("*")
                    .build();

            // Need the ability to create the target topics (eg sourceClusterName.topic1)
            AclRule clusterResourceAlter = new AclRuleBuilder()
                    .withNewAclRuleClusterResource()
                    .endAclRuleClusterResource()
                    .withOperation(AclOperation.ALTER)  //createAclPermission
                    .withHost("*")
                    .build();

            connectorTargetAcls.add(clusterResourceCreate);
            connectorTargetAcls.add(clusterResourceAlter);

            targetConnectorKafkaUser = createKafkaUser(connectorTargetAcls, getTargetConnectorKafkaUserName(), internalClientAuth);
        } else {
            targetConnectorKafkaUser = null;
        }
    }

    // Used to allow the mirror maker connector to create topics on the source cluster, read and write this topic and read from the source topic
    // This user is only used when the cluster is a source cluster but is made in advance ready to use
    private void createSourceConnectorKafkaUser(EventStreams instance, KafkaListenerAuthentication externalClientAuth) {

        // sourceConnectorKafkaUser is not needed if the internalClientAuth isn't set as no special permissions are needed
        //  to write to Kafka topics
        if (externalClientAuth != null) {
            // Need the ability to read from the source topics (don't know the names of these at this point)
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


            // Need the ability to create the source offset topic
            AclRule clusterResourceCreate = new AclRuleBuilder()
                    .withNewAclRuleClusterResource()
                    .endAclRuleClusterResource()
                    .withOperation(AclOperation.CREATE) //createTopicPermission
                    .withHost("*")
                    .build();

            // Need the ability to read the source offset topic
            AclRule clusterResourceRead = new AclRuleBuilder()
                    .withNewAclRuleClusterResource()
                    .endAclRuleClusterResource()
                    .withOperation(AclOperation.READ)  //readAclPermission
                    .withHost("*")
                    .build();

            // Need the ability to read the source offset topic
            AclRule clusterResourceDescribe = new AclRuleBuilder()
                    .withNewAclRuleClusterResource()
                    .endAclRuleClusterResource()
                    .withOperation(AclOperation.DESCRIBE)  //readAclPermission
                    .withHost("*")
                    .build();

            // Need the ability to write to the source side offset syncs topic which is called mm2-offset-syncs." + targetClusterAlias() + ".internal
            // We don't know the full name so using the prefix
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

            sourceConnectorKafkaUser = createKafkaUser(connectorSourceAclList, getSourceConnectorKafkaUserName(), externalClientAuth);
        } else {
            sourceConnectorKafkaUser = null;
        }
    }

    private KafkaUser createKafkaUser(List<AclRule> aclList, String kafkaUserName, KafkaListenerAuthentication kafkaAuth) {
        KafkaUserSpecBuilder kafkaUserSpec = new KafkaUserSpecBuilder()
                .withNewKafkaUserAuthorizationSimple()
                    .withAcls(aclList)
                .endKafkaUserAuthorizationSimple();

        if (kafkaAuth instanceof KafkaListenerAuthenticationTls) {
            kafkaUserSpec.withAuthentication(new KafkaUserTlsClientAuthentication());
        } else if (kafkaAuth instanceof KafkaListenerAuthenticationScramSha512) {
            kafkaUserSpec.withAuthentication(new KafkaUserScramSha512ClientAuthentication());
        }

        return super.createKafkaUser(kafkaUserName, kafkaUserSpec.build());
    }

    public static boolean isValidInstance(EventStreams instance) {
        boolean validInstance = true;

        KafkaListenerAuthentication internalClientAuth = ReplicatorModel.getInternalTlsKafkaListenerAuthentication(instance);
        if (internalClientAuth != null && !isSupportedAuthType(internalClientAuth)) {
            validInstance = false;
        }

        KafkaListenerAuthentication externalClientAuth = ReplicatorModel.getExternalKafkaListenerAuthentication(instance);
        if (externalClientAuth != null && !isSupportedAuthType(externalClientAuth)) {
            validInstance = false;
        }

        return validInstance;
    }

    public String getConnectKafkaUserName() {
        return getConnectKafkaUserName(getInstanceName());
    }

    public static String getConnectKafkaUserName(String instanceName) {
        return getKafkaUserName(instanceName, CONNECT_KAFKA_USER_NAME);
    }

    /**
     * @return KafkaUser return the KafkaUser used to connect KafkaConnect worker to Kafka
     */
    public KafkaUser getConnectKafkaUser() {
        return connectKafkaUser;
    }

    public String getSourceConnectorKafkaUserName() {
        return getSourceConnectorKafkaUserName(getInstanceName());
    }

    public static String getSourceConnectorKafkaUserName(String instanceName) {
        return getKafkaUserName(instanceName, SOURCE_CONNECTOR_KAFKA_USER_NAME);
    }

    /**
     * @return KafkaUser return the KafkaUser used to connect MirrorMaker connector to source Kafka
     */
    public KafkaUser getSourceConnectorKafkaUser() {
        return sourceConnectorKafkaUser;
    }

    public String getTargetConnectorKafkaUserName() {
        return getTargetConnectorKafkaUserName(getInstanceName());
    }

    public static String getTargetConnectorKafkaUserName(String instanceName) {
        return getKafkaUserName(instanceName, TARGET_CONNECTOR_KAFKA_USER_NAME);
    }

    /**
     * @return KafkaUser return the KafkaUser used to connect MirrorMaker connector to target Kafka
     */
    public KafkaUser getTargetConnectorKafkaUser() {
        return targetConnectorKafkaUser;
    }

}
