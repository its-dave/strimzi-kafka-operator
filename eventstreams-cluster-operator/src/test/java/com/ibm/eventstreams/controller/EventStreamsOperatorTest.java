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

package com.ibm.eventstreams.controller;

import static com.ibm.eventstreams.api.model.AbstractModel.APP_NAME;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.ibm.eventstreams.api.Listener;
import com.ibm.eventstreams.api.model.AbstractSecureEndpointModel;
import com.ibm.eventstreams.api.model.AdminApiModel;
import com.ibm.eventstreams.api.model.AdminProxyModel;
import com.ibm.eventstreams.api.model.AdminUIModel;
import com.ibm.eventstreams.api.model.CertificateSecretModel;
import com.ibm.eventstreams.api.model.ClusterSecretsModel;
import com.ibm.eventstreams.api.model.CollectorModel;
import com.ibm.eventstreams.api.model.ReplicatorModel;
import com.ibm.eventstreams.api.model.RestProducerModel;
import com.ibm.eventstreams.api.model.SchemaRegistryModel;
import com.ibm.eventstreams.api.model.utils.MockEventStreamsKube;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.status.EventStreamsStatus;
import com.ibm.eventstreams.api.status.EventStreamsStatusBuilder;
import com.ibm.eventstreams.api.status.EventStreamsVersions;
import com.ibm.eventstreams.controller.utils.ControllerUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.DoneableSecret;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.strimzi.api.kafka.model.CertAndKeySecretSourceBuilder;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternalConfigurationBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternalRouteBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenerTlsBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenersBuilder;
import io.strimzi.api.kafka.model.listener.TlsListenerConfigurationBuilder;
import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.api.kafka.model.status.ConditionBuilder;
import io.strimzi.api.kafka.model.status.KafkaStatusBuilder;
import io.strimzi.api.kafka.model.status.ListenerAddressBuilder;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.status.ListenerStatusBuilder;
import io.strimzi.certs.CertAndKey;
import io.strimzi.operator.KubernetesVersion;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.operator.resource.ReconcileResult;
import io.strimzi.operator.common.operator.resource.RouteOperator;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


@SuppressWarnings({"checkstyle:ClassFanOutComplexity", "checkstyle:ClassDataAbstractionCoupling"})
@ExtendWith(VertxExtension.class)
public class EventStreamsOperatorTest {

    private static final Logger LOGGER = LogManager.getLogger(EventStreamsOperatorTest.class);

    private static final String NAMESPACE = "test-namespace";
    private static final String CLUSTER_NAME = "my-es";
    private static final String PROXY_ROUTE_NAME = CLUSTER_NAME + "-ibm-es-" + AdminProxyModel.COMPONENT_NAME;
    private static final String UI_ROUTE_NAME = CLUSTER_NAME + "-ibm-es-" + AdminUIModel.COMPONENT_NAME;
    private static final String REST_PRODUCER_ROUTE_NAME = CLUSTER_NAME + "-ibm-es-" + RestProducerModel.COMPONENT_NAME;
    private static final String SCHEMA_REGISTRY_ROUTE_NAME = CLUSTER_NAME + "-ibm-es-" + SchemaRegistryModel.COMPONENT_NAME;
    private static final String ADMIN_API_ROUTE_NAME = CLUSTER_NAME + "-ibm-es-" + AdminApiModel.COMPONENT_NAME;
    private static final String ROUTE_HOST_POSTFIX = "apps.route.test";
    private static final int EXPECTED_DEFAULT_REPLICAS = 1;
    private static final String REPLICATOR_DATA = "[replicatorTestData]";
    private static final String VERSION = "2020.1.1";
    private static final int TWO_YEARS_PLUS_IN_SECONDS = 70000000;

    private static Vertx vertx;
    private KubernetesClient mockClient;
    private EventStreamsResourceOperator esResourceOperator;
    private EventStreamsOperator esOperator;
    private EventStreamsOperatorConfig.ImageLookup imageConfig;
    private RouteOperator routeOperator;
    private PlatformFeaturesAvailability pfa;

    private long kafkaStatusReadyTimeoutMs = 0;

    public enum KubeResourceType {
        DEPLOYMENTS,
        SERVICES,
        CONFIG_MAPS,
        ROUTES,
        SECRETS
    };

    @BeforeAll
    public static void setup() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    public static void closeVertxInstance() {
        vertx.close();
    }

    @BeforeEach
    public void init() {
        // setting up a mock Kubernetes client
        mockClient = new MockEventStreamsKube()
                .withInitialSecrets(ModelUtils.generateClusterCa(NAMESPACE, CLUSTER_NAME, APP_NAME, ModelUtils.Certificates.CLUSTER_CA, ModelUtils.Keys.CLUSTER_CA_KEY))
                .build();
        when(mockClient.getNamespace()).thenReturn(NAMESPACE);

        imageConfig = mock(EventStreamsOperatorConfig.ImageLookup.class);

        Kafka mockKafka = new Kafka();
        mockKafka.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build());
        mockKafka.setStatus(new KafkaStatusBuilder().build());
        Optional<Kafka> mockKafkaInstance = Optional.of(mockKafka);

        esResourceOperator = mock(EventStreamsResourceOperator.class);
        when(esResourceOperator.kafkaCRHasReadyStatus(anyString(), anyString(), anyLong(), anyLong())).thenReturn(Future.succeededFuture());
        when(esResourceOperator.createOrUpdate(any(EventStreams.class))).thenReturn(Future.succeededFuture());
        when(esResourceOperator.getKafkaInstance(anyString(), anyString())).thenReturn(mockKafkaInstance);

        pfa = mock(PlatformFeaturesAvailability.class);
        when(pfa.hasRoutes()).thenReturn(true);

        // mock ICP Config Map is present
        Map<String, String> configMapData = new HashMap<>();
        configMapData.put("cluster_address", "0.0.0.0");
        ConfigMap testICPConfigMap = new ConfigMap();
        testICPConfigMap.setData(configMapData);
        NonNamespaceOperation mockNamespaceOperation = mock(NonNamespaceOperation.class);
        Resource<ConfigMap, DoneableConfigMap> mockResource = mock(Resource.class);
        when(mockClient.configMaps().inNamespace("kube-public")).thenReturn(mockNamespaceOperation);
        when(mockNamespaceOperation.withName("ibmcloud-cluster-info")).thenReturn(mockResource);
        when(mockResource.get()).thenReturn(testICPConfigMap);

