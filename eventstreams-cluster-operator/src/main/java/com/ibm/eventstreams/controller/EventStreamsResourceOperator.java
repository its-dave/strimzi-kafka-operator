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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.eventstreams.api.Crds;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsDoneable;
import com.ibm.eventstreams.api.spec.EventStreamsList;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.OperationSupport;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.api.kafka.model.status.ConditionBuilder;
import io.strimzi.api.kafka.model.status.KafkaStatus;
import io.strimzi.operator.common.operator.resource.AbstractWatchableResourceOperator;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
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
     * Succeeds when the cluster operator verticle has finished deploying
     * the Kafka Custom Resource.
     *
     * This can be because it is now in a successful ready state, or
     * because the deploy has failed and is now in an irrecoverable error
     * state.
     *
     * @param namespace     Namespace.
     * @param name          Name of the Kafka instance.
     * @param pollIntervalMs    Interval in which we poll.
     * @param timeoutMs     Timeout.
     * @return A future that succeeds when the Kafka Custom Resource is ready.
     */
    public Future<Void> kafkaCRHasStoppedDeploying(String namespace, String name, long pollIntervalMs, long timeoutMs) {
        return waitFor(namespace, name, pollIntervalMs, timeoutMs, this::hasKafkaCRStoppedDeploying);
    }

    /**
     * Checks if the Kafka Custom Resource is still deploying.
     *
     * It may have stopped deploying because it has finished and
     * is now in a healthy ready state, or because it has failed
     * and is in an irrecoverable error state.
     *
     * @param namespace The namespace.
     * @param name The name of the Kafka instance.
     * @return true if the Kafka Custom Resource is no longer being
     *  deployed (either because it is ready or has failed), or
     *  false if the deploy is still in progress.
     */
    boolean hasKafkaCRStoppedDeploying(String namespace, String name) {
        final String deployingReason = "Creating";
        final String deployingType = "NotReady";
        final String deployingStatus = "True";

        // while the Kafka CR is deploying, it will have a condition with
        //  these attributes - the presence of this is an indicator that
        //  we should continue to wait
        Condition defaultDeploying = new ConditionBuilder()
                .withReason(deployingReason)
                .withType(deployingType)
                .withStatus(deployingStatus)
                .build();

        return getKafkaInstance(namespace, name)
                .map(Kafka::getStatus)
                .map(KafkaStatus::getConditions)
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


    /**
     * Updates the status subresource for an Event Streams instance.
     *
     * @param resource instance of Event Streams with an updated status subresource
     * @return A future that succeeds with the status has been updated.
     */
    public Future<EventStreams> updateEventStreamsStatus(EventStreams resource) {
        Promise<EventStreams> blockingPromise = Promise.promise();
        vertx.createSharedWorkerExecutor("kubernetes-ops-pool").executeBlocking(future -> {
            try {
                OkHttpClient client = this.client.adapt(OkHttpClient.class);
                RequestBody postBody = RequestBody.create(OperationSupport.JSON,
                        new ObjectMapper().writeValueAsString(resource));

                Request request = new Request.Builder().put(postBody).url(
                        this.client.getMasterUrl().toString() +
                                "apis/" + resource.getApiVersion() +
                                "/namespaces/" + resource.getMetadata().getNamespace() +
                                "/eventstreams/" + resource.getMetadata().getName() +
                                "/status").build();

                String method = request.method();
                Response response = client.newCall(request).execute();
                EventStreams returnedResource = null;
                try {
                    final int code = response.code();

                    if (code != 200) {
                        Status status = OperationSupport.createStatus(response);
                        log.debug("Got unexpected {} status code {}: {}", method, code, status);
                        throw OperationSupport.requestFailure(request, status);
                    } else if (response.body() != null) {
                        try (InputStream bodyInputStream = response.body().byteStream()) {
                            returnedResource = Serialization.unmarshal(bodyInputStream, EventStreams.class, Collections.emptyMap());
                        }
                    }
                } finally {
                    // Only messages with body should be closed
                    if (response.body() != null) {
                        response.close();
                    }
                }
                future.complete(returnedResource);
            } catch (IOException | RuntimeException e) {
                log.debug("Updating status failed", e);
                future.fail(e);
            }
        }, true, blockingPromise);

        return blockingPromise.future();
    }
}