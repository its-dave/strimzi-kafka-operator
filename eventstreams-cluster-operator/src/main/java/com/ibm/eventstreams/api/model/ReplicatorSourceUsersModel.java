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
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import io.strimzi.api.kafka.model.AclRuleBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ReplicatorSourceUsersModel extends AbstractModel {

    public static final String SOURCE_CONNECTOR_KAFKA_USER_NAME = "georep-source-user";
    private KafkaUser sourceConnectorKafkaUser;

    private static final Logger log = LogManager.getLogger(ReplicatorSourceUsersModel.class.getName());

    public ReplicatorSourceUsersModel(EventStreams instance) {
        super(instance, ReplicatorModel.COMPONENT_NAME, ReplicatorModel.APPLICATION_NAME);

        setOwnerReference(instance);

        KafkaListenerAuthentication externalClientAuth = ReplicatorModel.getExternalKafkaListenerAuthentication(instance);
        createSourceConnectorKafkaUser(externalClientAuth);

    }

    // Used to allow the mirror maker connector to create topics on the source cluster, read and write this topic and read from the source topic
    // This user is only used when the cluster is a source cluster but is made in advance ready to use
    private void createSourceConnectorKafkaUser(KafkaListenerAuthentication externalClientAuth) {

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

            AclRule allTopicDescribeConfig = new AclRuleBuilder()
                .withNewAclRuleTopicResource()
                .withName("*")
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleTopicResource()
                .withOperation(AclOperation.DESCRIBECONFIGS)
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
                    .withName("mm2-offset-syncs.")
                    .withPatternType(AclResourcePatternType.PREFIX)
                    .endAclRuleTopicResource()
                    .withOperation(AclOperation.WRITE)
                    .withHost("*")
                    .build();

            connectorSourceAclList.add(allTopicsRead);
            connectorSourceAclList.add(allTopicDescribe);
            connectorSourceAclList.add(allTopicDescribeConfig);
            connectorSourceAclList.add(clusterResourceCreate);
            connectorSourceAclList.add(clusterResourceRead);
            connectorSourceAclList.add(clusterResourceDescribe);
            connectorSourceAclList.add(offsetTopicWrite);

            sourceConnectorKafkaUser = createKafkaUser(connectorSourceAclList, getSourceConnectorKafkaUserName(), externalClientAuth);
        } else {
            sourceConnectorKafkaUser = null;
        }
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


}
