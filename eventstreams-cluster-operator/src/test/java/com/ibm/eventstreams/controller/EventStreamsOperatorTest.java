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
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
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

import com.ibm.eventstreams.api.Labels;
import com.ibm.eventstreams.api.Listener;
import com.ibm.eventstreams.api.model.AbstractSecureEndpointModel;
import com.ibm.eventstreams.api.model.AdminApiModel;
import com.ibm.eventstreams.api.model.AdminUIModel;
import com.ibm.eventstreams.api.model.CertificateSecretModel;
import com.ibm.eventstreams.api.model.ClusterSecretsModel;
import com.ibm.eventstreams.api.model.CollectorModel;
import com.ibm.eventstreams.api.model.InternalKafkaUserModel;
import com.ibm.eventstreams.api.model.ReplicatorModel;
import com.ibm.eventstreams.api.model.RestProducerModel;
import com.ibm.eventstreams.api.model.SchemaRegistryModel;
import com.ibm.eventstreams.api.model.utils.MockEventStreamsKube;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.status.EventStreamsAvailableVersions;
import com.ibm.eventstreams.api.status.EventStreamsEndpoint;
import com.ibm.eventstreams.api.status.EventStreamsEndpointBuilder;
import com.ibm.eventstreams.api.status.EventStreamsStatus;
import com.ibm.eventstreams.api.status.EventStreamsStatusBuilder;
import com.ibm.eventstreams.api.status.EventStreamsVersions;
import com.ibm.eventstreams.controller.utils.ControllerUtils;

