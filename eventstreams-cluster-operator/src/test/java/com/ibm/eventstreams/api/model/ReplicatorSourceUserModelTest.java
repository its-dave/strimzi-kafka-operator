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
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.strimzi.api.kafka.model.AclOperation;
import io.strimzi.api.kafka.model.AclResourcePatternType;
import io.strimzi.api.kafka.model.AclRule;
import io.strimzi.api.kafka.model.AclRuleClusterResource;
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

public class ReplicatorSourceUserModelTest {

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

    private ReplicatorSecretModel createDefaultReplicatorSecretModel() {
        EventStreams instance = createDefaultEventStreams(ModelUtils.getMutualTLSOnBothInternalAndExternalListenerSpec()).build();
        return new ReplicatorSecretModel(instance);
    }

    private ReplicatorSourceUsersModel createDefaultReplicatorSourceUserModel() {
        EventStreams instance = createDefaultEventStreams(ModelUtils.getMutualTLSOnBothInternalAndExternalListenerSpec()).build();
        return new ReplicatorSourceUsersModel(instance);
    }

    private ReplicatorSourceUsersModel createReplicatorSourceUserModel(KafkaListeners listenerSpec) {
        EventStreams instance = createDefaultEventStreams(listenerSpec).build();
        return new ReplicatorSourceUsersModel(instance);
    }

    @Test
    public void testReplicatorUserCreatedWithTlsAuthentication() {
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createDefaultReplicatorSourceUserModel();
        KafkaUser sourceConnectorKafkaUser = replicatorSourceUsersModel.getSourceConnectorKafkaUser();

        assertThat(sourceConnectorKafkaUser.getMetadata().getName(), is(instanceName + "-" + AbstractModel.APP_NAME + "-" + ReplicatorSourceUsersModel.SOURCE_CONNECTOR_KAFKA_USER_NAME));

        Map<String, String> replicatorSourceConnectorUserLabels = sourceConnectorKafkaUser.getMetadata().getLabels();
        for (Map.Entry<String, String> label : replicatorSourceConnectorUserLabels.entrySet()) {
            if (!label.getKey().equals(io.strimzi.operator.common.model.Labels.STRIMZI_CLUSTER_LABEL)) {
                assertThat(label.getKey(), not(containsString(io.strimzi.operator.common.model.Labels.STRIMZI_DOMAIN)));
            }
        }
        assertThat(replicatorSourceConnectorUserLabels.get(io.strimzi.operator.common.model.Labels.STRIMZI_CLUSTER_LABEL), is(instanceName));
        assertThat(sourceConnectorKafkaUser.getKind(), is("KafkaUser"));
        assertThat(sourceConnectorKafkaUser.getMetadata().getNamespace(), is(namespace));
        assertThat(sourceConnectorKafkaUser.getSpec().getAuthorization().getType(), is("simple"));
    }

