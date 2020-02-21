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
import io.strimzi.api.kafka.model.AclOperation;
import io.strimzi.api.kafka.model.AclResourcePatternType;
import io.strimzi.api.kafka.model.AclRule;
import io.strimzi.api.kafka.model.AclRuleBuilder;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserSpecBuilder;

import java.util.ArrayList;
import java.util.List;

public class InternalKafkaUserModel extends AbstractModel {
    public static final String COMPONENT_NAME = "kafka-user";
    public KafkaUser kafkaUser;


    public InternalKafkaUserModel(EventStreams instance) {
        super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), COMPONENT_NAME);

        setOwnerReference(instance);
        setArchitecture(instance.getSpec().getArchitecture());

        List<AclRule> aclList = new ArrayList<>();
        AclRule rule1 = new AclRuleBuilder()
            .withNewAclRuleTopicResource()
                .withName("my-topic")
                .withPatternType(AclResourcePatternType.LITERAL)
            .endAclRuleTopicResource()
            .withOperation(AclOperation.READ)
            .withHost("*")
            .build();

        aclList.add(rule1);

        
        kafkaUser = createKafkaUser(COMPONENT_NAME,
                new KafkaUserSpecBuilder()
                        .withNewKafkaUserTlsClientAuthentication()
                        .endKafkaUserTlsClientAuthentication()
                        .withNewKafkaUserAuthorizationSimple()
                            .withAcls(aclList)
                        .endKafkaUserAuthorizationSimple()
                        .build());
    }

    public KafkaUser getKafkaUser() {
        return this.kafkaUser;
    }

    public static String getKafkaUserName(String instanceName) {
        return getDefaultResourceName(instanceName, COMPONENT_NAME);
    }
}