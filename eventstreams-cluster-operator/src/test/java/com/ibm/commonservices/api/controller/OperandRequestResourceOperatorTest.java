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
import com.ibm.commonservices.api.spec.OperandRequestBuilder;
import com.ibm.commonservices.api.spec.OperandRequestDoneable;
import com.ibm.commonservices.api.spec.OperandRequestList;
import com.ibm.eventstreams.api.model.utils.MockEventStreamsKube;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.strimzi.test.mockkube.MockKube;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


import java.util.HashSet;
import java.util.Set;

@ExtendWith(VertxExtension.class)
public class OperandRequestResourceOperatorTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String NAME = "my-es";

    private static Vertx vertx;
    private static KubernetesClient mockClient;

    @BeforeAll
    public static void beforeAll() {
        vertx = Vertx.vertx();
        mockClient = new MockEventStreamsKube().build();
    }

    @AfterAll
    public static void afterAll() {
        vertx.close();
        mockClient.close();
    }

    @Test
    public void testWaitForReadyWhenOperandRequestReady(VertxTestContext context) {
        KubernetesClient mockedKube = initMockKube(createOperandRequest("Running", "Running"));

        Checkpoint async  = context.checkpoint();
        OperandRequestResourceOperator operandRequestResourceOperator = new OperandRequestResourceOperator(vertx, mockedKube);
        operandRequestResourceOperator.waitForReady(NAMESPACE, NAME, 10, 10)
            .setHandler(context.succeeding(v ->  async.flag()));
    }

    @Test
    public void testWaitForReadyFailsWhenOperandRequestNotReady(VertxTestContext context) {
        KubernetesClient mockedKube = initMockKube(createOperandRequest("NotRunning", "NotRunning"));

        Checkpoint async  = context.checkpoint();

        OperandRequestResourceOperator operandRequestResourceOperator = new OperandRequestResourceOperator(vertx, mockedKube);
        operandRequestResourceOperator.waitForReady(NAMESPACE, NAME, 10, 10)
                .setHandler(context.failing(v ->  async.flag()));
    }

    @Test
    public void testWaitForReadyWhenOperandRequestReadyAndOperatorsNot(VertxTestContext context) {
        KubernetesClient mockedKube = initMockKube(createOperandRequest("Running", "NotRunning"));

        Checkpoint async  = context.checkpoint();

        OperandRequestResourceOperator operandRequestResourceOperator = new OperandRequestResourceOperator(vertx, mockedKube);
        operandRequestResourceOperator.waitForReady(NAMESPACE, NAME, 10, 10)
                .setHandler(context.succeeding(v ->  async.flag()));
    }

    @Test
    public void testIsReadyHandlesOperandRequestMissingPhaseInfo() {
        KubernetesClient mockedKube = initMockKube(createInvalidOperandRequest());

        OperandRequestResourceOperator operandRequestResourceOperator = new OperandRequestResourceOperator(vertx, mockedKube);
        boolean outcome = operandRequestResourceOperator.isReady(NAMESPACE, NAME);
        assertThat(outcome, is(false));
    }

    @Test
    public void testWaitForReadyFailsWhenOperandRequestMissing(VertxTestContext context) {
        KubernetesClient mockedKube = initMockKube(null);

        Checkpoint async  = context.checkpoint();

        OperandRequestResourceOperator operandRequestResourceOperator = new OperandRequestResourceOperator(vertx, mockedKube);
        operandRequestResourceOperator.waitForReady(NAMESPACE, NAME, 10, 10)
                .setHandler(context.failing(v ->  async.flag()));
    }

    @Test
    public void testWaitForReadyFailsWhenOperandRequestNoStatus(VertxTestContext context) {
        KubernetesClient mockedKube = initMockKube(new OperandRequestBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(NAME).withNamespace(NAMESPACE).build())
                .build());

        Checkpoint async  = context.checkpoint();

        OperandRequestResourceOperator operandRequestResourceOperator = new OperandRequestResourceOperator(vertx, mockedKube);
        operandRequestResourceOperator.waitForReady(NAMESPACE, NAME, 10, 10)
                .setHandler(context.failing(v ->  async.flag()));
    }

    /**
     * @param operandRequest an OperandRequest to be pre-created in the mocked Kubernetes environment
     * @return Mocked Kubernetes client, setup to deal with OperandRequests
     */
    private KubernetesClient initMockKube(OperandRequest operandRequest) {
        CustomResourceDefinition crd = mockClient.customResourceDefinitions().withName(OperandRequest.CRD_NAME).get();
        Set<OperandRequest> initial = new HashSet<>(1);
        if (operandRequest != null) {
            initial.add(operandRequest);
        }
        return new MockKube()
                .withCustomResourceDefinition(crd, OperandRequest.class, OperandRequestList.class, OperandRequestDoneable.class)
                    .withInitialInstances(initial)
                .end()
                .build();
    }

    private OperandRequest createOperandRequest(String operandPhase, String operatorPhase) {
        JsonObject status = new JsonObject();
        JsonArray members = new JsonArray();
        for (String operand : OperandRequestModel.REQUESTED_OPERANDS) {
            members.add(new JsonObject()
                    .put("name", operand)
                    .put("phase", new JsonObject()
                            .put("operandPhase", operandPhase)
                            .put("operatorPhase", operatorPhase)));
        }
        status.put("members", members);

        OperandRequest operandRequest = new OperandRequestBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(NAME).withNamespace(NAMESPACE).build())
                .build();
        if (status != null) {
            operandRequest.setStatus(DatabindCodec.mapper().convertValue(status.getMap(), Object.class));
        }

        return operandRequest;
    }

    private OperandRequest createInvalidOperandRequest() {
        JsonObject status = new JsonObject();
        JsonArray members = new JsonArray();
        for (String operand : OperandRequestModel.REQUESTED_OPERANDS) {
            members.add(new JsonObject()
                    .put("name", operand)
                    .put("phase", new JsonObject()));
        }
        status.put("members", members);

        return new OperandRequestBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(NAME).withNamespace(NAMESPACE).build())
                .withStatus(DatabindCodec.mapper().convertValue(status.getMap(), Object.class))
                .build();
    }
}