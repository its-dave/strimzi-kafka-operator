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


import com.ibm.eventstreams.api.model.utils.MockEventStreamsKube;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.controller.utils.ConditionUtils;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.OperationSupport;
import io.strimzi.api.kafka.KafkaList;
import io.strimzi.api.kafka.model.DoneableKafka;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.status.KafkaStatus;
import io.strimzi.api.kafka.model.status.KafkaStatusBuilder;
import io.strimzi.test.mockkube.MockKube;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class EventStreamsResourceOperatorTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String CLUSTER_NAME = "my-es";

    private static Vertx vertx;
    private static KubernetesClient mockClient;

    @BeforeAll
    public static void setup() {
        vertx = Vertx.vertx();
        mockClient = new MockEventStreamsKube().build();
    }

    @AfterAll
    public static void closeVertxInstance() {
        vertx.close();
        mockClient.close();
    }


    @Test
    public void testNoStatusIsStillDeploying() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(null);

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.hasKafkaCRStoppedDeploying(NAMESPACE, CLUSTER_NAME), is(false));

        mockedKafkaClient.close();
    }

    @Test
    public void testNoStatusConditionsIsStillDeploying() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.hasKafkaCRStoppedDeploying(NAMESPACE, CLUSTER_NAME), is(false));

        mockedKafkaClient.close();
    }

    @Test
    public void testEmptyConditionsIsNotStillDeploying() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(ConditionUtils.getEmptyConditions()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.hasKafkaCRStoppedDeploying(NAMESPACE, CLUSTER_NAME), is(true));

        mockedKafkaClient.close();
    }

    @Test
    public void testInitialConditionsIsStillDeploying() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(ConditionUtils.getInitialConditions()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.hasKafkaCRStoppedDeploying(NAMESPACE, CLUSTER_NAME), is(false));

        mockedKafkaClient.close();
    }

    @Test
    public void testInitialConditionsWithWarningsIsStillDeploying() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(ConditionUtils.getInitialConditionsWithWarnings()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.hasKafkaCRStoppedDeploying(NAMESPACE, CLUSTER_NAME), is(false));

        mockedKafkaClient.close();
    }

    @Test
    public void testFailureWithWarningsIsNotDeploying() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(ConditionUtils.getNotReadyConditionsWithWarnings()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.hasKafkaCRStoppedDeploying(NAMESPACE, CLUSTER_NAME), is(true));

        mockedKafkaClient.close();
    }

    @Test
    public void testFailureIsNotDeploying() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(ConditionUtils.getFailureCondition()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.hasKafkaCRStoppedDeploying(NAMESPACE, CLUSTER_NAME), is(true));

        mockedKafkaClient.close();
    }

    @Test
    public void testReadyWithWarningsIsNotDeploying() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(ConditionUtils.getReadyConditionsWithWarnings()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.hasKafkaCRStoppedDeploying(NAMESPACE, CLUSTER_NAME), is(true));

        mockedKafkaClient.close();
    }

    @Test
    public void testReadyIsNotDeploying() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(ConditionUtils.getReadyCondition()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.hasKafkaCRStoppedDeploying(NAMESPACE, CLUSTER_NAME), is(true));

        mockedKafkaClient.close();
    }

    @Test
    public void testUpdateStatusAsync(VertxTestContext context) throws IOException {
        KubernetesClient mockClient = mock(KubernetesClient.class);

        OkHttpClient mockOkHttp = mock(OkHttpClient.class);
        when(mockClient.adapt(eq(OkHttpClient.class))).thenReturn(mockOkHttp);
        URL fakeUrl = new URL("http", "my-host", 9443, "/");
        when(mockClient.getMasterUrl()).thenReturn(fakeUrl);
        Call mockCall = mock(Call.class);
        when(mockOkHttp.newCall(any(Request.class))).thenReturn(mockCall);
        ResponseBody body = ResponseBody.create(OperationSupport.JSON, "{ }");
        Response response = new Response.Builder().code(200).request(new Request.Builder().url(fakeUrl).build()).body(body).message("Created").protocol(Protocol.HTTP_1_1).build();
        when(mockCall.execute()).thenReturn(response);

        EventStreams es = ModelUtils.createDefaultEventStreams("test").build();

        Checkpoint async = context.checkpoint();
        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockClient);
        operator.updateEventStreamsStatus(es).setHandler(res -> {
            context.verify(() -> assertThat(res.succeeded(), is(true)));
            async.flag();
        });
    }

    @Test
    public void testUpdateStatusFail(VertxTestContext context) throws IOException {
        KubernetesClient mockClient = mock(KubernetesClient.class);

        OkHttpClient mockOkHttp = mock(OkHttpClient.class);
        when(mockClient.adapt(eq(OkHttpClient.class))).thenReturn(mockOkHttp);
        URL fakeUrl = new URL("http", "my-host", 9443, "/");
        when(mockClient.getMasterUrl()).thenReturn(fakeUrl);
        Call mockCall = mock(Call.class);
        when(mockOkHttp.newCall(any(Request.class))).thenReturn(mockCall);
        ResponseBody body = ResponseBody.create(OperationSupport.JSON, "{ }");
        Response response = new Response.Builder().code(409).request(new Request.Builder().url(fakeUrl).build()).body(body).message("Conflict").protocol(Protocol.HTTP_1_1).build();
        when(mockCall.execute()).thenReturn(response);

        EventStreams es = ModelUtils.createDefaultEventStreams("test").build();

        Checkpoint async = context.checkpoint();
        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockClient);
        operator.updateEventStreamsStatus(es).setHandler(res -> {
            context.verify(() -> assertThat(res.succeeded(), is(false)));
            async.flag();
        });
    }



    private KubernetesClient prepareKubeClient(KafkaStatus status) {
        Kafka mockKafka = new Kafka();
        mockKafka.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build());
        if (status != null) {
            mockKafka.setStatus(status);
        }

        CustomResourceDefinition crd = mockClient.customResourceDefinitions().withName(Kafka.CRD_NAME).get();
        return new MockKube().withCustomResourceDefinition(crd, Kafka.class, KafkaList.class, DoneableKafka.class)
                .withInitialInstances(Collections.singleton(mockKafka)).end().build();
    }
}