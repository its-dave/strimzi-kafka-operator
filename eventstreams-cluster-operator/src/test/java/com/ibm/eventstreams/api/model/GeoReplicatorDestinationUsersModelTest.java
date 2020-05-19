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

import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicator;
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicatorBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.strimzi.api.kafka.model.AclOperation;
import io.strimzi.api.kafka.model.AclResourcePatternType;
import io.strimzi.api.kafka.model.AclRule;
import io.strimzi.api.kafka.model.AclRuleClusterResource;
import io.strimzi.api.kafka.model.AclRuleGroupResource;
import io.strimzi.api.kafka.model.AclRuleTopicResource;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserAuthorizationSimple;
import io.strimzi.api.kafka.model.KafkaUserScramSha512ClientAuthentication;
import io.strimzi.api.kafka.model.KafkaUserTlsClientAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.strimzi.operator.common.model.Labels;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;

@SuppressWarnings({"checkstyle:JavaNCSS", "checkstyle:MethodLength"})

public class GeoReplicatorDestinationUsersModelTest {

    private final String instanceName = "test";
    private final int defaultReplicas = 1;
    private final String namespace = "myproject";

    private EventStreamsBuilder createDefaultEventStreams(KafkaListeners listenerSpec) {

        return ModelUtils.createDefaultEventStreams(instanceName)
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(instanceName)
                        .withNewNamespace(namespace)
                        .build())
                .editSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                        .withReplicas(3)
                        .withListeners(listenerSpec)
                        .withNewTemplate()
                        .withNewPod()
                        .withNewMetadata()
                        .endMetadata()
                        .endPod()
                        .endTemplate()
                        .endKafka()
                        .build()
                )
                .endSpec();
    }

    private EventStreamsGeoReplicatorBuilder createDefaultEventStreamsGeoReplicator() {
        return ModelUtils.createDefaultEventStreamsGeoReplicator(instanceName);
    }

    private GeoReplicatorSecretModel createDefaultReplicatorSecretModel() {
        EventStreams instance = createDefaultEventStreams(ModelUtils.getMutualTLSOnBothInternalAndExternalListenerSpec()).build();
        EventStreamsGeoReplicator replicatorInstance = createDefaultEventStreamsGeoReplicator().build();
        return new GeoReplicatorSecretModel(instance);
    }

    private GeoReplicatorDestinationUsersModel createDefaultReplicatorDestinationUserModel() {
        EventStreams instance = createDefaultEventStreams(ModelUtils.getMutualTLSOnBothInternalAndExternalListenerSpec()).build();
        EventStreamsGeoReplicator replicatorInstance = createDefaultEventStreamsGeoReplicator().build();
        return new GeoReplicatorDestinationUsersModel(replicatorInstance, instance);
    }

    private GeoReplicatorDestinationUsersModel createReplicatorUserModel(KafkaListeners listenerSpec) {
        EventStreams instance = createDefaultEventStreams(listenerSpec).build();
        EventStreamsGeoReplicator replicatorInstance = createDefaultEventStreamsGeoReplicator().build();
        return new GeoReplicatorDestinationUsersModel(replicatorInstance, instance);
    }

    @Test
    public void testReplicatorUsersCreatedWithTlsAuthentication() {

        GeoReplicatorDestinationUsersModel replicatorDestinationUsers = createDefaultReplicatorDestinationUserModel();
        KafkaUser connectKafkaUser = replicatorDestinationUsers.getConnectKafkaUser();
        KafkaUser connectExternalKafkaUser = replicatorDestinationUsers.getConnectExternalKafkaUser();
        KafkaUser targetConnectorKafkaUser = replicatorDestinationUsers.getTargetConnectorKafkaUser();


        assertThat(connectKafkaUser.getMetadata().getName(), is(instanceName + "-" + AbstractModel.APP_NAME + "-" + GeoReplicatorDestinationUsersModel.CONNECT_KAFKA_USER_NAME));
        assertThat(connectExternalKafkaUser.getMetadata().getName(), is(instanceName + "-" + AbstractModel.APP_NAME + "-" + GeoReplicatorDestinationUsersModel.CONNECT_EXTERNAL_KAFKA_USER_NAME));
        assertThat(targetConnectorKafkaUser.getMetadata().getName(), is(instanceName + "-" + AbstractModel.APP_NAME + "-" + GeoReplicatorDestinationUsersModel.TARGET_CONNECTOR_KAFKA_USER_NAME));

        Map<String, String> replicatorConnectUserLabels = connectKafkaUser.getMetadata().getLabels();
        for (Map.Entry<String, String> label : replicatorConnectUserLabels.entrySet()) {
            if (!label.getKey().equals(io.strimzi.operator.common.model.Labels.STRIMZI_CLUSTER_LABEL)) {
                assertThat(label.getKey(), not(containsString(io.strimzi.operator.common.model.Labels.STRIMZI_DOMAIN)));
            }
        }
        assertThat(replicatorConnectUserLabels.get(io.strimzi.operator.common.model.Labels.STRIMZI_CLUSTER_LABEL), is(instanceName));

        Map<String, String> replicatorDestinationConnectorUserLabels = targetConnectorKafkaUser.getMetadata().getLabels();
        for (Map.Entry<String, String> label : replicatorDestinationConnectorUserLabels.entrySet()) {
            if (!label.getKey().equals(io.strimzi.operator.common.model.Labels.STRIMZI_CLUSTER_LABEL)) {
                assertThat(label.getKey(), not(containsString(io.strimzi.operator.common.model.Labels.STRIMZI_DOMAIN)));
            }
        }
        assertThat(replicatorDestinationConnectorUserLabels.get(io.strimzi.operator.common.model.Labels.STRIMZI_CLUSTER_LABEL), is(instanceName));

        assertThat(connectKafkaUser.getKind(), is("KafkaUser"));
        assertThat(connectExternalKafkaUser.getKind(), is("KafkaUser"));
        assertThat(targetConnectorKafkaUser.getKind(), is("KafkaUser"));

        assertThat(connectKafkaUser.getMetadata().getNamespace(), is(namespace));
        assertThat(connectExternalKafkaUser.getMetadata().getNamespace(), is(namespace));
        assertThat(targetConnectorKafkaUser.getMetadata().getNamespace(), is(namespace));

        Matcher hasCorrectLabels = allOf(
                aMapWithSize(7),
                hasEntry(Labels.KUBERNETES_NAME_LABEL, "replicator"),
                hasEntry(Labels.KUBERNETES_INSTANCE_LABEL, instanceName),
                hasEntry(Labels.KUBERNETES_MANAGED_BY_LABEL, "eventstreams-cluster-operator"),
                hasEntry(Labels.KUBERNETES_PART_OF_LABEL, "eventstreams-" + instanceName),
                hasEntry(Labels.STRIMZI_NAME_LABEL, "test-ibm-es-replicator"),
                hasEntry(Labels.STRIMZI_CLUSTER_LABEL, instanceName),
                hasEntry(Labels.STRIMZI_KIND_LABEL, "EventStreams"));

        assertThat(connectKafkaUser.getSpec().getAuthorization().getType(), is("simple"));
        assertThat(connectExternalKafkaUser.getSpec().getAuthorization().getType(), is("simple"));
        assertThat(targetConnectorKafkaUser.getSpec().getAuthorization().getType(), is("simple"));
    }


    @Test
    public void testReplicatorConnectUserAcls() {

        GeoReplicatorDestinationUsersModel replicatorUsers = createDefaultReplicatorDestinationUserModel();
        KafkaUser replicatorConnectUser = replicatorUsers.getConnectKafkaUser();

        //Connect User ACLs
        assertThat(replicatorConnectUser.getSpec().getAuthorization(), instanceOf(KafkaUserAuthorizationSimple.class));
        KafkaUserAuthorizationSimple kafkaUserAuth = (KafkaUserAuthorizationSimple) replicatorConnectUser.getSpec().getAuthorization();
        List<AclRule> acls = kafkaUserAuth.getAcls();
        aclChecker(acls);

        KafkaUser replicatorConnectExternalUser = replicatorUsers.getConnectExternalKafkaUser();

        //Connect User ACLs
        assertThat(replicatorConnectExternalUser.getSpec().getAuthorization(), instanceOf(KafkaUserAuthorizationSimple.class));
        KafkaUserAuthorizationSimple kafkaExternalUserAuth = (KafkaUserAuthorizationSimple) replicatorConnectExternalUser.getSpec().getAuthorization();
        List<AclRule> aclsExternal = kafkaExternalUserAuth.getAcls();
        aclChecker(aclsExternal);

    }


    private void aclChecker(List<AclRule> acls) {
        assertThat(acls.size(), is(19));

        AclRule rule1read = acls.get(0);
        assertThat(rule1read.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule1readTopic = (AclRuleTopicResource) rule1read.getResource();
        assertThat(rule1readTopic.getName(), is(GeoReplicatorModel.CONFIG_STORAGE_TOPIC_NAME));
        assertThat(rule1readTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule1read.getOperation(), is(AclOperation.READ));
        assertThat(rule1read.getHost(), is("*"));

        AclRule rule1write = acls.get(1);
        assertThat(rule1write.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule1writeTopic = (AclRuleTopicResource) rule1write.getResource();
        assertThat(rule1writeTopic.getName(), is(GeoReplicatorModel.CONFIG_STORAGE_TOPIC_NAME));
        assertThat(rule1writeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule1write.getOperation(), is(AclOperation.DESCRIBE));
        assertThat(rule1write.getHost(), is("*"));

        AclRule rule1describe = acls.get(2);
        assertThat(rule1describe.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule1describeTopic = (AclRuleTopicResource) rule1describe.getResource();
        assertThat(rule1describeTopic.getName(), is(GeoReplicatorModel.CONFIG_STORAGE_TOPIC_NAME));
        assertThat(rule1describeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule1describe.getOperation(), is(AclOperation.WRITE));
        assertThat(rule1describe.getHost(), is("*"));

        AclRule rule2read = acls.get(3);
        assertThat(rule2read.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule2readTopic = (AclRuleTopicResource) rule2read.getResource();
        assertThat(rule2readTopic.getName(), is(GeoReplicatorModel.OFFSET_STORAGE_TOPIC_NAME));
        assertThat(rule2readTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule2read.getOperation(), is(AclOperation.READ));
        assertThat(rule2read.getHost(), is("*"));

        AclRule rule2describe = acls.get(4);
        assertThat(rule2describe.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule2describeTopic = (AclRuleTopicResource) rule2describe.getResource();
        assertThat(rule2describeTopic.getName(), is(GeoReplicatorModel.OFFSET_STORAGE_TOPIC_NAME));
        assertThat(rule2describeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule2describe.getOperation(), is(AclOperation.DESCRIBE));
        assertThat(rule2describe.getHost(), is("*"));

        AclRule rule2write = acls.get(5);
        assertThat(rule2write.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule2writeTopic = (AclRuleTopicResource) rule2write.getResource();
        assertThat(rule2writeTopic.getName(), is(GeoReplicatorModel.OFFSET_STORAGE_TOPIC_NAME));
        assertThat(rule2writeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule2write.getOperation(), is(AclOperation.WRITE));
        assertThat(rule2write.getHost(), is("*"));

        AclRule rule3read = acls.get(6);
        assertThat(rule3read.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule3readTopic = (AclRuleTopicResource) rule3read.getResource();
        assertThat(rule3readTopic.getName(), is(GeoReplicatorModel.STATUS_STORAGE_TOPIC_NAME));
        assertThat(rule3readTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule3read.getOperation(), is(AclOperation.READ));
        assertThat(rule3read.getHost(), is("*"));

        AclRule rule3describe = acls.get(7);
        assertThat(rule3describe.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule3describeTopic = (AclRuleTopicResource) rule3describe.getResource();
        assertThat(rule3describeTopic.getName(), is(GeoReplicatorModel.STATUS_STORAGE_TOPIC_NAME));
        assertThat(rule3describeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule3describe.getOperation(), is(AclOperation.DESCRIBE));
        assertThat(rule3describe.getHost(), is("*"));

        AclRule rule3write = acls.get(8);
        assertThat(rule3write.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule3writeTopic = (AclRuleTopicResource) rule3write.getResource();
        assertThat(rule3writeTopic.getName(), is(GeoReplicatorModel.STATUS_STORAGE_TOPIC_NAME));
        assertThat(rule3writeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule3write.getOperation(), is(AclOperation.WRITE));
        assertThat(rule3write.getHost(), is("*"));

        AclRule rule4create = acls.get(9);
        assertThat(rule4create.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule4create.getOperation(), is(AclOperation.CREATE));
        assertThat(rule4create.getHost(), is("*"));

        AclRule rule4describeconfig = acls.get(10);
        assertThat(rule4describeconfig.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule4describeconfig.getOperation(), is(AclOperation.DESCRIBECONFIGS));
        assertThat(rule4describeconfig.getHost(), is("*"));

        AclRule rule5read = acls.get(11);
        assertThat(rule5read.getResource(), instanceOf(AclRuleGroupResource.class));
        AclRuleGroupResource rule5readgroup = (AclRuleGroupResource) rule5read.getResource();
        assertThat(rule5readgroup.getName(), is(GeoReplicatorModel.getDefaultReplicatorClusterName(instanceName)));
        assertThat(rule5readgroup.getPatternType(), is(AclResourcePatternType.PREFIX));
        assertThat(rule5read.getOperation(), is(AclOperation.READ));
        assertThat(rule5read.getHost(), is("*"));

        AclRule rule5describe = acls.get(12);
        assertThat(rule5describe.getResource(), instanceOf(AclRuleGroupResource.class));
        AclRuleGroupResource rule5describegroup = (AclRuleGroupResource) rule5describe.getResource();
        assertThat(rule5describegroup.getName(), is(GeoReplicatorModel.getDefaultReplicatorClusterName(instanceName)));
        assertThat(rule5describegroup.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule5describe.getOperation(), is(AclOperation.DESCRIBE));
        assertThat(rule5describe.getHost(), is("*"));

        AclRule rule6 = acls.get(13);
        assertThat(rule6.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule6topic = (AclRuleTopicResource) rule6.getResource();
        assertThat(rule6topic.getName(), is("*"));
        assertThat(rule6topic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule6.getOperation(), is(AclOperation.WRITE));
        assertThat(rule6.getHost(), is("*"));

        AclRule rule7read = acls.get(14);
        assertThat(rule7read.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule7readTopic = (AclRuleTopicResource) rule7read.getResource();
        assertThat(rule7readTopic.getName(), is("__consumer_offsets"));
        assertThat(rule7readTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule7read.getOperation(), is(AclOperation.READ));
        assertThat(rule7read.getHost(), is("*"));


        AclRule rule7describe = acls.get(15);
        assertThat(rule7describe.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule7describeTopic = (AclRuleTopicResource) rule7describe.getResource();
        assertThat(rule7describeTopic.getName(), is("__consumer_offsets"));
        assertThat(rule7describeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule7describe.getOperation(), is(AclOperation.DESCRIBE));
        assertThat(rule7describe.getHost(), is("*"));

        AclRule configStorageTopicDescribeConfigs = acls.get(16);
        assertThat(configStorageTopicDescribeConfigs.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource configStorageTopicDescribeConfigsTopic = (AclRuleTopicResource) configStorageTopicDescribeConfigs.getResource();
        assertThat(configStorageTopicDescribeConfigsTopic.getName(), is(GeoReplicatorModel.CONFIG_STORAGE_TOPIC_NAME));
        assertThat(configStorageTopicDescribeConfigsTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(configStorageTopicDescribeConfigs.getOperation(), is(AclOperation.DESCRIBECONFIGS));
        assertThat(configStorageTopicDescribeConfigs.getHost(), is("*"));

        AclRule offsetStorageTopicDescribeConfigs = acls.get(17);
        assertThat(offsetStorageTopicDescribeConfigs.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource offsetStorageTopicDescribeConfigsTopic = (AclRuleTopicResource) offsetStorageTopicDescribeConfigs.getResource();
        assertThat(offsetStorageTopicDescribeConfigsTopic.getName(), is(GeoReplicatorModel.OFFSET_STORAGE_TOPIC_NAME));
        assertThat(offsetStorageTopicDescribeConfigsTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(offsetStorageTopicDescribeConfigs.getOperation(), is(AclOperation.DESCRIBECONFIGS));
        assertThat(offsetStorageTopicDescribeConfigs.getHost(), is("*"));

        AclRule statusStorageTopicDescribeConfigs = acls.get(18);
        assertThat(statusStorageTopicDescribeConfigs.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource statusStorageTopicDescribeConfigsTopic = (AclRuleTopicResource) statusStorageTopicDescribeConfigs.getResource();
        assertThat(statusStorageTopicDescribeConfigsTopic.getName(), is(GeoReplicatorModel.STATUS_STORAGE_TOPIC_NAME));
        assertThat(statusStorageTopicDescribeConfigsTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(statusStorageTopicDescribeConfigs.getOperation(), is(AclOperation.DESCRIBECONFIGS));
        assertThat(statusStorageTopicDescribeConfigs.getHost(), is("*"));
    }

    @Test
    public void testReplicatorConnectorUserAcls() {
        GeoReplicatorDestinationUsersModel replicatorDestinationUsers = createDefaultReplicatorDestinationUserModel();
        KafkaUser replicatorDestinationConnectorUser = replicatorDestinationUsers.getTargetConnectorKafkaUser();

        //MM2 to destination Kafka ACL
        assertThat(replicatorDestinationConnectorUser.getSpec().getAuthorization(), instanceOf(KafkaUserAuthorizationSimple.class));
        KafkaUserAuthorizationSimple kafkaDestUserAuth = (KafkaUserAuthorizationSimple) replicatorDestinationConnectorUser.getSpec().getAuthorization();
        List<AclRule> aclsDest = kafkaDestUserAuth.getAcls();
        assertThat(aclsDest.size(), is(3));

        AclRule rule1destcreate = aclsDest.get(0);
        assertThat(rule1destcreate.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule1destcreate.getOperation(), is(AclOperation.CREATE));
        assertThat(rule1destcreate.getHost(), is("*"));

        AclRule rule1destalter = aclsDest.get(1);
        assertThat(rule1destalter.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule1destalter.getOperation(), is(AclOperation.ALTER));
        assertThat(rule1destalter.getHost(), is("*"));

        AclRule rule1destalterconfig = aclsDest.get(2);
        assertThat(rule1destalterconfig.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule1destalterconfigtopic = (AclRuleTopicResource) rule1destalterconfig.getResource();
        assertThat(rule1destalterconfigtopic.getName(), is("*"));
        assertThat(rule1destalterconfigtopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule1destalterconfig.getOperation(), is(AclOperation.ALTERCONFIGS));
        assertThat(rule1destalterconfig.getHost(), is("*"));
    }

    @Test
    public void testReplicatorConnectUsersWhenInternalTLSOnlyEnabledWithNoMutualAuth() {
        GeoReplicatorDestinationUsersModel replicatorDestinationUsers =  createReplicatorUserModel(ModelUtils.getServerAuthOnlyInternalListenerSpec());
        assertThat(replicatorDestinationUsers.getConnectKafkaUser(), is(nullValue()));
        assertThat(replicatorDestinationUsers.getConnectExternalKafkaUser(), is(nullValue()));
        assertThat(replicatorDestinationUsers.getTargetConnectorKafkaUser(), is(nullValue()));
    }

    @Test
    public void testReplicatorConnectUserWhenInternalTLSOnlyEnabledWithMutualAuthTLS() {
        //this combination isn't value - prevented at replicator operator level
    }

    @Test
    public void testReplicatorConnectUserWhenInternalTLSOnlyEnabledWithMutualAuthScram() {
        //this combination isn't value - prevented at replicator operator level
    }

    @Test
    public void testReplicatorConnectUserWhenExternalTLSOnlyEnabledWithNoMutualAuth() {
        GeoReplicatorDestinationUsersModel replicatorDestinationUsers = createReplicatorUserModel(ModelUtils.getServerAuthOnlyExternalListenerSpec());
        assertThat(replicatorDestinationUsers.getConnectKafkaUser(), is(nullValue()));
        assertThat(replicatorDestinationUsers.getConnectExternalKafkaUser(), is(nullValue()));
        assertThat(replicatorDestinationUsers.getTargetConnectorKafkaUser(), is(nullValue()));
    }

    @Test
    public void testReplicatorConnectUserWhenExternalTLSOnlyEnabledWithMutualAuthTLS() {
        GeoReplicatorDestinationUsersModel replicatorDestinationUsers = createReplicatorUserModel(ModelUtils.getMutualTLSOnExternalListenerSpec());

        assertThat(replicatorDestinationUsers.getConnectKafkaUser(), is(nullValue()));

        assertThat(replicatorDestinationUsers.getTargetConnectorKafkaUser(), is(nullValue()));

        KafkaUser replicatorDestinationExternalConnectUser = replicatorDestinationUsers.getConnectExternalKafkaUser();
        assertThat(replicatorDestinationExternalConnectUser.getSpec().getAuthentication(), is(instanceOf(KafkaUserTlsClientAuthentication.class)));
        assertThat(replicatorDestinationExternalConnectUser.getSpec().getAuthorization().getType(), is("simple"));
    }

    @Test
    public void testReplicatorConnectUserWhenExternalTLSOnlyEnabledWithMutualAuthScram() {
        GeoReplicatorDestinationUsersModel replicatorDestinationUsers = createReplicatorUserModel(ModelUtils.getMutualScramOnExternalListenerSpec());

        assertThat(replicatorDestinationUsers.getConnectKafkaUser(), is(nullValue()));
        assertThat(replicatorDestinationUsers.getTargetConnectorKafkaUser(), is(nullValue()));

        KafkaUser replicatorDestinationExternalConnectUser = replicatorDestinationUsers.getConnectExternalKafkaUser();
        assertThat(replicatorDestinationExternalConnectUser.getSpec().getAuthentication(), is(instanceOf(KafkaUserScramSha512ClientAuthentication.class)));
        assertThat(replicatorDestinationExternalConnectUser.getSpec().getAuthorization().getType(), is("simple"));
    }

    @Test
    public void testReplicatorConnectUserWhenNoSecurity() {
        GeoReplicatorDestinationUsersModel replicatorDestinationUsers = createReplicatorUserModel(ModelUtils.getNoSecurityListenerSpec());

        assertThat(replicatorDestinationUsers.getConnectKafkaUser(), is(nullValue()));
        assertThat(replicatorDestinationUsers.getConnectExternalKafkaUser(), is(nullValue()));
        assertThat(replicatorDestinationUsers.getTargetConnectorKafkaUser(), is(nullValue()));
    }

}
