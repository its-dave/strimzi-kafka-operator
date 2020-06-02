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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.strimzi.operator.cluster.operator.resource.StatefulSetDiff;
import io.strimzi.operator.cluster.operator.resource.StatefulSetOperator;
import io.strimzi.operator.common.operator.resource.ReconcileResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

public class SchemaRegistrySetOperator extends StatefulSetOperator {

    private static final Logger log = LogManager.getLogger(SchemaRegistrySetOperator.class.getName());

    public SchemaRegistrySetOperator(Vertx vertx, KubernetesClient client, long operationTimeoutMs) {
        super(vertx, client, operationTimeoutMs);
        log.info("Creating SchemaRegistrySetOperator");
    }


    @Override
    protected Future<ReconcileResult<StatefulSet>> internalCreate(String namespace, String name, StatefulSet desired) {
        setGeneration(desired, INIT_GENERATION);

        // The implementation in the superclass waits for the pods to become
        // ready before continuing.
        // We don't need to do this, so overriding it do the create only.
        // If we wanted this the reconcile step for creating the schema registry
        // to wait for the pods to become ready before continuing, we could
        // remove this override implementation.
        try {
            ReconcileResult<StatefulSet> result = ReconcileResult.created(operation().inNamespace(namespace).withName(name).create(desired));
            log.debug("{} {} in namespace {} has been created", resourceKind, name, namespace);
            return Future.succeededFuture(result);
        } catch (Exception e) {
            log.debug("Caught exception while creating {} {} in namespace {}", resourceKind, name, namespace, e);
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<Void> maybeRollingUpdate(StatefulSet sts, Function<Pod, String> podNeedsRestart, Secret clusterCaSecret, Secret coKeySecret) {
        // Rolling updates are not needed for Schema Registry as it is not
        // a complex stateful cluster like Kafka or ZooKeeper where each
        // replica is doing different work.
        // All changes will be made to the STS directly, so this method
        // won't be called, but we need an implementation to satisfy the
        // superclass.
        log.error("Unexpected call to maybeRollingUpdate");
        return Future.succeededFuture();
    }

    @Override
    protected boolean shouldIncrementGeneration(StatefulSetDiff diff) {
        return !diff.isEmpty() && needsRollingUpdate(diff);
    }

    public static boolean needsRollingUpdate(StatefulSetDiff diff) {
        if (diff.changesLabels()) {
            log.debug("Changed labels => needs rolling update");
            return true;
        }
        if (diff.changesSpecTemplate()) {
            log.debug("Changed template spec => needs rolling update");
            return true;
        }
        if (diff.changesVolumeClaimTemplates()) {
            log.debug("Changed volume claim template => needs rolling update");
            return true;
        }
        if (diff.changesVolumeSize()) {
            log.debug("Changed size of the volume claim template => no need for rolling update");
            return false;
        }
        return false;
    }

    @Override
    protected boolean shouldPreventScale() {
        // schema registry is a stateless app with shared storage, so
        //  there is no need to prevent scaling via kubernetes patch
        return false;
    }
}