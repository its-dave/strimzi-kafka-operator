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
import com.ibm.eventstreams.api.spec.EventStreamsReplicatorDoneable;
import com.ibm.eventstreams.api.spec.EventStreamsReplicator;
import com.ibm.eventstreams.api.spec.EventStreamsReplicatorList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.KafkaMirrorMaker2List;
import io.strimzi.api.kafka.model.DoneableKafkaMirrorMaker2;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.status.KafkaMirrorMaker2Status;
import io.strimzi.operator.common.operator.resource.AbstractWatchableResourceOperator;
import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;


public class EventStreamsReplicatorResourceOperator extends
        AbstractWatchableResourceOperator<KubernetesClient, EventStreamsReplicator, EventStreamsReplicatorList, EventStreamsReplicatorDoneable, Resource<EventStreamsReplicator, EventStreamsReplicatorDoneable>> {

    private static final Logger log = LogManager.getLogger(EventStreamsResourceOperator.class.getName());

    private KubernetesClient client;

    public final CrdOperator<KubernetesClient, KafkaMirrorMaker2, KafkaMirrorMaker2List, DoneableKafkaMirrorMaker2> mirrorMaker2Operator;

    public EventStreamsReplicatorResourceOperator(Vertx vertx, KubernetesClient client, String resourceKind) {
        super(vertx, client, resourceKind);
        log.info("Creating EventStreamsReplicatorResourceOperator");
        this.client = client;
        this.mirrorMaker2Operator = new CrdOperator<>(vertx, client, KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class, DoneableKafkaMirrorMaker2.class, Crds.getCrd(EventStreamsReplicator.class));
    }

    @Override
    protected MixedOperation<EventStreamsReplicator, EventStreamsReplicatorList, EventStreamsReplicatorDoneable, Resource<EventStreamsReplicator, EventStreamsReplicatorDoneable>> operation() {
        return client.customResources(Crds.getCrd(EventStreamsReplicator.class), EventStreamsReplicator.class, EventStreamsReplicatorList.class,
                EventStreamsReplicatorDoneable.class);

    }

    /**
     * Succeeds when the Replicator's MirrorMaker2 Custom Resource is ready.
     *
     * @param namespace     Namespace.
     * @param name          Name of the Replicator's MirrorMaker2 instance.
     * @param pollIntervalMs    Interval in which we poll.
     * @param timeoutMs     Timeout.
     * @return A future that succeeds when the Replicator's MirrorMaker2 Custom Resource is ready.
     */
    public Future<Void> mirrorMaker2CRHasReadyStatus(String namespace, String name, long pollIntervalMs, long timeoutMs) {
        return waitFor(namespace, name, pollIntervalMs, timeoutMs, this::isMirrorMaker2CRReady);
    }

    /**
     * Checks if the Replicator's MirrorMaker2 Custom Resource is ready.
     *
     * @param namespace The namespace.
     * @param name The name of the Replicator's MirrorMaker2 instance.
     * @return Whether the Replicator's MirrorMaker2 Custom Resource is ready.
     */
    public boolean isMirrorMaker2CRReady(String namespace, String name) {

        KafkaMirrorMaker2 mm2 = mirrorMaker2Operator.get(namespace, name);

        return Optional.ofNullable(mm2)
                .map(KafkaMirrorMaker2::getStatus)
                .map(KafkaMirrorMaker2Status::getConditions)
                .map(condition -> condition.get(0))
                .map(condition -> condition.getType().equals("Ready") && condition.getStatus().equals("True"))
                .orElse(false);

    }

    /**
     * Returns the instance of Replicator's MirrorMaker2 for the given namespace and name.
     *
     * @param namespace The namespace.
     * @param replicatorMirrorMaker2InstanceName The name of the Replicator's MirrorMaker2  instance.
     * @return Optional of mirrorMaker2
     */
    public Optional<KafkaMirrorMaker2> getReplicatorMirrorMaker2Instance(String namespace, String replicatorMirrorMaker2InstanceName) {

        return Optional.ofNullable(mirrorMaker2Operator.get(namespace, replicatorMirrorMaker2InstanceName));

    }
}
