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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.CoreMatchers.instanceOf;

import java.util.HashMap;
import java.util.Map;
import java.util.Base64.Encoder;
import java.util.Base64;
import java.util.List;
import java.nio.charset.StandardCharsets;

import com.ibm.eventstreams.Main;
import com.ibm.eventstreams.api.model.utils.ModelUtils;

import com.ibm.eventstreams.api.Labels;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.ReplicatorSpecBuilder;

import io.strimzi.api.kafka.model.KafkaConnect;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserAuthorizationSimple;
import io.strimzi.api.kafka.model.AclRule;
import io.strimzi.api.kafka.model.AclRuleTopicResource;
import io.strimzi.api.kafka.model.AclResourcePatternType;
import io.strimzi.api.kafka.model.AclOperation;
import io.strimzi.api.kafka.model.AclRuleClusterResource;
import io.strimzi.api.kafka.model.AclRuleGroupResource;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.Secret;

public class ReplicatorModelTest {

    private final String instanceName = "test";
    private final String componentPrefix = instanceName + "-" + AbstractModel.APP_NAME + "-" + ReplicatorModel.COMPONENT_NAME;
    private final int defaultReplicas = 1;
    private final String  kafkaInstanceName = EventStreamsKafkaModel.getKafkaInstanceName(instanceName);
    private final String namespace = "myproject";
    private final String bootstrap = kafkaInstanceName + "-kafka-bootstrap." + namespace + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.KAFKA_PORT;
    private final int numberOfConnectorTopics = 3;

     
    private KafkaConnect createDefaultReplicator() {
       
        return  createDefaultReplicatorModel().getReplicator();
    
    }

