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
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicator;
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicatorDoneable;
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicatorList;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.OperationSupport;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.strimzi.api.kafka.KafkaMirrorMaker2List;
import io.strimzi.api.kafka.model.DoneableKafkaMirrorMaker2;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2;
import io.strimzi.operator.common.operator.resource.AbstractWatchableResourceOperator;
import io.strimzi.operator.common.operator.resource.CrdOperator;
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


public class EventStreamsGeoReplicatorResourceOperator extends
        AbstractWatchableResourceOperator<KubernetesClient, EventStreamsGeoReplicator, EventStreamsGeoReplicatorList, EventStreamsGeoReplicatorDoneable, Resource<EventStreamsGeoReplicator, EventStreamsGeoReplicatorDoneable>> {

    private static final Logger log = LogManager.getLogger(EventStreamsResourceOperator.class.getName());

    private KubernetesClient client;

    public final CrdOperator<KubernetesClient, KafkaMirrorMaker2, KafkaMirrorMaker2List, DoneableKafkaMirrorMaker2> mirrorMaker2Operator;

    public EventStreamsGeoReplicatorResourceOperator(Vertx vertx, KubernetesClient client, String resourceKind) {
        super(vertx, client, resourceKind);
        log.info("Creating EventStreamsGeoReplicatorResourceOperator");
        this.client = client;
        this.mirrorMaker2Operator = new CrdOperator<>(vertx, client, KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class, DoneableKafkaMirrorMaker2.class, Crds.getCrd(EventStreamsGeoReplicator.class));
    }

    @Override
    protected MixedOperation<EventStreamsGeoReplicator, EventStreamsGeoReplicatorList, EventStreamsGeoReplicatorDoneable, Resource<EventStreamsGeoReplicator, EventStreamsGeoReplicatorDoneable>> operation() {
        return client.customResources(Crds.getCrd(EventStreamsGeoReplicator.class), EventStreamsGeoReplicator.class, EventStreamsGeoReplicatorList.class,
                EventStreamsGeoReplicatorDoneable.class);

    }


    /**
     * Returns the instance of the geo-replicator's MirrorMaker2 for the given namespace and name.
     *
     * @param namespace The namespace.
     * @param replicatorMirrorMaker2InstanceName The name of the geo-replicator's MirrorMaker2  instance.
     * @return Optional of mirrorMaker2
     */
    public Optional<KafkaMirrorMaker2> getReplicatorMirrorMaker2Instance(String namespace, String replicatorMirrorMaker2InstanceName) {

        return Optional.ofNullable(mirrorMaker2Operator.get(namespace, replicatorMirrorMaker2InstanceName));

    }

    /**
     * Updates the status subresource for an Event Streams instance.
     *
     * @param resource instance of Event Streams with an updated status subresource
     * @return A future that succeeds with the status has been updated.
     */
    public Future<EventStreamsGeoReplicator> updateEventStreamsGeoReplicatorStatus(EventStreamsGeoReplicator resource) {
        Promise<EventStreamsGeoReplicator> blockingPromise = Promise.promise();
        vertx.createSharedWorkerExecutor("kubernetes-ops-pool").executeBlocking(future -> {
            try {
                OkHttpClient client = this.client.adapt(OkHttpClient.class);
                RequestBody postBody = RequestBody.create(OperationSupport.JSON,
                        new ObjectMapper().writeValueAsString(resource));

                Request request = new Request.Builder().put(postBody).url(
                        this.client.getMasterUrl().toString() +
                                "apis/" + resource.getApiVersion() +
                                "/namespaces/" + resource.getMetadata().getNamespace() +
                                "/eventstreamsgeoreplicators/" + resource.getMetadata().getName() +
                                "/status").build();

                String method = request.method();
                Response response = client.newCall(request).execute();
                EventStreamsGeoReplicator returnedResource = null;
                try {
                    final int code = response.code();

                    if (code != 200) {
                        Status status = OperationSupport.createStatus(response);
                        log.debug("Got unexpected {} status code {}: {}", method, code, status);
                        throw OperationSupport.requestFailure(request, status);
                    } else if (response.body() != null) {
                        try (InputStream bodyInputStream = response.body().byteStream()) {
                            returnedResource = Serialization.unmarshal(bodyInputStream, EventStreamsGeoReplicator.class, Collections.emptyMap());
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
