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
package com.ibm.eventstreams.controller;

import com.ibm.eventstreams.api.Crds;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.KafkaList;
import io.strimzi.api.kafka.model.DoneableKafka;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.status.KafkaStatus;
import io.strimzi.operator.common.operator.resource.AbstractWatchableResourceOperator;
import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Optional;

public class KafkaResourceOperator extends
        AbstractWatchableResourceOperator<KubernetesClient, Kafka, KafkaList, DoneableKafka, Resource<Kafka, DoneableKafka>> {

    private static final Logger log = LogManager.getLogger(KafkaResourceOperator.class.getName());

    private KubernetesClient client;

    private CrdOperator<KubernetesClient, Kafka, KafkaList, DoneableKafka> operations;

    public KafkaResourceOperator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, Kafka.RESOURCE_KIND);
        log.info("Creating EventStreamsResourceOperator");
        this.client = client;
        this.operations = new CrdOperator<>(vertx, client, Kafka.class, KafkaList.class, DoneableKafka.class);
    }

    @Override
    protected MixedOperation<Kafka, KafkaList, DoneableKafka, Resource<Kafka, DoneableKafka>> operation() {
        return client.customResources(Crds.getCrd(Kafka.class), Kafka.class, KafkaList.class,
                DoneableKafka.class);
    }

    /**
     * Succeeds when the Kafka Custom Resource is ready.
     *
     * @param namespace     Namespace.
     * @param name          Name of the Kafka instance.
     * @param pollIntervalMs    Interval in which we poll.
     * @param timeoutMs     Timeout.
     * @return A future that succeeds when the Kafka Custom Resource is ready.
     */
    public Future<Void> kafkaCRHasReadyStatus(String namespace, String name, long pollIntervalMs, long timeoutMs) {
        return waitFor(namespace, name, pollIntervalMs, timeoutMs, this::isKafkaCRReady);
    }

    /**
     * Checks if the Kafka Custom Resource is ready.
     *
     * @param namespace The namespace.
     * @param name The name of the Kafka instance.
     * @return Whether the Kafka Custom Resource is ready.
     */
    boolean isKafkaCRReady(String namespace, String name) {
        return Optional.ofNullable(operations.get(namespace, name))
                .map(Kafka::getStatus)
                .map(KafkaStatus::getConditions)
                // if no conditions found then use empty list to trivially return false
                .orElse(Collections.emptyList())
                .stream()
                .anyMatch(condition -> condition.getType().equals("Ready") &&
                                       condition.getStatus().equals("True"));
    }
}