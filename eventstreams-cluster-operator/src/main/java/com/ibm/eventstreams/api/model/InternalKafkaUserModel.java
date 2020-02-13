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
import io.strimzi.api.kafka.model.KafkaUserBuilder;
import io.strimzi.operator.common.model.Labels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        
        Map<String, String> labels = new HashMap<>();
        labels.put(Labels.STRIMZI_CLUSTER_LABEL, EventStreamsKafkaModel.getKafkaInstanceName(getInstanceName()));
        
        kafkaUser = new KafkaUserBuilder()
        .withApiVersion(KafkaUser.RESOURCE_GROUP + "/" + KafkaUser.V1BETA1)
        .withNewMetadata()
            .withName(getDefaultResourceName())
            .withOwnerReferences(getEventStreamsOwnerReference())
            .withNamespace(getNamespace())
            .withLabels(labels)
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

    public KafkaUser getKafkaUser() {
        return this.kafkaUser;
    }

    public static String getSecretName(String instanceName) {
        return getResourcePrefix(instanceName) + "-" + COMPONENT_NAME;
    }
}