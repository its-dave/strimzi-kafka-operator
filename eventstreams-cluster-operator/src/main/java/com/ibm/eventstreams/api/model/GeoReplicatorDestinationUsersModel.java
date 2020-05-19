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
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicator;
import io.strimzi.api.kafka.model.AclOperation;
import io.strimzi.api.kafka.model.AclResourcePatternType;
import io.strimzi.api.kafka.model.AclRule;
import io.strimzi.api.kafka.model.AclRuleBuilder;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"checkstyle:MethodLength"})
public class GeoReplicatorDestinationUsersModel extends AbstractModel {

    public static final String CONNECT_KAFKA_USER_NAME = "georep-user";
    public static final String CONNECT_EXTERNAL_KAFKA_USER_NAME = "georep-ext-user";
    public static final String TARGET_CONNECTOR_KAFKA_USER_NAME = "georep-target-user";

    private KafkaUser connectKafkaUser;
    private KafkaUser targetConnectorKafkaUser;
    private KafkaUser connectExternalKafkaUser;

    private static final Logger log = LogManager.getLogger(GeoReplicatorDestinationUsersModel.class.getName());

    /**
     * This class is used to create the KafkaUser custom resources required to deploy the geo-replicator
     * @param replicatorInstance The EventStreams geo-replicator instance
     * @param instance The Event Streams instance is used to get the security information from the main install
     */
    public GeoReplicatorDestinationUsersModel(EventStreamsGeoReplicator replicatorInstance, EventStreams instance) {
        super(instance, GeoReplicatorModel.COMPONENT_NAME, GeoReplicatorModel.APPLICATION_NAME);

        setOwnerReference(replicatorInstance);

        KafkaListenerAuthentication internalClientAuth = GeoReplicatorModel.getInternalTlsKafkaListenerAuthentication(instance);
        KafkaListenerAuthentication externalClientAuth = GeoReplicatorModel.getExternalKafkaListenerAuthentication(instance);

        createConnectKafkaUser(replicatorInstance, internalClientAuth);
        createExternalConnectKafkaUser(replicatorInstance, externalClientAuth);
        createTargetConnectorKafkaUser(replicatorInstance, internalClientAuth);
    }


    // Used to store the credentials for the Source cluster to connect to the destination cluster
    // Needs the same permissions as the connectClusterUser because we check this user has permission to do
    // all the gre-rep stuff before mapping to the internal equivalent
    // Two users are needed because the external enpoint may have a different auth mechanism in place to the internal one
    // https://docs.confluent.io/4.1.0/connect/security.html
    private void createExternalConnectKafkaUser(EventStreamsGeoReplicator replicatorInstance, KafkaListenerAuthentication externalClientAuth) {

        if (GeoReplicatorModel.isReplicatorEnabled(replicatorInstance) && externalClientAuth != null) {
            List<AclRule> connectAclList = createConnectAclList();
            connectExternalKafkaUser = createKafkaUser(connectAclList, getConnectExternalKafkaUserName(), externalClientAuth);
        } else {
            connectExternalKafkaUser = null;
        }

    }

    // Used to store the credentials for the Connect workers connecting to Kafka
    // https://docs.confluent.io/4.1.0/connect/security.html
    private void createConnectKafkaUser(EventStreamsGeoReplicator replicatorInstance, KafkaListenerAuthentication internalClientAuth) {

        // connectKafkaUser is not needed if the internalClientAuth isn't set as no special permissions are needed
        //  to write to Kafka topics

        if (GeoReplicatorModel.isReplicatorEnabled(replicatorInstance) && internalClientAuth != null) {
            List<AclRule> connectAclList = createConnectAclList();
            connectKafkaUser = createKafkaUser(connectAclList, getConnectKafkaUserName(), internalClientAuth);
        } else {
            connectKafkaUser = null;
        }
    }

