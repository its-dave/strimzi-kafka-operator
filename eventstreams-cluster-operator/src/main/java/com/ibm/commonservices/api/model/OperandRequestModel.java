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
package com.ibm.commonservices.api.model;

import com.ibm.commonservices.api.spec.OperandRequest;
import com.ibm.commonservices.api.spec.OperandRequestBuilder;
import com.ibm.eventstreams.api.model.AbstractModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.strimzi.operator.common.model.Labels;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/*
Example:

apiVersion: operator.ibm.com/v1alpha1
kind: OperandRequest
metadata:
  name: eventstreams-cluster-operator
spec:
  requests:
    - operands:
        - name: ibm-management-ingress-operator
        - name: ibm-monitoring-exporters-operator
        - name: ibm-monitoring-prometheusext-operator
        - name: ibm-monitoring-grafana-operator
        - name: ibm-iam-operator
        - name: ibm-commonui-operator
        - name: ibm-platform-api-operator
        - name: ibm-metering-operator
        - name: ibm-licensing-operator
      registry: common-service
      registryNamespace: ibm-common-services
 */

public class OperandRequestModel extends AbstractModel {
    private static final String COMPONENT_NAME = DEFAULT_COMPONENT_NAME;

    public static final String MANAGEMENT_INGRESS_OPERATOR_NAME = "ibm-management-ingress-operator";
    public static final String MONITORING_EXPORTER_OPERATOR_NAME = "ibm-monitoring-exporters-operator";
    public static final String MONITORING_PROMETHEUS_OPERATOR_NAME = "ibm-monitoring-prometheusext-operator";
    public static final String MONITORING_GRAFANA_OPERATOR_NAME = "ibm-monitoring-grafana-operator";
    public static final String IAM_OPERATOR_NAME = "ibm-iam-operator";
    public static final String COMMON_SERVICES_UI_OPERATOR_NAME = "ibm-commonui-operator";
    public static final String COMMON_SERVICES_API_OPERATOR_NAME = "ibm-platform-api-operator";
    public static final String METERING_OPERATOR_NAME = "ibm-metering-operator";
    public static final String LICENSING_OPERATOR_NAME = "ibm-licensing-operator";

    // Add all requests to a list to ensure all logic handling them use the exhaustive list
    public static final List<String> REQUESTED_OPERANDS = Collections.unmodifiableList(Arrays.asList(
            MANAGEMENT_INGRESS_OPERATOR_NAME,
            MONITORING_EXPORTER_OPERATOR_NAME,
            MONITORING_PROMETHEUS_OPERATOR_NAME,
            MONITORING_GRAFANA_OPERATOR_NAME,
            IAM_OPERATOR_NAME,
            COMMON_SERVICES_UI_OPERATOR_NAME,
            COMMON_SERVICES_API_OPERATOR_NAME,
            METERING_OPERATOR_NAME,
            LICENSING_OPERATOR_NAME));

    private OperandRequest operandRequest;

    public OperandRequestModel(EventStreams instance) {
        super(instance, COMPONENT_NAME, Labels.APPLICATION_NAME);
        setOwnerReference(instance);

        operandRequest = new OperandRequestBuilder()
                .withApiVersion(OperandRequest.CRD_API_VERSION)
                .withMetadata(new ObjectMetaBuilder()
                        .withName(operandRequestName(instance.getMetadata().getName()))
                        .withOwnerReferences(getEventStreamsOwnerReference())
                        .withNamespace(getNamespace())
                        .withLabels(labels().toMap())
                        .build())
                .withSpec(spec())
                .build();
    }

    /**
     * @return the untyped spec of the OperandRequest
     */
    private Object spec() {
        JsonArray operands = new JsonArray();
        for (String request : REQUESTED_OPERANDS) {
            operands.add(new JsonObject().put("name", request));
        }

        JsonObject request = new JsonObject()
                .put("operands", operands)
                .put("registry", "common-service")
                .put("registryNamespace", "ibm-common-services");

        JsonObject spec = new JsonObject()
                .put("requests", new JsonArray().add(request));

        // For the spec be readable by Kubernetes we need to map the Json onto a Java Object class
        return DatabindCodec.mapper().convertValue(spec.getMap(), Object.class);
    }

    public OperandRequest getOperandRequest() {
        return this.operandRequest;
    }

    public static String operandRequestName(String instanceName) {
        return getDefaultResourceName(instanceName, COMPONENT_NAME);
    }
}
