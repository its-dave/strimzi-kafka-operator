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
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeer;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeerBuilder;
import io.strimzi.operator.cluster.model.KafkaCluster;
import io.strimzi.operator.common.model.Labels;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

public class KafkaNetworkPolicyExtensionModelTest {

    private static final String INSTANCE_NAME = "test-instance";

    private EventStreamsBuilder createDefaultEventStreams() {
        return ModelUtils.createDefaultEventStreams(INSTANCE_NAME)
            .editSpec()
            .endSpec();
    }

    @Test
    public void testNetworkPolicy() {
        EventStreams eventStreams = createDefaultEventStreams().build();
        KafkaNetworkPolicyExtensionModel kafkaNetworkPolicyExtensionModel = new KafkaNetworkPolicyExtensionModel(eventStreams);

        NetworkPolicy kafkaNetworkPolicyExtensionNetworkPolicy = kafkaNetworkPolicyExtensionModel.getNetworkPolicy();
        assertThat(kafkaNetworkPolicyExtensionNetworkPolicy.getMetadata().getName(), is(kafkaNetworkPolicyExtensionModel.getDefaultResourceName()));
        assertThat(kafkaNetworkPolicyExtensionNetworkPolicy.getKind(), is("NetworkPolicy"));

        assertThat(kafkaNetworkPolicyExtensionNetworkPolicy.getSpec().getIngress(), hasSize(1));
        assertThat(kafkaNetworkPolicyExtensionNetworkPolicy.getSpec().getIngress().get(0).getPorts(), hasSize(1));
        assertThat(kafkaNetworkPolicyExtensionNetworkPolicy.getSpec().getIngress().get(0).getPorts().get(0).getPort().getIntVal(), is(KafkaCluster.RUNAS_PORT));
        assertThat(kafkaNetworkPolicyExtensionNetworkPolicy.getSpec().getIngress().get(0).getFrom(), hasSize(2));

        NetworkPolicyPeer expectedRestProducerPeer = new NetworkPolicyPeerBuilder()
            .withNewPodSelector()
            .addToMatchLabels(Labels.STRIMZI_NAME_LABEL, "test-instance-ibm-es-rest-producer")
            .endPodSelector()
            .build();

        NetworkPolicyPeer expectedAdminAPIPeer = new NetworkPolicyPeerBuilder()
            .withNewPodSelector()
            .addToMatchLabels(Labels.STRIMZI_NAME_LABEL, "test-instance-ibm-es-admin-api")
            .endPodSelector()
            .build();

        assertThat(kafkaNetworkPolicyExtensionNetworkPolicy.getSpec().getIngress().get(0).getFrom(), hasItems(expectedRestProducerPeer, expectedAdminAPIPeer));
    }
}