        // mock ICP cluster ca cert
        Map<String, String> secretData = new HashMap<>();
        secretData.put("ca.crt", "QnJOY0twdXdjaUxiCg==");
        Secret ibmCloudClusterCaCert = new Secret();
        ibmCloudClusterCaCert.setData(secretData);
        Resource<Secret, DoneableSecret> mockSecret = mock(Resource.class);
        when(mockClient.secrets().inNamespace("kube-public")).thenReturn(mockNamespaceOperation);
        when(mockNamespaceOperation.withName("ibmcloud-cluster-ca-cert")).thenReturn(mockSecret);
        when(mockSecret.get()).thenReturn(ibmCloudClusterCaCert);

    }

    @AfterEach
    public void closeMockClient() {
        mockClient.close();
    }

    @Test
    public void testCreateDefaultEventStreamsInstanceOpenShift(VertxTestContext context) {
        createMockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Map<String, Integer> expectedResourcesWithReplicas = getExpectedResourcesWithReplicas(CLUSTER_NAME);
        Set<String> expectedResources = expectedResourcesWithReplicas.keySet();
        Set<String> expectedServices = getExpectedServiceNames(CLUSTER_NAME);
        Set<String> expectedRoutes = getExpectedRouteNames(CLUSTER_NAME);
        Set<String> expectedConfigMaps = getExpectedConfigMapNames(CLUSTER_NAME);
        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            context.verify(() -> {
                if (ar.failed()) {
                    ar.cause().printStackTrace(System.err);
                }
                assertTrue(ar.succeeded());
            });
            verifyResources(context, expectedConfigMaps, KubeResourceType.CONFIG_MAPS);
            verifyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
            verifyResources(context, expectedServices, KubeResourceType.SERVICES);
            verifyResources(context, expectedRoutes, KubeResourceType.ROUTES);
            verifyResources(context, expectedSecrets, KubeResourceType.SECRETS);
            verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
            async.flag();
        });
    }

    @Test
    public void testCreateDefaultEventStreamsInstanceK8s(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator,
                                              imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Map<String, Integer> expectedResourcesWithReplicas = getExpectedResourcesWithReplicas(CLUSTER_NAME);
        Set<String> expectedResources = expectedResourcesWithReplicas.keySet();
        Set<String> expectedServices = getExpectedServiceNames(CLUSTER_NAME);
        Set<String> expectedRoutes = new HashSet<>();
        Set<String> expectedConfigMaps = getExpectedConfigMapNames(CLUSTER_NAME);
        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            context.verify(() -> assertTrue(ar.succeeded(), ar.toString()));
            verifyResources(context, expectedConfigMaps, KubeResourceType.CONFIG_MAPS);
            verifyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
            verifyResources(context, expectedServices, KubeResourceType.SERVICES);
            verifyResources(context, expectedRoutes, KubeResourceType.ROUTES);
            verifyResources(context, expectedSecrets, KubeResourceType.SECRETS);
            verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
            async.flag();
        });
    }

    @Test
    public void testVersions(VertxTestContext context) {
        createMockRoutes();

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint(1);
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            if (ar.succeeded()) {

                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator).createOrUpdate(argument.capture());

                assertThat(argument.getValue().getStatus().getVersions().getReconciledVersion(), is("2020.1.1"));
                assertThat(argument.getValue().getStatus().getVersions().getAvailableAppVersions(), contains("2020.1.1"));

                context.completeNow();
            } else {
                context.failNow(ar.cause());
            }
        });
    }

    @Test
    public void testFailWhenIAMNotPresent(VertxTestContext context) {
        createMockRoutes();

        // mock ICP Config Map not present
        NonNamespaceOperation mockNamespaceOperation = mock(NonNamespaceOperation.class);
        Resource<ConfigMap, DoneableConfigMap> mockResource = mock(Resource.class);
        when(mockClient.configMaps().inNamespace("kube-public")).thenReturn(mockNamespaceOperation);
        when(mockNamespaceOperation.withName("ibmcloud-cluster-info")).thenReturn(mockResource);
        when(mockResource.get()).thenReturn(null);

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            if (ar.succeeded()) {
                context.failNow(new Throwable("Test should fail as IAM is not present"));
            } else {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator).createOrUpdate(argument.capture());
                assertEquals("Could not retrieve cloud pak resources", argument.getValue().getStatus().getConditions().get(0).getMessage());

                context.completeNow();
            }
        });
    }

    @Test
    public void testIAMPresentIsFalseInStatusWhenExceptionGettingICPConfigMap(VertxTestContext context) {
        createMockRoutes();

        // mock an exception when attempting to get ICP Config Map
        when(mockClient.configMaps().inNamespace("kube-public")).thenThrow(new KubernetesClientException("Exception"));

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            if (ar.succeeded()) {
                context.failNow(new Throwable("Test should fail as IAM could not be retrieved"));
            } else {
                assertTrue(ar.cause().toString().contains("Exit Reconcile as IAM not present"));

                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator).createOrUpdate(argument.capture());
                assertEquals("Could not retrieve cloud pak resources", argument.getValue().getStatus().getConditions().get(0).getMessage());

                context.completeNow();
            }
        });
    }

    @Test
    public void testCustomImagesOverride(VertxTestContext context) {
        createMockRoutes();

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);

        esCluster.getSpec().getAdminUI().setImage("adminUi-image:test");

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            if (ar.succeeded()) {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator).createOrUpdate(argument.capture());
                assertTrue(argument.getValue().getStatus().isCustomImages());
                context.completeNow();
            } else {
                context.failNow(ar.cause());
            }
        });
    }

    @Test
    public void testCustomImagesOverrideWithDefaultIBMCom(VertxTestContext context) {
        createMockRoutes();

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);

        esCluster.getSpec().getAdminApi().setImage(AdminApiModel.DEFAULT_IBMCOM_IMAGE);

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            if (ar.succeeded()) {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator).createOrUpdate(argument.capture());
                assertFalse(argument.getValue().getStatus().isCustomImages());
                context.completeNow();
            } else {
                context.failNow(ar.cause());
            }
        });
    }

    @Test
    public void testEventStreamsNameTooLong(VertxTestContext context) {
        createMockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        // 17 Characters long
        String clusterName = "long-instancename";

        EventStreams esCluster = createESCluster(NAMESPACE, clusterName);
        ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
        Checkpoint async = context.checkpoint(1);

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, clusterName), esCluster).setHandler(ar -> {
            if (ar.failed()) {
                assertThat(ar.cause().toString(), containsString("Invalid Custom Resource: check status"));
                // check status
                verify(esResourceOperator).createOrUpdate(updatedEventStreams.capture());
                assertThat("Status is incorrect, found status : " + updatedEventStreams.getValue().getStatus(),
                        updatedEventStreams.getValue().getStatus().getConditions().get(0).getMessage().equals("Invalid custom resource: EventStreams metadata name too long. Maximum length is 16"));
                context.completeNow();
            } else {
                context.failNow(ar.cause());
            }
            async.flag();
        });
    }

    @Test
    public void testEventStreamsUnsupportedVersion(VertxTestContext context) {
        createMockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        String clusterName = "instancename";
        EventStreams esCluster = createESCluster(NAMESPACE, clusterName);
        esCluster.getSpec().setAppVersion("2018.1.1");
        ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
        Checkpoint async = context.checkpoint(1);

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, clusterName), esCluster).setHandler(ar -> {
            if (ar.failed()) {
                assertThat(ar.cause().toString(), containsString("Invalid Custom Resource: check status"));
                // check status
                verify(esResourceOperator).createOrUpdate(updatedEventStreams.capture());
                assertThat("Status is incorrect, found status : " + updatedEventStreams.getValue().getStatus(),
                        updatedEventStreams.getValue().getStatus().getConditions().get(0).getMessage().equals("Invalid custom resource: Unsupported version. Supported versions are [2020.1.1, 2020.1]"));
                context.completeNow();
            } else {
                context.failNow(ar.cause());
            }
            async.flag();
        });
    }

    @Test
    public void testUpdateEventStreamsInstanceOpenShift(VertxTestContext context) {
        createMockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator,
                                              imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Map<String, Integer> expectedResourcesWithReplicas = getExpectedResourcesWithReplicas(CLUSTER_NAME);
        Set<String> expectedResources = expectedResourcesWithReplicas.keySet();
        Set<String> expectedServices = getExpectedServiceNames(CLUSTER_NAME);
        Set<String> expectedRoutes = getExpectedRouteNames(CLUSTER_NAME);
        Set<String> expectedConfigMaps = getExpectedConfigMapNames(CLUSTER_NAME);
        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);

        // Create a cluster
        Checkpoint async = context.checkpoint(2);
        Future<Void> install = esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            context.verify(() -> {
                if (ar.failed()) {
                    ar.cause().printStackTrace(System.err);
                }
                assertTrue(ar.succeeded());
            });
            verifyResources(context, expectedConfigMaps, KubeResourceType.CONFIG_MAPS);
            verifyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
            verifyResources(context, expectedServices, KubeResourceType.SERVICES);
            verifyResources(context, expectedRoutes, KubeResourceType.ROUTES);
            verifyResources(context, expectedSecrets, KubeResourceType.SECRETS);
            verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
            async.flag();
        });
        // update the cluster
        LOGGER.debug("Start updating cluster");
        install.compose(v -> {
            return esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
                context.verify(() -> assertTrue(ar.succeeded()));
                verifyResources(context, expectedConfigMaps, KubeResourceType.CONFIG_MAPS);
                verifyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
                verifyResources(context, expectedServices, KubeResourceType.SERVICES);
                verifyResources(context, expectedRoutes, KubeResourceType.ROUTES);
                verifyResources(context, expectedSecrets, KubeResourceType.SECRETS);
                verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
                async.flag();
            });
        });
    }

    @Test
    public void testReplicatorSecretContentNotResetOnReconciliation(VertxTestContext context) {

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);
        Checkpoint async = context.checkpoint(3);

        Future<Void> install = esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            context.verify(() -> assertTrue(ar.succeeded()));
            verifyResources(context, expectedSecrets, KubeResourceType.SECRETS);
            async.flag();
        });

        Set<HasMetadata> actualResources =  getActualResources(expectedSecrets, KubeResourceType.SECRETS);
        updateReplicatorSecretData(actualResources);

        //Refresh the cluster
        install.compose(v -> {
            return esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
                context.verify(() -> assertTrue(ar.succeeded()));
                LOGGER.debug("Refreshed cluster");
                verifyResources(context, expectedSecrets, KubeResourceType.SECRETS);
                async.flag();
            });
        });

        verifyReplicatorSecretDataIsUnchanged(context, actualResources);
        async.flag();
    }

    @Test
    public void testKafkaBootstrapRetrievedFromStatus(VertxTestContext context) {
        final String internalListenerType = "plain";
        final String internalHost = "internalHost";
        final Integer internalPort = 1234;

        final String externalListenerType = "external";
        final String externalHost = "externalHost";
        final Integer externalPort = 9876;

        createMockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Condition condition = new ConditionBuilder()
                .withNewLastTransitionTime(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()))
                .withNewType("Ready")
                .withNewStatus("True")
                .build();

        ListenerStatus internalListener = new ListenerStatusBuilder()
                .withNewType(internalListenerType)
                .withAddresses(new ListenerAddressBuilder()
                        .withHost(internalHost)
                        .withPort(internalPort)
                        .build())
                .build();

        ListenerStatus externalListener = new ListenerStatusBuilder()
                .withNewType(externalListenerType)
                .withAddresses(new ListenerAddressBuilder()
                        .withHost(externalHost)
                        .withPort(externalPort)
                        .build())
                .build();

        EventStreamsStatus status = new EventStreamsStatusBuilder()
                .addToKafkaListeners(internalListener, externalListener)
                .addToConditions(condition)
                .build();

        esCluster.setStatus(status);

        Map<String, Integer> expectedResourcesWithReplicas = getExpectedResourcesWithReplicas(CLUSTER_NAME);
        Set<String> expectedResources = expectedResourcesWithReplicas.keySet();
        Set<String> expectedServices = getExpectedServiceNames(CLUSTER_NAME);
        Set<String> expectedRoutes = getExpectedRouteNames(CLUSTER_NAME);
        Set<String> expectedConfigMaps = getExpectedConfigMapNames(CLUSTER_NAME);

        Kafka mockKafka = new Kafka();
        mockKafka.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build());
        mockKafka.setStatus(new KafkaStatusBuilder().withListeners(internalListener, externalListener).build());
        Optional<Kafka> mockKafkaInstance = Optional.of(mockKafka);
        when(esResourceOperator.getKafkaInstance(anyString(), anyString())).thenReturn(mockKafkaInstance);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            context.verify(() -> assertTrue(ar.succeeded(), ar.toString()));
            verifyResources(context, expectedConfigMaps, KubeResourceType.CONFIG_MAPS);
            verifyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
            verifyResources(context, expectedServices, KubeResourceType.SERVICES);
            verifyResources(context, expectedRoutes, KubeResourceType.ROUTES);
            verifyReplicasInDeployments(context, expectedResourcesWithReplicas);

            String expectedInternalBootstrap = internalHost + ":" + internalPort;
            String expectedExternalBootstrap = externalHost + ":" + externalPort;
            String deploymentName = CLUSTER_NAME + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME;
            verifyKafkaBootstrapUrl(NAMESPACE, deploymentName, expectedInternalBootstrap);
            verifyKafkaBootstrapAdvertisedListeners(NAMESPACE, deploymentName, expectedExternalBootstrap);

            deploymentName = CLUSTER_NAME + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME;
            verifyKafkaBootstrapUrl(NAMESPACE, deploymentName, expectedInternalBootstrap);

            ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
            verify(esResourceOperator).createOrUpdate(argument.capture());
            assertEquals(2, argument.getValue().getStatus().getKafkaListeners().size());
            assertEquals(internalListenerType, argument.getValue().getStatus().getKafkaListeners().get(0).getType());
            assertEquals(internalHost, argument.getValue().getStatus().getKafkaListeners().get(0).getAddresses().get(0).getHost());
            assertEquals(internalPort, argument.getValue().getStatus().getKafkaListeners().get(0).getAddresses().get(0).getPort());

            assertEquals(externalListenerType, argument.getValue().getStatus().getKafkaListeners().get(1).getType());
            assertEquals(externalHost, argument.getValue().getStatus().getKafkaListeners().get(1).getAddresses().get(0).getHost());
            assertEquals(externalPort, argument.getValue().getStatus().getKafkaListeners().get(1).getAddresses().get(0).getPort());

            async.flag();
        });
    }

    @Test
    public void testStatusIsCorrectlyDisplayed(VertxTestContext context) {
        createMockRoutes();

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);

        Set<String> expectedRouteHosts = getExpectedRouteNames(CLUSTER_NAME).stream()
                .map(this::formatRouteHost)
                .collect(Collectors.toSet());

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            try {
                if (ar.failed()) {
                    context.failNow(ar.cause());
                }
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator).createOrUpdate(argument.capture());
                assertFalse(argument.getValue().getStatus().isCustomImages());
                assertEquals(EventStreamsVersions.OPERAND_VERSION, esCluster.getStatus().getVersions().getReconciledVersion());
                assertEquals(EventStreamsVersions.AVAILABLE_VERSIONS, esCluster.getStatus().getVersions().getAvailableAppVersions());
                assertTrue(expectedRouteHosts.containsAll(esCluster.getStatus().getRoutes().values()), expectedRouteHosts + " expected to contain all values " + esCluster.getStatus().getRoutes().values() + "but did not");
                assertEquals("https://" + formatRouteHost(UI_ROUTE_NAME), esCluster.getStatus().getAdminUiUrl());
                context.completeNow();
            } catch (AssertionError e) {
                context.failNow(e);
            }
        });
    }
    
    @Test
    public void testSingleListenerCertificateSecretContentIsValid(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();
        Listener listener = Listener.externalTls();
        List<Listener> listeners = Collections.singletonList(listener);
        ModelUtils.EndpointModel endpointModel = new ModelUtils.EndpointModel(esCluster, NAMESPACE, "endpoint-component", listeners);
        Map<String, String> additionalHosts = Collections.singletonMap(listener.getName(), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("There is only one additional secret created", mockClient.secrets().list().getItems().size(), is(3));
            Secret secret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            assertThat("The expected secret is created", secret, is(notNullValue()));
            CertAndKey certAndKey = reconciliationState.certificateManager.certificateAndKey(secret, endpointModel.getCertSecretCertID(listener.getName()), endpointModel.getCertSecretKeyID(listener.getName()));
            X509Certificate certificate = ControllerUtils.checkCertificate(reconciliationState.certificateManager, certAndKey);
            ControllerUtils.checkSans(reconciliationState.certificateManager, certificate, endpointModel.getExternalService(), additionalHosts.get(listener.getName()));
            async.flag();
        });
    }

    @Test
    public void testMultipleListenerCertificateSecretContentIsValid(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();
        Listener internalListener = Listener.internalPlain();
        Listener internalTlsListener = Listener.internalTls();
        Listener externalTlsListener = Listener.externalTls();
        List<Listener> listeners = Arrays.asList(internalListener, internalTlsListener, externalTlsListener);
        ModelUtils.EndpointModel endpointModel = new ModelUtils.EndpointModel(esCluster, NAMESPACE, "endpoint-component", listeners);
        Map<String, String> additionalHosts = Collections.singletonMap(externalTlsListener.getName(), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("There is only one additional secret created", mockClient.secrets().list().getItems().size(), is(3));
            Secret secret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            assertThat("The expected secret is created", secret, is(notNullValue()));
            assertThat("The secret does not contain cert key ID for plain listener", secret.getData().containsKey(endpointModel.getCertSecretKeyID(internalListener.getName())), is(false));
            assertThat("The secret does not contain cert ID for plain listener", secret.getData().containsKey(endpointModel.getCertSecretCertID(internalListener.getName())), is(false));
            CertAndKey internalTlsCertAndKey = reconciliationState.certificateManager.certificateAndKey(secret, endpointModel.getCertSecretCertID(internalTlsListener.getName()), endpointModel.getCertSecretKeyID(internalTlsListener.getName()));
            CertAndKey externalTlsCertAndKey = reconciliationState.certificateManager.certificateAndKey(secret, endpointModel.getCertSecretCertID(externalTlsListener.getName()), endpointModel.getCertSecretKeyID(externalTlsListener.getName()));
            X509Certificate internalTlsCertificate = ControllerUtils.checkCertificate(reconciliationState.certificateManager, internalTlsCertAndKey);
            X509Certificate externalTlsCertificate = ControllerUtils.checkCertificate(reconciliationState.certificateManager, externalTlsCertAndKey);
            ControllerUtils.checkSans(reconciliationState.certificateManager, internalTlsCertificate, endpointModel.getInternalService(), "");
            ControllerUtils.checkSans(reconciliationState.certificateManager, externalTlsCertificate, endpointModel.getExternalService(), additionalHosts.get(externalTlsListener.getName()));
            async.flag();
        });
    }

    @Test
    public void testEndpointCertificateSecretContentUnchangedByStandardReconciliation(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();
        List<Listener> listeners = Arrays.asList(Listener.externalTls(), Listener.internalTls());
        ModelUtils.EndpointModel endpointModel = new ModelUtils.EndpointModel(esCluster, NAMESPACE, "endpoint-component", listeners);
        Map<String, String> additionalHosts = Collections.singletonMap(Listener.externalTls().getName(), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("There is only one additional secret created", mockClient.secrets().list().getItems().size(), is(3));
            Secret firstSecret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            reconciliationState.reconcileCerts(endpointModel, additionalHosts,  Date::new).setHandler(ar2 -> {
                assertThat("There is only one additional secret created", mockClient.secrets().list().getItems().size(), is(3));
                Secret secondSecret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
                assertThat("The secret has not changed", secondSecret, is(firstSecret));
                async.flag();
            });
        });
    }

    @Test
    public void testEndpointCertificateSecretRegeneratedWhenExpired(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();
        List<Listener> listeners = Arrays.asList(Listener.externalTls(), Listener.internalTls());
        ModelUtils.EndpointModel endpointModel = new ModelUtils.EndpointModel(esCluster, NAMESPACE, "endpoint-component", listeners);
        Map<String, String> additionalHosts = Collections.singletonMap(Listener.externalTls().getName(), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("There is only one additional secret created", mockClient.secrets().list().getItems().size(), is(3));
            Secret firstSecret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            reconciliationState.reconcileCerts(endpointModel, additionalHosts,  () -> Date.from(Instant.now().plusSeconds(TWO_YEARS_PLUS_IN_SECONDS))).setHandler(ar2 -> {
                assertThat("There is only one additional secret created", mockClient.secrets().list().getItems().size(), is(3));
                Secret secondSecret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
                assertThat("The secret has changed", secondSecret, not(firstSecret));
                async.flag();
            });
        });
    }

    @Test
    public void testEndpointCertificateSecretRegeneratedWhenCAChanges(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();
        List<Listener> listeners = Arrays.asList(Listener.externalTls(), Listener.internalTls());
        ModelUtils.EndpointModel endpointModel = new ModelUtils.EndpointModel(esCluster, NAMESPACE, "endpoint-component", listeners);
        Map<String, String> additionalHosts = Collections.singletonMap(Listener.externalTls().getName(), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("There is only one additional secret created", mockClient.secrets().list().getItems().size(), is(3));
            Secret firstSecret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            List<Secret> newClusterCA = new ArrayList<>(ModelUtils.generateClusterCa(NAMESPACE, CLUSTER_NAME, APP_NAME, ModelUtils.Certificates.NEW_CLUSTER_CA, ModelUtils.Keys.NEW_CLUSTER_CA_KEY));
            mockClient.secrets().createOrReplace(newClusterCA.get(0));
            mockClient.secrets().createOrReplace(newClusterCA.get(1));

            reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar2 -> {
                assertThat("There is only one additional secret created", mockClient.secrets().list().getItems().size(), is(3));
                Secret secondSecret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
                assertThat("The secret has changed", secondSecret, not(firstSecret));
                async.flag();
            });
        });
    }

    @Test
    public void testEndpointCertificateSecretRegeneratedWhenSansAreChanged(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();
        Listener internalTlsListener = Listener.internalTls();
        List<Listener> listeners = Arrays.asList(Listener.externalTls(), internalTlsListener);
        ModelUtils.EndpointModel endpointModel = new ModelUtils.EndpointModel(esCluster, NAMESPACE, "endpoint-component", listeners);
        Map<String, String> additionalHosts = Collections.singletonMap(Listener.externalTls().getName(), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("There is only one additional secret created", mockClient.secrets().list().getItems().size(), is(3));
            Secret firstSecret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            CertAndKey originalInternalTlsCertAndKey = reconciliationState.certificateManager.certificateAndKey(firstSecret, endpointModel.getCertSecretCertID(internalTlsListener.getName()), endpointModel.getCertSecretKeyID(internalTlsListener.getName()));
            Map<String, String> newHosts = Collections.singletonMap(Listener.externalTls().getName(), "extra.host.name.2");
            reconciliationState.reconcileCerts(endpointModel, newHosts, Date::new).setHandler(ar2 -> {
                assertThat("There is only one additional secret created", mockClient.secrets().list().getItems().size(), is(3));
                Secret secondSecret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
                assertThat("The secret has changed", secondSecret, not(firstSecret));
                CertAndKey newInternalTlsCertAndKey = reconciliationState.certificateManager.certificateAndKey(secondSecret, endpointModel.getCertSecretCertID(internalTlsListener.getName()), endpointModel.getCertSecretKeyID(internalTlsListener.getName()));
                assertThat("The internalTls cert data hasn't changed", originalInternalTlsCertAndKey.cert(), is(newInternalTlsCertAndKey.cert()));
                assertThat("The internalTls key data hasn't changed", originalInternalTlsCertAndKey.key(), is(newInternalTlsCertAndKey.key()));
                async.flag();
            });
        });
    }

    @Test
    public void testEndpointCertificatePopulatedWithProvidedBrokerCerts(VertxTestContext context) {
        String secretName = "provided-broker-cert";
        String secretKey = "broker.cert";
        String secretCertificate = "broker.key";
        Map<String, String> data = new HashMap<>();
        data.put(secretKey, "YW55IG9sZCBndWJiaW5zCg==");
        data.put(secretCertificate, "YW55IG9sZCBndWJiaW5zCg==");
        Secret providedSecret = ModelUtils.generateSecret(NAMESPACE, secretName, data);
        mockClient.secrets().create(providedSecret);
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESClusterWithProvidedBrokerCerts(NAMESPACE, CLUSTER_NAME, secretName, secretKey, secretCertificate);
        Checkpoint async = context.checkpoint(1);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();
        Listener internalTlsListener = Listener.internalTls();
        Listener externalTlsListener = Listener.externalTls();
        List<Listener> listeners = Arrays.asList(externalTlsListener, internalTlsListener);
        ModelUtils.EndpointModel endpointModel = new ModelUtils.EndpointModel(esCluster, NAMESPACE, "endpoint-component", listeners);
        Map<String, String> additionalHosts = Collections.singletonMap(externalTlsListener.getName(), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("There is only one additional secret created", mockClient.secrets().list().getItems().size(), is(4));
            Secret secret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            assertThat("The admin api cert secret has been populated with the internal provided cert", secret.getData().get(endpointModel.getCertSecretCertID(internalTlsListener.getName())), is(providedSecret.getData().get(secretCertificate)));
            assertThat("The admin api cert secret has been populated with the internal provided key", secret.getData().get(endpointModel.getCertSecretKeyID(internalTlsListener.getName())), is(providedSecret.getData().get(secretKey)));
            assertThat("The admin api cert secret has been populated with the external provided cert", secret.getData().get(endpointModel.getCertSecretCertID(externalTlsListener.getName())), is(providedSecret.getData().get(secretCertificate)));
            assertThat("The admin api cert secret has been populated with the external provided key", secret.getData().get(endpointModel.getCertSecretKeyID(externalTlsListener.getName())), is(providedSecret.getData().get(secretKey)));
            mockClient.secrets().delete(providedSecret);
            async.flag();
        });
    }

    @Test
    public void testEndpointCertificatePopulatedWithProvidedExternalBrokerCerts(VertxTestContext context) {
        String secretName = "provided-broker-cert";
        String secretKey = "broker.cert";
        String secretCertificate = "broker.key";
        Map<String, String> data = new HashMap<>();
        data.put(secretKey, "YW55IG9sZCBndWJiaW5zCg==");
        data.put(secretCertificate, "YW55IG9sZCBndWJiaW5zCg==");
        Secret providedSecret = ModelUtils.generateSecret(NAMESPACE, secretName, data);
        mockClient.secrets().create(providedSecret);
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESClusterWithProvidedExternalBrokerCerts(NAMESPACE, CLUSTER_NAME, secretName, secretKey, secretCertificate);
        Checkpoint async = context.checkpoint(1);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();
        Listener internalTlsListener = Listener.internalTls();
        Listener externalTlsListener = Listener.externalTls();
        List<Listener> listeners = Arrays.asList(externalTlsListener, internalTlsListener);
        ModelUtils.EndpointModel endpointModel = new ModelUtils.EndpointModel(esCluster, NAMESPACE, "endpoint-component", listeners);
        Map<String, String> additionalHosts = Collections.singletonMap(externalTlsListener.getName(), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("There is only one additional secret created", mockClient.secrets().list().getItems().size(), is(4));
            Secret secret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            assertThat("The admin api cert secret has not been populated with the internal provided cert", secret.getData().get(endpointModel.getCertSecretCertID(internalTlsListener.getName())), not(providedSecret.getData().get(secretCertificate)));
            assertThat("The admin api cert secret has not been populated with the internal provided key", secret.getData().get(endpointModel.getCertSecretKeyID(internalTlsListener.getName())), not(providedSecret.getData().get(secretKey)));
            CertAndKey internalTlsCertAndKey = reconciliationState.certificateManager.certificateAndKey(secret, endpointModel.getCertSecretCertID(internalTlsListener.getName()), endpointModel.getCertSecretKeyID(internalTlsListener.getName()));
            X509Certificate internalTlsCertificate = ControllerUtils.checkCertificate(reconciliationState.certificateManager, internalTlsCertAndKey);
            ControllerUtils.checkSans(reconciliationState.certificateManager, internalTlsCertificate, endpointModel.getInternalService(), "");
            assertThat("The admin api cert secret has been populated with the external provided cert", secret.getData().get(endpointModel.getCertSecretCertID(externalTlsListener.getName())), is(providedSecret.getData().get(secretCertificate)));
            assertThat("The admin api cert secret has been populated with the external provided key", secret.getData().get(endpointModel.getCertSecretKeyID(externalTlsListener.getName())), is(providedSecret.getData().get(secretKey)));
            mockClient.secrets().delete(providedSecret);
            async.flag();
        });
    }

    @Test
    public void testAllSecureEndpointModelsCertsCreatedOpenShift(VertxTestContext context) {
        createMockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Checkpoint async = context.checkpoint(2);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();
        Listener listener = Listener.externalTls();
        Listener.setEnabledListeners(Collections.singletonList(listener));

        CompositeFuture.join(reconciliationState.createRestProducer(Date::new),
                reconciliationState.createSchemaRegistry(Date::new),
                reconciliationState.createAdminApi(Date::new)).setHandler(ar -> {
                    assertThat("There are three additional secrets created", mockClient.secrets().list().getItems().size(), is(5));
                    List<Secret> secrets = mockClient.secrets().withLabel("instance", CLUSTER_NAME).list().getItems();
                    secrets.forEach(secret -> {
                        Optional<Service> serviceOpt = mockClient.services().list().getItems()
                                    .stream()
                                    .filter(service -> service.getMetadata().getName().contains("external"))
                                    .filter(service -> service.getMetadata().getName().startsWith(secret.getMetadata().getName().replace("-cert", "")))
                                    .findAny();
                        Optional<Route> routeOpt = mockClient.adapt(OpenShiftClient.class).routes().list().getItems()
                                    .stream()
                                    .filter(route -> route.getMetadata().getName().endsWith("tls"))
                                    .filter(route -> route.getMetadata().getName().startsWith(secret.getMetadata().getName().replace("-cert", "")))
                                    .findAny();
                        assertThat("We found the service for this secret", serviceOpt.isPresent(), is(true));
                        assertThat("We found the route for this secret", routeOpt.isPresent(), is(true));
                        String certID = secret.getData().keySet().stream().filter(string -> string.endsWith(CertificateSecretModel.formatCertID(listener.getName()))).findAny().get();
                        String keyID = secret.getData().keySet().stream().filter(string -> string.endsWith(CertificateSecretModel.formatKeyID(listener.getName()))).findAny().get();
                        CertAndKey certAndKey = reconciliationState.certificateManager.certificateAndKey(secret, certID, keyID);
                        X509Certificate certificate = ControllerUtils.checkCertificate(reconciliationState.certificateManager, certAndKey);
                        ControllerUtils.checkSans(reconciliationState.certificateManager, certificate, serviceOpt.get(), routeOpt.get().getSpec().getHost());
                    });
                    async.flag();
                });
        Listener.setEnabledListeners(Arrays.asList(Listener.externalTls(), Listener.externalPlain(), Listener.internalTls()));
        async.flag();
    }

    // Not possible to check that the deployment does change when the certificate changes as the resourceVersion isn't
    // implemented properly in the mockClient
    @Test
    public void testNoRollingUpdateForDeploymentWhenCertificatesDoNotChange(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Checkpoint async = context.checkpoint(3);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();

        CompositeFuture.join(reconciliationState.createRestProducer(Date::new),
                reconciliationState.createSchemaRegistry(Date::new),
                reconciliationState.createAdminApi(Date::new)).setHandler(ar -> {
                    // Immediately run again as the mocking agent doesn't create the deployments correctly the first time
                    CompositeFuture.join(
                            reconciliationState.createRestProducer(Date::new),
                            reconciliationState.createSchemaRegistry(Date::new),
                            reconciliationState.createAdminApi(Date::new)).setHandler(ar2 -> {
                                List<Deployment> deployments = mockClient.apps().deployments().list().getItems();
                                assertThat("There are three deployments created", deployments.size(), is(3));
                                async.flag();
                                CompositeFuture.join(
                                    reconciliationState.createRestProducer(Date::new),
                                    reconciliationState.createSchemaRegistry(Date::new),
                                    reconciliationState.createAdminApi(Date::new)
                                ).setHandler(ar3 -> {
                                    List<Deployment> deployments2 = mockClient.apps().deployments().list().getItems();
                                    assertThat("There are still only three deployments", deployments2.size(), is(3));
                                    deployments2.forEach(deployment -> assertTrue(deployments.contains(deployment)));
                                    async.flag();
                                });
                            });
                });
        async.flag();
    }

    @Test
    public void testCreateOrUpdateRoutesReturnNothingk8s(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Checkpoint async = context.checkpoint(1);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();
        Listener internalTlsListener = Listener.internalTls();
        Listener externalTlsListener = Listener.externalTls();
        List<Listener> listeners = Arrays.asList(externalTlsListener, internalTlsListener);
        ModelUtils.EndpointModel endpointModel = new ModelUtils.EndpointModel(esCluster, NAMESPACE, "endpoint-component", listeners);

        reconciliationState.createOrUpdateRoutes(endpointModel, endpointModel.getRoutes()).setHandler(ar -> {
            if (ar.failed()) {
                context.failNow(ar.cause());
            }
            assertThat(ar.result().isEmpty(), is(true));
            async.flag();
        });
    }

    @Test
    public void testCreateOrUpdateRoutesMapOpenShiftNoExternalListeners(VertxTestContext context) {
        createMockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Checkpoint async = context.checkpoint(1);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();
        Listener internalTlsListener = Listener.internalTls();
        List<Listener> listeners = Collections.singletonList(internalTlsListener);
        // Use admin api name for route matching with the mock client
        ModelUtils.EndpointModel endpointModel = new ModelUtils.EndpointModel(esCluster, NAMESPACE, "admin-api", listeners);

        reconciliationState.createOrUpdateRoutes(endpointModel, endpointModel.getRoutes()).setHandler(ar -> {
            if (ar.failed()) {
                context.failNow(ar.cause());
            }
            assertThat(ar.result().size(), is(0));
            async.flag();
        });
    }

    @Test
    public void testCreateOrUpdateRoutesMapOpenShift(VertxTestContext context) {
        createMockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Checkpoint async = context.checkpoint(1);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();
        Listener internalTlsListener = Listener.internalTls();
        Listener externalTlsListener = Listener.externalTls();
        Listener externalPlainListener = Listener.externalPlain();
        List<Listener> listeners = Arrays.asList(externalTlsListener, internalTlsListener, externalPlainListener);
        // Use admin api name for route matching with the mock client
        ModelUtils.EndpointModel endpointModel = new ModelUtils.EndpointModel(esCluster, NAMESPACE, "admin-api", listeners);

        reconciliationState.createOrUpdateRoutes(endpointModel, endpointModel.getRoutes()).setHandler(ar -> {
            if (ar.failed()) {
                context.failNow(ar.cause());
            }
            assertThat(ar.result().size(), is(2));
            assertThat(ar.result().containsKey(externalTlsListener.getName()), is(true));
            assertThat(ar.result().containsKey(externalPlainListener.getName()), is(true));
            assertThat(ar.result().get(externalTlsListener.getName()), is(formatRouteHost(ADMIN_API_ROUTE_NAME + "-" + Listener.EXTERNAL_TLS_NAME)));
            assertThat(ar.result().get(externalPlainListener.getName()), is(formatRouteHost(ADMIN_API_ROUTE_NAME + "-" + Listener.EXTERNAL_PLAIN_NAME)));
            async.flag();
        });
    }

    private void updateReplicatorSecretData(Set<HasMetadata> actualResourcesList) {
        actualResourcesList.forEach(item -> {
            if (item instanceof Secret) {
                Secret replicatorSecret = (Secret) item;

                if (replicatorSecret.getMetadata().getName().contains(ReplicatorModel.REPLICATOR_SECRET_NAME)) {
                    Encoder encoder = Base64.getEncoder();
                    String newSecretString = encoder.encodeToString(REPLICATOR_DATA.getBytes(StandardCharsets.UTF_8));
                    Map<String, String> newSecretData = Collections.singletonMap(ReplicatorModel.REPLICATOR_SECRET_KEY_NAME, newSecretString);
                    replicatorSecret.setData(newSecretData);
                } else {
                    LOGGER.debug("Replicator secret not found to set data");
                }
            }
        });
    }

    private void verifyReplicatorSecretDataIsUnchanged(VertxTestContext context, Set<HasMetadata> actualResourcesList) {
        List<Secret> replicatorSecrets = actualResourcesList.stream()
                .filter(Secret.class::isInstance)
                .map(Secret.class::cast)
                .filter(secret -> secret.getMetadata().getName().contains(ReplicatorModel.REPLICATOR_SECRET_NAME))
                .collect(Collectors.toList()
                );
        assertEquals(1, replicatorSecrets.size(), "Replicator secret Not Found");
        Secret replicatorSecret = replicatorSecrets.get(0);
        Encoder encoder = Base64.getEncoder();
        String newSecretString = encoder.encodeToString(REPLICATOR_DATA.getBytes(StandardCharsets.UTF_8));
        context.verify(() -> assertThat(
                replicatorSecret.getData().get(ReplicatorModel.REPLICATOR_SECRET_KEY_NAME), is(newSecretString)));
    }

    private void verifyKafkaBootstrapUrl(String namespace, String deploymentName, String expectedKafkaBootstrap) {
        List<EnvVar> envVars = getDeployEnvVars(namespace, deploymentName);
        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_URL").withValue(expectedKafkaBootstrap).build();
        assertThat(envVars, hasItem(kafkaBootstrapUrlEnv));
    }

    private void verifyKafkaBootstrapAdvertisedListeners(String namespace, String deploymentName, String expectedExternalBootstrap) {
        List<EnvVar> envVars = getDeployEnvVars(namespace, deploymentName);
        EnvVar kafkaAdvertisedListenerEnv = new EnvVarBuilder().withName("KAFKA_ADVERTISED_LISTENER_BOOTSTRAP_ADDRESS").withValue(expectedExternalBootstrap).build();
        assertThat(envVars, hasItem(kafkaAdvertisedListenerEnv));
    }

    private List<EnvVar> getDeployEnvVars(String namespace, String deploymentName) {
        return mockClient.apps().deployments()
                .inNamespace(namespace)
                .withName(deploymentName)
                .get()
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getEnv();
    }

    private void verifyReplicasInDeployments(VertxTestContext context, Map<String, Integer> expectedResourcesWithReplicas) {
        Set<HasMetadata> capturedDeployments = getActualResources(expectedResourcesWithReplicas.keySet(), KubeResourceType.DEPLOYMENTS);
        Set<String> capturedDeploymentNames = capturedDeployments.stream().map(deploy -> deploy.getMetadata().getName()).collect(Collectors.toSet());
        for (String deploymentName : capturedDeploymentNames) {
            Integer actualReplicas = getActualReplicas(deploymentName, expectedResourcesWithReplicas.get(deploymentName));
            LOGGER.debug("Deployment name {} set {} replicas", deploymentName, actualReplicas);
            context.verify(() -> assertThat("For deployment " + deploymentName, mockClient.apps().deployments().inNamespace(NAMESPACE).withName(deploymentName).get().getSpec().getReplicas(),
                    is(expectedResourcesWithReplicas.get(deploymentName))));
        }
    }

    private void verifyResources(VertxTestContext context, Set<String> expectedResources, KubeResourceType type) {
        Set<HasMetadata> actualResources =  getActualResources(expectedResources, type);
        Set<HasMetadata> finalActualResources = actualResources;
        context.verify(() -> assertThat(finalActualResources.size(), is(expectedResources.size())));
        Set<String> actualResourceNames = actualResources.stream().map(res -> res.getMetadata().getName()).collect(Collectors.toSet());
        context.verify(() -> assertThat(actualResourceNames, is(expectedResources)));
    }

    private Set<HasMetadata> getActualResources(Set<String> expectedResources, KubeResourceType type) {
        int retryCount = 0;
        int maxRetry = 3;
        Set<HasMetadata> actualResources = new HashSet<>();
        while (retryCount < maxRetry) {
            actualResources = getResources(NAMESPACE, type);
            LOGGER.debug("Actual resource count " + actualResources.size() + " for type " + type);
            LOGGER.debug("Expected resource count " + expectedResources.size() + " for type " + type);
            if (actualResources.size() == expectedResources.size()) {
                break;
            } else {
                retryCount++;
                try {
                    LOGGER.debug("Waiting in retry loop " + retryCount);
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return actualResources;
    }

    private Integer getActualReplicas(String deploymentName, Integer expectedReplica) {
        int retryCount = 0;
        int maxRetry = 5;
        Integer actualReplica = 0;
        while (retryCount < maxRetry) {
            actualReplica = mockClient.apps().deployments().inNamespace(NAMESPACE).withName(deploymentName).get().getSpec().getReplicas();
            LOGGER.debug("Actual replica for " + deploymentName + " is " + actualReplica);
            LOGGER.debug("Expected replica for " + deploymentName + " is " + expectedReplica);
            if (expectedReplica == actualReplica) {
                break;
            } else {
                retryCount++;
                try {
                    LOGGER.debug("Waiting in retry loop " + retryCount);
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return actualReplica;
    }

    private Map<String, Integer> getExpectedResourcesWithReplicas(String clusterName) {
        Map<String, Integer> expectedDeployments = new HashMap<>();
        expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + AdminProxyModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
        expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
        expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
        expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
        expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
        expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + CollectorModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
        return expectedDeployments;
    }

    private Set<String> getExpectedServiceNames(String clusterName) {
        Set<String> expectedServices = new HashSet<>();
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + AdminProxyModel.COMPONENT_NAME);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_POSTFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_POSTFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_POSTFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.INTERNAL_SERVICE_POSTFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.INTERNAL_SERVICE_POSTFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.INTERNAL_SERVICE_POSTFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + CollectorModel.COMPONENT_NAME);
        return expectedServices;
    }

    private Set<String> getExpectedConfigMapNames(String clusterName) {
        Set<String> expectedCm = new HashSet<>();
        expectedCm.add(clusterName + "-" + APP_NAME + "-" + AdminProxyModel.COMPONENT_NAME + AdminProxyModel.CONFIG_MAP_SUFFIX);
        return expectedCm;
    }

    private Set<String> getExpectedRouteNames(String clusterName) {
        Set<String> expectedRoutes = new HashSet<>();
        expectedRoutes.add(PROXY_ROUTE_NAME);
        expectedRoutes.add(UI_ROUTE_NAME);
        expectedRoutes.add(REST_PRODUCER_ROUTE_NAME);
        expectedRoutes.add(ADMIN_API_ROUTE_NAME);
        expectedRoutes.add(REST_PRODUCER_ROUTE_NAME + "-" + Listener.EXTERNAL_TLS_NAME);
        expectedRoutes.add(SCHEMA_REGISTRY_ROUTE_NAME + "-" + Listener.EXTERNAL_TLS_NAME);
        expectedRoutes.add(ADMIN_API_ROUTE_NAME + "-" + Listener.EXTERNAL_TLS_NAME);
        expectedRoutes.add(REST_PRODUCER_ROUTE_NAME + "-" + Listener.EXTERNAL_PLAIN_NAME);
        expectedRoutes.add(SCHEMA_REGISTRY_ROUTE_NAME + "-" + Listener.EXTERNAL_PLAIN_NAME);
        expectedRoutes.add(ADMIN_API_ROUTE_NAME + "-" + Listener.EXTERNAL_PLAIN_NAME);
        return expectedRoutes;
    }

    private Set<String> getExpectedSecretNames(String clusterName) {
        Set<String> expectedSecrets = new HashSet<>();
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + ReplicatorModel.REPLICATOR_SECRET_NAME);
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME + "-" + CertificateSecretModel.CERT_SECRET_NAME_POSTFIX);
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME + "-" + CertificateSecretModel.CERT_SECRET_NAME_POSTFIX);
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + CertificateSecretModel.CERT_SECRET_NAME_POSTFIX);
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + ClusterSecretsModel.EVENTSTREAMS_IBMCLOUD_CA_CERT_SECRET_SUFFIX);
        return expectedSecrets;
    }

    private Set<HasMetadata> getResources(String namespace, KubeResourceType type) {
        Set<HasMetadata> result = new HashSet<>();
        switch (type) {
            case CONFIG_MAPS:
                result = new HashSet<>(mockClient.configMaps().inNamespace(namespace).list().getItems());
                break;
            case SERVICES:
                result = new HashSet<>(mockClient.services().inNamespace(namespace).list().getItems());
                break;
            case DEPLOYMENTS:
                result = new HashSet<>(mockClient.apps().deployments().inNamespace(namespace).list().getItems());
                break;
            case ROUTES:
                result = new HashSet<>(mockClient.adapt(OpenShiftClient.class).routes().inNamespace(namespace).list().getItems());
                break;
            case SECRETS:
                result = new HashSet<>(mockClient.secrets().inNamespace(namespace).withLabel("app.kubernetes.io/managed-by", "eventstreams-operator").list().getItems());
                break;
        }
        return result;
    }

    private EventStreams createESCluster(String namespace, String clusterName) {
        KafkaSpecBuilder kafka = new KafkaSpecBuilder()
                .editOrNewKafka()
                    .withReplicas(3)
                .endKafka()
                .editOrNewZookeeper()
                    .withReplicas(3)
                .endZookeeper();

        return createESClusterWithStrimziOverrides(namespace, clusterName, kafka.build());
    }

    private EventStreams createESClusterWithProvidedExternalBrokerCerts(String namespace, String clusterName, String secretName, String secretKey, String secretCertificate) {
        CertAndKeySecretSourceBuilder certAndKey = new CertAndKeySecretSourceBuilder()
                .withKey(secretKey)
                .withCertificate(secretCertificate)
                .withSecretName(secretName);

        KafkaListenerExternalConfigurationBuilder brokerConfiguration = new KafkaListenerExternalConfigurationBuilder()
                .withBrokerCertChainAndKey(certAndKey.build());

        KafkaListenerExternalRouteBuilder externalRoute = new KafkaListenerExternalRouteBuilder()
                .withConfiguration(brokerConfiguration.build());

        KafkaListenersBuilder listeners = new KafkaListenersBuilder()
                .withExternal(externalRoute.build());

        KafkaSpecBuilder kafka = new KafkaSpecBuilder()
                .editOrNewKafka()
                    .withListeners(listeners.build())
                    .withReplicas(3)
                .endKafka()
                .editOrNewZookeeper()
                    .withReplicas(3)
                .endZookeeper();

        return createESClusterWithStrimziOverrides(namespace, clusterName, kafka.build());
    }

    private EventStreams createESClusterWithProvidedBrokerCerts(String namespace, String clusterName, String secretName, String secretKey, String secretCertificate) {
        CertAndKeySecretSourceBuilder externalCertAndKey = new CertAndKeySecretSourceBuilder()
                .withKey(secretKey)
                .withCertificate(secretCertificate)
                .withSecretName(secretName);

        KafkaListenerExternalConfigurationBuilder brokerConfiguration = new KafkaListenerExternalConfigurationBuilder()
                .withBrokerCertChainAndKey(externalCertAndKey.build());

        KafkaListenerExternalRouteBuilder externalRoute = new KafkaListenerExternalRouteBuilder()
                .withConfiguration(brokerConfiguration.build());

        CertAndKeySecretSourceBuilder internalCertAndKey = new CertAndKeySecretSourceBuilder()
                .withKey(secretKey)
                .withCertificate(secretCertificate)
                .withSecretName(secretName);

        TlsListenerConfigurationBuilder configuration = new TlsListenerConfigurationBuilder()
                .withBrokerCertChainAndKey(internalCertAndKey.build());

        KafkaListenerTlsBuilder tls = new KafkaListenerTlsBuilder()
                .withConfiguration(configuration.build());

        KafkaListenersBuilder listeners = new KafkaListenersBuilder()
                .withTls(tls.build())
                .withExternal(externalRoute.build());

        KafkaSpecBuilder kafka = new KafkaSpecBuilder()
                .editOrNewKafka()
                .withListeners(listeners.build())
                .withReplicas(3)
                .endKafka()
                .editOrNewZookeeper()
                .withReplicas(3)
                .endZookeeper();

        return createESClusterWithStrimziOverrides(namespace, clusterName, kafka.build());
    }

    private EventStreams createESClusterWithStrimziOverrides(String namespace, String clusterName, KafkaSpec kafka) {
        return new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(clusterName).withNamespace(namespace).build())
                .withNewSpec()
                    .withNewAdminApi()
                        .withReplicas(1)
                    .endAdminApi()
                    .withNewRestProducer()
                        .withReplicas(1)
                    .endRestProducer()
                    .withNewAdminProxy()
                        .withReplicas(1)
                    .endAdminProxy()
                    .withNewSchemaRegistry()
                        .withReplicas(1)
                    .endSchemaRegistry()
                    .withNewAdminUI()
                        .withReplicas(1)
                    .endAdminUI()
                    .withNewCollector()
                        .withReplicas(1)
                    .endCollector()
                    .withNewReplicator()
                        .withReplicas(1)
                    .endReplicator()
                    .withStrimziOverrides(kafka)
                    .withAppVersion(VERSION)
                .endSpec()
                .build();
    }

    private String formatRouteHost(String name) {
        return String.format("%s.%s", name, ROUTE_HOST_POSTFIX);
    }

    private void createMockRoutes() {
        List<Route> routes = new ArrayList<>();
        routes.add(createRoute(PROXY_ROUTE_NAME, NAMESPACE));
        routes.add(createRoute(UI_ROUTE_NAME, NAMESPACE));
        routes.add(createRoute(REST_PRODUCER_ROUTE_NAME, NAMESPACE));
        routes.add(createRoute(ADMIN_API_ROUTE_NAME, NAMESPACE));
        routes.add(createRoute(REST_PRODUCER_ROUTE_NAME + "-" + Listener.EXTERNAL_TLS_NAME, NAMESPACE));
        routes.add(createRoute(SCHEMA_REGISTRY_ROUTE_NAME + "-" + Listener.EXTERNAL_TLS_NAME, NAMESPACE));
        routes.add(createRoute(ADMIN_API_ROUTE_NAME + "-" + Listener.EXTERNAL_TLS_NAME, NAMESPACE));
        routes.add(createRoute(REST_PRODUCER_ROUTE_NAME + "-" + Listener.EXTERNAL_PLAIN_NAME, NAMESPACE));
        routes.add(createRoute(SCHEMA_REGISTRY_ROUTE_NAME + "-" + Listener.EXTERNAL_PLAIN_NAME, NAMESPACE));
        routes.add(createRoute(ADMIN_API_ROUTE_NAME + "-" + Listener.EXTERNAL_PLAIN_NAME, NAMESPACE));

        routeOperator = mock(RouteOperator.class);
        when(routeOperator.hasAddress(anyString(), anyString(), anyLong(), anyLong())).thenReturn(Future.succeededFuture());

        routes.forEach(this::deployRoute);
    }

    private void deployRoute(Route route) {
        when(routeOperator.createOrUpdate(ArgumentMatchers.argThat(observedRoute -> {
            if (observedRoute == null) {
                return false;
            }
            return observedRoute.getMetadata().getName().equals(route.getMetadata().getName());
        }))).thenReturn(Future.succeededFuture(ReconcileResult.created(route)));

        when(routeOperator.get(anyString(), eq(route.getMetadata().getName()))).thenReturn(route);

        mockClient.adapt(OpenShiftClient.class).routes().inNamespace(NAMESPACE).create(route);
    }

    private Route createRoute(String name, String namespace) {
        return new RouteBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(name).withNamespace(namespace).build())
                .withNewSpec()
                    .withHost(formatRouteHost(name))
                .endSpec()
                .withNewStatus()
                    .addNewIngress().withNewHost(formatRouteHost(name)).endIngress()
                .endStatus()
                .build();
    }
}