    private List<AclRule> createConnectAclList() {

        List<AclRule> connectAclList = new ArrayList<>();

// Kakfa Connect needs the ability to read/write to/from the three configuration topics
        AclRule configStorageTopicRead = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(GeoReplicatorModel.CONFIG_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        AclRule configStorageTopicWrite = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(GeoReplicatorModel.CONFIG_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.WRITE)
                .withHost("*")
                .build();

        AclRule configStorageTopicDescribe = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(GeoReplicatorModel.CONFIG_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBE)
                .withHost("*")
                .build();

        AclRule configStorageTopicDescribeConfigs = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(GeoReplicatorModel.CONFIG_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBECONFIGS)
                .withHost("*")
                .build();

        AclRule offsetStorageTopicRead = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(GeoReplicatorModel.OFFSET_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        AclRule offsetStorageTopicWrite = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(GeoReplicatorModel.OFFSET_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.WRITE)
                .withHost("*")
                .build();

        AclRule offsetStorageTopicDescribe = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(GeoReplicatorModel.OFFSET_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBE)
                .withHost("*")
                .build();

        AclRule offsetStorageTopicDescribeConfigs = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(GeoReplicatorModel.OFFSET_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBECONFIGS)
                .withHost("*")
                .build();

        AclRule statusStorageTopicRead = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(GeoReplicatorModel.STATUS_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        AclRule statusStorageTopicWrite = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(GeoReplicatorModel.STATUS_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.WRITE)
                .withHost("*")
                .build();

        AclRule statusStorageTopicDescribe = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(GeoReplicatorModel.STATUS_STORAGE_TOPIC_NAME)
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBE)
                .withHost("*")
                .build();

        AclRule statusStorageTopicDescribeConfigs = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName(GeoReplicatorModel.STATUS_STORAGE_TOPIC_NAME)
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
                .withName(GeoReplicatorModel.getDefaultReplicatorClusterName(getInstanceName()))
                .withPatternType(AclResourcePatternType.PREFIX)
                .endAclRuleGroupResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

        //Connect also needs describe on group.id
        AclRule connectClusterGroupDescribe = new AclRuleBuilder()
                .withNewAclRuleGroupResource()
                .withName(GeoReplicatorModel.getDefaultReplicatorClusterName(getInstanceName()))
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

        return connectAclList;
    }

    // A User to allow the mirror maker connector to create target topics and ACLs
    //  Only created if the cluster is a target cluster
    private void createTargetConnectorKafkaUser(EventStreamsGeoReplicator replicatorInstance, KafkaListenerAuthentication internalClientAuth) {

        // targetConnectorKafkaUser is not needed if the internalClientAuth isn't set as no special permissions are needed
        //  to write to Kafka topics
        if (GeoReplicatorModel.isReplicatorEnabled(replicatorInstance) && internalClientAuth != null) {
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

            // Need the ability to alter the target topics (eg sourceClusterName.topic1)
            AclRule clusterResourceAlterConfigs = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName("*")
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.ALTERCONFIGS)  //createAclPermission
                .withHost("*")
                .build();

            connectorTargetAcls.add(clusterResourceCreate);
            connectorTargetAcls.add(clusterResourceAlter);
            connectorTargetAcls.add(clusterResourceAlterConfigs);

            targetConnectorKafkaUser = createKafkaUser(connectorTargetAcls, getTargetConnectorKafkaUserName(), internalClientAuth);
        } else {
            targetConnectorKafkaUser = null;
        }
    }

    public static boolean isValidInstance(EventStreams instance) {
        boolean validInstance = true;

        KafkaListenerAuthentication internalClientAuth = GeoReplicatorModel.getInternalTlsKafkaListenerAuthentication(instance);
        if (internalClientAuth != null && !isSupportedAuthType(internalClientAuth)) {
            validInstance = false;
        }

        KafkaListenerAuthentication externalClientAuth = GeoReplicatorModel.getExternalKafkaListenerAuthentication(instance);
        if (externalClientAuth != null && !isSupportedAuthType(externalClientAuth)) {
            validInstance = false;
        }

        return validInstance;
    }

    public static boolean isValidInternExternalConfig(EventStreams instance) {
        boolean validInstance = true;

        KafkaListenerAuthentication internalClientAuth = GeoReplicatorModel.getInternalTlsKafkaListenerAuthentication(instance);
        KafkaListenerAuthentication externalClientAuth = GeoReplicatorModel.getExternalKafkaListenerAuthentication(instance);

        if (internalClientAuth != null && externalClientAuth == null) {
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

    public String getConnectExternalKafkaUserName() {
        return getConnectExternalKafkaUserName(getInstanceName());
    }

    public static String getConnectExternalKafkaUserName(String instanceName) {
        return getKafkaUserName(instanceName, CONNECT_EXTERNAL_KAFKA_USER_NAME);
    }

    /**
     * @return KafkaUser return the KafkaUser used to connect KafkaConnect worker to Kafka
     */
    public KafkaUser getConnectKafkaUser() {
        return connectKafkaUser;
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

    public KafkaUser getConnectExternalKafkaUser() {
        return connectExternalKafkaUser;
    }

    public void setConnectExternalKafkaUser(KafkaUser connectExternalKafkaUser) {
        this.connectExternalKafkaUser = connectExternalKafkaUser;
    }


}
