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
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeer;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeerBuilder;
import io.strimzi.operator.cluster.model.KafkaCluster;
import io.strimzi.operator.common.model.Labels;

import java.util.ArrayList;
import java.util.List;

public class KafkaNetworkPolicyExtensionModel extends AbstractModel {

    private final NetworkPolicy networkPolicy;

    public KafkaNetworkPolicyExtensionModel(EventStreams instance) {
        super(instance, "kafka", "kafka"); // TODO reference KafkaCluster.APPLICATION_NAME

        setOwnerReference(instance);

        networkPolicy = createNetworkPolicy();
    }

    private NetworkPolicy createNetworkPolicy() {
        List<NetworkPolicyIngressRule> ingressRules = new ArrayList<>();

        // restricted ingress on 8091 (runas) for communication with admin-api, schema-reg and restproducer
        // in addition to the kafka network policies added by Strimzi
        ingressRules.add(generateRunAsIngressRule());

        return createNetworkPolicy(createLabelSelector(getComponentName()), ingressRules, null);
    }

    private NetworkPolicyIngressRule generateRunAsIngressRule() {
        NetworkPolicyPeer adminAPIPodPeer = new NetworkPolicyPeerBuilder()
            .withNewPodSelector()
            .addToMatchLabels(Labels.STRIMZI_NAME_LABEL, AdminApiModel.getDefaultResourceName(getInstanceName(), AdminApiModel.COMPONENT_NAME))
            .endPodSelector()
            .build();

        NetworkPolicyPeer restProducerPodPeer = new NetworkPolicyPeerBuilder()
            .withNewPodSelector()
            .addToMatchLabels(Labels.STRIMZI_NAME_LABEL, AdminApiModel.getDefaultResourceName(getInstanceName(), RestProducerModel.COMPONENT_NAME))
            .endPodSelector()
            .build();

        NetworkPolicyPeer schemaRegistryPodPeer = new NetworkPolicyPeerBuilder()
            .withNewPodSelector()
            .addToMatchLabels(Labels.STRIMZI_NAME_LABEL, AdminApiModel.getDefaultResourceName(getInstanceName(), SchemaRegistryModel.COMPONENT_NAME))
            .endPodSelector()
            .build();

        return new NetworkPolicyIngressRuleBuilder()
            .addNewPort()
            .withNewPort(KafkaCluster.RUNAS_PORT)
            .endPort()
            .addToFrom(adminAPIPodPeer)
            .addToFrom(restProducerPodPeer)
            .addToFrom(schemaRegistryPodPeer)
            .build();
    }

    /**
     * @return NetworkPolicy return the network policy
     */
    public NetworkPolicy getNetworkPolicy() {
        return this.networkPolicy;
    }
}
