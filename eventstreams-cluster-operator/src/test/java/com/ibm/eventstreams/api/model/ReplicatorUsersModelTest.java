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

import com.ibm.eventstreams.api.Labels;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.EventStreamsReplicator;
import com.ibm.eventstreams.api.spec.EventStreamsReplicatorBuilder;
import com.ibm.eventstreams.replicator.ReplicatorCredentials;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@SuppressWarnings({"checkstyle:JavaNCSS", "checkstyle:MethodLength"})

public class ReplicatorUsersModelTest {

    private final String instanceName = "test";
    private final int defaultReplicas = 1;
    private final String namespace = "myproject";


    private ReplicatorCredentials repUtils;

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

    private EventStreamsReplicatorBuilder createDefaultEventStreamsReplicator() {

        return ModelUtils.createDefaultEventStreamsReplicator(instanceName);
    }

    private ReplicatorSecretModel createDefaultReplicatorSecretModel() {
        EventStreams instance = createDefaultEventStreams(ModelUtils.getMutualTLSOnBothInternalAndExternalListenerSpec()).build();
        EventStreamsReplicator replicatorInstance = createDefaultEventStreamsReplicator().build();
        return new ReplicatorSecretModel(instance);
    }

    private ReplicatorDestinationUsersModel createDefaultReplicatorDestinationUserModel() {
        EventStreams instance = createDefaultEventStreams(ModelUtils.getMutualTLSOnBothInternalAndExternalListenerSpec()).build();
        EventStreamsReplicator replicatorInstance = createDefaultEventStreamsReplicator().build();
        return new ReplicatorDestinationUsersModel(replicatorInstance, instance);
    }

    private ReplicatorSourceUsersModel createDefaultReplicatorSourceUserModel() {
        EventStreams instance = createDefaultEventStreams(ModelUtils.getMutualTLSOnBothInternalAndExternalListenerSpec()).build();
        return new ReplicatorSourceUsersModel(instance);
    }

    private ReplicatorSourceUsersModel createReplicatorSourceUserModel(KafkaListeners listenerSpec) {
        EventStreams instance = createDefaultEventStreams(listenerSpec).build();
        return new ReplicatorSourceUsersModel(instance);

    }

    private ReplicatorDestinationUsersModel createReplicatorUserModel(KafkaListeners listenerSpec) {
        EventStreams instance = createDefaultEventStreams(listenerSpec).build();
        EventStreamsReplicator replicatorInstance = createDefaultEventStreamsReplicator().build();
        return new ReplicatorDestinationUsersModel(replicatorInstance, instance);
    }

