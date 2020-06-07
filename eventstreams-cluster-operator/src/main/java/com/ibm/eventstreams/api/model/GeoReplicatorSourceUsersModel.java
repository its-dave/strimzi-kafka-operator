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
import com.ibm.eventstreams.controller.models.StatusCondition;
import io.strimzi.api.kafka.model.AclOperation;
import io.strimzi.api.kafka.model.AclResourcePatternType;
import io.strimzi.api.kafka.model.AclRule;
import io.strimzi.api.kafka.model.AclRuleBuilder;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class GeoReplicatorSourceUsersModel extends AbstractModel {

    public static final String SOURCE_CONNECTOR_KAFKA_USER_NAME = "georep-source-user";
    private KafkaUser sourceConnectorKafkaUser;

    private static final Logger log = LogManager.getLogger(GeoReplicatorSourceUsersModel.class.getName());
    public static final String INVALID_REASON = "AuthenticationNotSupportedByGeoReplication";
    public static final String INVALID_MESSAGE = "A Kafka Listener is configured with authentication that is not supported by geo-replication. "
        + "Valid authentication types for geo-replication are 'tls' or 'scram-sha-512'. "
        + "Edit spec.strimziOverrides.kafka.listeners.external to provide a valid authentication type.";

    public GeoReplicatorSourceUsersModel(EventStreams instance) {
        super(instance, GeoReplicatorModel.COMPONENT_NAME, GeoReplicatorModel.APPLICATION_NAME);

        setOwnerReference(instance);

        KafkaListenerAuthentication externalClientAuth = GeoReplicatorModel.getExternalKafkaListenerAuthentication(instance);
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

            // MirrorCheckpointConnector needs the ability to list the source consumer groups
            AclRule allGroupsDescribe = new AclRuleBuilder()
                .withNewAclRuleGroupResource()
                .withName("*")
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleGroupResource()
                .withOperation(AclOperation.DESCRIBE)
                .withHost("*")
                .build();

            // MirrorCheckpointConnector needs the ability to read the offsets for the source consumer groups
            AclRule allGroupsRead = new AclRuleBuilder()
                .withNewAclRuleGroupResource()
                .withName("*")
                .withPatternType(AclResourcePatternType.LITERAL)
                .endAclRuleGroupResource()
                .withOperation(AclOperation.READ)
                .withHost("*")
                .build();

            connectorSourceAclList.add(allTopicsRead);
            connectorSourceAclList.add(allTopicDescribe);
            connectorSourceAclList.add(allTopicDescribeConfig);
            connectorSourceAclList.add(clusterResourceCreate);
            connectorSourceAclList.add(clusterResourceRead);
            connectorSourceAclList.add(clusterResourceDescribe);
            connectorSourceAclList.add(offsetTopicWrite);
            connectorSourceAclList.add(allGroupsDescribe);
            connectorSourceAclList.add(allGroupsRead);

            sourceConnectorKafkaUser = createKafkaUser(connectorSourceAclList, getSourceConnectorKafkaUserName(), externalClientAuth);
        } else {
            sourceConnectorKafkaUser = null;
        }
    }

    public static List<StatusCondition> validateCr(EventStreams spec) {
        boolean isInvalidKafkaListenerAuth = Optional.ofNullable(GeoReplicatorModel.getExternalKafkaListenerAuthentication(spec))
            .map(auth -> !isSupportedAuthType(auth))
            .orElse(false);
        if (isInvalidKafkaListenerAuth) {
            return Collections.singletonList(StatusCondition.createErrorCondition(INVALID_REASON, INVALID_MESSAGE));
        }

        return Collections.emptyList();
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
