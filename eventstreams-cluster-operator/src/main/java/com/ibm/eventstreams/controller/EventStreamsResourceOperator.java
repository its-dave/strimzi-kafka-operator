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
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsDoneable;
import com.ibm.eventstreams.api.spec.EventStreamsList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.status.KafkaStatus;
import io.strimzi.operator.common.operator.resource.AbstractWatchableResourceOperator;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Optional;

public class EventStreamsResourceOperator extends
        AbstractWatchableResourceOperator<KubernetesClient, EventStreams, EventStreamsList, EventStreamsDoneable, Resource<EventStreams, EventStreamsDoneable>> {

    private static final Logger log = LogManager.getLogger(EventStreamsResourceOperator.class.getName());

    private KubernetesClient client;

    public EventStreamsResourceOperator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, EventStreams.RESOURCE_KIND);
        log.info("Creating EventStreamsResourceOperator");
        this.client = client;
    }

    @Override
    protected MixedOperation<EventStreams, EventStreamsList, EventStreamsDoneable, Resource<EventStreams, EventStreamsDoneable>> operation() {
        return client.customResources(Crds.getCrd(EventStreams.class), EventStreams.class, EventStreamsList.class,
                EventStreamsDoneable.class);

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
        return getKafkaInstance(namespace, name)
                .map(Kafka::getStatus)
                .map(KafkaStatus::getConditions)
                .orElse(Collections.emptyList())
                .stream()
                .anyMatch(condition -> "Ready".equals(condition.getType()) &&
                                       "True".equals(condition.getStatus()));
    }

    /**
     * Returns the instance of kafka for the given namespace and name.
     *
     * @param namespace The namespace.
     * @param kafkaInstanceName The name of the kafka instance.
     * @return Optional of kafka
     */
    public Optional<Kafka> getKafkaInstance(String namespace, String kafkaInstanceName) {
        return io.strimzi.api.kafka.Crds.kafkaOperation(client)
            .inNamespace(namespace)
            .list()
            .getItems()
            .stream()
            .filter(k -> k.getMetadata().getName().equals(kafkaInstanceName))
            .findFirst();
    }
}