    @Test
    public void testReplicatorUsersCreatedWithTlsAuthentication() {

        ReplicatorDestinationUsersModel replicatorDestinationUsers = createDefaultReplicatorDestinationUserModel();
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createDefaultReplicatorSourceUserModel();
        ReplicatorSecretModel replicator = createDefaultReplicatorSecretModel();
        KafkaUser connectKafkaUser = replicatorDestinationUsers.getConnectKafkaUser();
        KafkaUser targetConnectorKafkaUser = replicatorDestinationUsers.getTargetConnectorKafkaUser();
        KafkaUser sourceConnectorKafkaUser = replicatorSourceUsersModel.getSourceConnectorKafkaUser();

        assertThat(connectKafkaUser.getMetadata().getName(), is(instanceName + "-" + AbstractModel.APP_NAME + "-" + ReplicatorDestinationUsersModel.CONNECT_KAFKA_USER_NAME));
        assertThat(targetConnectorKafkaUser.getMetadata().getName(), is(instanceName + "-" + AbstractModel.APP_NAME + "-" + ReplicatorDestinationUsersModel.TARGET_CONNECTOR_KAFKA_USER_NAME));
        assertThat(sourceConnectorKafkaUser.getMetadata().getName(), is(instanceName + "-" + AbstractModel.APP_NAME + "-" + ReplicatorSourceUsersModel.SOURCE_CONNECTOR_KAFKA_USER_NAME));

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


        Map<String, String> replicatorSourceConnectorUserLabels = sourceConnectorKafkaUser.getMetadata().getLabels();
        for (Map.Entry<String, String> label : replicatorSourceConnectorUserLabels.entrySet()) {
            if (!label.getKey().equals(io.strimzi.operator.common.model.Labels.STRIMZI_CLUSTER_LABEL)) {
                assertThat(label.getKey(), not(containsString(io.strimzi.operator.common.model.Labels.STRIMZI_DOMAIN)));
            }
        }
        assertThat(replicatorSourceConnectorUserLabels.get(io.strimzi.operator.common.model.Labels.STRIMZI_CLUSTER_LABEL), is(instanceName));

        assertThat(connectKafkaUser.getKind(), is("KafkaUser"));
        assertThat(targetConnectorKafkaUser.getKind(), is("KafkaUser"));
        assertThat(sourceConnectorKafkaUser.getKind(), is("KafkaUser"));

        assertThat(connectKafkaUser.getMetadata().getNamespace(), is(namespace));
        assertThat(targetConnectorKafkaUser.getMetadata().getNamespace(), is(namespace));
        assertThat(sourceConnectorKafkaUser.getMetadata().getNamespace(), is(namespace));

        Map<String, String> replicatorConnectUserSecretLabels = replicator.getSecret().getMetadata().getLabels();
        assertThat(replicatorConnectUserSecretLabels.get(Labels.APP_LABEL), is(AbstractModel.APP_NAME));
        assertThat(replicatorConnectUserSecretLabels.get(Labels.INSTANCE_LABEL), is(instanceName));
        assertThat(replicatorConnectUserSecretLabels.get(Labels.RELEASE_LABEL), is(instanceName));

        Map<String, String> replicatorDestinationConnectorSecretUserLabels = replicator.getSecret().getMetadata().getLabels();
        assertThat(replicatorDestinationConnectorSecretUserLabels.get(Labels.APP_LABEL), is(AbstractModel.APP_NAME));
        assertThat(replicatorDestinationConnectorSecretUserLabels.get(Labels.INSTANCE_LABEL), is(instanceName));
        assertThat(replicatorDestinationConnectorSecretUserLabels.get(Labels.RELEASE_LABEL), is(instanceName));

        Map<String, String> replicatorSourceConnectorUserSecretLabels = replicator.getSecret().getMetadata().getLabels();
        assertThat(replicatorSourceConnectorUserSecretLabels.get(Labels.APP_LABEL), is(AbstractModel.APP_NAME));
        assertThat(replicatorSourceConnectorUserSecretLabels.get(Labels.INSTANCE_LABEL), is(instanceName));
        assertThat(replicatorSourceConnectorUserSecretLabels.get(Labels.RELEASE_LABEL), is(instanceName));

        assertThat(connectKafkaUser.getSpec().getAuthorization().getType(), is("simple"));
        assertThat(targetConnectorKafkaUser.getSpec().getAuthorization().getType(), is("simple"));
        assertThat(sourceConnectorKafkaUser.getSpec().getAuthorization().getType(), is("simple"));
    }


