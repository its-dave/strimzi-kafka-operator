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
package com.ibm.eventstreams.controller;

import io.strimzi.api.kafka.Crds;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.KafkaUserList;
import io.strimzi.api.kafka.model.DoneableKafkaUser;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.api.kafka.model.status.ConditionBuilder;
import io.strimzi.api.kafka.model.status.KafkaUserStatus;
import io.strimzi.operator.common.operator.resource.AbstractResourceOperator;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Optional;

public class KafkaUserOperator extends
        AbstractResourceOperator<KubernetesClient, KafkaUser, KafkaUserList, DoneableKafkaUser, Resource<KafkaUser, DoneableKafkaUser>> {

    private static final Logger log = LogManager.getLogger(KafkaUserOperator.class.getName());

    private KubernetesClient client;

    public KafkaUserOperator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, KafkaUser.RESOURCE_KIND);
        log.info("Creating KafkaUserResourceOperator");
        this.client = client;
    }

    /**
     * Succeeds when the user operator deploying the user resource
     *
     * This can be because it is now in a successful ready state, or
     * because the deploy has failed and is now in an irrecoverable error
     * state.
     *
     * @param namespace     Namespace.
     * @param name          Name of the Kafka user instance.
     * @param pollIntervalMs    Interval in which we poll.
     * @param timeoutMs     Timeout.
     * @return A future that succeeds when the Kafka User Resource is ready.
     */
    public Future<Void> kafkaUserHasStoppedDeploying(String namespace, String name, long pollIntervalMs, long timeoutMs) {
        return waitFor(namespace, name, pollIntervalMs, timeoutMs, this::hasKafkaUserStoppedDeploying);
    }

    /**
     * Checks if the Kafka User Resource is still deploying.
     *
     * It may have stopped deploying because it has finished and
     * is now in a healthy ready state, or because it has failed
     * and is in an irrecoverable error state.
     *
     * @param namespace The namespace.
     * @param name The name of the Kafka user.
     * @return true if the Kafka User Resource is no longer being
     *  deployed (either because it is ready or has failed), or
     *  false if the deploy is still in progress.
     */
    boolean hasKafkaUserStoppedDeploying(String namespace, String name) {
        final String deployingReason = "Creating";
        final String deployingType = "NotReady";
        final String deployingStatus = "True";

        // while the Kafka User is deploying, it will have a condition with
        //  these attributes - the presence of this is an indicator that
        //  we should continue to wait
        Condition defaultDeploying = new ConditionBuilder()
            .withReason(deployingReason)
            .withType(deployingType)
            .withStatus(deployingStatus)
            .build();

        return getKafkaUser(namespace, name)
            .map(KafkaUser::getStatus)
            .map(KafkaUserStatus::getConditions)
            // There is a small race condition when the CR is created that
            // means there won't be a status, or a status with no conditions.
            // We assume that we can treat no-conditions as indicating a
            // start up state, and that it's safe to assume there will be an
            // explicit error condition when in an error state.
            .orElse(Collections.singletonList(defaultDeploying))
            .stream()
            // if none of the conditions are a DEPLOYING condition, then
            // we must have stopped deploying
            .noneMatch(condition -> deployingReason.equals(condition.getReason()) &&
                deployingType.equals(condition.getType()) &&
                deployingStatus.equals(condition.getStatus()));
    }

    /**
     * Returns the instance of kafka user for the given namespace and name.
     *
     * @param namespace The namespace.
     * @param kafkaUserName The name of the kafka user instance.
     * @return Optional of kafka user
     */
    public Optional<KafkaUser> getKafkaUser(String namespace, String kafkaUserName) {
        return io.strimzi.api.kafka.Crds.kafkaUserOperation(client)
            .inNamespace(namespace)
            .list()
            .getItems()
            .stream()
            .filter(k -> k.getMetadata().getName().equals(kafkaUserName))
            .findFirst();
    }

    @Override
    protected MixedOperation<KafkaUser, KafkaUserList, DoneableKafkaUser, Resource<KafkaUser, DoneableKafkaUser>> operation() {
        return client.customResources(Crds.kafkaUser(), KafkaUser.class, KafkaUserList.class,
                DoneableKafkaUser.class);
    }
}