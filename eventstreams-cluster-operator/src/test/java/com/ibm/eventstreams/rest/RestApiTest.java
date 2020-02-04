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

package com.ibm.eventstreams.rest;

import com.ibm.eventstreams.api.model.utils.MockEventStreamsKube;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import com.ibm.eventstreams.controller.EventStreamsVerticle;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.strimzi.operator.KubernetesVersion;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;


@ExtendWith(VertxExtension.class)
public abstract class RestApiTest {

    private static Vertx vertx;
    private static KubernetesClient mockClient;
    private static EventStreamsVerticle eventStreamsVerticle;

    HttpClient httpClient;

    private static final String TEST_NAMESPACE = "test";


    @BeforeAll
    public static void setup() throws InterruptedException {
        vertx = Vertx.vertx();
        mockClient = new MockEventStreamsKube().build();

        eventStreamsVerticle = new EventStreamsVerticle(vertx, mockClient, TEST_NAMESPACE,
                new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9),
                EventStreamsOperatorConfig.fromMap(getDefaultEnvVars(TEST_NAMESPACE)));

        CountDownLatch blockUntilStarted = new CountDownLatch(1);
        vertx.deployVerticle(eventStreamsVerticle,
            result -> {
                if (result.succeeded()) {
                    blockUntilStarted.countDown();
                } else {
                    fail(result.cause());
                }
            });
        blockUntilStarted.await(30, TimeUnit.SECONDS);
    }

    @AfterAll
    public static void shutdown(VertxTestContext context) throws Exception {
        eventStreamsVerticle.stop();
        vertx.close(voidAsyncResult -> {
            mockClient.close();
            context.completeNow();
        });
    }

    @BeforeEach
    public void initClient() {
        httpClient = vertx.createHttpClient();
    }

    @AfterEach
    public void closeClient() {
        httpClient.close();
    }


    private static Map<String, String> getDefaultEnvVars(String namespaces) {
        Map<String, String> env = new HashMap<>();
        env.put(EventStreamsOperatorConfig.EVENTSTREAMS_NAMESPACE, namespaces);
        return env;
    }
}