    private EventStreamsBuilder createDefaultEventStreams() {
        return ModelUtils.createDefaultEventStreams(instanceName)
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(instanceName)
                        .withNewNamespace(namespace)
                        .build())
                .editSpec()
                    .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                            .withReplicas(3)
                        .withNewTemplate()
                        .withNewPod()
                        .withNewMetadata()
                        .endMetadata()
                        .endPod()
                        .endTemplate()
                        .endKafka()
                        .build()
                    )
                    .withNewReplicator()
                        .withReplicas(defaultReplicas)
                    .endReplicator()
                .endSpec();
    }

    private ReplicatorModel createDefaultReplicatorModel() {
        EventStreams instance = createDefaultEventStreams().build();
        return new ReplicatorModel(instance);
    }

    @Test
    public void testDefaultReplicatorIsCreated() {
        KafkaConnect replicator = createDefaultReplicator();

        assertThat(replicator.getKind(), is("KafkaConnect"));
        assertThat(replicator.getApiVersion(), is("kafka.strimzi.io/v1beta1"));

        assertThat(replicator.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(replicator.getSpec().getReplicas(), is(defaultReplicas));
        assertThat(replicator.getSpec().getBootstrapServers(), is(bootstrap));
        assertThat(replicator.getSpec().getConfig().get("config.storage.replication.factor"), is(numberOfConnectorTopics));
        assertThat(replicator.getSpec().getConfig().get("offset.storage.replication.factor"), is(numberOfConnectorTopics));
        assertThat(replicator.getSpec().getConfig().get("status.storage.replication.factor"), is(numberOfConnectorTopics));
    }

    @Test
    public void testDefaultReplicatorHasRequiredLabels() {
        KafkaConnect replicator = createDefaultReplicator();

        Map<String, String> replicatorPodLabels = replicator.getSpec()
                .getTemplate()
                .getPod()
                .getMetadata()
                .getLabels();

        assertThat(replicatorPodLabels.get(Labels.APP_LABEL),  is(AbstractModel.APP_NAME));
        assertThat(replicatorPodLabels.get(Labels.SERVICE_SELECTOR_LABEL),  is(ReplicatorModel.COMPONENT_NAME));
        assertThat(replicatorPodLabels.get(Labels.INSTANCE_LABEL),  is(instanceName));
        assertThat(replicatorPodLabels.get(Labels.RELEASE_LABEL),  is(instanceName));
    }


    @Test
    public void testDefaultReplicatorHasRequiredMeteringAnnotations() {
        KafkaConnect replicator = createDefaultReplicator();

        Map<String, String> replicatorPodAnnotations = replicator.getSpec().getTemplate().getPod()
                .getMetadata().getAnnotations();
       
        assertThat(replicatorPodAnnotations.get("productID"),  is("ID"));
        assertThat(replicatorPodAnnotations.get("cloudpakId"),  is("c8b82d189e7545f0892db9ef2731b90d"));
        assertThat(replicatorPodAnnotations.get("productChargedContainers"),  is("replicator"));
        assertThat(replicatorPodAnnotations.get("prometheus.io/port"),  is("8081"));

    }


    @Test
    public void testReplicatorWithCustomLabels() {
        String customBootstrap = "custom-bootstrap";
        int customReplicas = 2;
        Map<String, Object> kafkaConnectConfig = new HashMap<>();
        kafkaConnectConfig.put("config.storage.replication.factor", 2);
        kafkaConnectConfig.put("offset.storage.replication.factor", 2);
        kafkaConnectConfig.put("status.storage.replication.factor", 2);
        kafkaConnectConfig.put("key.converter2", "org.apache.kafka.connect.converters.ByteArrayConverter2");
        kafkaConnectConfig.put("value.converter2", "org.apache.kafka.connect.converters.ByteArrayConverter2");
        


        EventStreams instance = createDefaultEventStreams()
            .editSpec()
                .withReplicator(new ReplicatorSpecBuilder()
                    .withBootstrapServers(customBootstrap)
                    .withReplicas(customReplicas)
                .build())
            .endSpec().build();

        
        assertThat(instance.getSpec().getReplicator().getBootstrapServers(),  is(customBootstrap));
        assertThat(instance.getSpec().getReplicator().getReplicas(), is(customReplicas));
        
    }


    @Test
    public void testDefaultReplicatorNetworkPolicy() {
        
        ReplicatorModel replicator = createDefaultReplicatorModel();
        NetworkPolicy networkPolicy = replicator.getNetworkPolicy();

        String expectedNetworkPolicyName = componentPrefix + "-network-policy";

        String expectedClusterOperatorName = "cluster-operator";

        assertThat(networkPolicy.getMetadata().getName(), is(expectedNetworkPolicyName));
        assertThat(networkPolicy.getKind(), is("NetworkPolicy"));

        assertThat(networkPolicy.getSpec().getEgress().size(), is(0));
        assertThat(networkPolicy.getSpec().getIngress().size(), is(1));

        assertThat(networkPolicy.getSpec().getIngress().get(0).getPorts().size(), is(1));
        assertThat(networkPolicy.getSpec().getIngress().get(0).getPorts().get(0).getPort().getIntVal(), is(ReplicatorModel.REPLICATOR_PORT));
        assertThat(networkPolicy.getSpec().getIngress().get(0).getFrom().size(), is(2));
        assertThat(networkPolicy.getSpec().getIngress().get(0).getFrom().get(0).getPodSelector().getMatchLabels().size(), is(1));

        assertThat(networkPolicy.getSpec().getIngress().get(0).getFrom().get(0).getPodSelector().getMatchLabels().get(Labels.COMPONENT_LABEL), is(AdminApiModel.COMPONENT_NAME));    
        assertThat(networkPolicy.getSpec().getIngress().get(0).getFrom().get(1).getPodSelector().getMatchLabels().size(), is(1));

        assertThat(networkPolicy.getSpec().getIngress().get(0).getFrom().get(1).getPodSelector().getMatchLabels().get(io.strimzi.operator.common.model.Labels.STRIMZI_KIND_LABEL), is(expectedClusterOperatorName));
        assertThat(networkPolicy.getSpec().getIngress().get(0).getFrom().get(1).getNamespaceSelector().getMatchExpressions().size(), is(0));

    }

    @Test
    public void testDefaultReplicatorSecret() {
        
        ReplicatorModel replicator = createDefaultReplicatorModel();
        Secret replicatorSecret = replicator.getSecret();

        assertThat(replicatorSecret.getMetadata().getName(), is(instanceName + "-" + AbstractModel.APP_NAME + "-"  + ReplicatorModel.REPLICATOR_SECRET_NAME));
        assertThat(replicatorSecret.getKind(), is("Secret"));
        assertThat(replicatorSecret.getMetadata().getNamespace(), is(namespace));

        Encoder encoder = Base64.getEncoder();
        assertThat(replicatorSecret.getData().get(ReplicatorModel.REPLICATOR_SECRET_KEY_NAME), is(encoder.encodeToString("[]".getBytes(StandardCharsets.UTF_8))));

        Map<String, String> replicatorSecretLabels = replicator.getSecret().getMetadata().getLabels();

        assertThat(replicatorSecretLabels.get(Labels.APP_LABEL),  is(AbstractModel.APP_NAME));
        assertThat(replicatorSecretLabels.get(Labels.INSTANCE_LABEL),  is(instanceName));
        assertThat(replicatorSecretLabels.get(Labels.RELEASE_LABEL),  is(instanceName));

    }

    @Test
    public void testReplicatorUsersCreated() {

        ReplicatorModel replicator = createDefaultReplicatorModel();
        KafkaUser replicatorConnectUser = replicator.getReplicatorConnectUser();
        KafkaUser replicatorDestinationConnectorUser = replicator.getReplicatorDestinationConnectorUser();
        KafkaUser replicatorSourceConnectorUser = replicator.getReplicatorSourceConnectorUser();

        assertThat(replicatorConnectUser.getMetadata().getName(), is(instanceName + "-" + AbstractModel.APP_NAME + "-" + ReplicatorModel.REPLICATOR_CONNECT_USER_NAME));
        assertThat(replicatorDestinationConnectorUser.getMetadata().getName(), is(instanceName + "-" + AbstractModel.APP_NAME + "-" + ReplicatorModel.REPLICATOR_DESTINATION_CLUSTER_CONNNECTOR_USER_NAME));
        assertThat(replicatorSourceConnectorUser.getMetadata().getName(), is(instanceName + "-" + AbstractModel.APP_NAME + "-" + ReplicatorModel.REPLICATOR_SOURCE_CLUSTER_CONNECTOR_USER_NAME));

        assertThat(replicatorConnectUser.getKind(), is("KafkaUser"));
        assertThat(replicatorDestinationConnectorUser.getKind(), is("KafkaUser"));
        assertThat(replicatorSourceConnectorUser.getKind(), is("KafkaUser"));

        assertThat(replicatorConnectUser.getMetadata().getNamespace(), is(namespace));
        assertThat(replicatorDestinationConnectorUser.getMetadata().getNamespace(), is(namespace));
        assertThat(replicatorSourceConnectorUser.getMetadata().getNamespace(), is(namespace));

        Map<String, String> replicatorConnectUserLabels = replicator.getSecret().getMetadata().getLabels();
        assertThat(replicatorConnectUserLabels.get(Labels.APP_LABEL), is(AbstractModel.APP_NAME));
        assertThat(replicatorConnectUserLabels.get(Labels.INSTANCE_LABEL), is(instanceName));
        assertThat(replicatorConnectUserLabels.get(Labels.RELEASE_LABEL), is(instanceName));

        Map<String, String> replicatorDestinationConnectorUserLabels = replicator.getSecret().getMetadata().getLabels();
        assertThat(replicatorDestinationConnectorUserLabels.get(Labels.APP_LABEL), is(AbstractModel.APP_NAME));
        assertThat(replicatorDestinationConnectorUserLabels.get(Labels.INSTANCE_LABEL), is(instanceName));
        assertThat(replicatorDestinationConnectorUserLabels.get(Labels.RELEASE_LABEL), is(instanceName));

        Map<String, String> replicatorSourceConnectorUserLabels = replicator.getSecret().getMetadata().getLabels();
        assertThat(replicatorSourceConnectorUserLabels.get(Labels.APP_LABEL), is(AbstractModel.APP_NAME));
        assertThat(replicatorSourceConnectorUserLabels.get(Labels.INSTANCE_LABEL), is(instanceName));
        assertThat(replicatorSourceConnectorUserLabels.get(Labels.RELEASE_LABEL), is(instanceName));

        assertThat(replicatorConnectUser.getSpec().getAuthorization().getType(), is("simple"));
        assertThat(replicatorDestinationConnectorUser.getSpec().getAuthorization().getType(), is("simple"));
        assertThat(replicatorSourceConnectorUser.getSpec().getAuthorization().getType(), is("simple"));
    }


    @Test
    public void testReplicatorConnectUserAcls() {

        ReplicatorModel replicator = createDefaultReplicatorModel();
        KafkaUser replicatorConnectUser = replicator.getReplicatorConnectUser();

        //Connect User ACLs
        assertThat(replicatorConnectUser.getSpec().getAuthorization(), instanceOf(KafkaUserAuthorizationSimple.class));
        KafkaUserAuthorizationSimple kafkaUserAuth = (KafkaUserAuthorizationSimple) replicatorConnectUser.getSpec().getAuthorization();
        List<AclRule> acls = kafkaUserAuth.getAcls();
        assertThat(acls.size(), is(9));

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
        assertThat(rule1write.getOperation(), is(AclOperation.WRITE));
        assertThat(rule1write.getHost(), is("*"));

        AclRule rule2read = acls.get(2);
        assertThat(rule2read.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule2readTopic = (AclRuleTopicResource) rule2read.getResource();
        assertThat(rule2readTopic.getName(), is(ReplicatorModel.OFFSET_STORAGE_TOPIC_NAME));
        assertThat(rule2readTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule2read.getOperation(), is(AclOperation.READ));
        assertThat(rule2read.getHost(), is("*"));

        AclRule rule2write = acls.get(3);
        assertThat(rule2write.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule2writeTopic = (AclRuleTopicResource) rule2write.getResource();
        assertThat(rule2writeTopic.getName(), is(ReplicatorModel.OFFSET_STORAGE_TOPIC_NAME));
        assertThat(rule2writeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule2write.getOperation(), is(AclOperation.WRITE));
        assertThat(rule2write.getHost(), is("*"));

        AclRule rule3read = acls.get(4);
        assertThat(rule3read.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule3readTopic = (AclRuleTopicResource) rule3read.getResource();
        assertThat(rule3readTopic.getName(), is(ReplicatorModel.STATUS_STORAGE_TOPIC_NAME));
        assertThat(rule3readTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule3read.getOperation(), is(AclOperation.READ));
        assertThat(rule3read.getHost(), is("*"));

        AclRule rule3write = acls.get(5);
        assertThat(rule3write.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule3writeTopic = (AclRuleTopicResource) rule3write.getResource();
        assertThat(rule3writeTopic.getName(), is(ReplicatorModel.STATUS_STORAGE_TOPIC_NAME));
        assertThat(rule3writeTopic.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule3write.getOperation(), is(AclOperation.WRITE));
        assertThat(rule3write.getHost(), is("*"));

        AclRule rule4 = acls.get(6);
        assertThat(rule4.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule4.getOperation(), is(AclOperation.CREATE));
        assertThat(rule4.getHost(), is("*"));

        AclRule rule5 = acls.get(7);
        assertThat(rule5.getResource(), instanceOf(AclRuleGroupResource.class));
        AclRuleGroupResource rule5group = (AclRuleGroupResource) rule5.getResource();
        assertThat(rule5group.getName(), is("*"));
        assertThat(rule5group.getPatternType(), is(AclResourcePatternType.PREFIX));
        assertThat(rule5.getOperation(), is(AclOperation.READ));
        assertThat(rule5.getHost(), is("*"));

        AclRule rule6 = acls.get(8);
        assertThat(rule6.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule6topic = (AclRuleTopicResource) rule6.getResource();
        assertThat(rule6topic.getName(), is("*"));
        assertThat(rule6topic.getPatternType(), is(AclResourcePatternType.PREFIX));
        assertThat(rule6.getOperation(), is(AclOperation.WRITE));
        assertThat(rule6.getHost(), is("*"));

    }


    @Test
    public void testReplicatorConnectorUserAcls() {

        ReplicatorModel replicator = createDefaultReplicatorModel();
        KafkaUser replicatorDestinationConnectorUser = replicator.getReplicatorDestinationConnectorUser();
        KafkaUser replicatorSourceConnectorUser = replicator.getReplicatorSourceConnectorUser();

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
        assertThat(aclsSource.size(), is(4));

        AclRule rule1source = aclsSource.get(0);
        assertThat(rule1source.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule1sourcetopic = (AclRuleTopicResource) rule1source.getResource();
        assertThat(rule1sourcetopic.getName(), is("*"));
        assertThat(rule1sourcetopic.getPatternType(), is(AclResourcePatternType.PREFIX));
        assertThat(rule1source.getOperation(), is(AclOperation.READ));
        assertThat(rule1source.getHost(), is("*"));

        AclRule rule2sourcecreate = aclsSource.get(1);
        assertThat(rule2sourcecreate.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule2sourcecreate.getOperation(), is(AclOperation.CREATE));
        assertThat(rule2sourcecreate.getHost(), is("*"));

        AclRule rule2sourceread = aclsSource.get(2);
        assertThat(rule2sourceread.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule2sourceread.getOperation(), is(AclOperation.READ));
        assertThat(rule2sourceread.getHost(), is("*"));

        AclRule rule3source = aclsSource.get(3);
        assertThat(rule3source.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule3sourcetopic = (AclRuleTopicResource) rule3source.getResource();
        assertThat(rule3sourcetopic.getName(), is("mm2-offset-syncs.*"));
        assertThat(rule3sourcetopic.getPatternType(), is(AclResourcePatternType.PREFIX));
        assertThat(rule3source.getOperation(), is(AclOperation.WRITE));
        assertThat(rule3source.getHost(), is("*"));

    }
}