    @Test
    public void testReplicatorSourceConnectorUserAcls() {
        ReplicatorSourceUsersModel replicatorSourceUsers = createDefaultReplicatorSourceUserModel();
        KafkaUser replicatorSourceConnectorUser = replicatorSourceUsers.getSourceConnectorKafkaUser();

        //MM2 to source Kafka ACL
        assertThat(replicatorSourceConnectorUser.getSpec().getAuthorization(), instanceOf(KafkaUserAuthorizationSimple.class));
        KafkaUserAuthorizationSimple kafkasourceUserAuth = (KafkaUserAuthorizationSimple) replicatorSourceConnectorUser.getSpec().getAuthorization();
        List<AclRule> aclsSource = kafkasourceUserAuth.getAcls();
        assertThat(aclsSource.size(), is(7));

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

        AclRule rule1sourcedescribeconfig = aclsSource.get(2);
        assertThat(rule1sourcedescribeconfig.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule1sourcedescribetopicconfig = (AclRuleTopicResource) rule1sourcedescribeconfig.getResource();
        assertThat(rule1sourcedescribetopicconfig.getName(), is("*"));
        assertThat(rule1sourcedescribetopicconfig.getPatternType(), is(AclResourcePatternType.LITERAL));
        assertThat(rule1sourcedescribeconfig.getOperation(), is(AclOperation.DESCRIBECONFIGS));
        assertThat(rule1sourcedescribeconfig.getHost(), is("*"));

        AclRule rule2sourcecreate = aclsSource.get(3);
        assertThat(rule2sourcecreate.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule2sourcecreate.getOperation(), is(AclOperation.CREATE));
        assertThat(rule2sourcecreate.getHost(), is("*"));

        AclRule rule2sourceread = aclsSource.get(4);
        assertThat(rule2sourceread.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule2sourceread.getOperation(), is(AclOperation.READ));
        assertThat(rule2sourceread.getHost(), is("*"));

        AclRule rule2sourcedescribe = aclsSource.get(5);
        assertThat(rule2sourcedescribe.getResource(), instanceOf(AclRuleClusterResource.class));
        assertThat(rule2sourcedescribe.getOperation(), is(AclOperation.DESCRIBE));
        assertThat(rule2sourcedescribe.getHost(), is("*"));

        AclRule rule3source = aclsSource.get(6);
        assertThat(rule3source.getResource(), instanceOf(AclRuleTopicResource.class));
        AclRuleTopicResource rule3sourcetopic = (AclRuleTopicResource) rule3source.getResource();
        assertThat(rule3sourcetopic.getName(), is("mm2-offset-syncs."));
        assertThat(rule3sourcetopic.getPatternType(), is(AclResourcePatternType.PREFIX));
        assertThat(rule3source.getOperation(), is(AclOperation.WRITE));
        assertThat(rule3source.getHost(), is("*"));
    }


    @Test
    public void testReplicatorConnectSourceUserWhenInternalTLSOnlyEnabledWithNoMutualAuth() {
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createReplicatorSourceUserModel(ModelUtils.getServerAuthOnlyInternalListenerSpec());
        assertThat(replicatorSourceUsersModel.getSourceConnectorKafkaUser(), is(nullValue()));
    }

    @Test
    public void testReplicatorConnectSourceUserWhenInternalTLSOnlyEnabledWithMutualAuthTLS() {
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createReplicatorSourceUserModel(ModelUtils.getMutualTLSOnInternalListenerSpec());
        assertThat(replicatorSourceUsersModel.getSourceConnectorKafkaUser(), is(nullValue()));
    }

    @Test
    public void testReplicatorConnectSourceUserWhenInternalTLSOnlyEnabledWithMutualAuthScram() {
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createReplicatorSourceUserModel(ModelUtils.getMutualScramOnInternalListenerSpec());
        assertThat(replicatorSourceUsersModel.getSourceConnectorKafkaUser(), is(nullValue()));
    }

    @Test
    public void testReplicatorConnecSourcetUserWhenExternalTLSOnlyEnabledWithNoMutualAuth() {
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createReplicatorSourceUserModel(ModelUtils.getServerAuthOnlyExternalListenerSpec());
        assertThat(replicatorSourceUsersModel.getSourceConnectorKafkaUser(), is(nullValue()));
    }

    @Test
    public void testReplicatorConnecSourcetUserWhenExternalTLSOnlyEnabledWithMutualAuthTLS() {
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createDefaultReplicatorSourceUserModel();
        KafkaUser replicatorSourceConnectorUser = replicatorSourceUsersModel.getSourceConnectorKafkaUser();
        assertThat(replicatorSourceConnectorUser.getSpec().getAuthentication(), is(instanceOf(KafkaUserTlsClientAuthentication.class)));
        assertThat(replicatorSourceConnectorUser.getSpec().getAuthorization().getType(), is("simple"));
    }

    @Test
    public void testReplicatorConnectUserWhenExternalTLSOnlyEnabledWithMutualAuthScram() {
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createReplicatorSourceUserModel(ModelUtils.getMutualScramOnExternalListenerSpec());
        KafkaUser replicatorSourceConnectorUser = replicatorSourceUsersModel.getSourceConnectorKafkaUser();
        assertThat(replicatorSourceConnectorUser.getSpec().getAuthentication(), is(instanceOf(KafkaUserScramSha512ClientAuthentication.class)));
        assertThat(replicatorSourceConnectorUser.getSpec().getAuthorization().getType(), is("simple"));
    }

    @Test
    public void testReplicatorConnectUserWhenNoSecurity() {
        ReplicatorSourceUsersModel replicatorSourceUsersModel = createReplicatorSourceUserModel(ModelUtils.getNoSecurityListenerSpec());
        assertThat(replicatorSourceUsersModel.getSourceConnectorKafkaUser(), is(nullValue()));
    }

}