    @Test
    public void testReplicatorConnectUserAcls() {

        ReplicatorDestinationUsersModel replicatorUsers = createDefaultReplicatorDestinationUserModel();
        KafkaUser replicatorConnectUser = replicatorUsers.getConnectKafkaUser();

        //Connect User ACLs
        assertThat(replicatorConnectUser.getSpec().getAuthorization(), instanceOf(KafkaUserAuthorizationSimple.class));
        KafkaUserAuthorizationSimple kafkaUserAuth = (KafkaUserAuthorizationSimple) replicatorConnectUser.getSpec().getAuthorization();
        List<AclRule> acls = kafkaUserAuth.getAcls();
        assertThat(acls.size(), is(19));

        AclRule rule1read = acls.get(0);
        assertThat(rule1read.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule1readTopic = (AclRuleTopicResource) rule1read.getResource();
        assertThat(rule1readTopic.getName(), is(ReplicatorModel.CONFIG_STORAGE_TOPIC_NAME));
        assertThat(rule1readTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule1read.getOperation(), is(AclOperation.READ));
        assertThat(rule1read.getHost(), is("*"));

        AclRule rule1write = acls.get(1);
        assertThat(rule1write.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule1writeTopic = (AclRuleTopicResource) rule1write.getResource();
        assertThat(rule1writeTopic.getName(), is(ReplicatorModel.CONFIG_STORAGE_TOPIC_NAME));
        assertThat(rule1writeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule1write.getOperation(), is(AclOperation.DESCRIBE));
        assertThat(rule1write.getHost(), is("*"));

        AclRule rule1describe = acls.get(2);
        assertThat(rule1describe.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule1describeTopic = (AclRuleTopicResource) rule1describe.getResource();
        assertThat(rule1describeTopic.getName(), is(ReplicatorModel.CONFIG_STORAGE_TOPIC_NAME));
        assertThat(rule1describeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule1describe.getOperation(), is(AclOperation.WRITE));
        assertThat(rule1describe.getHost(), is("*"));

        AclRule rule2read = acls.get(3);
        assertThat(rule2read.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule2readTopic = (AclRuleTopicResource) rule2read.getResource();
        assertThat(rule2readTopic.getName(), is(ReplicatorModel.OFFSET_STORAGE_TOPIC_NAME));
        assertThat(rule2readTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule2read.getOperation(), is(AclOperation.READ));
        assertThat(rule2read.getHost(), is("*"));

        AclRule rule2describe = acls.get(4);
        assertThat(rule2describe.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule2describeTopic = (AclRuleTopicResource) rule2describe.getResource();
        assertThat(rule2describeTopic.getName(), is(ReplicatorModel.OFFSET_STORAGE_TOPIC_NAME));
        assertThat(rule2describeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule2describe.getOperation(), is(AclOperation.DESCRIBE));
        assertThat(rule2describe.getHost(), is("*"));

        AclRule rule2write = acls.get(5);
        assertThat(rule2write.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule2writeTopic = (AclRuleTopicResource) rule2write.getResource();
        assertThat(rule2writeTopic.getName(), is(ReplicatorModel.OFFSET_STORAGE_TOPIC_NAME));
        assertThat(rule2writeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule2write.getOperation(), is(AclOperation.WRITE));
        assertThat(rule2write.getHost(), is("*"));

        AclRule rule3read = acls.get(6);
        assertThat(rule3read.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule3readTopic = (AclRuleTopicResource) rule3read.getResource();
        assertThat(rule3readTopic.getName(), is(ReplicatorModel.STATUS_STORAGE_TOPIC_NAME));
        assertThat(rule3readTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule3read.getOperation(), is(AclOperation.READ));
        assertThat(rule3read.getHost(), is("*"));

        AclRule rule3describe = acls.get(7);
        assertThat(rule3describe.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule3describeTopic = (AclRuleTopicResource) rule3describe.getResource();
        assertThat(rule3describeTopic.getName(), is(ReplicatorModel.STATUS_STORAGE_TOPIC_NAME));
        assertThat(rule3describeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule3describe.getOperation(), is(AclOperation.DESCRIBE));
        assertThat(rule3describe.getHost(), is("*"));

        AclRule rule3write = acls.get(8);
        assertThat(rule3write.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule3writeTopic = (AclRuleTopicResource) rule3write.getResource();
        assertThat(rule3writeTopic.getName(), is(ReplicatorModel.STATUS_STORAGE_TOPIC_NAME));
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
        assertThat(rule5readgroup.getName(), is(ReplicatorModel.getDefaultReplicatorClusterName(instanceName)));
        assertThat(rule5readgroup.getPatternType(), is(AclResourcePatternType.PREFIX));
        assertThat(rule5read.getOperation(), is(AclOperation.READ));
        assertThat(rule5read.getHost(), is("*"));

        AclRule rule5describe = acls.get(12);
        assertThat(rule5describe.getResource(), instanceOf(AclRuleGroupResource.class));
        AclRuleGroupResource rule5describegroup = (AclRuleGroupResource) rule5describe.getResource();
        assertThat(rule5describegroup.getName(), is(ReplicatorModel.getDefaultReplicatorClusterName(instanceName)));
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
        assertThat(configStorageTopicDescribeConfigsTopic.getName(), is(ReplicatorModel.CONFIG_STORAGE_TOPIC_NAME));
        assertThat(configStorageTopicDescribeConfigsTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(configStorageTopicDescribeConfigs.getOperation(), is(AclOperation.DESCRIBECONFIGS));
        assertThat(configStorageTopicDescribeConfigs.getHost(), is("*"));

        AclRule offsetStorageTopicDescribeConfigs = acls.get(17);
        assertThat(offsetStorageTopicDescribeConfigs.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource offsetStorageTopicDescribeConfigsTopic = (AclRuleTopicResource) offsetStorageTopicDescribeConfigs.getResource();
        assertThat(offsetStorageTopicDescribeConfigsTopic.getName(), is(ReplicatorModel.OFFSET_STORAGE_TOPIC_NAME));
        assertThat(offsetStorageTopicDescribeConfigsTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(offsetStorageTopicDescribeConfigs.getOperation(), is(AclOperation.DESCRIBECONFIGS));
        assertThat(offsetStorageTopicDescribeConfigs.getHost(), is("*"));

        AclRule statusStorageTopicDescribeConfigs = acls.get(18);
        assertThat(statusStorageTopicDescribeConfigs.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource statusStorageTopicDescribeConfigsTopic = (AclRuleTopicResource) statusStorageTopicDescribeConfigs.getResource();
        assertThat(statusStorageTopicDescribeConfigsTopic.getName(), is(ReplicatorModel.STATUS_STORAGE_TOPIC_NAME));
        assertThat(statusStorageTopicDescribeConfigsTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(statusStorageTopicDescribeConfigs.getOperation(), is(AclOperation.DESCRIBECONFIGS));
        assertThat(statusStorageTopicDescribeConfigs.getHost(), is("*"));

    }


    @Test
    public void testReplicatorConnectorUserAcls() {

        ReplicatorDestinationUsersModel replicatorDestinationUsers = createDefaultReplicatorDestinationUserModel();
        ReplicatorSourceUsersModel replicatorSourceUsers = createDefaultReplicatorSourceUserModel();
        KafkaUser replicatorDestinationConnectorUser = replicatorDestinationUsers.getTargetConnectorKafkaUser();
        KafkaUser replicatorSourceConnectorUser = replicatorSourceUsers.getSourceConnectorKafkaUser();

        //MM2 to destination Kafka ACL
        assertThat(replicatorDestinationConnectorUser.getSpec().getAuthorization(), instanceOf(KafkaUserAuthorizationSimple.class));
        KafkaUserAuthorizationSimple kafkaDestUserAuth = (KafkaUserAuthorizationSimple) replicatorDestinationConnectorUser.getSpec().getAuthorization();
        List<AclRule> aclsDest = kafkaDestUserAuth.getAcls();
        assertThat(aclsDest.size(), is(2));

        AclRule rule1destcreate = aclsDest.get(0);
        assertThat(rule1destcreate.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule1destcreate.getOperation(), is(AclOperation.CREATE));
        assertThat(rule1destcreate.getHost(), is("*"));

        AclRule rule1destalter = aclsDest.get(1);
        assertThat(rule1destalter.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule1destalter.getOperation(), is(AclOperation.ALTER));
        assertThat(rule1destalter.getHost(), is("*"));

        //MM2 to source Kafka ACL
        assertThat(replicatorSourceConnectorUser.getSpec().getAuthorization(), instanceOf(KafkaUserAuthorizationSimple.class));
        KafkaUserAuthorizationSimple kafkasourceUserAuth = (KafkaUserAuthorizationSimple) replicatorSourceConnectorUser.getSpec().getAuthorization();
        List<AclRule> aclsSource = kafkasourceUserAuth.getAcls();
        assertThat(aclsSource.size(), is(6));

        AclRule rule1sourceread = aclsSource.get(0);
        assertThat(rule1sourceread.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule1sourcereadtopic = (AclRuleTopicResource) rule1sourceread.getResource();
        assertThat(rule1sourcereadtopic.getName(), is("*"));
        assertThat(rule1sourcereadtopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule1sourceread.getOperation(), is(AclOperation.READ));
        assertThat(rule1sourceread.getHost(), is("*"));

        AclRule rule1sourcedescribe = aclsSource.get(1);
        assertThat(rule1sourcedescribe.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule1sourcedescribetopic = (AclRuleTopicResource) rule1sourcedescribe.getResource();
        assertThat(rule1sourcedescribetopic.getName(), is("*"));
        assertThat(rule1sourcedescribetopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule1sourcedescribe.getOperation(), is(AclOperation.DESCRIBE));
        assertThat(rule1sourcedescribe.getHost(), is("*"));

        AclRule rule2sourcecreate = aclsSource.get(2);
        assertThat(rule2sourcecreate.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule2sourcecreate.getOperation(), is(AclOperation.CREATE));
        assertThat(rule2sourcecreate.getHost(), is("*"));

        AclRule rule2sourceread = aclsSource.get(3);
        assertThat(rule2sourceread.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule2sourceread.getOperation(), is(AclOperation.READ));
        assertThat(rule2sourceread.getHost(), is("*"));

        AclRule rule2sourcedescribe = aclsSource.get(4);
        assertThat(rule2sourcedescribe.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule2sourcedescribe.getOperation(), is(AclOperation.DESCRIBE));
        assertThat(rule2sourcedescribe.getHost(), is("*"));

        AclRule rule3source = aclsSource.get(5);
        assertThat(rule3source.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule3sourcetopic = (AclRuleTopicResource) rule3source.getResource();
        assertThat(rule3sourcetopic.getName(), is("mm2-offset-syncs.*"));
        assertThat(rule3sourcetopic.getPatternType(), is(AclResourcePatternType.PREFIX));
        assertThat(rule3source.getOperation(), is(AclOperation.WRITE));
        assertThat(rule3source.getHost(), is("*"));

    }


    @Test
    public void testReplicatorConnectUserWhenInternalTLSOnlyEnabledWithNoMutualAuth() {

        ReplicatorDestinationUsersModel replicatorDestinationUsers =  createReplicatorUserModel(ModelUtils.getServerAuthOnlyInternalListenerSpec());
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createReplicatorSourceUserModel(ModelUtils.getServerAuthOnlyInternalListenerSpec());

        assertThat(replicatorDestinationUsers.getConnectKafkaUser(), is(nullValue()));
        assertThat(replicatorDestinationUsers.getTargetConnectorKafkaUser(), is(nullValue()));
        assertThat(replicatorSourceUsersModel.getSourceConnectorKafkaUser(), is(nullValue()));

    }
    @Test
    public void testReplicatorConnectUserWhenInternalTLSOnlyEnabledWithMutualAuthTLS() {
        ReplicatorDestinationUsersModel replicatorDestinationUsers = createReplicatorUserModel(ModelUtils.getMutualTLSOnInternalListenerSpec());
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createReplicatorSourceUserModel(ModelUtils.getMutualTLSOnInternalListenerSpec());

        assertThat(replicatorSourceUsersModel.getSourceConnectorKafkaUser(), is(nullValue()));

        KafkaUser replicatorDestinationConnectorUser = replicatorDestinationUsers.getTargetConnectorKafkaUser();
        KafkaUser replicatorConnectConnectorUser = replicatorDestinationUsers.getConnectKafkaUser();

        assertThat(replicatorDestinationConnectorUser.getSpec().getAuthentication(), is(instanceOf(KafkaUserTlsClientAuthentication.class)));
        assertThat(replicatorConnectConnectorUser.getSpec().getAuthentication(), is(instanceOf(KafkaUserTlsClientAuthentication.class)));

        assertThat(replicatorConnectConnectorUser.getSpec().getAuthorization().getType(), is("simple"));
        assertThat(replicatorDestinationConnectorUser.getSpec().getAuthorization().getType(), is("simple"));
    }

    @Test
    public void testReplicatorConnectUserWhenInternalTLSOnlyEnabledWithMutualAuthScram() {
        ReplicatorDestinationUsersModel replicatorDestinationUsers = createReplicatorUserModel(ModelUtils.getMutualScramOnInternalListenerSpec());
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createReplicatorSourceUserModel(ModelUtils.getMutualScramOnInternalListenerSpec());

        assertThat(replicatorSourceUsersModel.getSourceConnectorKafkaUser(), is(nullValue()));

        KafkaUser replicatorDestinationConnectorUser = replicatorDestinationUsers.getTargetConnectorKafkaUser();
        KafkaUser replicatorConnectConnectorUser = replicatorDestinationUsers.getConnectKafkaUser();

        assertThat(replicatorDestinationConnectorUser.getSpec().getAuthentication(), is(instanceOf(KafkaUserScramSha512ClientAuthentication.class)));
        assertThat(replicatorConnectConnectorUser.getSpec().getAuthentication(), is(instanceOf(KafkaUserScramSha512ClientAuthentication.class)));

        assertThat(replicatorConnectConnectorUser.getSpec().getAuthorization().getType(), is("simple"));
        assertThat(replicatorDestinationConnectorUser.getSpec().getAuthorization().getType(), is("simple"));
    }

    @Test
    public void testReplicatorConnectUserWhenExternalTLSOnlyEnabledWithNoMutualAuth() {
        ReplicatorDestinationUsersModel replicatorDestinationUsers = createReplicatorUserModel(ModelUtils.getServerAuthOnlyExternalListenerSpec());
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createReplicatorSourceUserModel(ModelUtils.getServerAuthOnlyExternalListenerSpec());

        assertThat(replicatorDestinationUsers.getConnectKafkaUser(), is(nullValue()));
        assertThat(replicatorDestinationUsers.getTargetConnectorKafkaUser(), is(nullValue()));
        assertThat(replicatorSourceUsersModel.getSourceConnectorKafkaUser(), is(nullValue()));

    }
    @Test
    public void testReplicatorConnectUserWhenExternalTLSOnlyEnabledWithMutualAuthTLS() {
        ReplicatorDestinationUsersModel replicatorDestinationUsers = createReplicatorUserModel(ModelUtils.getMutualTLSOnExternalListenerSpec());
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createDefaultReplicatorSourceUserModel();

        assertThat(replicatorDestinationUsers.getConnectKafkaUser(), is(nullValue()));
        assertThat(replicatorDestinationUsers.getTargetConnectorKafkaUser(), is(nullValue()));

        KafkaUser replicatorSourceConnectorUser = replicatorSourceUsersModel.getSourceConnectorKafkaUser();
        assertThat(replicatorSourceConnectorUser.getSpec().getAuthentication(), is(instanceOf(KafkaUserTlsClientAuthentication.class)));

        assertThat(replicatorSourceConnectorUser.getSpec().getAuthorization().getType(), is("simple"));


    }

    @Test
    public void testReplicatorConnectUserWhenExternalTLSOnlyEnabledWithMutualAuthScram() {
        ReplicatorDestinationUsersModel replicatorDestinationUsers = createReplicatorUserModel(ModelUtils.getMutualScramOnExternalListenerSpec());
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createReplicatorSourceUserModel(ModelUtils.getMutualScramOnExternalListenerSpec());

        assertThat(replicatorDestinationUsers.getConnectKafkaUser(), is(nullValue()));
        assertThat(replicatorDestinationUsers.getTargetConnectorKafkaUser(), is(nullValue()));

        KafkaUser replicatorSourceConnectorUser = replicatorSourceUsersModel.getSourceConnectorKafkaUser();

        assertThat(replicatorSourceConnectorUser.getSpec().getAuthentication(), is(instanceOf(KafkaUserScramSha512ClientAuthentication.class)));

        assertThat(replicatorSourceConnectorUser.getSpec().getAuthorization().getType(), is("simple"));

    }

    @Test
    public void testReplicatorConnectUserWhenNoSecurity() {
        ReplicatorDestinationUsersModel replicatorDestinationUsers = createReplicatorUserModel(ModelUtils.getNoSecurityListenerSpec());
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createReplicatorSourceUserModel(ModelUtils.getNoSecurityListenerSpec());

        assertThat(replicatorDestinationUsers.getConnectKafkaUser(), is(nullValue()));
        assertThat(replicatorDestinationUsers.getTargetConnectorKafkaUser(), is(nullValue()));
        assertThat(replicatorSourceUsersModel.getSourceConnectorKafkaUser(), is(nullValue()));

    }

}
