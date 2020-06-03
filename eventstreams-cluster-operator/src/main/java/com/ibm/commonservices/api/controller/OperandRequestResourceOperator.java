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
package com.ibm.commonservices.api.controller;

import com.ibm.commonservices.api.model.OperandRequestModel;
import com.ibm.commonservices.api.spec.OperandRequest;
import com.ibm.commonservices.api.spec.OperandRequestDoneable;
import com.ibm.commonservices.api.spec.OperandRequestList;
import io.fabric8.kubernetes.client.KubernetesClient;
import com.ibm.eventstreams.api.Crds;

import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Implementation of CrdOperator, this gives us easy access to several utility methods for interacting with Kubernetes objects
 */
public class OperandRequestResourceOperator extends CrdOperator<KubernetesClient, OperandRequest, OperandRequestList, OperandRequestDoneable> {
    /**
     * Constructor
     *
     * @param vertx       The Vertx instance
     * @param client      The Kubernetes client
     */
    public OperandRequestResourceOperator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, OperandRequest.class, OperandRequestList.class, OperandRequestDoneable.class, Crds.getCrd(OperandRequest.class));
    }

    /**
     * Succeeds when the OperandRequest reports that the requested Common Services is ready
     *
     * @param namespace     Namespace.
     * @param name          Name of the Kafka instance.
     * @param pollIntervalMs    Interval in which we poll.
     * @param timeoutMs     Timeout.
     * @return A future that succeeds when the Kafka Custom Resource is ready.
     */
    public Future<Void> waitForReady(String namespace, String name, long pollIntervalMs, long timeoutMs) {
        return waitFor(namespace, name, pollIntervalMs, timeoutMs, this::isReady);
    }

    /**
     * Checks if the OperandRequest is Ready
     *
     * The criteria for an OperandRequest being ready is that each of the requested operands is reported as Running
     * in the respective element of the status.members field
     *
     * ```
     * status:
     *   members:
     *     - name: ibm-management-ingress-operator
     *       phase:
     *         operandPhase: Running
     *         operatorPhase: Running
     *     - name: ibm-monitoring-exporters-operator
     *       phase:
     *         operandPhase: Running
     *         operatorPhase: Running
     *     - name: ibm-monitoring-prometheusext-operator
     *       phase:
     *         operandPhase: Running
     *         operatorPhase: Running
     *     - name: ibm-monitoring-grafana-operator
     *       phase:
     *         operandPhase: Running
     *         operatorPhase: Running
     *     - name: ibm-iam-operator
     *       phase:
     *         operandPhase: Running
     *         operatorPhase: Running
     *     - name: ibm-commonui-operator
     *       phase:
     *         operandPhase: Running
     *         operatorPhase: Running
     *     - name: ibm-platform-api-operator
     *       phase:
     *         operandPhase: Running
     *         operatorPhase: Running
     *     - name: ibm-metering-operator
     *       phase:
     *         operandPhase: Running
     *         operatorPhase: Running
     *     - name: ibm-licensing-operator
     *       phase:
     *         operandPhase: Running
     *         operatorPhase: Running
     * ```
     *
     * @param namespace The namespace the operand request is present in
     * @param name The name of the OperandRequest
     * @return true if the OperandRequest meets the ready criteria
     */
    boolean isReady(String namespace, String name) {
        final String desiredPhase = "Running";

        // Synchronous get as this method must be synchronous to qualify as a BiPredicate for the waitFor method
        OperandRequest operandRequest = get(namespace, name);
        if (operandRequest == null) {
            log.debug("isReady failed : OperandRequest does not exist in cluster");
            return false;
        }
        Object status = operandRequest.getStatus();
        if (status == null) {
            log.debug("isReady failed : OperandRequest has null status");
            return false;
        }
        // This should never be null as prior check of status ensures status != null
        JsonObject readableStatus = JsonObject.mapFrom(status);
        JsonArray members = readableStatus.getJsonArray("members");
        if (members == null) {
            log.debug("isReady failed : OperandRequest has null members field in status");
            return false;
        }

        for (String operand : OperandRequestModel.REQUESTED_OPERANDS) {
            boolean found = false;
            for (Object member : members.getList()) {
                JsonObject readableMember = JsonObject.mapFrom(member);
                if (readableMember.getString("name").equals(operand)) {
                    found = true;
                    JsonObject phase = readableMember.getJsonObject("phase");
                    if (phase == null) {
                        log.debug("isReady failed : OperandRequest with member for {} in status has no phase", operand);
                        return false;
                    }
                    String operandPhase = phase.getString("operandPhase");
                    String operatorPhase = phase.getString("operatorPhase");
                    if (!operatorPhase.equals(desiredPhase)) {
                        log.warn("isReady warning : OperandRequest with member for {} in status has operatorPhase {}",
                                operand, operatorPhase);
                    }

                    if (!operandPhase.equals(desiredPhase)) {
                        log.debug("isReady failed : OperandRequest with member for {} in status. Waiting for {} found {}",
                                operand, desiredPhase, operandPhase);
                        return false;
                    }

                    // We have found the correct members entry - since names should be unique we can break the members loop
                    break;
                }
            }
            if (!found) {
                log.debug("isReady failed : OperandRequest has no member field for {} in status members {}",
                        operand, members.toString());
                return false;
            }
        }

        // If we have not exited out for any reason then it is safe to assume that all operands requested are running
        return true;
    }
}
