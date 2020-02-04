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
package com.ibm.eventstreams;

import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.strimzi.operator.KubernetesVersion;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.strimzi.test.mockkube.MockKube;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class EventStreamsVerticleTest {

    private static final Logger LOGGER = LogManager.getLogger(EventStreamsVerticleTest.class);

    private Vertx vertx;

    private KubernetesClient mockClient;

    @BeforeEach
    public void before() {
        vertx = Vertx.vertx();
        mockClient = new MockKube().build();
    }

    @AfterEach
    public void after() {
        vertx.close();
    }

    @Test
    public void deploySingleNamespaceVerticle(VertxTestContext context) throws InterruptedException {
        String namespaces = "myproject";
        Set<String> actualWatchedNamespaces = new HashSet<>();
        AtomicInteger numberOfWatchersCreated = mockOperations(namespaces, mockClient, actualWatchedNamespaces);
        Map<String, String> defaultEnvVars = getDefaultEnvVars(namespaces);

        launchEventStreamsVerticles(context, defaultEnvVars);

        verifyVerticlesWithNamespaces(context, namespaces, actualWatchedNamespaces, numberOfWatchersCreated);
        context.completeNow();
    }

    @Test
    public void deployMultiNamespaceVerticle(VertxTestContext context) throws InterruptedException {
        String namespaces = "myproject,es";
        Set<String> actualWatchedNamespaces = new HashSet<>();
        AtomicInteger numberOfWatchersCreated = mockOperations(namespaces, mockClient, actualWatchedNamespaces);
        Map<String, String> defaultEnvVars = getDefaultEnvVars(namespaces);

        launchEventStreamsVerticles(context, defaultEnvVars);

        verifyVerticlesWithNamespaces(context, namespaces, actualWatchedNamespaces, numberOfWatchersCreated);
        context.completeNow();
    }

    @Test
    public void deployAnyNamespaceVerticle(VertxTestContext context) throws InterruptedException {
        String namespaces = "";
        AtomicInteger numberOfWatchersCreated = mockOperationsInAnyNamespace(namespaces, mockClient);
        Map<String, String> defaultEnvVars = getDefaultEnvVars(namespaces);

        launchEventStreamsVerticles(context, defaultEnvVars);

        LOGGER.info("Number of watchers: " + numberOfWatchersCreated.get());
        int finalNumberOfWatchers = numberOfWatchersCreated.get();

        context.verify(() -> assertEquals(expectedNamespaceAsList(namespaces).size(), vertx.deploymentIDs().size(), "Verticles not equal to supplied namespace"));
        context.verify(() -> assertEquals(expectedNamespaceAsList(namespaces).size(), finalNumberOfWatchers, "Watches not equal to namespaces supplied"));
        context.completeNow();
    }

    private AtomicInteger mockOperations(String namespaces, KubernetesClient client, Set<String> actualNamespaceWatches) {
        AtomicInteger numberOfWatchesCreated = new AtomicInteger(0);
        Set<String> namespaceList = expectedNamespaceAsList(namespaces);
        MixedOperation mockMixedOp = mock(MixedOperation.class);
        when(client.customResources(any(), any(), any(), any())).thenReturn(mockMixedOp);

        for (String namespace : namespaceList) {
            MixedOperation mockNamespacedOp = mock(MixedOperation.class);
            mockWatch(numberOfWatchesCreated, mockNamespacedOp);
            when(mockMixedOp.inNamespace(namespace)).thenAnswer(event -> {
                actualNamespaceWatches.add(event.getArgument(0).toString());
                return mockNamespacedOp;
            });
        }
        return numberOfWatchesCreated;
    }

    private void launchEventStreamsVerticles(VertxTestContext context, Map<String, String> defaultEnvVars) throws InterruptedException {
        CountDownLatch async = new CountDownLatch(1);
        Main.runEventStreams(vertx, mockClient, new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9), EventStreamsOperatorConfig.fromMap(defaultEnvVars)).setHandler(ar -> {
            context.verify(() -> assertThat("Not all verticles started", ar.cause(), is(nullValue())));
            async.countDown();
        });
        if (!async.await(60, TimeUnit.SECONDS)) {
            context.failNow(new Throwable("Test timeout"));
        }
    }

    private AtomicInteger mockOperationsInAnyNamespace(String namespaces, KubernetesClient client) {
        AtomicInteger numberOfWatchesCreated = new AtomicInteger(0);
        Set<String> namespaceList = expectedNamespaceAsList(namespaces);
        MixedOperation mockMixedOp = mock(MixedOperation.class);
        when(client.customResources(any(), any(), any(), any())).thenReturn(mockMixedOp);

        for (String namespace : namespaceList) {
            MixedOperation mockAnyNamespaceOp = mock(MixedOperation.class);
            mockWatch(numberOfWatchesCreated, mockAnyNamespaceOp);
            when(mockMixedOp.inAnyNamespace()).thenReturn(mockAnyNamespaceOp);
        }
        return numberOfWatchesCreated;
    }

    private void mockWatch(AtomicInteger numberOfWatchers, MixedOperation resourceOperation) {
        when(resourceOperation.watch(any())).thenAnswer(actualWatchHandler -> {
            numberOfWatchers.incrementAndGet();
            Watch mockWatch = mock(Watch.class);
            doAnswer(mockWatchHandler -> {
                ((Watcher) actualWatchHandler.getArgument(0)).onClose(null);
                return null;
            }).when(mockWatch).close();
            return mockWatch;
        });
    }

    private Set<String>  expectedNamespaceAsList(String namespaces) {
        List<String> namespaceList = asList(namespaces.split(" *,+ *"));
        return new HashSet<>(namespaceList);
    }

    private static Map<String, String> getDefaultEnvVars(String namespaces) {
        Map<String, String> env = new HashMap<>();
        env.put(EventStreamsOperatorConfig.EVENTSTREAMS_NAMESPACE, namespaces);
        env.put(EventStreamsOperatorConfig.EVENTSTREAMS_FULL_RECONCILIATION_INTERVAL_MS, "120000");
        return env;
    }

    private void verifyVerticlesWithNamespaces(VertxTestContext context, String namespaces, Set<String> actualWatchedNamespaces, AtomicInteger numberOfWatchersCreated) {
        LOGGER.info("Number of watchers: " + numberOfWatchersCreated.get());
        int finalNumberOfWatchers = numberOfWatchersCreated.get();

        context.verify(() -> assertEquals(vertx.deploymentIDs().size(), expectedNamespaceAsList(namespaces).size(), "Verticles not equal to supplied namespace"));
        context.verify(() -> assertThat("Watch does not match the namesapce", actualWatchedNamespaces, is(expectedNamespaceAsList(namespaces))));
        context.verify(() -> assertEquals(expectedNamespaceAsList(namespaces).size(), finalNumberOfWatchers, "Watches not equal to namespaces supplied"));
    }
}
