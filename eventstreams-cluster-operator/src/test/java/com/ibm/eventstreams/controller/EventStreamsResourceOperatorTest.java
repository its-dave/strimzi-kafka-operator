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
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.OperationSupport;
import io.strimzi.api.kafka.KafkaList;
import io.strimzi.api.kafka.model.DoneableKafka;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.api.kafka.model.status.ConditionBuilder;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

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
    public void testNoStatusIsNotReady() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(null);

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.isKafkaCRReady(NAMESPACE, CLUSTER_NAME), is(false));

        mockedKafkaClient.close();
    }

    @Test
    public void testNoStatusConditionsIsNotReady() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.isKafkaCRReady(NAMESPACE, CLUSTER_NAME), is(false));

        mockedKafkaClient.close();
    }

    @Test
    public void testEmptyConditionsIsNotReady() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(getEmptyConditions()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.isKafkaCRReady(NAMESPACE, CLUSTER_NAME), is(false));

        mockedKafkaClient.close();
    }

    @Test
    public void testInitialConditionsIsNotReady() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(getInitialConditions()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.isKafkaCRReady(NAMESPACE, CLUSTER_NAME), is(false));

        mockedKafkaClient.close();
    }

    @Test
    public void testInitialConditionsWithWarningsIsNotReady() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(getInitialConditionsWithWarnings()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.isKafkaCRReady(NAMESPACE, CLUSTER_NAME), is(false));

        mockedKafkaClient.close();
    }

    @Test
    public void testNotReadyIsNotReady() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(getNotReadyCondition()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.isKafkaCRReady(NAMESPACE, CLUSTER_NAME), is(false));

        mockedKafkaClient.close();
    }

    @Test
    public void testNotReadyWithWarningsIsNotReady() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(getNotReadyConditionsWithWarnings()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.isKafkaCRReady(NAMESPACE, CLUSTER_NAME), is(false));

        mockedKafkaClient.close();
    }

    @Test
    public void testReadyWithWarningsIsReady() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(getReadyConditionsWithWarnings()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.isKafkaCRReady(NAMESPACE, CLUSTER_NAME), is(true));

        mockedKafkaClient.close();
    }

    @Test
    public void testReadyIsReady() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new KafkaStatusBuilder().withConditions(getReadyCondition()).build());

        EventStreamsResourceOperator operator = new EventStreamsResourceOperator(vertx, mockedKafkaClient);
        assertThat(operator.isKafkaCRReady(NAMESPACE, CLUSTER_NAME), is(true));

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

    private static Condition createCondition(String type, String status) {
        return new ConditionBuilder()
                .withNewLastTransitionTime(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()))
                .withNewType(type)
                .withNewStatus(status)
                .build();
    }
    private static Condition createConditionWithReason(String type, String status, String reason, String message) {
        return new ConditionBuilder()
                .withNewLastTransitionTime(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()))
                .withNewType(type)
                .withNewStatus(status)
                .withNewReason(reason)
                .withNewMessage(message)
                .build();
    }


    private static List<Condition> getInitialConditions() {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(createConditionWithReason("NotReady", "True", "Creating", "Kafka cluster is being deployed"));
        return conditions;
    }

    private static List<Condition> getInitialConditionsWithWarnings() {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(createConditionWithReason(
                "Warning",
                "True",
                "KafkaStorage",
                "A Kafka cluster with a single replica and ephemeral storage will lose topic messages after any restart or rolling update."));
        conditions.add(createConditionWithReason(
                "Warning",
                "True",
                "ZooKeeperStorage",
                "A ZooKeeper cluster with a single replica and ephemeral storage will be in a defective state after any restart or rolling update."));
        conditions.add(createConditionWithReason("NotReady", "True", "Creating", "Kafka cluster is being deployed"));
        return conditions;
    }

    private static List<Condition> getReadyConditionsWithWarnings() {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(createConditionWithReason(
            "Warning",
            "True",
            "KafkaStorage",
            "A Kafka cluster with a single replica and ephemeral storage will lose topic messages after any restart or rolling update."));
        conditions.add(createConditionWithReason(
            "Warning",
            "True",
            "ZooKeeperStorage",
            "A ZooKeeper cluster with a single replica and ephemeral storage will be in a defective state after any restart or rolling update."));
        conditions.add(createCondition("Ready", "True"));
        return conditions;
    }

    private static List<Condition> getNotReadyConditionsWithWarnings() {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(createConditionWithReason(
                "Warning",
                "True",
                "KafkaStorage",
                "A Kafka cluster with a single replica and ephemeral storage will lose topic messages after any restart or rolling update."));
        conditions.add(createConditionWithReason("NotReady", "True", "MockFailure", "Something went wrong"));
        return conditions;
    }

    private static List<Condition> getReadyCondition() {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(createCondition("Ready", "True"));
        return conditions;
    }

    private static List<Condition> getNotReadyCondition() {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(createConditionWithReason("NotReady", "True", "MockFailure", "Something went wrong"));
        return conditions;
    }

    private static List<Condition> getEmptyConditions() {
        List<Condition> conditions = new ArrayList<>();
        return conditions;
    }
}