import io.strimzi.api.kafka.KafkaMirrorMaker2List;
import io.strimzi.api.kafka.model.DoneableKafkaMirrorMaker2;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

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
import io.strimzi.api.kafka.KafkaList;
import io.strimzi.api.kafka.KafkaUserList;
import io.strimzi.api.kafka.model.CertAndKeySecretSourceBuilder;
import io.strimzi.api.kafka.model.DoneableKafka;
import io.strimzi.api.kafka.model.DoneableKafkaUser;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.KafkaUser;
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
    private static final String UI_ROUTE_NAME = CLUSTER_NAME + "-ibm-es-" + AdminUIModel.COMPONENT_NAME;
    private static final String REST_PRODUCER_ROUTE_NAME = CLUSTER_NAME + "-ibm-es-" + RestProducerModel.COMPONENT_NAME;
    private static final String SCHEMA_REGISTRY_ROUTE_NAME = CLUSTER_NAME + "-ibm-es-" + SchemaRegistryModel.COMPONENT_NAME;
    private static final String ADMIN_API_ROUTE_NAME = CLUSTER_NAME + "-ibm-es-" + AdminApiModel.COMPONENT_NAME;
    private static final String ROUTE_HOST_POSTFIX = "apps.route.test";
    private static final int EXPECTED_DEFAULT_REPLICAS = 1;
    private static final String REPLICATOR_DATA = "[replicatorTestData]";
    private static final String DEFAULT_VERSION = "2020.1.1";
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
        SECRETS,
        SERVICE_ACCOUNTS,
        NETWORK_POLICYS,
        KAFKAS,
        KAFKA_USERS,
        KAFKA_MIRROR_MAKER_2S
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
        Set<Secret> initialSecrets = new HashSet<>();
        ModelUtils.generateClusterCa(NAMESPACE, CLUSTER_NAME, APP_NAME, ModelUtils.Certificates.CLUSTER_CA, ModelUtils.Keys.CLUSTER_CA_KEY).forEach(s -> initialSecrets.add(s));
        ModelUtils.generateReplicatorConnectSecrets(NAMESPACE, CLUSTER_NAME, APP_NAME, ModelUtils.Certificates.CLUSTER_CA, ModelUtils.Keys.CLUSTER_CA_KEY).forEach(s -> initialSecrets.add(s));

        mockClient = new MockEventStreamsKube()
                .withInitialSecrets(initialSecrets)
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

        mockRoutes();
    }

    @AfterEach
    public void closeMockClient() {
        mockClient.close();
    }
        
    @Test
    public void testCreateDefaultEventStreamsInstanceOpenShift(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Map<String, Integer> expectedResourcesWithReplicas = getExpectedResourcesWithReplicas(CLUSTER_NAME);
        Set<String> expectedResources = expectedResourcesWithReplicas.keySet();
        Set<String> expectedServices = getExpectedServiceNames(CLUSTER_NAME);
        Set<String> expectedRoutes = getExpectedRouteNames(CLUSTER_NAME);
        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);
        Set<String> expectedKafkaUsers = getExpectedKafkaUsers(CLUSTER_NAME);
        Set<String> expectedKafkas = getExpectedKafkas(CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            context.verify(() -> {
                if (ar.failed()) {
                    ar.cause().printStackTrace(System.err);
                }
                assertTrue(ar.succeeded());
            });
            verifyHasOnlyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
            verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
            verifyHasOnlyResources(context, expectedServices, KubeResourceType.SERVICES);
            verifyHasOnlyResources(context, expectedRoutes, KubeResourceType.ROUTES);
            verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);

            verifyHasOnlyResources(context, expectedKafkas, KubeResourceType.KAFKAS);
            Set<HasMetadata> kafkas = getResources(NAMESPACE, KubeResourceType.KAFKAS);
            kafkas.forEach(user -> {
                for (Map.Entry<String, String> label: user.getMetadata().getLabels().entrySet()) {
                    assertThat("Kafka Custom Resources should not contain reserved domain labels", label.getKey(), not(containsString(io.strimzi.operator.common.model.Labels.STRIMZI_DOMAIN)));
                }
            });

            verifyHasOnlyResources(context, expectedKafkaUsers, KubeResourceType.KAFKA_USERS);
            Set<HasMetadata> kafkaUsers = getResources(NAMESPACE, KubeResourceType.KAFKA_USERS);
            kafkaUsers.forEach(user -> {
                for (Map.Entry<String, String> label: user.getMetadata().getLabels().entrySet()) {
                    if (!label.getKey().equals(io.strimzi.operator.common.model.Labels.STRIMZI_CLUSTER_LABEL)) {
                        assertThat("KafkaUser Custom Resources should not contain reserved domain labels, with the exception of the cluster label", label.getKey(), not(containsString(io.strimzi.operator.common.model.Labels.STRIMZI_DOMAIN)));
                    }
                }
            });

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
        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            context.verify(() -> assertTrue(ar.succeeded(), ar.toString()));
            verifyHasOnlyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
            verifyHasOnlyResources(context, expectedServices, KubeResourceType.SERVICES);
            verifyHasOnlyResources(context, expectedRoutes, KubeResourceType.ROUTES);
            verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
            verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
            async.flag();
        });
    }

    @Test
    public void testVersions(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(ar -> {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator).createOrUpdate(argument.capture());

                context.verify(() -> {
                    assertThat(argument.getValue().getStatus().getVersions().getReconciledVersion(), is(DEFAULT_VERSION));
                    assertThat(argument.getValue().getStatus().getVersions().getAvailable().getStrictVersions(), contains(DEFAULT_VERSION));
                    assertThat(argument.getValue().getStatus().getVersions().getAvailable().getLooseVersions(), contains("2020.1"));
                });

                async.flag();
            }));
    }

    @Test
    public void testDefaultClusterProducesEndpointsInStatus(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint(1);
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .setHandler(context.succeeding(v -> context.verify(() -> {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator).createOrUpdate(argument.capture());

                List<EventStreamsEndpoint> endpoints = argument.getValue().getStatus().getEndpoints();
                // check that there aren't duplicates in the list
                assertThat(endpoints, hasSize(3));

                // check that each expected endpoint is present
                assertThat(endpoints, Matchers.hasItems(
                    new EventStreamsEndpointBuilder()
                        .withName("admin")
                        .withType(EventStreamsEndpoint.EndpointType.api)
                        .withNewUri("https://" + ADMIN_API_ROUTE_NAME + "-" + Listener.EXTERNAL_TLS_NAME + "." + ROUTE_HOST_POSTFIX)
                        .build(),
                    new EventStreamsEndpointBuilder()
                        .withName("ui")
                        .withType(EventStreamsEndpoint.EndpointType.ui)
                        .withNewUri("https://" + UI_ROUTE_NAME + "." + ROUTE_HOST_POSTFIX)
                        .build(),
                    new EventStreamsEndpointBuilder()
                        .withName("schemaregistry")
                        .withType(EventStreamsEndpoint.EndpointType.api)
                        .withNewUri("https://" + SCHEMA_REGISTRY_ROUTE_NAME + "-external-tls." + ROUTE_HOST_POSTFIX)
                        .build()));
                context.completeNow();
            })));
    }

    @Test
    public void testFailWhenIAMNotPresent(VertxTestContext context) {
        mockRoutes();

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
        mockRoutes();

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
        mockRoutes();

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
        mockRoutes();

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
        mockRoutes();
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
        mockRoutes();
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
    public void testUpdateEventStreamsInstanceOpenShiftNoChanges(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator,
                                              imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Map<String, Integer> expectedResourcesWithReplicas = getExpectedResourcesWithReplicas(CLUSTER_NAME);
        Set<String> expectedResources = expectedResourcesWithReplicas.keySet();
        Set<String> expectedServices = getExpectedServiceNames(CLUSTER_NAME);
        Set<String> expectedRoutes = getExpectedRouteNames(CLUSTER_NAME);
        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);

        // Create a cluster
        Checkpoint async = context.checkpoint(2);

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(ar -> {
//                verifyHasOnlyResources(context, expectedConfigMaps, KubeResourceType.CONFIG_MAPS);
                verifyHasOnlyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
                verifyHasOnlyResources(context, expectedServices, KubeResourceType.SERVICES);
                verifyHasOnlyResources(context, expectedRoutes, KubeResourceType.ROUTES);
                verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
                verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
                async.flag();
                LOGGER.debug("Start updating cluster");
            }))
            // update the cluster
            .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster))
            .onComplete(context.succeeding(ar -> {
//                verifyHasOnlyResources(context, expectedConfigMaps, KubeResourceType.CONFIG_MAPS);
                verifyHasOnlyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
                verifyHasOnlyResources(context, expectedServices, KubeResourceType.SERVICES);
                verifyHasOnlyResources(context, expectedRoutes, KubeResourceType.ROUTES);
                verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
                verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
                async.flag();
            }));
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
            verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
            async.flag();
        });

        Set<HasMetadata> actualResources =  getActualResources(expectedSecrets, KubeResourceType.SECRETS);
        updateReplicatorSecretData(actualResources);

        //Refresh the cluster
        install.compose(v -> {
            return esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
                context.verify(() -> assertTrue(ar.succeeded()));
                LOGGER.debug("Refreshed cluster");
                verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
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

        mockRoutes();
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

        Kafka mockKafka = new Kafka();
        mockKafka.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build());
        mockKafka.setStatus(new KafkaStatusBuilder().withListeners(internalListener, externalListener).build());
        Optional<Kafka> mockKafkaInstance = Optional.of(mockKafka);
        when(esResourceOperator.getKafkaInstance(anyString(), anyString())).thenReturn(mockKafkaInstance);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).setHandler(ar -> {
            context.verify(() -> assertTrue(ar.succeeded(), ar.toString()));
            verifyHasOnlyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
            verifyHasOnlyResources(context, expectedServices, KubeResourceType.SERVICES);
            verifyHasOnlyResources(context, expectedRoutes, KubeResourceType.ROUTES);
            verifyReplicasInDeployments(context, expectedResourcesWithReplicas);

            String expectedInternalBootstrap = internalHost + ":" + internalPort;
            String expectedExternalBootstrap = externalHost + ":" + externalPort;
            String deploymentName = CLUSTER_NAME + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME;
            verifyKafkaBootstrapUrl(NAMESPACE, deploymentName, expectedInternalBootstrap);
            verifyKafkaBootstrapAdvertisedListeners(NAMESPACE, deploymentName, expectedExternalBootstrap);

            deploymentName = CLUSTER_NAME + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME;
            verifyKafkaBootstrapServers(NAMESPACE, deploymentName, expectedInternalBootstrap);

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
                assertEquals(EventStreamsAvailableVersions.LOOSE_VERSIONS, esCluster.getStatus().getVersions().getAvailable().getLooseVersions());
                assertEquals(EventStreamsAvailableVersions.STRICT_VERSIONS, esCluster.getStatus().getVersions().getAvailable().getStrictVersions());
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
        String routeName = endpointModel.getRouteName(listener.getName());
        Map<String, String> additionalHosts = Collections.singletonMap(routeName, "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("Number of secrets do not match " + mockClient.secrets().list().getItems(), mockClient.secrets().list().getItems().size(), is(6));
            Secret secret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            assertThat("The expected secret is created", secret, is(notNullValue()));
            CertAndKey certAndKey = reconciliationState.certificateManager.certificateAndKey(secret, endpointModel.getCertSecretCertID(listener.getName()), endpointModel.getCertSecretKeyID(listener.getName()));
            X509Certificate certificate = ControllerUtils.checkCertificate(reconciliationState.certificateManager, certAndKey);
            ControllerUtils.checkSans(context, reconciliationState.certificateManager, certificate, endpointModel.getExternalService(), additionalHosts.get(routeName));
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

        Map<String, String> additionalHosts = Collections.singletonMap(endpointModel.getRouteName(externalTlsListener.getName()), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems(), hasSize(6));
            Secret secret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            assertThat("The certificate secret should be created", secret, is(notNullValue()));
            assertThat("The secret does not contain cert key ID for plain listener", secret.getData().containsKey(endpointModel.getCertSecretKeyID(internalListener.getName())), is(false));
            assertThat("The secret does not contain cert ID for plain listener", secret.getData().containsKey(endpointModel.getCertSecretCertID(internalListener.getName())), is(false));

            CertAndKey internalTlsCertAndKey = reconciliationState.certificateManager.certificateAndKey(secret, endpointModel.getCertSecretCertID(internalTlsListener.getName()), endpointModel.getCertSecretKeyID(internalTlsListener.getName()));
            CertAndKey externalTlsCertAndKey = reconciliationState.certificateManager.certificateAndKey(secret, endpointModel.getCertSecretCertID(externalTlsListener.getName()), endpointModel.getCertSecretKeyID(externalTlsListener.getName()));

            X509Certificate internalTlsCertificate = ControllerUtils.checkCertificate(reconciliationState.certificateManager, internalTlsCertAndKey);
            X509Certificate externalTlsCertificate = ControllerUtils.checkCertificate(reconciliationState.certificateManager, externalTlsCertAndKey);
            ControllerUtils.checkSans(context, reconciliationState.certificateManager, internalTlsCertificate, endpointModel.getInternalService(), "");
            ControllerUtils.checkSans(context, reconciliationState.certificateManager, externalTlsCertificate, endpointModel.getExternalService(), additionalHosts.get(endpointModel.getRouteName(externalTlsListener.getName())));
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
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(6));
            Secret firstSecret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            reconciliationState.reconcileCerts(endpointModel, additionalHosts,  Date::new).setHandler(ar2 -> {
                assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(6));
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
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(6));
            Secret firstSecret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            reconciliationState.reconcileCerts(endpointModel, additionalHosts,  () -> Date.from(Instant.now().plusSeconds(TWO_YEARS_PLUS_IN_SECONDS))).setHandler(ar2 -> {
                assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(6));
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
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(6));
            Secret firstSecret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            List<Secret> newClusterCA = new ArrayList<>(ModelUtils.generateClusterCa(NAMESPACE, CLUSTER_NAME, APP_NAME, ModelUtils.Certificates.NEW_CLUSTER_CA, ModelUtils.Keys.NEW_CLUSTER_CA_KEY));
            mockClient.secrets().createOrReplace(newClusterCA.get(0));
            mockClient.secrets().createOrReplace(newClusterCA.get(1));
            reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar2 -> {
                assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(6));
                Secret secondSecret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
                assertThat("The secret has changed", secondSecret, not(firstSecret));
                async.flag();
            });
        });
    }

    @Test
    public void testEndpointCertificateSecretRegeneratedWhenSansAreChanged(VertxTestContext context) {
        Checkpoint async = context.checkpoint(1);
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);

        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();

        Listener internalTlsListener = Listener.internalTls();
        Listener externalTlsListener = Listener.externalTls();
        List<Listener> listeners = Arrays.asList(externalTlsListener, internalTlsListener);

        ModelUtils.EndpointModel endpointModel = new ModelUtils.EndpointModel(esCluster, NAMESPACE, "endpoint-component", listeners);
        String expectedRouteName = endpointModel.getRouteName(externalTlsListener.getName());
        Map<String, String> additionalHosts = Collections.singletonMap(expectedRouteName, "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(6));
            Secret firstSecret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            CertAndKey originalInternalTlsCertAndKey = reconciliationState.certificateManager.certificateAndKey(firstSecret, endpointModel.getCertSecretCertID(internalTlsListener.getName()), endpointModel.getCertSecretKeyID(internalTlsListener.getName()));
            Map<String, String> newHosts = Collections.singletonMap(Listener.externalTls().getName(), "extra.host.name.2");
            reconciliationState.reconcileCerts(endpointModel, newHosts, Date::new).setHandler(ar2 -> {
                assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(6));
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
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(7));
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
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(7));
            Secret secret = mockClient.secrets().withName(endpointModel.getCertSecretName()).get();
            assertThat("The admin api cert secret has not been populated with the internal provided cert", secret.getData().get(endpointModel.getCertSecretCertID(internalTlsListener.getName())), not(providedSecret.getData().get(secretCertificate)));
            assertThat("The admin api cert secret has not been populated with the internal provided key", secret.getData().get(endpointModel.getCertSecretKeyID(internalTlsListener.getName())), not(providedSecret.getData().get(secretKey)));
            CertAndKey internalTlsCertAndKey = reconciliationState.certificateManager.certificateAndKey(secret, endpointModel.getCertSecretCertID(internalTlsListener.getName()), endpointModel.getCertSecretKeyID(internalTlsListener.getName()));
            X509Certificate internalTlsCertificate = ControllerUtils.checkCertificate(reconciliationState.certificateManager, internalTlsCertAndKey);
            ControllerUtils.checkSans(context, reconciliationState.certificateManager, internalTlsCertificate, endpointModel.getInternalService(), "");
            assertThat("The admin api cert secret has been populated with the external provided cert", secret.getData().get(endpointModel.getCertSecretCertID(externalTlsListener.getName())), is(providedSecret.getData().get(secretCertificate)));
            assertThat("The admin api cert secret has been populated with the external provided key", secret.getData().get(endpointModel.getCertSecretKeyID(externalTlsListener.getName())), is(providedSecret.getData().get(secretKey)));
            mockClient.secrets().delete(providedSecret);
            async.flag();
        });
    }

    @Test
    public void testAllSecureEndpointModelsCertsCreatedOpenShift(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Checkpoint async = context.checkpoint(2);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState state = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        state.icpClusterData = Collections.emptyMap();
        Listener listener = Listener.externalTls();
        Listener.setEnabledListeners(Collections.singletonList(listener));

        CompositeFuture.join(state.createRestProducer(Date::new),
            state.createSchemaRegistry(Date::new),
            state.createAdminApi(Date::new))
            .setHandler(context.succeeding(v -> context.verify(() -> {
                List<Secret> secrets = mockClient.secrets().withLabel(Labels.INSTANCE_LABEL, CLUSTER_NAME).list().getItems();
                secrets.forEach(secret -> {
                    if (secret.getMetadata().getName().endsWith("-cert")) {
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
                        assertThat("We found the service for the secret " + secret.getMetadata().getName(), serviceOpt.isPresent(), is(true));
                        assertThat("We found the route for the secret " + secret.getMetadata().getName(), routeOpt.isPresent(), is(true));

                        String certID = secret.getData().keySet().stream().filter(string -> string.endsWith(CertificateSecretModel.formatCertID(listener.getName()))).findAny().get();
                        String keyID = secret.getData().keySet().stream().filter(string -> string.endsWith(CertificateSecretModel.formatKeyID(listener.getName()))).findAny().get();
                        CertAndKey certAndKey = state.certificateManager.certificateAndKey(secret, certID, keyID);
                        X509Certificate certificate = ControllerUtils.checkCertificate(state.certificateManager, certAndKey);
                        ControllerUtils.checkSans(context, state.certificateManager, certificate, serviceOpt.get(), routeOpt.get().getSpec().getHost());
                    }
                });
                async.flag();
            })));
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

        reconciliationState.reconcileRoutes(endpointModel, endpointModel.getRoutes()).setHandler(ar -> {
            if (ar.failed()) {
                context.failNow(ar.cause());
            }
            assertThat(ar.result().isEmpty(), is(true));
            async.flag();
        });
    }

    @Test
    public void testCreateOrUpdateRoutesMapOpenShiftNoExternalListeners(VertxTestContext context) {
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

        reconciliationState.reconcileRoutes(endpointModel, endpointModel.getRoutes()).setHandler(ar -> {
            if (ar.failed()) {
                context.failNow(ar.cause());
            }
            assertThat(ar.result().size(), is(0));
            async.flag();
        });
    }

    @Test
    public void testCreateOrUpdateRoutesMapOpenShift(VertxTestContext context) {
        Checkpoint async = context.checkpoint(1);
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        EventStreams esCluster = createESCluster(NAMESPACE, CLUSTER_NAME);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always"));
        reconciliationState.icpClusterData = Collections.emptyMap();

        Listener internalTlsListener = Listener.internalTls();
        Listener externalTlsListener = Listener.externalTls();
        Listener externalPlainListener = Listener.externalPlain();
        List<Listener> listeners = Arrays.asList(externalTlsListener, internalTlsListener, externalPlainListener);

        // Use admin api name for route matching with the mock client
        ModelUtils.EndpointModel endpointModel = new ModelUtils.EndpointModel(esCluster, NAMESPACE, "admin-api", listeners);

        reconciliationState.reconcileRoutes(endpointModel, endpointModel.getRoutes()).setHandler(context.succeeding(routes -> context.verify(() -> {
            assertThat(routes, aMapWithSize(2));
            String externalTlsListenerRoute = endpointModel.getRouteName(externalTlsListener.getName());
            String externalPlainListenerRoute = endpointModel.getRouteName(externalPlainListener.getName());

            assertThat(routes, hasKey(externalTlsListenerRoute));
            assertThat(routes, hasKey(externalPlainListenerRoute));

            assertThat("routes : " + routes.toString(), routes.get(externalTlsListenerRoute), is(formatRouteHost(ADMIN_API_ROUTE_NAME + "-" + Listener.EXTERNAL_TLS_NAME)));
            assertThat("routes : " + routes.toString(), routes.get(externalPlainListenerRoute), is(formatRouteHost(ADMIN_API_ROUTE_NAME + "-" + Listener.EXTERNAL_PLAIN_NAME)));
            async.flag();
        })));
    }

    @Test
    public void testCreateMinimalEventStreams(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams minimalCluster = new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(CLUSTER_NAME)
                        .withNewNamespace(NAMESPACE)
                        .build())
                .withNewSpec()
                    .withNewAppVersion(DEFAULT_VERSION)
                    .withNewAdminApi()
                    .endAdminApi()
                    .withStrimziOverrides(new KafkaSpecBuilder()
                            .withNewKafka()
                                .withReplicas(1)
                                .withNewListeners()
                                .endListeners()
                                .withNewEphemeralStorage()
                                .endEphemeralStorage()
                            .endKafka()
                            .withNewZookeeper()
                                .withReplicas(1)
                                .withNewEphemeralStorage()
                                .endEphemeralStorage()
                            .endZookeeper()
                            .build())
                .endSpec()
            .build();

        Set<String> expectedDeployments = new HashSet<>();
        expectedDeployments.add(CLUSTER_NAME + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME);

        Set<String> expectedServices = new HashSet<>();
        expectedServices.add(CLUSTER_NAME + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_SUFFIX);
        expectedServices.add(CLUSTER_NAME + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.INTERNAL_SERVICE_SUFFIX);

        // Set<String> expectedRoutes = new HashSet<>();
        // expectedRoutes.add(PROXY_ROUTE_NAME);
        // expectedRoutes.add(ADMIN_API_ROUTE_NAME);


        // Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);
        // expectedSecrets.add(CLUSTER_NAME + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + CertificateSecretModel.CERT_SECRET_NAME_POSTFIX);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalCluster).onComplete(context.succeeding(ar -> {
            verifyHasOnlyResources(context, expectedDeployments, KubeResourceType.DEPLOYMENTS);
            verifyHasOnlyResources(context, expectedServices, KubeResourceType.SERVICES);
            // verifyResources(context, expectedRoutes, KubeResourceType.ROUTES);
            // verifyResources(context, expectedSecrets, KubeResourceType.SECRETS);
            // verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
            async.flag();
        }));
    }

    @Test
    public void testComponentResourcesAreDeletedWhenRemovedFromCR(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
        EventStreams instanceMinimal = new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(CLUSTER_NAME)
                        .withNewNamespace(NAMESPACE)
                        .build())
                .withNewSpec()
                    .withNewAppVersion(DEFAULT_VERSION)
                    .withNewAdminApi()
                    .endAdminApi()
                    .withStrimziOverrides(new KafkaSpecBuilder()
                            .withNewKafka()
                                .withReplicas(1)
                                .withNewListeners()
                                .endListeners()
                                .withNewEphemeralStorage()
                                .endEphemeralStorage()
                            .endKafka()
                            .withNewZookeeper()
                                .withReplicas(1)
                                .withNewEphemeralStorage()
                                .endEphemeralStorage()
                            .endZookeeper()
                            .build())
                .endSpec()
            .build();

        EventStreams instance = new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder()
                    .withNewName(CLUSTER_NAME)
                    .withNewNamespace(NAMESPACE)
                .build())
                .withNewSpecLike(instanceMinimal.getSpec())
                    .withNewRestProducer()
                    .endRestProducer()
                    .withNewCollector()
                    .endCollector()
                    .withNewSchemaRegistry()
                    .endSchemaRegistry()
                    .withNewAdminUI()
                    .endAdminUI()
                .endSpec()
            .build();

        Set<String> expectedDeployments = new HashSet<>();
        expectedDeployments.add(CLUSTER_NAME + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME);
        expectedDeployments.add(CLUSTER_NAME + "-" + APP_NAME + "-" + CollectorModel.COMPONENT_NAME);
        expectedDeployments.add(CLUSTER_NAME + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME);
        expectedDeployments.add(CLUSTER_NAME + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME);

        boolean shouldExist = true;
        Checkpoint async = context.checkpoint(2);
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), instance)
            .onComplete(context.succeeding(ar -> {
                verifyContainsResources(context, expectedDeployments, KubeResourceType.DEPLOYMENTS, shouldExist);
                async.flag();
            }))
            .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), instanceMinimal))
            .onComplete(context.succeeding(ar -> {
                verifyContainsResources(context, expectedDeployments, KubeResourceType.DEPLOYMENTS, !shouldExist);
                async.flag();
            }));
    }

    @Test
    public void testRestProducerComponentCreatedAndDeletedWhenAddedAndRemovedFromCR(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        EventStreams minimalInstance = new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(CLUSTER_NAME)
                        .withNewNamespace(NAMESPACE)
                        .build())
                .withNewSpec()
                    .withNewAppVersion(DEFAULT_VERSION)
                    .withStrimziOverrides(new KafkaSpecBuilder()
                            .withNewKafka()
                                .withReplicas(1)
                                .withNewListeners()
                                .endListeners()
                                .withNewEphemeralStorage()
                                .endEphemeralStorage()
                            .endKafka()
                            .withNewZookeeper()
                                .withReplicas(1)
                                .withNewEphemeralStorage()
                                .endEphemeralStorage()
                            .endZookeeper()
                            .build())
                .endSpec()
                .build();

        EventStreams instance = new EventStreamsBuilder(minimalInstance)
                .editSpec()
                    .withNewRestProducer()
                        .withReplicas(1)
                    .endRestProducer()
                .endSpec()
                .build();

        String defaultComponentResourceName = CLUSTER_NAME + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME;

        String serviceAccountName = defaultComponentResourceName;
        String networkPolicyName = defaultComponentResourceName;
        String deploymentName = defaultComponentResourceName;
        String internalServiceName = defaultComponentResourceName + "-internal";
        String externalServiceName = defaultComponentResourceName + "-external";
        Set<String> serviceNames = new HashSet<>();
        serviceNames.add(internalServiceName);
        serviceNames.add(externalServiceName);
        String externalPlainRouteName = defaultComponentResourceName + "-" + Listener.EXTERNAL_PLAIN_NAME;
        String externalTlsRouteName = defaultComponentResourceName + "-" + Listener.EXTERNAL_TLS_NAME;
        Set<String> routeNames = new HashSet<>();
        routeNames.add(externalPlainRouteName);
        routeNames.add(externalTlsRouteName);

        Checkpoint async = context.checkpoint(3);

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance)
            .onComplete(context.succeeding(v -> {
                verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                verifyContainsResources(context, serviceNames, KubeResourceType.SERVICES, false);
                verifyContainsResources(context, routeNames, KubeResourceType.ROUTES, false);
                async.flag();
            }))
            .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), instance))
            .onComplete(context.succeeding(v -> {
                verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, true);
                verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, true);
                verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, true);
                verifyContainsResources(context, serviceNames, KubeResourceType.SERVICES, true);
                verifyContainsResources(context, routeNames, KubeResourceType.ROUTES, true);
                async.flag();
            }))
            .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance))
            .onComplete(context.succeeding(v -> {
                verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                verifyContainsResources(context, serviceNames, KubeResourceType.SERVICES, false);
                verifyContainsResources(context, routeNames, KubeResourceType.ROUTES, false);
                async.flag();
            }));
    }

    @Test
    public void testAdminUIComponentCreatedAndDeletedWhenAddedAndRemovedFromCR(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        EventStreams minimalInstance = new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(CLUSTER_NAME)
                        .withNewNamespace(NAMESPACE)
                        .build())
                .withNewSpec()
                .withNewAppVersion(DEFAULT_VERSION)
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                            .withReplicas(1)
                            .withNewListeners()
                            .endListeners()
                            .withNewEphemeralStorage()
                            .endEphemeralStorage()
                        .endKafka()
                        .withNewZookeeper()
                            .withReplicas(1)
                            .withNewEphemeralStorage()
                            .endEphemeralStorage()
                        .endZookeeper()
                        .build())
                .endSpec()
                .build();

        EventStreams instance = new EventStreamsBuilder(minimalInstance)
                .editSpec()
                    .withNewAdminUI()
                        .withReplicas(1)
                    .endAdminUI()
                .endSpec()
                .build();

        String defaultComponentResourceName = CLUSTER_NAME + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME;

        String serviceAccountName = defaultComponentResourceName;
        String networkPolicyName = defaultComponentResourceName;
        String deploymentName = defaultComponentResourceName;
        String serviceName = defaultComponentResourceName;
        String routeName = defaultComponentResourceName;

        Checkpoint async = context.checkpoint(3);

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance)
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                    verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                    verifyContainsResource(context, serviceName, KubeResourceType.SERVICES, false);
                    verifyContainsResource(context, routeName, KubeResourceType.ROUTES, false);
                    async.flag();
                }))
                .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), instance))
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, true);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, true);
                    verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, true);
                    verifyContainsResource(context, serviceName, KubeResourceType.SERVICES, true);
                    verifyContainsResource(context, routeName, KubeResourceType.ROUTES, true);
                    async.flag();
                }))
                .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance))
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                    verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                    verifyContainsResource(context, serviceName, KubeResourceType.SERVICES, false);
                    verifyContainsResource(context, routeName, KubeResourceType.ROUTES, false);
                    async.flag();
                }));
    }

    @Test
    public void testCollectorComponentCreatedAndDeletedWhenAddedAndRemovedFromCR(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        EventStreams minimalInstance = new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(CLUSTER_NAME)
                        .withNewNamespace(NAMESPACE)
                        .build())
                .withNewSpec()
                .withNewAppVersion(DEFAULT_VERSION)
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                        .withReplicas(1)
                        .withNewListeners()
                        .endListeners()
                        .withNewEphemeralStorage()
                        .endEphemeralStorage()
                        .endKafka()
                        .withNewZookeeper()
                        .withReplicas(1)
                        .withNewEphemeralStorage()
                        .endEphemeralStorage()
                        .endZookeeper()
                        .build())
                .endSpec()
                .build();

        EventStreams instance = new EventStreamsBuilder(minimalInstance)
                .editSpec()
                    .withNewCollector()
                        .withReplicas(1)
                    .endCollector()
                .endSpec()
                .build();

        String defaultComponentResourceName = CLUSTER_NAME + "-" + APP_NAME + "-" + CollectorModel.COMPONENT_NAME;

        String serviceAccountName = defaultComponentResourceName;
        String networkPolicyName = defaultComponentResourceName;
        String deploymentName = defaultComponentResourceName;
        String serviceName = defaultComponentResourceName;

        Checkpoint async = context.checkpoint(3);

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance)
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                    verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                    verifyContainsResource(context, serviceName, KubeResourceType.SERVICES, false);
                    async.flag();
                }))
                .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), instance))
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, true);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, true);
                    verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, true);
                    verifyContainsResource(context, serviceName, KubeResourceType.SERVICES, true);
                    async.flag();
                }))
                .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance))
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                    verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                    verifyContainsResource(context, serviceName, KubeResourceType.SERVICES, false);
                    async.flag();
                }));
    }

    @Test
    public void testReplicatorComponentCreatedAndDeletedWhenAddedAndRemovedFromCR(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        EventStreams minimalInstance = new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(CLUSTER_NAME)
                        .withNewNamespace(NAMESPACE)
                        .build())
                .withNewSpec()
                .withNewAppVersion(DEFAULT_VERSION)
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                            .withReplicas(1)
                            .withListeners(ModelUtils.getMutualTLSOnBothInternalAndExternalListenerSpec())
                            .withNewEphemeralStorage()
                            .endEphemeralStorage()
                        .endKafka()
                        .withNewZookeeper()
                            .withReplicas(1)
                            .withNewEphemeralStorage()
                            .endEphemeralStorage()
                        .endZookeeper()
                        .build())
                .endSpec()
                .build();

        EventStreams instance = new EventStreamsBuilder(minimalInstance)
                .editSpec()
                    .withNewReplicator()
                        .withReplicas(1)
                    .endReplicator()
                .endSpec()
                .build();

        String defaultComponentResourceName = CLUSTER_NAME + "-" + APP_NAME + "-" + ReplicatorModel.COMPONENT_NAME;

        String kafkaMirrorMaker2Name = defaultComponentResourceName;
        String networkPolicyName = defaultComponentResourceName;
        String secretName = defaultComponentResourceName + "-secret";
        Set<String> kafkaUserNames = new HashSet<>();
        String replicatorConnectUserName = CLUSTER_NAME + "-" + APP_NAME + "-rep-connect-user";
        String replicatorTargetConnectorUser = CLUSTER_NAME + "-" + APP_NAME + "-rep-target-user";
        String replicatorSourceConnectorUser = CLUSTER_NAME + "-" + APP_NAME + "-rep-source-user";
        kafkaUserNames.add(replicatorConnectUserName);
        kafkaUserNames.add(replicatorTargetConnectorUser);
        kafkaUserNames.add(replicatorSourceConnectorUser);

        Boolean secretNotOptional = true;

        Checkpoint async = context.checkpoint(3);

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance)
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, kafkaMirrorMaker2Name, KubeResourceType.KAFKA_MIRROR_MAKER_2S, false);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                    verifyContainsResources(context, kafkaUserNames, KubeResourceType.KAFKA_USERS, false);
                    verifyContainsResource(context, secretName, KubeResourceType.SECRETS, secretNotOptional);
                    async.flag();
                }))
                .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), instance))
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, kafkaMirrorMaker2Name, KubeResourceType.KAFKA_MIRROR_MAKER_2S, true);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, true);
                    verifyContainsResources(context, kafkaUserNames, KubeResourceType.KAFKA_USERS, true);
                    verifyContainsResource(context, secretName, KubeResourceType.SECRETS, secretNotOptional);
                    async.flag();
                }))
                .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance))
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, kafkaMirrorMaker2Name, KubeResourceType.KAFKA_MIRROR_MAKER_2S, false);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                    verifyContainsResources(context, kafkaUserNames, KubeResourceType.KAFKA_USERS, true);
                    verifyContainsResource(context, secretName, KubeResourceType.SECRETS, secretNotOptional);
                    async.flag();
                }));
    }

    @Test
    public void testSchemaRegistryComponentCreatedAndDeletedWhenAddedAndRemovedFromCR(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

        EventStreams minimalInstance = new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(CLUSTER_NAME)
                        .withNewNamespace(NAMESPACE)
                        .build())
                .withNewSpec()
                .withNewAppVersion(DEFAULT_VERSION)
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                            .withReplicas(1)
                            .withNewListeners()
                            .endListeners()
                            .withNewEphemeralStorage()
                            .endEphemeralStorage()
                        .endKafka()
                        .withNewZookeeper()
                            .withReplicas(1)
                            .withNewEphemeralStorage()
                            .endEphemeralStorage()
                        .endZookeeper()
                        .build())
                .endSpec()
                .build();

        EventStreams instance = new EventStreamsBuilder(minimalInstance)
                .editSpec()
                    .withNewSchemaRegistry()
                        .withReplicas(1)
                    .endSchemaRegistry()
                .endSpec()
                .build();

        String defaultComponentResourceName = CLUSTER_NAME + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME;

        String serviceAccountName = defaultComponentResourceName;
        String networkPolicyName = defaultComponentResourceName;
        String deploymentName = defaultComponentResourceName;
        String internalServiceName = defaultComponentResourceName + "-internal";
        String externalServiceName = defaultComponentResourceName + "-external";
        Set<String> serviceNames = new HashSet<>();
        serviceNames.add(internalServiceName);
        serviceNames.add(externalServiceName);
        String externalPlainRouteName = defaultComponentResourceName + "-" + Listener.EXTERNAL_PLAIN_NAME;
        String externalTlsRouteName = defaultComponentResourceName + "-" + Listener.EXTERNAL_TLS_NAME;
        Set<String> routeNames = new HashSet<>();
        routeNames.add(externalPlainRouteName);
        routeNames.add(externalTlsRouteName);

        Checkpoint async = context.checkpoint(3);

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance)
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                    verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                    verifyContainsResources(context, serviceNames, KubeResourceType.SERVICES, false);
                    verifyContainsResources(context, routeNames, KubeResourceType.ROUTES, false);
                    async.flag();
                }))
                .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), instance))
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, true);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, true);
                    verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, true);
                    verifyContainsResources(context, serviceNames, KubeResourceType.SERVICES, true);
                    verifyContainsResources(context, routeNames, KubeResourceType.ROUTES, true);
                    async.flag();
                }))
                .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance))
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                    verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                    verifyContainsResources(context, serviceNames, KubeResourceType.SERVICES, false);
                    verifyContainsResources(context, routeNames, KubeResourceType.ROUTES, false);
                    async.flag();
                }));
    }

    private void updateReplicatorSecretData(Set<HasMetadata> actualResourcesList) {
        actualResourcesList.forEach(item -> {
            if (item instanceof Secret) {
                Secret replicatorSecret = (Secret) item;

                if (replicatorSecret.getMetadata().getName().contains(ReplicatorModel.REPLICATOR_SECRET_NAME)) {
                    Encoder encoder = Base64.getEncoder();
                    String newSecretString = encoder.encodeToString(REPLICATOR_DATA.getBytes(StandardCharsets.UTF_8));
                    Map<String, String> newSecretData = Collections.singletonMap(ReplicatorModel.REPLICATOR_TARGET_CLUSTERS_SECRET_KEY_NAME, newSecretString);
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
                replicatorSecret.getData().get(ReplicatorModel.REPLICATOR_TARGET_CLUSTERS_SECRET_KEY_NAME), is(newSecretString)));
    }

    private void verifyKafkaBootstrapUrl(String namespace, String deploymentName, String expectedKafkaBootstrap) {
        List<EnvVar> envVars = getDeployEnvVars(namespace, deploymentName);
        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(expectedKafkaBootstrap).build();
        assertThat(envVars, hasItem(kafkaBootstrapUrlEnv));
    }

    private void verifyKafkaBootstrapServers(String namespace, String deploymentName, String expectedKafkaBootstrap) {
        List<EnvVar> envVars = getDeployEnvVars(namespace, deploymentName);
        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(expectedKafkaBootstrap).build();
        assertThat(envVars, hasItem(kafkaBootstrapUrlEnv));
    }

    private void verifyKafkaBootstrapAdvertisedListeners(String namespace, String deploymentName, String expectedExternalBootstrap) {
        List<EnvVar> envVars = getDeployEnvVars(namespace, deploymentName);
        EnvVar kafkaAdvertisedListenerEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_EXTERNAL_URL").withValue(expectedExternalBootstrap).build();
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

    private void verifyHasOnlyResources(VertxTestContext context, Set<String> expectedResources, KubeResourceType type) {
        Set<HasMetadata> actualResources =  getActualResources(expectedResources, type);
        Set<String> actualResourceNames = actualResources.stream().map(res -> res.getMetadata().getName()).collect(Collectors.toSet());
        context.verify(() -> assertThat(actualResourceNames, is(expectedResources)));
    }

    private void verifyContainsResources(VertxTestContext context, Set<String> resources, KubeResourceType type, boolean shouldExist) {
        Set<HasMetadata> actualResources =  getResources(NAMESPACE, type);
        Set<String> actualResourceNames = actualResources.stream().map(res -> res.getMetadata().getName()).collect(Collectors.toSet());
        if (shouldExist) {
            context.verify(() -> assertTrue(actualResourceNames.containsAll(resources), "for type: " + type + " expected: " + actualResourceNames.toString() + " to contain: " + resources.toString()));
        } else {
            context.verify(() -> assertFalse(actualResourceNames.containsAll(resources), "for type: " + type + " expected: " + actualResourceNames.toString() + " to not contain: " + resources.toString()));
        }
    }

    private void verifyContainsResource(VertxTestContext context, String resource, KubeResourceType type, boolean shouldExist) {
        Set<HasMetadata> actualResources =  getResources(NAMESPACE, type);
        Set<String> actualResourceNames = actualResources.stream().map(res -> res.getMetadata().getName()).collect(Collectors.toSet());
        if (shouldExist) {
            context.verify(() -> assertTrue(actualResourceNames.contains(resource), "for type: " + type + " expected: " + actualResourceNames.toString() + " to contain: " + resource));
        } else {
            context.verify(() -> assertFalse(actualResourceNames.contains(resource), "for type: " + type + " expected: " + actualResourceNames.toString() + " to not contain: " + resource));
        }
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
        expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
        expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
        expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
        expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
        expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + CollectorModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
        return expectedDeployments;
    }

    private Set<String> getExpectedServiceNames(String clusterName) {
        Set<String> expectedServices = new HashSet<>();
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_SUFFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_SUFFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_SUFFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.INTERNAL_SERVICE_SUFFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.INTERNAL_SERVICE_SUFFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + AbstractSecureEndpointModel.INTERNAL_SERVICE_SUFFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + CollectorModel.COMPONENT_NAME);
        return expectedServices;
    }

    private Set<String> getExpectedRouteNames(String clusterName) {
        Set<String> expectedRoutes = new HashSet<>();
        expectedRoutes.add(UI_ROUTE_NAME);
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
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + ReplicatorModel.REPLICATOR_SOURCE_CLUSTER_CONNECTOR_USER_NAME);
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + ReplicatorModel.REPLICATOR_TARGET_CLUSTER_CONNNECTOR_USER_NAME);
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + ReplicatorModel.REPLICATOR_CONNECT_USER_NAME);

        expectedSecrets.add(clusterName + "-cluster-ca");
        expectedSecrets.add(clusterName + "-cluster-ca-cert");

        return expectedSecrets;
    }

    private Set<String> getExpectedKafkas(String clusterName) {
        Set<String> expectedKafkas = new HashSet<>();
        expectedKafkas.add(clusterName);
        return expectedKafkas;
    }

    private Set<String> getExpectedKafkaUsers(String clusterName) {
        Set<String> expectedKafkaUsers = new HashSet<>();
        expectedKafkaUsers.add(clusterName + "-" + APP_NAME + "-" + ReplicatorModel.REPLICATOR_TARGET_CLUSTER_CONNNECTOR_USER_NAME);
        expectedKafkaUsers.add(clusterName + "-" + APP_NAME + "-" + ReplicatorModel.REPLICATOR_CONNECT_USER_NAME);
        expectedKafkaUsers.add(clusterName + "-" + APP_NAME + "-" + InternalKafkaUserModel.COMPONENT_NAME);

        return expectedKafkaUsers;
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
                result = new HashSet<>(mockClient.secrets().inNamespace(namespace).list().getItems());
                break;
            case SERVICE_ACCOUNTS:
                result = new HashSet<>(mockClient.serviceAccounts().inNamespace(namespace).list().getItems());
                break;
            case NETWORK_POLICYS:
                result = new HashSet<>(mockClient.network().networkPolicies().inNamespace(namespace).list().getItems());
                break;
            case KAFKA_USERS:
                result = new HashSet<>(mockClient.customResources(io.strimzi.api.kafka.Crds.kafkaUser(), KafkaUser.class, KafkaUserList.class, DoneableKafkaUser.class).inNamespace(namespace).list().getItems());
                break;
            case KAFKAS:
                result = new HashSet<>(mockClient.customResources(io.strimzi.api.kafka.Crds.kafka(), Kafka.class, KafkaList.class, DoneableKafka.class).inNamespace(namespace).list().getItems());
                break;
            case KAFKA_MIRROR_MAKER_2S:
                result = new HashSet<>(mockClient.customResources(io.strimzi.api.kafka.Crds.kafkaMirrorMaker2(), KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class, DoneableKafkaMirrorMaker2.class).inNamespace(namespace).list().getItems());
                break;
            default:
                System.out.println("Unexpected type " + type);
        }
        return result;
    }

    private EventStreams createESCluster(String namespace, String clusterName) {
        KafkaSpecBuilder kafka = new KafkaSpecBuilder()
            .editOrNewKafka()
                .withReplicas(3)
                .withNewListeners()
                    .withNewTls()
                        .withNewKafkaListenerAuthenticationTlsAuth()
                        .endKafkaListenerAuthenticationTlsAuth()
                    .endTls()
                .endListeners()
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
                    .withAppVersion(DEFAULT_VERSION)
                .endSpec()
                .build();
    }

    private String formatRouteHost(String name) {
        return String.format("%s.%s", name, ROUTE_HOST_POSTFIX);
    }

    private void createRoutesInMockClient() {
        List<Route> routes = new ArrayList<>();
        routes.add(createRoute(UI_ROUTE_NAME, NAMESPACE));
        routes.add(createRoute(REST_PRODUCER_ROUTE_NAME + "-" + Listener.EXTERNAL_TLS_NAME, NAMESPACE));
        routes.add(createRoute(SCHEMA_REGISTRY_ROUTE_NAME + "-" + Listener.EXTERNAL_TLS_NAME, NAMESPACE));
        routes.add(createRoute(ADMIN_API_ROUTE_NAME + "-" + Listener.EXTERNAL_TLS_NAME, NAMESPACE));
        routes.add(createRoute(REST_PRODUCER_ROUTE_NAME + "-" + Listener.EXTERNAL_PLAIN_NAME, NAMESPACE));
        routes.add(createRoute(SCHEMA_REGISTRY_ROUTE_NAME + "-" + Listener.EXTERNAL_PLAIN_NAME, NAMESPACE));
        routes.add(createRoute(ADMIN_API_ROUTE_NAME + "-" + Listener.EXTERNAL_PLAIN_NAME, NAMESPACE));

        routes.forEach(this::deployRouteInMockClient);
    }

    private void deployRouteInMockClient(Route route) {
        // dunno if needed
        when(routeOperator.get(anyString(), eq(route.getMetadata().getName()))).thenReturn(route);
        // hey is this right?
        mockClient.adapt(OpenShiftClient.class).routes().inNamespace(NAMESPACE).createOrReplace(route);
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

    private void mockRoutes() {
        routeOperator = mock(RouteOperator.class);

        int namespaceIndex = 0;
        int resourceNamesIndex = 1;
        int desiredRouteIndex = 2;

        when(routeOperator.reconcile(anyString(), anyString(), any())).thenAnswer(params -> {
            String namespace = params.getArgument(namespaceIndex);
            String name = params.getArgument(resourceNamesIndex);
            Route desiredRoute = params.getArgument(desiredRouteIndex);

            if (desiredRoute != null) {
                // Create a new route with a host
                // This mocks what would happen when a route is applied to OpenShift
                Route routeWithHost = new RouteBuilder(desiredRoute)
                        .editOrNewSpec()
                            .withNewHost(formatRouteHost(name))
                        .endSpec()
                        .build();
                deployRouteInMockClient(routeWithHost);
                return Future.succeededFuture(ReconcileResult.created(routeWithHost));
            } else {
                mockClient.adapt(OpenShiftClient.class)
                    .routes().inNamespace(namespace).withName(name)
                    .delete();
                return Future.succeededFuture(ReconcileResult.deleted());
            }
        });

        when(routeOperator.createOrUpdate(any(Route.class)))
            .thenAnswer(params -> {
                Route route = params.getArgument(0);
                deployRouteInMockClient(route);
                return Future.succeededFuture(ReconcileResult.created(route));
            });

    }
}
