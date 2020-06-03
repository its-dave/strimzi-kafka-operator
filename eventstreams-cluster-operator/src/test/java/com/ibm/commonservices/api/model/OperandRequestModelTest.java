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
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class OperandRequestModelTest {

    private final String instanceName = "test";
    private final String namespace = "myproject";

    private EventStreamsBuilder createDefaultEventStreams() {
        return ModelUtils.createDefaultEventStreams(instanceName)
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(instanceName)
                        .withNewNamespace(namespace)
                        .build())
                .editSpec()
                .endSpec();
    }

    private OperandRequestModel createDefaultOperandRequestModel() {
        return new OperandRequestModel(createDefaultEventStreams().build());
    }

    /*
    Valid Example:

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
    @Test
    public void testOperandRequestMModel() {
        OperandRequestModel operandRequestModel = createDefaultOperandRequestModel();
        OperandRequest operandRequest = operandRequestModel.getOperandRequest();
        ObjectMeta objMeta = operandRequest.getMetadata();
        assertThat(objMeta.getName(), is("test-ibm-es-eventstreams"));
        assertThat(objMeta.getNamespace(), is(namespace));
        assertThat(objMeta.getOwnerReferences(), notNullValue());
        assertThat(objMeta.getLabels(), notNullValue());

        // Serialize Object into Json for verification
        JsonObject spec = JsonObject.mapFrom(operandRequest.getSpec());
        assertThat(spec, allOf(
                JsonObjectMatchers.hasSize(1),
                JsonObjectMatchers.hasKey("requests")
        ));

        JsonArray requests = (JsonArray) spec.getValue("requests");
        assertThat(requests, JsonArrayMatchers.hasSize(1));

        JsonObject request = requests.getJsonObject(0);
        assertThat(request, allOf(
                JsonObjectMatchers.hasKeys("operands", "registry", "registryNamespace")
        ));

        JsonArray operands = (JsonArray) request.getValue("operands");
        assertThat(operands, allOf(
                JsonArrayMatchers.hasSize(9),
                is(new JsonArray()
                        .add(new JsonObject().put("name", "ibm-management-ingress-operator"))
                        .add(new JsonObject().put("name", "ibm-monitoring-exporters-operator"))
                        .add(new JsonObject().put("name", "ibm-monitoring-prometheusext-operator"))
                        .add(new JsonObject().put("name", "ibm-monitoring-grafana-operator"))
                        .add(new JsonObject().put("name", "ibm-iam-operator"))
                        .add(new JsonObject().put("name", "ibm-commonui-operator"))
                        .add(new JsonObject().put("name", "ibm-platform-api-operator"))
                        .add(new JsonObject().put("name", "ibm-metering-operator"))
                        .add(new JsonObject().put("name", "ibm-licensing-operator")))
        ));
        assertThat(request.getString("registry"), is("common-service"));
        assertThat(request.getString("registryNamespace"), is("ibm-common-services"));
    }
}