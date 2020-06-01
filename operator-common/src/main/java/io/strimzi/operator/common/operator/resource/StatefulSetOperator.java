/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.operator.resource;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.strimzi.operator.common.Annotations;
import io.strimzi.operator.common.model.Labels;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * Operations for {@code StatefulSet}s.
 */
public class StatefulSetOperator extends AbstractScalableResourceOperator<KubernetesClient, StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> {

    private final PodOperator podOperations;

    /**
     * Constructor
     * @param vertx The Vertx instance
     * @param client The Kubernetes client
     */
    public StatefulSetOperator(Vertx vertx, KubernetesClient client) {
        this(vertx, client, new PodOperator(vertx, client));
    }

    public StatefulSetOperator(Vertx vertx, KubernetesClient client, PodOperator podOperations) {
        super(vertx, client, "StatefulSet");
        this.podOperations = podOperations;
    }

    @Override
    protected MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> operation() {
        return client.apps().statefulSets();
    }

    @Override
    protected Integer currentScale(String namespace, String name) {
        StatefulSet sts = get(namespace, name);
        if (sts != null) {
            return sts.getSpec().getReplicas();
        } else {
            return null;
        }
    }

    /**
     * Asynchronously roll the stateful set returning a Future which will complete once all the pods have been rolled
     * and the sts is ready.
     * @param namespace The namespace of the stateful set
     * @param name The name of the stateful set
     * @param operationTimeoutMs The timeout
     * @return A future which completes when all the pods in the stateful set have been restarted.
     */
    public Future<Void> rollingUpdate(String namespace, String name, long operationTimeoutMs) {
        return getAsync(namespace, name)
                .compose(deployment -> deletePod(namespace, name))
                .compose(ignored -> readiness(namespace, name, 1_000, operationTimeoutMs));
    }

    /**
     * Asynchronously delete the given pod.
     * @param namespace The namespace of the pod.
     * @param name The name of the pod.
     * @return A Future which will complete once all the pods has been deleted.
     */
    public Future<ReconcileResult<Pod>> deletePod(String namespace, String name) {
        Labels labels = Labels.EMPTY.withStrimziName(name);
        String podName = podOperations.list(namespace, labels).get(0).getMetadata().getName();
        return podOperations.reconcile(namespace, podName, null);
    }

    @Override
    protected Future<ReconcileResult<StatefulSet>> internalPatch(String namespace, String name, StatefulSet current, StatefulSet desired, boolean cascading) {
        String k8sRev = Annotations.annotations(current).get(Annotations.ANNO_DEP_KUBE_IO_REVISION);
        Annotations.annotations(desired).put(Annotations.ANNO_DEP_KUBE_IO_REVISION, k8sRev);
        return super.internalPatch(namespace, name, current, desired, cascading);
    }

    /**
     * Asynchronously polls the deployment until either the observed generation matches the desired
     * generation sequence number or timeout.
     *
     * @param namespace The namespace.
     * @param name The resource name.
     * @param pollIntervalMs The polling interval
     * @param timeoutMs The timeout
     * @return  A future which completes when the observed generation of the deployment matches the
     * generation sequence number of the desired state.
     */
    public Future<Void> waitForObserved(String namespace, String name, long pollIntervalMs, long timeoutMs) {
        return waitFor(namespace, name, "observed", pollIntervalMs, timeoutMs, this::isObserved);
    }

    /**
     * Check if a deployment has been observed.
     *
     * @param namespace The namespace.
     * @param name The resource name.
     * @return Whether the deployment has been observed.
     */
    private boolean isObserved(String namespace, String name) {
        StatefulSet sts = get(namespace, name);
        if (sts != null)   {
            return sts.getMetadata().getGeneration().equals(sts.getStatus().getObservedGeneration());
        } else {
            return false;
        }
    }
}
