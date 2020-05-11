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

import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.TlsVersion;
import com.ibm.eventstreams.api.model.AbstractModel;
import com.ibm.eventstreams.api.model.AbstractSecureEndpointsModel;
import com.ibm.eventstreams.api.model.AdminApiModel;
import com.ibm.eventstreams.api.model.AdminUIModel;
import com.ibm.eventstreams.api.model.CertificateSecretModel;
import com.ibm.eventstreams.api.model.ClusterSecretsModel;
import com.ibm.eventstreams.api.model.CollectorModel;
import com.ibm.eventstreams.api.model.InternalKafkaUserModel;
import com.ibm.eventstreams.api.model.MessageAuthenticationModel;
import com.ibm.eventstreams.api.model.ReplicatorSecretModel;
import com.ibm.eventstreams.api.model.ReplicatorSourceUsersModel;
import com.ibm.eventstreams.api.model.RestProducerModel;
import com.ibm.eventstreams.api.model.SchemaRegistryModel;
import com.ibm.eventstreams.api.model.utils.MockEventStreamsKube;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EndpointSpec;
import com.ibm.eventstreams.api.spec.EndpointSpecBuilder;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.EventStreamsReplicator;
import com.ibm.eventstreams.api.spec.SchemaRegistrySpec;
import com.ibm.eventstreams.api.spec.SecurityComponentSpec;
import com.ibm.eventstreams.api.spec.SecurityComponentSpecBuilder;
import com.ibm.eventstreams.api.spec.SecuritySpecBuilder;
import com.ibm.eventstreams.api.status.EventStreamsAvailableVersions;
import com.ibm.eventstreams.api.status.EventStreamsEndpoint;
import com.ibm.eventstreams.api.status.EventStreamsEndpointBuilder;
import com.ibm.eventstreams.api.status.EventStreamsStatus;
import com.ibm.eventstreams.api.status.EventStreamsStatusBuilder;
import com.ibm.eventstreams.api.status.EventStreamsVersions;
import com.ibm.eventstreams.controller.utils.ConditionUtils;
import com.ibm.eventstreams.controller.utils.ControllerUtils;
import com.ibm.eventstreams.controller.utils.MetricsUtils;
import com.ibm.eventstreams.rest.AuthenticationValidation;
import com.ibm.iam.api.controller.Cp4iServicesBindingResourceOperator;
import com.ibm.iam.api.spec.Cp4iServicesBinding;
import com.ibm.iam.api.spec.Cp4iServicesBindingDoneable;
import com.ibm.iam.api.spec.Cp4iServicesBindingList;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.DoneableSecret;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionList;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionListBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.strimzi.api.kafka.KafkaList;
import io.strimzi.api.kafka.KafkaUserList;
import io.strimzi.api.kafka.model.CertAndKeySecretSource;
import io.strimzi.api.kafka.model.CertAndKeySecretSourceBuilder;
import io.strimzi.api.kafka.model.DoneableKafka;
import io.strimzi.api.kafka.model.DoneableKafkaUser;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternalConfigurationBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternalRouteBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenerTlsBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenersBuilder;
import io.strimzi.api.kafka.model.listener.TlsListenerConfigurationBuilder;
import io.strimzi.api.kafka.model.status.KafkaStatusBuilder;
import io.strimzi.api.kafka.model.status.KafkaUserStatusBuilder;
import io.strimzi.api.kafka.model.status.ListenerAddressBuilder;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.status.ListenerStatusBuilder;
import io.strimzi.certs.CertAndKey;
import io.strimzi.operator.KubernetesVersion;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.strimzi.operator.common.MetricsProvider;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.operator.resource.ReconcileResult;
import io.strimzi.operator.common.operator.resource.RouteOperator;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
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

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.ibm.eventstreams.api.model.AbstractModel.APP_NAME;
import static com.ibm.eventstreams.api.model.AbstractModel.AUTHENTICATION_LABEL_SEPARATOR;
import static com.ibm.eventstreams.api.model.AbstractSecureEndpointsModel.getInternalServiceName;
import static com.ibm.eventstreams.api.model.InternalKafkaUserModel.getInternalKafkaUserName;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasProperty;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@SuppressWarnings({"checkstyle:ClassFanOutComplexity", "checkstyle:ClassDataAbstractionCoupling", "checkstyle:JavaNCSS"})
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
    private static final String DEFAULT_VERSION = "2020.2.1";
    private static final int TWO_YEARS_PLUS_IN_SECONDS = 70000000;
    private static final String CP4I_TEST_HEADER_URL = "https://icp4i-services-demo.my-ns.svc.cluster.local:3000";
    private static final String CP4I_ADMIN_UI_ENVAR_NAME = "ICP4I_PLATFORM_SERVICES_URL";
    private static final String IAM_BEARER_LABEL = "IAM-BEARER";
    private static final String SCRAM_SHA_512_LABEL = "SCRAM-SHA-512";
    private static final String TLS_LABEL = "TLS";

    private static Vertx vertx;
    private KubernetesClient mockClient;
    private EventStreamsResourceOperator esResourceOperator;
    private Cp4iServicesBindingResourceOperator cp4iResourceOperator;
    private EventStreamsReplicatorResourceOperator esReplicatorResourceOperator;
    private EventStreamsOperator esOperator;
    private KafkaUserOperator kafkaUserOperator;
    private EventStreamsOperatorConfig.ImageLookup imageConfig;
    private RouteOperator routeOperator;
    private PlatformFeaturesAvailability pfa;
    private MetricsProvider metricsProvider;

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
    }

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

        EventStreams mockEventStreams = ModelUtils.createDefaultEventStreams(CLUSTER_NAME).build();

        Kafka mockKafka = new Kafka();
        mockKafka.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build());
        mockKafka.setStatus(new KafkaStatusBuilder().withConditions(ConditionUtils.getReadyCondition()).build());
        Optional<Kafka> mockKafkaInstance = Optional.of(mockKafka);

        KafkaUser mockKafkaUser = new KafkaUser();
        mockKafkaUser.setMetadata(new ObjectMetaBuilder().withName(getInternalKafkaUserName(CLUSTER_NAME)).withNamespace(NAMESPACE).build());
        mockKafkaUser.setStatus(new KafkaUserStatusBuilder()
            .withConditions(ConditionUtils.getReadyCondition())
            .withNewUsername(InternalKafkaUserModel.getInternalKafkaUserName(CLUSTER_NAME))
            .build());
        Optional<KafkaUser> mockKafkaUserInstance = Optional.of(mockKafkaUser);

        esResourceOperator = mock(EventStreamsResourceOperator.class);
        when(esResourceOperator.kafkaCRHasStoppedDeploying(anyString(), anyString(), anyLong(), anyLong())).thenReturn(Future.succeededFuture());
        when(esResourceOperator.createOrUpdate(any(EventStreams.class))).thenReturn(Future.succeededFuture());
        when(esResourceOperator.getKafkaInstance(anyString(), anyString())).thenReturn(mockKafkaInstance);
        when(esResourceOperator.getAsync(anyString(), anyString())).thenReturn(Future.succeededFuture(mockEventStreams));
        when(esResourceOperator.updateEventStreamsStatus(any(EventStreams.class))).thenReturn(Future.succeededFuture(mockEventStreams));

        Cp4iServicesBinding mockCp4i = new Cp4iServicesBinding();
        mockCp4i.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build());

        cp4iResourceOperator = mockCp4iServicesBinding(CP4I_TEST_HEADER_URL, true);
        when(cp4iResourceOperator.waitForCp4iServicesBindingStatusAndMaybeGetUrl(anyString(), anyString(), anyLong(), anyLong(), any())).thenReturn(Future.succeededFuture());

        MixedOperation<Cp4iServicesBinding, Cp4iServicesBindingList, Cp4iServicesBindingDoneable, Resource<Cp4iServicesBinding, Cp4iServicesBindingDoneable>> mockCp4iCr = mock(MixedOperation.class);
        when(cp4iResourceOperator.createCp4iMixedOperation(any())).thenReturn(mockCp4iCr);

        EventStreamsReplicator mockEventStreamsReplicator = ModelUtils.createDefaultEventStreamsReplicator(CLUSTER_NAME).build();
        esReplicatorResourceOperator = mock(EventStreamsReplicatorResourceOperator.class);
        when(esReplicatorResourceOperator.getAsync(anyString(), anyString())).thenReturn(Future.succeededFuture(mockEventStreamsReplicator));

        NonNamespaceOperation mockNamespaceOperation = mock(NonNamespaceOperation.class);
        Resource<Cp4iServicesBinding, Cp4iServicesBindingDoneable> res = mock(Resource.class);
        when(mockCp4iCr.inNamespace(anyString())).thenReturn(mockNamespaceOperation);
        when(mockCp4iCr.inNamespace(anyString()).withName(anyString())).thenReturn(res);

        kafkaUserOperator = mock(KafkaUserOperator.class);
        when(kafkaUserOperator.getAsync(anyString(), anyString())).thenReturn(Future.succeededFuture(mockKafkaUser));
        when(kafkaUserOperator.kafkaUserHasStoppedDeploying(anyString(), anyString(), anyLong(), anyLong())).thenReturn(Future.succeededFuture());
        when(kafkaUserOperator.getKafkaUser(anyString(), anyString())).thenReturn(mockKafkaUserInstance);
        when(kafkaUserOperator.reconcile(anyString(), anyString(), any())).thenAnswer(i -> {
            KafkaUser kafkaUser = i.getArgument(2);
            if (kafkaUser != null) {
                KafkaUser reconciledKafkaUser = new KafkaUserBuilder(kafkaUser)
                    .withNewStatus()
                    .withNewUsername(kafkaUser.getMetadata().getName())
                    .endStatus().build();
                mockClient.customResources(io.strimzi.api.kafka.Crds.kafkaUser(), KafkaUser.class, KafkaUserList.class, DoneableKafkaUser.class).inNamespace(NAMESPACE).createOrReplace(reconciledKafkaUser);
                return Future.succeededFuture(ReconcileResult.created(kafkaUser));
            } else {
                return Future.succeededFuture(ReconcileResult.deleted());
            }
        });

        pfa = mock(PlatformFeaturesAvailability.class);
        when(pfa.hasRoutes()).thenReturn(true);

        metricsProvider = MetricsUtils.createMockMetricsProvider();

        // mock ICP Config Map is present
        Map<String, String> configMapData = new HashMap<>();
        configMapData.put("cluster_address", "0.0.0.0");
        ConfigMap testICPConfigMap = new ConfigMap();
        testICPConfigMap.setData(configMapData);
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

        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        Map<String, Integer> expectedResourcesWithReplicas = getExpectedResourcesWithReplicas(CLUSTER_NAME);
        Set<String> expectedResources = expectedResourcesWithReplicas.keySet();
        Set<String> expectedServices = getExpectedServiceNames(CLUSTER_NAME);
        Set<String> expectedRoutes = getExpectedRouteNames(CLUSTER_NAME);
        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);
        Set<String> expectedKafkaUsers = getExpectedKafkaUsers(CLUSTER_NAME);
        Set<String> expectedKafkas = getExpectedKafkas(CLUSTER_NAME);
        Set<String> expectedNetworkPolicies = getExpectedNetworkPolicyNames(CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                verifyHasOnlyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
                verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
                verifyHasOnlyResources(context, expectedServices, KubeResourceType.SERVICES);
                verifyHasOnlyResources(context, expectedRoutes, KubeResourceType.ROUTES);
                verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
                verifyHasOnlyResources(context, expectedNetworkPolicies, KubeResourceType.NETWORK_POLICYS);

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
            })));
    }

    @Test
    public void testCreateDefaultEventStreamsInstanceK8s(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator,
                                              cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider,
                                              kafkaStatusReadyTimeoutMs);

        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        Map<String, Integer> expectedResourcesWithReplicas = getExpectedResourcesWithReplicas(CLUSTER_NAME);
        Set<String> expectedResources = expectedResourcesWithReplicas.keySet();
        Set<String> expectedServices = getExpectedServiceNames(CLUSTER_NAME);
        Set<String> expectedRoutes = new HashSet<>();
        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);
        Set<String> expectedNetworkPolicies = getExpectedNetworkPolicyNames(CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                verifyHasOnlyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
                verifyHasOnlyResources(context, expectedServices, KubeResourceType.SERVICES);
                verifyHasOnlyResources(context, expectedRoutes, KubeResourceType.ROUTES);
                verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
                verifyHasOnlyResources(context, expectedNetworkPolicies, KubeResourceType.NETWORK_POLICYS);
                verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
                async.flag();
            })));
    }

    @Test
    public void testStatusHasCorrectVersions(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(argument.capture());
                assertThat(argument.getValue().getStatus().getVersions().getReconciled(), is(DEFAULT_VERSION));
                assertThat(argument.getValue().getStatus().getVersions().getAvailable().getVersions(),
                        hasItem(hasProperty("name", is(DEFAULT_VERSION))));
                assertThat(argument.getValue().getStatus().getVersions().getAvailable().getChannels(),
                        hasItem(hasProperty("name", is("2020.2"))));
                async.flag();
            })));
    }

    @Test
    public void testStatusHasAuthenticationChangeWhenAuthChanged(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        EventStreams unauthES = new EventStreamsBuilder()
            .withMetadata(new ObjectMetaBuilder()
                    .withNewName(CLUSTER_NAME)
                    .withNewNamespace(NAMESPACE)
                    .build())
            .withNewSpec()
            .withNewLicense()
                .withAccept(true)
            .endLicense()
            .withNewVersion(DEFAULT_VERSION)
            .withNewAdminApi()
                .withReplicas(1)
                .withEndpoints(new EndpointSpecBuilder()
                    .withNewName("auth")
                    .withContainerPort(9999)
                    .withAuthenticationMechanisms("TEST_AUTH")
                .build())
            .endAdminApi()
            .withStrimziOverrides(new KafkaSpecBuilder()
                    .withNewKafka()
                        .withReplicas(1)
                        .withNewListeners()
                            .withNewPlain()
                            .endPlain()
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

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), unauthES)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(argument.capture());

                EventStreamsStatus status = argument.getValue().getStatus();
                assertThat(status.getConditions(), hasItem(hasProperty("reason", is("AuthenticationConfigurationWarning"))));
                assertThat(status.getConditions(), hasItem(hasProperty("message", is(AuthenticationValidation.AUTH_ENDPOINT_UNAUTH_ES_WARNING))));
            })))
            .map(v -> {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(argument.capture());
                EventStreams authEs = new EventStreamsBuilder(unauthES)
                    .editSpec()
                        .withNewAdminApi()
                            .withEndpoints(new EndpointSpecBuilder()
                                .withContainerPort(9999)
                                .withNewName("access")
                                .withAuthenticationMechanisms(Collections.emptyList())
                            .build())
                        .endAdminApi()
                        .withStrimziOverrides(new KafkaSpecBuilder()
                            .withNewKafka()
                                .withNewListeners()
                                    .withNewPlain()
                                    .endPlain()
                                    .withNewTls()
                                        .withNewKafkaListenerAuthenticationScramSha512Auth()
                                        .endKafkaListenerAuthenticationScramSha512Auth()
                                    .endTls()
                                .endListeners()
                            .endKafka()
                        .build())
                    .endSpec()
                    .build();

                return authEs;
            })
            .compose(authEs -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), authEs))
            .onComplete(context.succeeding(v -> context.verify(() -> {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(3)).updateEventStreamsStatus(argument.capture());

                EventStreamsStatus status = argument.getValue().getStatus();
                assertThat(status.getConditions(), hasItem(hasProperty("reason", is("AuthenticationConfigurationWarning"))));
                assertThat(status.getConditions(), hasItem(hasProperty("message", is(AuthenticationValidation.UNAUTH_ENDPOINT_AUTH_ES_WARNING))));
                async.flag();
            })));
    }

    @Test
    public void testKafkaWarningsAreReported(VertxTestContext context) {
        Kafka mockKafka = new Kafka();
        mockKafka.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build());
        mockKafka.setStatus(new KafkaStatusBuilder().withConditions(ConditionUtils.getReadyConditionsWithWarnings()).build());
        Optional<Kafka> mockKafkaInstance = Optional.of(mockKafka);
        when(esResourceOperator.getKafkaInstance(anyString(), anyString())).thenReturn(mockKafkaInstance);

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(argument.capture());

                EventStreams val = argument.getValue();
                assertThat(val.getStatus().getPhase(), is("Running"));
                assertThat(val.getStatus().getConditions(), hasItem(hasProperty("reason", is("KafkaStorage"))));
                assertThat(val.getStatus().getConditions(), hasItem(hasProperty("reason", is("ZooKeeperStorage"))));

                async.flag();
            })));
    }

    @Test
    public void testKafkaFailuresAreReported(VertxTestContext context) {
        Kafka mockKafka = new Kafka();
        mockKafka.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build());
        mockKafka.setStatus(new KafkaStatusBuilder().withConditions(ConditionUtils.getFailureCondition()).build());
        Optional<Kafka> mockKafkaInstance = Optional.of(mockKafka);
        when(esResourceOperator.getKafkaInstance(anyString(), anyString())).thenReturn(mockKafkaInstance);

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.failing(e -> context.verify(() -> {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(argument.capture());
                EventStreams val = argument.getValue();
                assertThat(val.getStatus().getPhase(), is("Failed"));
                assertThat(val.getStatus().getConditions(), hasItem(hasProperty("reason", is("MockFailure"))));

                async.flag();
            })));
    }

    @Test
    public void testDefaultClusterHasEndpointsInStatus(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(argument.capture());

                List<EventStreamsEndpoint> endpoints = argument.getValue().getStatus().getEndpoints();
                // check that there aren't duplicates in the list
                assertThat(endpoints, hasSize(3));

                // check that each expected endpoint is present
                assertThat(endpoints, Matchers.hasItems(
                    new EventStreamsEndpointBuilder()
                        .withName("admin")
                        .withType(EventStreamsEndpoint.EndpointType.API)
                        .withNewUri("https://" + ADMIN_API_ROUTE_NAME + "-" + Endpoint.DEFAULT_EXTERNAL_NAME + "." + ROUTE_HOST_POSTFIX)
                        .build(),
                    new EventStreamsEndpointBuilder()
                        .withName("ui")
                        .withType(EventStreamsEndpoint.EndpointType.UI)
                        .withNewUri("https://" + UI_ROUTE_NAME + "." + ROUTE_HOST_POSTFIX)
                        .build(),
                    new EventStreamsEndpointBuilder()
                        .withName("schemaregistry")
                        .withType(EventStreamsEndpoint.EndpointType.API)
                        .withNewUri("https://" + SCHEMA_REGISTRY_ROUTE_NAME + "-" +  Endpoint.DEFAULT_EXTERNAL_NAME + "." + ROUTE_HOST_POSTFIX)
                        .build()));
                async.flag();
            })));
    }

    @Test
    public void testIamNotPresentThrows(VertxTestContext context) {
        mockRoutes();

        // mock ICP Config Map not present
        NonNamespaceOperation mockNamespaceOperation = mock(NonNamespaceOperation.class);
        Resource<ConfigMap, DoneableConfigMap> mockResource = mock(Resource.class);
        when(mockClient.configMaps().inNamespace("kube-public")).thenReturn(mockNamespaceOperation);
        when(mockNamespaceOperation.withName("ibmcloud-cluster-info")).thenReturn(mockResource);
        when(mockResource.get()).thenReturn(null);

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.failing(e -> context.verify(() -> {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(3)).updateEventStreamsStatus(argument.capture());
                assertThat(argument.getValue().getStatus().getConditions(),
                        hasItem(hasProperty("message", is("Could not retrieve cloud pak resources"))));
                async.flag();
            })));
    }

    @Test
    public void testIAMPresentIsFalseInStatusWhenExceptionThrownGettingICPConfigMap(VertxTestContext context) {
        mockRoutes();

        // mock an exception when attempting to get ICP Config Map
        when(mockClient.configMaps().inNamespace("kube-public")).thenThrow(new KubernetesClientException("Exception"));

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.failing(e -> context.verify(() -> {
                assertThat(e.getMessage(), is("Exit Reconcile as IAM not present"));

                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(3)).updateEventStreamsStatus(argument.capture());
                assertThat(argument.getValue().getStatus().getConditions(),
                        hasItem(hasProperty("message", is("Could not retrieve cloud pak resources"))));
                async.flag();
            })));
    }

    @Test
    public void testCustomImagesOverride(VertxTestContext context) {
        mockRoutes();

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        esCluster.getSpec().getAdminUI().setImage("adminUi-image:test");
        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(argument.capture());
                assertThat(argument.getValue().getStatus().isCustomImages(), is(true));
                async.flag();
            })));
    }

    @Test
    public void testCustomImagesOverrideWithDefaultIBMCom(VertxTestContext context) {
        mockRoutes();

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        esCluster.getSpec().getAdminApi().setImage(AdminApiModel.DEFAULT_IBMCOM_IMAGE);
        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(argument.capture());
                assertThat(argument.getValue().getStatus().isCustomImages(), is(false));
                async.flag();
            })));
    }

    @Test
    public void testEventStreamsNameTooLongThrows(VertxTestContext context) {
        mockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        // 17 Characters long
        String clusterName = "long-instancename";

        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, clusterName);
        ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
        Checkpoint async = context.checkpoint();

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, clusterName), esCluster)
                .onComplete(context.failing(e -> context.verify(() -> {
                    assertThat(e.getMessage(), is("Invalid Event Streams specification: further details in the status conditions"));
                    // check status
                    verify(esResourceOperator, times(2)).updateEventStreamsStatus(updatedEventStreams.capture());
                    assertThat("Status is incorrect, found status : " + updatedEventStreams.getValue().getStatus(),
                            updatedEventStreams.getValue().getStatus().getConditions().get(0).getMessage(),
                            is("Invalid custom resource: EventStreams metadata name not accepted"));
                    async.flag();
                })));
    }

    @Test
    public void testEventStreamsInvalidNameThrows(VertxTestContext context) {
        mockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        String clusterName = "bad.char";

        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, clusterName);
        ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
        Checkpoint async = context.checkpoint();

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, clusterName), esCluster)
                .onComplete(context.failing(e -> context.verify(() -> {
                    assertThat(e.getMessage(), is("Invalid Event Streams specification: further details in the status conditions"));
                    // check status
                    verify(esResourceOperator, times(2)).updateEventStreamsStatus(updatedEventStreams.capture());
                    assertThat("Status is incorrect, found status : " + updatedEventStreams.getValue().getStatus(),
                            updatedEventStreams.getValue().getStatus().getConditions().get(0).getMessage(),
                            is("Invalid custom resource: EventStreams metadata name not accepted"));
                    async.flag();
                })));
    }

    @Test
    public void testEventStreamsUnsupportedVersionThrows(VertxTestContext context) {
        mockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        String clusterName = "instancename";
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, clusterName);
        esCluster.getSpec().setVersion("2018.1.1");
        ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
        Checkpoint async = context.checkpoint();

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, clusterName), esCluster)
            .onComplete(context.failing(e -> context.verify(() -> {

                assertThat(e.getMessage(), is("Invalid Event Streams specification: further details in the status conditions"));
                // check status
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(updatedEventStreams.capture());
                assertThat("Status is incorrect, found status : " + updatedEventStreams.getValue().getStatus(),
                        updatedEventStreams.getValue().getStatus().getConditions().get(0).getMessage().equals("Invalid custom resource: Unsupported version. Supported versions are [2020.2.1, 2020.2]"));
                async.flag();
            })));
    }

    @Test
    public void testEventStreamsUIWithoutAPIThrows(VertxTestContext context) {
        mockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        String clusterName = "instancename";
        EventStreams esCluster = new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(clusterName).withNamespace(NAMESPACE).build())
                .withNewSpec()
                    .withNewLicense()
                        .withAccept(true)
                    .endLicense()
                    .withVersion(EventStreamsVersions.OPERAND_VERSION)
                    .withNewAdminUI()
                        .withReplicas(1)
                    .endAdminUI()
                    .withStrimziOverrides(new KafkaSpecBuilder()
                            .withNewKafka()
                                .withNewListeners()
                                    .withNewPlain().endPlain()
                                .endListeners()
                            .endKafka()
                            .build())
                .endSpec()
                .build();

        ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
        Checkpoint async = context.checkpoint();

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, clusterName), esCluster)
                .onComplete(context.failing(e -> context.verify(() -> {
                    assertThat(e.getMessage(), is("Invalid Event Streams specification: further details in the status conditions"));

                    verify(esResourceOperator, times(2)).updateEventStreamsStatus(updatedEventStreams.capture());
                    EventStreamsStatus status = updatedEventStreams.getValue().getStatus();
                    assertThat(status.getPhase(), is("Failed"));
                    assertThat(status.getConditions().get(0).getMessage(), is("adminApi is a required component to enable adminUi"));
                    async.flag();
                })));
    }


    @Test
    public void testEventStreamsInvalidListenerAuthenticationOauthThrows(VertxTestContext context) {
        mockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        KafkaSpec kafka = new KafkaSpecBuilder()
                .editOrNewKafka()
                    .withReplicas(3)
                    .withNewListeners()
                        .withNewTls()
                            .withNewKafkaListenerAuthenticationOAuth()
                            .endKafkaListenerAuthenticationOAuth()
                        .endTls()
                        .withNewKafkaListenerExternalRoute()
                            .withNewKafkaListenerAuthenticationOAuth()
                            .endKafkaListenerAuthenticationOAuth()
                        .endKafkaListenerExternalRoute()
                    .endListeners()
                .endKafka()
                .editOrNewZookeeper()
                    .withReplicas(3)
                .endZookeeper()
                .build();

        EventStreams eventStreams = createEventStreamsWithStrimziOverrides(NAMESPACE, CLUSTER_NAME, kafka);

        ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), eventStreams)
            .onComplete(context.failing(e -> context.verify(() -> {
                assertThat(e.getMessage(), is("Invalid Event Streams specification: further details in the status conditions"));
                // check status
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(updatedEventStreams.capture());
                assertThat("Status is incorrect, found status : " + updatedEventStreams.getValue().getStatus(),
                        updatedEventStreams.getValue().getStatus().getConditions().get(0).getMessage(),
                        is("Listener client authentication unsupported for Geo Replication. Supported versions are TLS and SCRAM"));
                async.flag();
            })));
    }

    @Test
    public void testUpdateEventStreamsInstanceOpenShiftNoChanges(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator,
                                              cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider,
                                              kafkaStatusReadyTimeoutMs);

        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        Map<String, Integer> expectedResourcesWithReplicas = getExpectedResourcesWithReplicas(CLUSTER_NAME);
        Set<String> expectedResources = expectedResourcesWithReplicas.keySet();
        Set<String> expectedServices = getExpectedServiceNames(CLUSTER_NAME);
        Set<String> expectedRoutes = getExpectedRouteNames(CLUSTER_NAME);
        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);

        // Create a cluster
        Checkpoint async = context.checkpoint();

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> {
//                verifyHasOnlyResources(context, expectedConfigMaps, KubeResourceType.CONFIG_MAPS);
                verifyHasOnlyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
                verifyHasOnlyResources(context, expectedServices, KubeResourceType.SERVICES);
                verifyHasOnlyResources(context, expectedRoutes, KubeResourceType.ROUTES);
                verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
                verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
                LOGGER.debug("Start updating cluster");
            }))
            // update the cluster
            .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster))
            .onComplete(context.succeeding(v -> {
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
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);
        Checkpoint async = context.checkpoint(3);

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
                async.flag();
            })))

            //Refresh the cluster
            .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster))
            .onComplete(context.succeeding(v -> context.verify(() -> {
                LOGGER.debug("Refreshed cluster");
                verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
                async.flag();
            })));

        Set<HasMetadata> actualResources =  getActualResources(expectedSecrets, KubeResourceType.SECRETS);
        updateReplicatorSecretData(actualResources);

        verifyReplicatorSecretDataIsUnchanged(context, actualResources);
        async.flag();
    }

    @Test
    public void testKafkaBootstrapRetrievedFromStatus(VertxTestContext context) {
        final String internalListenerType = "tls";
        final String internalHost = "internalHost";
        final Integer internalPort = 1234;

        final String externalListenerType = "external";
        final String externalHost = "externalHost";
        final Integer externalPort = 9876;

        mockRoutes();
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

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
                .withConditions(ConditionUtils.getReadyCondition())
                .build();

        esCluster.setStatus(status);

        Map<String, Integer> expectedResourcesWithReplicas = getExpectedResourcesWithReplicas(CLUSTER_NAME);
        Set<String> expectedResources = expectedResourcesWithReplicas.keySet();
        Set<String> expectedServices = getExpectedServiceNames(CLUSTER_NAME);
        Set<String> expectedRoutes = getExpectedRouteNames(CLUSTER_NAME);

        Kafka mockKafka = new Kafka();
        mockKafka.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build());
        mockKafka.setStatus(new KafkaStatusBuilder().withListeners(internalListener, externalListener).withConditions(ConditionUtils.getReadyCondition()).build());
        Optional<Kafka> mockKafkaInstance = Optional.of(mockKafka);
        when(esResourceOperator.getKafkaInstance(anyString(), anyString())).thenReturn(mockKafkaInstance);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
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
                verify(esResourceOperator).updateEventStreamsStatus(argument.capture());
                assertEquals(2, argument.getValue().getStatus().getKafkaListeners().size());
                assertEquals(internalListenerType, argument.getValue().getStatus().getKafkaListeners().get(0).getType());
                assertEquals(internalHost, argument.getValue().getStatus().getKafkaListeners().get(0).getAddresses().get(0).getHost());
                assertEquals(internalPort, argument.getValue().getStatus().getKafkaListeners().get(0).getAddresses().get(0).getPort());

                assertEquals(externalListenerType, argument.getValue().getStatus().getKafkaListeners().get(1).getType());
                assertEquals(externalHost, argument.getValue().getStatus().getKafkaListeners().get(1).getAddresses().get(0).getHost());
                assertEquals(externalPort, argument.getValue().getStatus().getKafkaListeners().get(1).getAddresses().get(0).getPort());

                async.flag();
            })));
    }

    @Test
    public void testStatusIsCorrectlyDisplayed(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Set<String> expectedRouteHosts = getExpectedRouteNames(CLUSTER_NAME).stream()
                .map(this::formatRouteHost)
                .collect(Collectors.toSet());

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(argument.capture());
                assertThat(argument.getValue().getStatus().isCustomImages(), is(false));
                assertThat(esCluster.getStatus().getVersions().getReconciled(), is(EventStreamsVersions.OPERAND_VERSION));
                assertThat(esCluster.getStatus().getVersions().getAvailable().getChannels(),
                        hasItem(hasProperty("name", is(EventStreamsAvailableVersions.CHANNELS.get(0)))));
                assertThat(esCluster.getStatus().getVersions().getAvailable().getVersions(),
                        hasItem(hasProperty("name", is(EventStreamsAvailableVersions.VERSIONS.get(0)))));
                assertThat(new HashSet<String>(esCluster.getStatus().getRoutes().values()), is(expectedRouteHosts));
                assertThat(esCluster.getStatus().getAdminUiUrl(), is("https://" + formatRouteHost(UI_ROUTE_NAME)));
                context.completeNow();
                async.flag();
            })));
    }

    @Test
    public void testSingleEndpointRouteCertificateSecretContentIsValid(VertxTestContext context) {
        String componentName = "endpoint-component";
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(esCluster, new SecurityComponentSpec(), componentName, "endpoint-component-label");
        List<Endpoint> endpoints = endpointModel.createEndpoints(esCluster, new SecurityComponentSpec());

        Endpoint endpoint = endpoints.get(0);
        String routeName = endpointModel.getRouteName(endpoint.getName());
        Map<String, String> additionalHosts = Collections.singletonMap(routeName, "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new)
            .onComplete(context.succeeding(ar -> context.verify(() -> {
                assertThat("Number of secrets do not match " + mockClient.secrets().list().getItems(), mockClient.secrets().list().getItems().size(), is(4));
                Secret secret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
                assertThat("The expected secret is created", secret, is(notNullValue()));
                assertThat("There is a key file of length greater than 0", secret.getData().get(endpointModel.getCertSecretKeyID(endpoint.getName())).length(), greaterThan(0));
                assertThat("There is a cert file of length greater than 0", secret.getData().get(endpointModel.getCertSecretCertID(endpoint.getName())).length(), greaterThan(0));
                CertAndKey certAndKey = reconciliationState.certificateManager.certificateAndKey(secret, endpointModel.getCertSecretCertID(endpoint.getName()), endpointModel.getCertSecretKeyID(endpoint.getName()));

                X509Certificate certificate = ControllerUtils.checkCertificate(reconciliationState.certificateManager, certAndKey);
                ControllerUtils.checkSans(context, reconciliationState.certificateManager, certificate, null, additionalHosts.get(routeName), componentName);
                async.flag();
            })));
    }

    @Test
    public void testSingleNonRouteEndpointCertificateSecretContentIsValid(VertxTestContext context) {
        String componentName = "endpoint-component";
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        EndpointSpec internal = new EndpointSpecBuilder()
            .withName("internal")
            .withContainerPort(9990)
            .withType(EndpointServiceType.INTERNAL)
            .build();

        SecurityComponentSpec spec = new SecurityComponentSpecBuilder()
            .withEndpoints(internal)
            .build();

        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(esCluster, spec, componentName, "endpoint-component-label");

        reconciliationState.reconcileCerts(endpointModel, Collections.emptyMap(), Date::new).setHandler(ar -> context.verify(() -> {
            assertThat("Number of secrets do not match " + mockClient.secrets().list().getItems(), mockClient.secrets().list().getItems().size(), is(4));
            Secret secret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
            assertThat("The expected secret is created", secret, is(notNullValue()));
            CertAndKey certAndKey = reconciliationState.certificateManager.certificateAndKey(secret, endpointModel.getCertSecretCertID(internal.getName()), endpointModel.getCertSecretKeyID(internal.getName()));
            assertThat("There is a key file data entry for tls internal endpoint", secret.getData().get(endpointModel.getCertSecretKeyID(internal.getName())).length(), greaterThan(0));
            assertThat("There is a cert file data entry for tls internal endpoint", secret.getData().get(endpointModel.getCertSecretCertID(internal.getName())).length(), greaterThan(0));

            assertThat("There is a key file data entry for tls P2P port ", secret.getData().get(endpointModel.getCertSecretKeyID(Endpoint.DEFAULT_P2P_TLS_NAME)).length(), greaterThan(0));
            assertThat("There is a cert file data entry for tls P2P port", secret.getData().get(endpointModel.getCertSecretCertID(Endpoint.DEFAULT_P2P_TLS_NAME)).length(), greaterThan(0));

            X509Certificate certificate = ControllerUtils.checkCertificate(reconciliationState.certificateManager, certAndKey);
            ControllerUtils.checkSans(context, reconciliationState.certificateManager, certificate, endpointModel.getSecurityService(internal.getType()), "", componentName);
            async.flag();
        }));
    }

    @Test
    public void testNonTlsInternalEndpointCertificateSecretContentIsValid(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        EndpointSpec internal = new EndpointSpecBuilder()
            .withName("internal")
            .withContainerPort(9990)
            .withTlsVersion(TlsVersion.NONE)
            .withType(EndpointServiceType.INTERNAL)
            .build();

        SecurityComponentSpec spec = new SecurityComponentSpecBuilder()
            .withEndpoints(internal)
            .build();

        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(esCluster, spec, "endpoint-component", "endpoint-component-label");

        reconciliationState.reconcileCerts(endpointModel, Collections.emptyMap(), Date::new).setHandler(ar -> context.verify(() -> {
            assertThat("Number of secrets do not match " + mockClient.secrets().list().getItems(), mockClient.secrets().list().getItems().size(), is(4));
            Secret secret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
            assertThat("The expected secret is created", secret, is(notNullValue()));
            assertThat("The secret has pod to pod data", secret.getData(), allOf(
                    aMapWithSize(2),
                    hasKey("p2ptls.crt"),
                    hasKey("p2ptls.key")
                ));
            async.flag();
        }));
    }

    @Test
    public void testMultiplePlainEndpointCertificateSecretContentIsEmpty(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        esCluster.getSpec().setSecurity(new SecuritySpecBuilder().withInternalTls(TlsVersion.TLS_V1_2).build());
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        EndpointSpec plainInternal = new EndpointSpecBuilder()
            .withName("route")
            .withTlsVersion(TlsVersion.NONE)
            .withContainerPort(9990)
            .withType(EndpointServiceType.ROUTE)
            .build();

        EndpointSpec plainNodePort = new EndpointSpecBuilder()
            .withName("node-port")
            .withTlsVersion(TlsVersion.NONE)
            .withContainerPort(9990)
            .withType(EndpointServiceType.NODE_PORT)
            .build();

        SecurityComponentSpec spec = new SecurityComponentSpecBuilder()
            .withEndpoints(plainInternal, plainNodePort)
            .build();

        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(esCluster, spec, "endpoint-component", "endpoint-component-label");

        reconciliationState.reconcileCerts(endpointModel, Collections.emptyMap(), Date::new).setHandler(ar -> {
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems(), hasSize(4));
            Secret secret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
            assertThat("The certificate secret should be created", secret, is(notNullValue()));
            assertThat("The secret does not contain cert key ID for plain node port", secret.getData().containsKey(endpointModel.getCertSecretKeyID(plainInternal.getName())), is(false));
            assertThat("The secret does not contain cert ID for plain node port", secret.getData().containsKey(endpointModel.getCertSecretCertID(plainInternal.getName())), is(false));
            assertThat("The secret does not contain cert key ID for plain route", secret.getData().containsKey(endpointModel.getCertSecretKeyID(plainNodePort.getName())), is(false));
            assertThat("The secret does not contain cert ID for plain route", secret.getData().containsKey(endpointModel.getCertSecretCertID(plainNodePort.getName())), is(false));
            async.flag();
        });
    }

    @Test
    public void testAllTlsEndpointCertificateSecretContentHasAllKeyss(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        esCluster.getSpec().setSecurity(new SecuritySpecBuilder().withInternalTls(TlsVersion.TLS_V1_2).build());
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        EndpointSpec route = new EndpointSpecBuilder()
            .withName("route")
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withContainerPort(9990)
            .build();

        EndpointSpec nodePort = new EndpointSpecBuilder()
            .withName("node-port")
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withContainerPort(8080)
            .withType(EndpointServiceType.NODE_PORT)
            .build();

        EndpointSpec internal = new EndpointSpecBuilder()
            .withName("internal")
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withContainerPort(1234)
            .withType(EndpointServiceType.INTERNAL)
            .build();

        SecurityComponentSpec spec = new SecurityComponentSpecBuilder()
            .withEndpoints(route, nodePort, internal)
            .build();

        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(esCluster, spec, "endpoint-component", "endpoint-component-label");

        reconciliationState.reconcileCerts(endpointModel, Collections.singletonMap(endpointModel.getRouteName(route.getName()), "additional.hosts"), Date::new).setHandler(ar -> {
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems(), hasSize(4));
            Secret secret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
            assertThat("The certificate secret should be created", secret, is(notNullValue()));
            assertThat("The secret does not contain cert key ID for tls route", secret.getData().get(endpointModel.getCertSecretKeyID(route.getName())).length(), greaterThan(0));
            assertThat("The secret does not contain cert ID for tls route", secret.getData().get(endpointModel.getCertSecretCertID(route.getName())).length(), greaterThan(0));
            assertThat("The secret does not contain cert key ID for tls node port", secret.getData().get(endpointModel.getCertSecretKeyID(nodePort.getName())).length(), greaterThan(0));
            assertThat("The secret does not contain cert ID for tls node port", secret.getData().get(endpointModel.getCertSecretCertID(nodePort.getName())).length(), greaterThan(0));
            assertThat("The secret does not contain cert key ID for tls internal", secret.getData().get(endpointModel.getCertSecretKeyID(internal.getName())).length(), greaterThan(0));
            assertThat("The secret does not contain cert ID for tls internal", secret.getData().get(endpointModel.getCertSecretCertID(internal.getName())).length(), greaterThan(0));

            async.flag();
        });
    }

    @Test
    public void testEndpointsCertificateSecretContentUnchangedByStandardReconciliation(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        esCluster.getSpec().setSecurity(new SecuritySpecBuilder().withInternalTls(TlsVersion.TLS_V1_2).build());
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        EndpointSpec tlsInternal = new EndpointSpecBuilder()
            .withName("internal")
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withContainerPort(9990)
            .withType(EndpointServiceType.INTERNAL)
            .build();

        SecurityComponentSpec spec = new SecurityComponentSpecBuilder()
            .withEndpoints(tlsInternal)
            .build();

        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(esCluster, spec, "endpoint-component", "endpoint-component-label");
        Map<String, String> additionalHosts = Collections.singletonMap(tlsInternal.getName(), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("The number of secrets does match", mockClient.secrets().list().getItems().size(), is(4));
            Secret firstSecret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
            reconciliationState.reconcileCerts(endpointModel, additionalHosts,  Date::new).setHandler(ar2 -> {
                assertThat("The number of secrets does match", mockClient.secrets().list().getItems().size(), is(4));
                Secret secondSecret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
                assertThat("The secret has not changed", secondSecret, is(firstSecret));
                async.flag();
            });
        });
    }

    @Test
    public void testEndpointsCertificateSecretRegeneratedWhenExpired(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);

        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        esCluster.getSpec().setSecurity(new SecuritySpecBuilder().withInternalTls(TlsVersion.TLS_V1_2).build());
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        EndpointSpec tlsInternal = new EndpointSpecBuilder()
            .withName("internal")
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withContainerPort(9990)
            .withType(EndpointServiceType.INTERNAL)
            .build();

        SecurityComponentSpec spec = new SecurityComponentSpecBuilder()
            .withEndpoints(tlsInternal)
            .build();

        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(esCluster, spec, "endpoint-component", "endpoint-component-label");
        Map<String, String> additionalHosts = Collections.singletonMap(tlsInternal.getName(), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(4));
            Secret firstSecret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
            reconciliationState.reconcileCerts(endpointModel, additionalHosts, () -> Date.from(Instant.now().plusSeconds(TWO_YEARS_PLUS_IN_SECONDS))).setHandler(ar2 -> {
                assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(4));
                Secret secondSecret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
                assertThat("The secret has changed", secondSecret, not(firstSecret));
                async.flag();
            });
        });
    }

    @Test
    public void testEndpointsCertificateSecretRegeneratedWhenCAChanges(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);

        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        esCluster.getSpec().setSecurity(new SecuritySpecBuilder().withInternalTls(TlsVersion.TLS_V1_2).build());
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        EndpointSpec tlsInternal = new EndpointSpecBuilder()
            .withName("internal")
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withContainerPort(9990)
            .withType(EndpointServiceType.INTERNAL)
            .build();

        SecurityComponentSpec spec = new SecurityComponentSpecBuilder()
            .withEndpoints(tlsInternal)
            .build();

        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(esCluster, spec, "endpoint-component", "endpoint-component-label");
        Map<String, String> additionalHosts = Collections.singletonMap(tlsInternal.getName(), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(4));
            Secret firstSecret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
            List<Secret> newClusterCA = new ArrayList<>(ModelUtils.generateClusterCa(NAMESPACE, CLUSTER_NAME, APP_NAME, ModelUtils.Certificates.NEW_CLUSTER_CA, ModelUtils.Keys.NEW_CLUSTER_CA_KEY));
            mockClient.secrets().createOrReplace(newClusterCA.get(0));
            mockClient.secrets().createOrReplace(newClusterCA.get(1));
            reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar2 -> {
                assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(4));
                Secret secondSecret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
                assertThat("The secret has changed", secondSecret, not(firstSecret));
                async.flag();
            });
        });
    }

    @Test
    public void testEndpointsCertificateSecretRegeneratedWhenSansAreChanged(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        Checkpoint async = context.checkpoint(1);

        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        esCluster.getSpec().setSecurity(new SecuritySpecBuilder().withInternalTls(TlsVersion.TLS_V1_2).build());
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        EndpointSpec tlsRoute = new EndpointSpecBuilder()
            .withName("route")
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withContainerPort(9990)
            .withType(EndpointServiceType.ROUTE)
            .build();

        EndpointSpec tlsInternal = new EndpointSpecBuilder()
            .withName("internal")
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withContainerPort(9990)
            .withType(EndpointServiceType.INTERNAL)
            .build();

        SecurityComponentSpec spec = new SecurityComponentSpecBuilder()
            .withEndpoints(tlsRoute, tlsInternal)
            .build();

        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(esCluster, spec, "endpoint-component", "endpoint-component-label");
        Map<String, String> additionalHosts = Collections.singletonMap(endpointModel.getRouteName(tlsRoute.getName()), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(4));
            Secret firstSecret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
            CertAndKey originalInternalTlsCertAndKey = reconciliationState.certificateManager.certificateAndKey(firstSecret, endpointModel.getCertSecretCertID(tlsInternal.getName()), endpointModel.getCertSecretKeyID(tlsInternal.getName()));
            CertAndKey originalTlsRouteCertAndKey = reconciliationState.certificateManager.certificateAndKey(firstSecret, endpointModel.getCertSecretCertID(tlsRoute.getName()), endpointModel.getCertSecretKeyID(tlsRoute.getName()));

            Map<String, String> newHosts = Collections.singletonMap(endpointModel.getRouteName(tlsRoute.getName()), "extra.host.name.2");
            reconciliationState.reconcileCerts(endpointModel, newHosts, Date::new).setHandler(ar2 -> {
                assertThat("The number of secrets does match", mockClient.secrets().list().getItems().size(), is(4));
                Secret secondSecret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
                assertThat("The secret has changed", secondSecret, not(firstSecret));
                CertAndKey newInternalTlsCertAndKey = reconciliationState.certificateManager.certificateAndKey(secondSecret, endpointModel.getCertSecretCertID(tlsInternal.getName()), endpointModel.getCertSecretKeyID(tlsInternal.getName()));
                CertAndKey newTlsRouteCertAndKey = reconciliationState.certificateManager.certificateAndKey(secondSecret, endpointModel.getCertSecretCertID(tlsRoute.getName()), endpointModel.getCertSecretKeyID(tlsRoute.getName()));

                assertThat("The internalTls cert data has changed", originalInternalTlsCertAndKey.cert(), is(newInternalTlsCertAndKey.cert()));
                assertThat("The internalTls key data has changed", originalInternalTlsCertAndKey.key(), is(newInternalTlsCertAndKey.key()));
                assertThat("The internalTls cert data has changed", originalTlsRouteCertAndKey.cert(), not(newTlsRouteCertAndKey.cert()));
                assertThat("The internalTls key data has changed", originalTlsRouteCertAndKey.key(), not(newTlsRouteCertAndKey.key()));
                async.flag();
            });
        });
    }

    @Test
    public void testEndpointsCertificatePopulatedWithProvidedBrokerCerts(VertxTestContext context) {
        String secretName = "provided-broker-cert";
        String secretKey = "broker.cert";
        String secretCertificate = "broker.key";
        Map<String, String> data = new HashMap<>();
        data.put(secretKey, "YW55IG9sZCBndWJiaW5zCg==");
        data.put(secretCertificate, "YW55IG9sZCBndWJiaW5zCg==");
        Secret providedSecret = ModelUtils.generateSecret(NAMESPACE, secretName, data);
        mockClient.secrets().create(providedSecret);

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createESClusterWithProvidedBrokerCerts(NAMESPACE, CLUSTER_NAME, secretName, secretKey, secretCertificate);
        esCluster.getSpec().setSecurity(new SecuritySpecBuilder().withInternalTls(TlsVersion.TLS_V1_2).build());

        Checkpoint async = context.checkpoint(1);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        CertAndKeySecretSource certOverrides = new CertAndKeySecretSourceBuilder()
            .withSecretName(secretName)
            .withKey(secretKey)
            .withCertificate(secretCertificate)
            .build();

        EndpointSpec tlsRoute = new EndpointSpecBuilder()
            .withName("route")
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withContainerPort(9990)
            .withType(EndpointServiceType.ROUTE)
            .withCertOverrides(certOverrides)
            .build();

        EndpointSpec tlsInternal = new EndpointSpecBuilder()
            .withName("internal")
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withContainerPort(9990)
            .withType(EndpointServiceType.INTERNAL)
            .withCertOverrides(certOverrides)
            .build();

        SecurityComponentSpec spec = new SecurityComponentSpecBuilder()
            .withEndpoints(tlsRoute, tlsInternal)
            .build();

        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(esCluster, spec, "endpoint-component", "endpoint-component-label");
        Map<String, String> additionalHosts = Collections.singletonMap(tlsInternal.getName(), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(5));
            Secret secret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
            assertThat("The admin api cert secret has been populated with the internal provided cert", secret.getData().get(endpointModel.getCertSecretCertID(tlsInternal.getName())), is(providedSecret.getData().get(secretCertificate)));
            assertThat("The admin api cert secret has been populated with the internal provided key", secret.getData().get(endpointModel.getCertSecretKeyID(tlsInternal.getName())), is(providedSecret.getData().get(secretKey)));
            assertThat("The admin api cert secret has been populated with the external provided cert", secret.getData().get(endpointModel.getCertSecretCertID(tlsRoute.getName())), is(providedSecret.getData().get(secretCertificate)));
            assertThat("The admin api cert secret has been populated with the external provided key", secret.getData().get(endpointModel.getCertSecretKeyID(tlsRoute.getName())), is(providedSecret.getData().get(secretKey)));
            mockClient.secrets().delete(providedSecret);
            async.flag();
        });
    }

    @Test
    public void testEndpointsChangeWhenBrokerSecretChanges(VertxTestContext context) {
        String secretName = "provided-broker-cert";
        String secretKey = "broker.cert";
        String secretCertificate = "broker.key";
        Map<String, String> firstDataSet = new HashMap<>();
        firstDataSet.put(secretKey, "YW55IG9sZCBndWJiaW5zCg==");
        firstDataSet.put(secretCertificate, "YW55IG9sZCBndWJiaW5zCg==");
        Map<String, String> secondDataSet = new HashMap<>();
        firstDataSet.put(secretKey, "RnJlc2ggRXllcyBpcyBvbiB0aGUgQ2FzZQo=");
        firstDataSet.put(secretCertificate, "RnJlc2ggRXllcyBpcyBvbiB0aGUgQ2FzZQo=");
        Secret firstProvidedSecret = ModelUtils.generateSecret(NAMESPACE, secretName, firstDataSet);
        mockClient.secrets().create(firstProvidedSecret);

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        esCluster.getSpec().setSecurity(new SecuritySpecBuilder().withInternalTls(TlsVersion.TLS_V1_2).build());

        Checkpoint async = context.checkpoint();
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        CertAndKeySecretSource certOverrides = new CertAndKeySecretSourceBuilder()
            .withSecretName(secretName)
            .withKey(secretKey)
            .withCertificate(secretCertificate)
            .build();

        EndpointSpec tlsInternal = new EndpointSpecBuilder()
            .withName("internal")
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withContainerPort(9990)
            .withType(EndpointServiceType.INTERNAL)
            .withCertOverrides(certOverrides)
            .build();

        SecurityComponentSpec spec = new SecurityComponentSpecBuilder()
            .withEndpoints(tlsInternal)
            .build();

        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(esCluster, spec, "endpoint-component", "endpoint-component-label");

        Map<String, String> additionalHosts = Collections.singletonMap(tlsInternal.getName(), "extra.host.name");

        reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar -> {
            assertThat("The number of secrets does not match", mockClient.secrets().list().getItems().size(), is(5));
            Secret secret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
            assertThat("The admin api cert secret has been populated with the internal provided cert", secret.getData().get(endpointModel.getCertSecretCertID(tlsInternal.getName())), is(firstProvidedSecret.getData().get(secretCertificate)));
            assertThat("The admin api cert secret has been populated with the internal provided key", secret.getData().get(endpointModel.getCertSecretKeyID(tlsInternal.getName())), is(firstProvidedSecret.getData().get(secretKey)));

            mockClient.secrets().delete(firstProvidedSecret);
            firstProvidedSecret.setData(secondDataSet);
            Secret secondProvidedSecret = ModelUtils.generateSecret(NAMESPACE, secretName, firstDataSet);
            mockClient.secrets().create(secondProvidedSecret);

            reconciliationState.reconcileCerts(endpointModel, additionalHosts, Date::new).setHandler(ar2 -> {
                Secret secondSecret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
                assertThat("The admin api cert secret has been populated with new internal provided cert", secondSecret.getData().get(endpointModel.getCertSecretCertID(tlsInternal.getName())), is(secondProvidedSecret.getData().get(secretCertificate)));
                assertThat("The admin api cert secret has been populated with new internal provided key", secondSecret.getData().get(endpointModel.getCertSecretKeyID(tlsInternal.getName())), is(secondProvidedSecret.getData().get(secretKey)));

                async.flag();
            });
        });
    }

    @Test
    public void testAllSecureEndpointModelsCertsCreatedOpenShift(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        Checkpoint async = context.checkpoint(2);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState state = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        state.icpClusterData = Collections.emptyMap();

        CompositeFuture.join(state.createRestProducer(Date::new),
            state.createSchemaRegistry(Date::new),
            state.createAdminApi(Date::new))
            .onComplete(context.succeeding(v -> context.verify(() -> {
                List<Secret> secrets = mockClient.secrets().withLabel(Labels.KUBERNETES_INSTANCE_LABEL, CLUSTER_NAME).list().getItems();
                secrets.forEach(secret -> {
                    if (secret.getMetadata().getName().endsWith("-cert")) {
                        String componentName = secret.getMetadata().getName().split("ibm-es-")[0];
                        componentName = componentName.replace("-secret", "");
                        Optional<Service> serviceOpt = mockClient.services().list().getItems()
                                .stream()
                                .filter(service -> service.getMetadata().getName().contains("external"))
                                .filter(service -> service.getMetadata().getName().startsWith(secret.getMetadata().getName().replace("-cert", "")))
                                .findAny();
                        assertThat("We should find the service for the secret " + secret.getMetadata().getName() + "services found " + mockClient.services().list().getItems(),
                                serviceOpt.isPresent(), is(true));

                        Optional<Route> routeOpt = mockClient.adapt(OpenShiftClient.class).routes().list().getItems()
                                .stream()
                                .filter(route -> route.getMetadata().getName().endsWith(Endpoint.DEFAULT_EXTERNAL_NAME))
                                .filter(route -> route.getMetadata().getName().startsWith(secret.getMetadata().getName().replace("-cert", "")))
                                .findAny();
                        assertThat("We should find the route for the secret " + secret.getMetadata().getName(), routeOpt.isPresent(), is(true));
                        String certID = secret.getData().keySet().stream().filter(string -> string.endsWith(CertificateSecretModel.formatCertID(Endpoint.DEFAULT_EXTERNAL_NAME))).findAny().get();
                        String keyID = secret.getData().keySet().stream().filter(string -> string.endsWith(CertificateSecretModel.formatKeyID(Endpoint.DEFAULT_EXTERNAL_NAME))).findAny().get();
                        CertAndKey certAndKey = state.certificateManager.certificateAndKey(secret, certID, keyID);
                        X509Certificate certificate = ControllerUtils.checkCertificate(state.certificateManager, certAndKey);
                        ControllerUtils.checkSans(context, state.certificateManager, certificate, null, routeOpt.get().getSpec().getHost(), componentName);
                    }
                });
                async.flag();
            })));
        async.flag();
    }

    @Test
    public void testEndpointSecretsChangeWhenSecurityTurnsOn(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        EndpointSpec internalTlsBearer = new EndpointSpecBuilder()
            .withName("bearer")
            .withContainerPort(8887)
            .withType(EndpointServiceType.INTERNAL)
            .withAuthenticationMechanisms(Collections.singletonList("BEARER"))
            .build();

        EndpointSpec internalTlsMutualTls = new EndpointSpecBuilder()
            .withName("mutual")
            .withContainerPort(8888)
            .withType(EndpointServiceType.INTERNAL)
            .build();

        EndpointSpec internalTlsNone = new EndpointSpecBuilder()
            .withName("none")
            .withContainerPort(9999)
            .withType(EndpointServiceType.INTERNAL)
            .build();

        EventStreams secureInstance = new EventStreamsBuilder()
            .withMetadata(new ObjectMetaBuilder()
                .withNewName(CLUSTER_NAME)
                .withNewNamespace(NAMESPACE)
                .build())
            .withNewSpec()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.NONE)
            .build())
            .withNewAdminApi()
                .withReplicas(1)
                .withEndpoints(new ArrayList<>(Arrays.asList(internalTlsBearer, internalTlsMutualTls)))
            .endAdminApi()
            .withNewLicense()
                .withAccept(true)
            .endLicense()
            .withNewVersion(DEFAULT_VERSION)
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .withReplicas(1)
                .withNewListeners()
                    .withNewPlain()
                    .endPlain()
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

        EventStreams insecureInstance = new EventStreamsBuilder(secureInstance)
            .editSpec()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.NONE)
                .build())
            .withNewAdminApi()
                .withReplicas(1)
                .withEndpoints(new ArrayList<>(Arrays.asList(internalTlsBearer, internalTlsNone)))
            .endAdminApi()
            .endSpec()
            .build();

        AtomicReference<Secret> secretReference = new AtomicReference<>();
        Checkpoint async = context.checkpoint(3);
        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(secureInstance, null, "admapi", "admin-api");

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), secureInstance)
            .onComplete(context.succeeding(v -> {
                Secret secret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();

                assertThat(secret, is(notNullValue()));

                assertThat(secret.getData().get(endpointModel.getCertSecretKeyID(internalTlsBearer.getName())).length(), greaterThan(0));
                assertThat(secret.getData().get(endpointModel.getCertSecretCertID(internalTlsBearer.getName())).length(), greaterThan(0));

                assertThat(secret.getData().get(endpointModel.getCertSecretKeyID(internalTlsMutualTls.getName())).length(), greaterThan(0));
                assertThat(secret.getData().get(endpointModel.getCertSecretCertID(internalTlsMutualTls.getName())).length(), greaterThan(0));
                secretReference.set(secret);

                async.flag();
            }))
            .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), insecureInstance))
            .onComplete(context.succeeding(v -> {
                Secret secret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
                assertThat(secret, is(notNullValue()));

                assertThat(secret, not(secretReference.get()));

                assertThat(secret.getData().get(endpointModel.getCertSecretKeyID(internalTlsBearer.getName())).length(), greaterThan(0));
                assertThat(secret.getData().get(endpointModel.getCertSecretCertID(internalTlsBearer.getName())).length(), greaterThan(0));

                assertThat(secret.getData().get(endpointModel.getCertSecretKeyID(internalTlsNone.getName())).length(), greaterThan(0));
                assertThat(secret.getData().get(endpointModel.getCertSecretCertID(internalTlsNone.getName())).length(), greaterThan(0));
                async.flag();
            }));
        async.flag();
    }

    @Test
    public void testEndpointSecretsChangeWhenSecurityTurnsOff(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        EndpointSpec internalTlsBearer = new EndpointSpecBuilder()
            .withName("bearer")
            .withContainerPort(8887)
            .withType(EndpointServiceType.INTERNAL)
            .withAuthenticationMechanisms(Collections.singletonList("BEARER"))
            .build();

        EndpointSpec internalTlsMutualTls = new EndpointSpecBuilder()
            .withName("mutual")
            .withContainerPort(8888)
            .withType(EndpointServiceType.INTERNAL)
            .build();

        EndpointSpec internalTlsNone = new EndpointSpecBuilder()
            .withName("none")
            .withContainerPort(9999)
            .withType(EndpointServiceType.INTERNAL)
            .build();

        EventStreams secureInstance = new EventStreamsBuilder()
            .withMetadata(new ObjectMetaBuilder()
                .withNewName(CLUSTER_NAME)
                .withNewNamespace(NAMESPACE)
                .build())
            .withNewSpec()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.TLS_V1_2)
                .build())
            .withNewAdminApi()
            .withReplicas(1)
            .withEndpoints(new ArrayList<>(Arrays.asList(internalTlsBearer, internalTlsMutualTls)))
            .endAdminApi()
            .withNewLicense()
                .withAccept(true)
            .endLicense()
            .withNewVersion(DEFAULT_VERSION)
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

        EventStreams insecureInstance = new EventStreamsBuilder(secureInstance)
            .editSpec()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.NONE)
                .build())
            .withNewAdminApi()
            .withReplicas(1)
            .withEndpoints(new ArrayList<>(Arrays.asList(internalTlsBearer, internalTlsNone)))
            .endAdminApi()
            .withNewSchemaRegistry()
            .withReplicas(1)
            .endSchemaRegistry()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .withReplicas(1)
                .withNewListeners()
                    .withNewPlain()
                    .endPlain()
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

        AtomicReference<Secret> secretReference = new AtomicReference<>();
        Checkpoint async = context.checkpoint(3);
        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(secureInstance, null, "admapi", "admin-api");

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), insecureInstance)
            .onComplete(context.succeeding(v -> {
                Secret secret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();

                assertThat(secret, is(notNullValue()));

                assertThat(secret.getData().get(endpointModel.getCertSecretKeyID(internalTlsBearer.getName())).length(), greaterThan(0));
                assertThat(secret.getData().get(endpointModel.getCertSecretCertID(internalTlsBearer.getName())).length(), greaterThan(0));

                assertThat(secret.getData().get(endpointModel.getCertSecretKeyID(internalTlsNone.getName())).length(), greaterThan(0));
                assertThat(secret.getData().get(endpointModel.getCertSecretCertID(internalTlsNone.getName())).length(), greaterThan(0));

                secretReference.set(secret);

                async.flag();
            }))
            .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), secureInstance))
            .onComplete(context.succeeding(v -> {
                Secret secret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
                assertThat(secret, is(notNullValue()));

                assertThat(secret, not(secretReference.get()));

                assertThat(secret.getData().get(endpointModel.getCertSecretKeyID(internalTlsBearer.getName())).length(), greaterThan(0));
                assertThat(secret.getData().get(endpointModel.getCertSecretCertID(internalTlsBearer.getName())).length(), greaterThan(0));

                assertThat(secret.getData().get(endpointModel.getCertSecretKeyID(internalTlsMutualTls.getName())).length(), greaterThan(0));
                assertThat(secret.getData().get(endpointModel.getCertSecretCertID(internalTlsMutualTls.getName())).length(), greaterThan(0));

                async.flag();
            }));
        async.flag();
    }

    @Test
    public void testEndpointCertificateSecretCreatedIfNotPresent(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        EndpointSpec internalTlsMutualTls = new EndpointSpecBuilder()
            .withName("mutual")
            .withContainerPort(9999)
            .withType(EndpointServiceType.INTERNAL)
            .build();

        EventStreams beforeInstance = createMinimalESInstance();
        beforeInstance.getSpec().setAdminApi(new SecurityComponentSpecBuilder()
            .withReplicas(1)
            .build());

        EventStreams afterInstance = new EventStreamsBuilder(beforeInstance)
            .editSpec()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.NONE)
                .build())
            .withNewAdminApi()
            .withReplicas(1)
            .withEndpoints(Collections.singletonList(internalTlsMutualTls))
            .endAdminApi()
            .endSpec()
            .build();

        AtomicReference<Secret> secretReference = new AtomicReference<>();
        Checkpoint async = context.checkpoint(3);
        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(beforeInstance, null, "admapi", "admin-api");

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), beforeInstance)
            .onComplete(context.succeeding(v -> {
                Secret secret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();

                assertThat(secret, is(notNullValue()));

                assertThat(secret.getData().get(endpointModel.getCertSecretKeyID(internalTlsMutualTls.getName())), is(nullValue()));
                assertThat(secret.getData().get(endpointModel.getCertSecretCertID(internalTlsMutualTls.getName())), is(nullValue()));

                assertThat(secret.getData().get(endpointModel.getCertSecretKeyID(Endpoint.DEFAULT_EXTERNAL_NAME)).length(), greaterThan(0));
                assertThat(secret.getData().get(endpointModel.getCertSecretCertID(Endpoint.DEFAULT_EXTERNAL_NAME)).length(), greaterThan(0));
                secretReference.set(secret);

                async.flag();
            }))
            .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), afterInstance))
            .onComplete(context.succeeding(v -> {
                Secret secret = mockClient.secrets().withName(endpointModel.getCertificateSecretName()).get();
                assertThat(secret, is(notNullValue()));

                assertThat(secret, not(secretReference.get()));

                assertThat(secret.getData().get(endpointModel.getCertSecretKeyID(internalTlsMutualTls.getName())).length(), greaterThan(0));
                assertThat(secret.getData().get(endpointModel.getCertSecretCertID(internalTlsMutualTls.getName())).length(), greaterThan(0));

                assertThat(secret.getData().get(endpointModel.getCertSecretKeyID(Endpoint.DEFAULT_EXTERNAL_NAME)), is(nullValue()));
                assertThat(secret.getData().get(endpointModel.getCertSecretCertID(Endpoint.DEFAULT_EXTERNAL_NAME)), is(nullValue()));
                async.flag();
            }));
        async.flag();
    }

    // Not possible to check that the deployment does change when the certificate changes as the resourceVersion isn't
    // implemented properly in the mockClient
    @Test
    public void testNoRollingUpdateForDeploymentWhenCertificatesDoNotChange(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        Checkpoint async = context.checkpoint(3);
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        reconciliationState.createRestProducer(Date::new)
            .compose(v -> reconciliationState.createSchemaRegistry(Date::new))
            .compose(v -> reconciliationState.createAdminApi(Date::new))
            .onComplete(context.succeeding(v -> context.verify(() -> {
                // Immediately run again as the mocking agent doesn't create the deployments correctly the first time
                reconciliationState.createRestProducer(Date::new)
                    .compose(v1 -> reconciliationState.createSchemaRegistry(Date::new))
                    .compose(v1 -> reconciliationState.createAdminApi(Date::new))
                    .onComplete(context.succeeding(v2 -> context.verify(() -> {
                        List<Deployment> deployments = mockClient.apps().deployments().list().getItems();
                        assertThat("There are three deployments created", deployments.size(), is(3));
                        async.flag();
                        reconciliationState.createRestProducer(Date::new)
                            .compose(v3 -> reconciliationState.createSchemaRegistry(Date::new))
                            .compose(v3 -> reconciliationState.createAdminApi(Date::new))
                            .onComplete(context.succeeding(v3 -> context.verify(() -> {
                                List<Deployment> deployments2 = mockClient.apps().deployments().list().getItems();
                                assertThat("There are still only three deployments", deployments2.size(), is(3));
                                deployments2.forEach(deployment -> assertTrue(deployments.contains(deployment)));
                                async.flag();
                            })));
                    })));
            })));
        async.flag();
    }

    public void testCreateMinimalEventStreams(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams minimalCluster = createMinimalESInstance();

        Set<String> expectedDeployments = new HashSet<>();
        expectedDeployments.add(CLUSTER_NAME + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME);

        Set<String> expectedServices = new HashSet<>();
        expectedServices.add(CLUSTER_NAME + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + AbstractSecureEndpointsModel.ROUTE_SERVICE_SUFFIX);
        expectedServices.add(CLUSTER_NAME + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + AbstractSecureEndpointsModel.INTERNAL_SERVICE_SUFFIX);

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
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams instanceMinimal = createMinimalESInstance();

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
                    .withNewAdminApi()
                    .endAdminApi()
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
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        EventStreams minimalInstance = createMinimalESInstance();

        String defaultComponentResourceName = CLUSTER_NAME + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME;

        String serviceAccountName = defaultComponentResourceName;
        String networkPolicyName = defaultComponentResourceName;
        String deploymentName = defaultComponentResourceName;
        String internalServiceName = defaultComponentResourceName + "-internal";
        String externalServiceName = defaultComponentResourceName + "-external";
        Set<String> serviceNames = new HashSet<>();
        serviceNames.add(internalServiceName);
        serviceNames.add(externalServiceName);
        String defaultRoute = defaultComponentResourceName + "-" + Endpoint.DEFAULT_EXTERNAL_NAME;
        String shortRouteName = RestProducerModel.COMPONENT_NAME + "-" + Endpoint.DEFAULT_EXTERNAL_NAME;
        Set<String> routeNames = new HashSet<>();
        routeNames.add(defaultRoute);

        Checkpoint async = context.checkpoint();

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance)
            .onComplete(context.succeeding(v -> {
                verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                verifyContainsResources(context, serviceNames, KubeResourceType.SERVICES, false);
                verifyContainsResources(context, routeNames, KubeResourceType.ROUTES, false);
            }))
            .map(v -> {
                ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(updatedEventStreams.capture());
                context.verify(() -> assertThat(updatedEventStreams.getValue().getStatus().getRoutes().get(shortRouteName), is(nullValue())));
                minimalInstance.getSpec().setRestProducer(new SecurityComponentSpec());
                minimalInstance.setStatus(updatedEventStreams.getValue().getStatus());
                return minimalInstance;
            })
            .compose(restProducerInstance -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), restProducerInstance))
            .onComplete(context.succeeding(v -> {
                verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, true);
                verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, true);
                verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, true);
                verifyContainsResources(context, serviceNames, KubeResourceType.SERVICES, true);
                verifyContainsResources(context, routeNames, KubeResourceType.ROUTES, true);
            }))
            .map(v -> {
                ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(3)).updateEventStreamsStatus(updatedEventStreams.capture());
                context.verify(() -> assertThat(updatedEventStreams.getValue().getStatus().getRoutes().get(shortRouteName), is(notNullValue())));
                minimalInstance.getSpec().setRestProducer(null);
                minimalInstance.setStatus(updatedEventStreams.getValue().getStatus());
                return minimalInstance;
            })
            .compose(noRestProducerInstance -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), noRestProducerInstance))
            .onComplete(context.succeeding(v -> {
                verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                verifyContainsResources(context, serviceNames, KubeResourceType.SERVICES, false);
                verifyContainsResources(context, routeNames, KubeResourceType.ROUTES, false);
                ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(4)).updateEventStreamsStatus(updatedEventStreams.capture());
                context.verify(() -> assertThat(updatedEventStreams.getValue().getStatus().getRoutes().get(shortRouteName), is(nullValue())));
                async.flag();
            }));
    }

    @Test
    public void testAdminUIComponentCreatedAndDeletedWhenAddedAndRemovedFromCR(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        EventStreams minimalInstance = createMinimalESInstance();

        EventStreams instance = new EventStreamsBuilder(minimalInstance)
                .editSpec()
                    .withNewAdminApi()
                        .withReplicas(1)
                    .endAdminApi()
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
                    verifyContainsResource(context, routeName, KubeResourceType.ROUTES, true);
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
                    verifyContainsResource(context, routeName, KubeResourceType.ROUTES, true);
                    async.flag();
                }));
    }

    @Test
    public void testCollectorComponentCreatedAndDeletedWhenAddedAndRemovedFromCR(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        EventStreams minimalInstance = createMinimalESInstance();

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
        String serviceName = defaultComponentResourceName + "-internal";
        String secretName = defaultComponentResourceName + "-cert";

        Checkpoint async = context.checkpoint(3);

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance)
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                    verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                    verifyContainsResource(context, serviceName, KubeResourceType.SERVICES, false);
                    verifyContainsResource(context, secretName, KubeResourceType.SECRETS, false);
                    async.flag();
                }))
                .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), instance))
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, true);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, true);
                    verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, true);
                    verifyContainsResource(context, serviceName, KubeResourceType.SERVICES, true);
                    verifyContainsResource(context, secretName, KubeResourceType.SECRETS, true);
                    async.flag();
                }))
                .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance))
                .onComplete(context.succeeding(v -> {
                    verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                    verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                    verifyContainsResource(context, serviceName, KubeResourceType.SERVICES, false);
                    verifyContainsResource(context, secretName, KubeResourceType.SECRETS, false);
                    async.flag();
                }));
    }

    @Test
    public void testSchemaRegistryComponentCreatedAndDeletedWhenAddedAndRemovedFromCR(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        EventStreams instance = new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(CLUSTER_NAME)
                        .withNewNamespace(NAMESPACE)
                        .build())
                .withNewSpec()
                .withNewLicense()
                    .withAccept(true)
                .endLicense()
                .withNewVersion(DEFAULT_VERSION)
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                            .withReplicas(1)
                            .withNewListeners()
                                .withNewPlain()
                                .endPlain()
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

        String defaultComponentResourceName = CLUSTER_NAME + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME;

        String serviceAccountName = defaultComponentResourceName;
        String networkPolicyName = defaultComponentResourceName;
        String deploymentName = defaultComponentResourceName;
        String internalServiceName = defaultComponentResourceName + "-internal";
        String externalServiceName = defaultComponentResourceName + "-external";
        Set<String> serviceNames = new HashSet<>();
        serviceNames.add(internalServiceName);
        serviceNames.add(externalServiceName);
        String externalTlsRouteName = defaultComponentResourceName + "-" + Endpoint.DEFAULT_EXTERNAL_NAME;
        Set<String> routeNames = new HashSet<>();
        routeNames.add(externalTlsRouteName);

        String shortRouteName = SchemaRegistryModel.COMPONENT_NAME + "-" + Endpoint.DEFAULT_EXTERNAL_NAME;
        Checkpoint async = context.checkpoint();

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), instance)
            .onComplete(context.succeeding(v -> {
                verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                verifyContainsResources(context, serviceNames, KubeResourceType.SERVICES, false);
                verifyContainsResources(context, routeNames, KubeResourceType.ROUTES, false);
                async.flag();
            }))
            .map(v -> {
                ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator).createOrUpdate(updatedEventStreams.capture());
                context.verify(() -> assertThat(updatedEventStreams.getValue().getStatus().getRoutes().get(shortRouteName), is(notNullValue())));
                updatedEventStreams.getValue().getSpec().setSchemaRegistry(new SchemaRegistrySpec());
                return updatedEventStreams.getValue();
            })
            .compose(schemaRegistryInstance -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), schemaRegistryInstance))
            .onComplete(context.succeeding(v -> {
                verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, true);
                verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, true);
                verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, true);
                verifyContainsResources(context, serviceNames, KubeResourceType.SERVICES, true);
                verifyContainsResources(context, routeNames, KubeResourceType.ROUTES, true);
            }))
            .map(v -> {
                ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(2)).createOrUpdate(updatedEventStreams.capture());
                context.verify(() -> assertThat(updatedEventStreams.getValue().getStatus().getRoutes().get(shortRouteName), is(notNullValue())));
                updatedEventStreams.getValue().getSpec().setSchemaRegistry(null);
                return updatedEventStreams.getValue();
            })
            .compose(noSchemaRegistryInstance -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), noSchemaRegistryInstance))
            .onComplete(context.succeeding(v -> {
                verifyContainsResource(context, serviceAccountName, KubeResourceType.SERVICE_ACCOUNTS, false);
                verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
                verifyContainsResource(context, deploymentName, KubeResourceType.DEPLOYMENTS, false);
                verifyContainsResources(context, serviceNames, KubeResourceType.SERVICES, false);
                verifyContainsResources(context, routeNames, KubeResourceType.ROUTES, false);
                ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(3)).createOrUpdate(updatedEventStreams.capture());
                context.verify(() -> assertThat(updatedEventStreams.getValue().getStatus().getRoutes().get(shortRouteName), is(nullValue())));
                async.flag();
            }));

    }

    @Test
    public void testRoutesAreDeletedFromStatus(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        String componentName = "endpoint-component";

        String previousDefaultEndpoint = "my-es-ibm-es-" + componentName + "-external";
        String previousCustomEndpoint = "my-es-ibm-es-" + componentName + "-random-name";

        Map<String, String> routes = new HashMap<>();
        routes.put(previousDefaultEndpoint, previousDefaultEndpoint + ".apps.test");
        routes.put(previousCustomEndpoint, previousCustomEndpoint + ".apps.test");

        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        esCluster.setStatus(new EventStreamsStatusBuilder()
            .withRoutes(routes)
            .build());
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        Checkpoint async = context.checkpoint();

        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(esCluster, null, componentName, "endpoint-component-label");
        assertThat(esCluster.getStatus().getRoutes().get(previousCustomEndpoint), is(notNullValue()));
        assertThat(esCluster.getStatus().getRoutes().get(previousDefaultEndpoint), is(notNullValue()));

        reconciliationState.deleteUnspecifiedRoutes(endpointModel, endpointModel.getRoutes())
            .onComplete(context.succeeding(list -> {
                assertThat(list, hasSize(2));
                assertThat(list.get(0), is(ReconcileResult.deleted()));
                assertThat(list.get(1), is(ReconcileResult.deleted()));
                async.flag();
            }));
    }

    @Test
    public void testRoutesFromDifferentComponentAreDeletedFromStatus(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        String differentComponentName = "different-component";
        String componentName = "endpoint-component";

        String defaultEndpoint = "my-es-ibm-es-" + differentComponentName + "-external";
        String customEndpoint = "my-es-ibm-es-" + differentComponentName + "-random-name";

        Map<String, String> routes = new HashMap<>();
        routes.put(defaultEndpoint, defaultEndpoint + ".apps.test");
        routes.put(customEndpoint, customEndpoint + ".apps.test");

        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
        esCluster.setStatus(new EventStreamsStatusBuilder()
            .withRoutes(routes)
            .build());
        Reconciliation reconciliation = new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME);
        EventStreamsOperator.ReconciliationState reconciliationState = esOperator.new ReconciliationState(reconciliation, esCluster, new EventStreamsOperatorConfig.ImageLookup(Collections.emptyMap(), "Always", Collections.emptyList()));
        reconciliationState.icpClusterData = Collections.emptyMap();

        Checkpoint async = context.checkpoint();

        ModelUtils.EndpointsModel endpointModel = new ModelUtils.EndpointsModel(esCluster, null, componentName, "endpoint-component-label");
        assertThat(esCluster.getStatus().getRoutes().get(customEndpoint), is(notNullValue()));
        assertThat(esCluster.getStatus().getRoutes().get(defaultEndpoint), is(notNullValue()));

        reconciliationState.deleteUnspecifiedRoutes(endpointModel, endpointModel.getRoutes())
            .onComplete(context.succeeding(list -> {
                assertThat(list, hasSize(0));
                async.flag();
            }));
    }

    @Test
    public void testCreateCp4iServicesBindingWithCrd(VertxTestContext context) {
        cp4iResourceOperator = mockCp4iServicesBinding("", true);
        when(cp4iResourceOperator.waitForCp4iServicesBindingStatusAndMaybeGetUrl(anyString(), anyString(), anyLong(), anyLong(), any()))
                .thenReturn(Future.succeededFuture());

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).onComplete(context.succeeding(v -> context.verify(() -> {
            async.flag();
        })));
    }

    @Test
    public void testCreateCp4iServicesBindingWithoutCrd(VertxTestContext context) {
        cp4iResourceOperator = mockCp4iServicesBinding("", false);

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster).onComplete(context.succeeding(v -> context.verify(() -> {
            async.flag();
        })));
    }

    @Test
    public void testCreateCp4iServicesBindingWithHeaderUrl(VertxTestContext context) {
        cp4iResourceOperator = mockCp4iServicesBinding(CP4I_TEST_HEADER_URL, true);
        when(cp4iResourceOperator.waitForCp4iServicesBindingStatusAndMaybeGetUrl(anyString(), anyString(), anyLong(), anyLong(), any()))
            .thenReturn(Future.succeededFuture(CP4I_TEST_HEADER_URL));

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                Optional<Deployment> adminUI = Optional.ofNullable(mockClient.apps().deployments().inNamespace(NAMESPACE).list())
                    .map(DeploymentList::getItems)
                    .map(list -> list.stream()
                        .filter(deploy -> deploy.getMetadata().getName().equals(CLUSTER_NAME + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME))
                        .findFirst())
                    .map(deployment -> (Deployment) deployment.get());

                assertThat(adminUI.isPresent(), is(true));
                Container uiContainer = adminUI.get().getSpec().getTemplate().getSpec().getContainers().get(0);
                assertThat(uiContainer.getEnv(), hasItem(new EnvVarBuilder().withName(CP4I_ADMIN_UI_ENVAR_NAME).withValue(CP4I_TEST_HEADER_URL).build()));
                async.flag();
            })));
    }

    @Test
    public void testAdminUiUpdatesWhenKafkaListenerUpdates(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams noAuth = createEventStreamsWithStrimziOverrides(NAMESPACE, CLUSTER_NAME, new KafkaSpecBuilder()
            .withNewKafka()
                .withNewListeners()
                    .withNewPlain()
                    .endPlain()
                .endListeners()
            .endKafka()
            .build());

        EventStreams withAuth = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), withAuth)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                Optional<Deployment> adminUI = Optional.ofNullable(mockClient.apps().deployments().inNamespace(NAMESPACE).list())
                    .map(DeploymentList::getItems)
                    .map(list -> list.stream()
                        .filter(deploy -> deploy.getMetadata().getName().equals(CLUSTER_NAME + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME))
                        .findFirst())
                    .map(deployment -> (Deployment) deployment.get());

                assertThat(adminUI.isPresent(), is(true));
                Container uiContainer = adminUI.get().getSpec().getTemplate().getSpec().getContainers().get(0);

                assertThat(uiContainer.getEnv(), hasItems(
                    new EnvVarBuilder().withName("ESFF_SECURITY_AUTH").withValue("true").build(),
                    new EnvVarBuilder().withName("ESFF_SECURITY_AUTHZ").withValue("true").build())
                );
            })))
            .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), noAuth))
            .onComplete(context.succeeding(v -> context.verify(() -> {
                Optional<Deployment> adminUI = Optional.ofNullable(mockClient.apps().deployments().inNamespace(NAMESPACE).list())
                    .map(DeploymentList::getItems)
                    .map(list -> list.stream()
                        .filter(deploy -> deploy.getMetadata().getName().equals(CLUSTER_NAME + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME))
                        .findFirst())
                    .map(deployment -> (Deployment) deployment.get());

                assertThat(adminUI.isPresent(), is(true));
                Container uiContainer = adminUI.get().getSpec().getTemplate().getSpec().getContainers().get(0);

                assertThat(uiContainer.getEnv(), hasItems(
                    new EnvVarBuilder().withName("ESFF_SECURITY_AUTH").withValue("false").build(),
                    new EnvVarBuilder().withName("ESFF_SECURITY_AUTHZ").withValue("false").build())
                );
                async.flag();
            })));
    }

    @Test
    public void testCreateCp4iServicesBindingWithoutHeaderUrl(VertxTestContext context) {
        cp4iResourceOperator = mockCp4iServicesBinding("", true);
        when(cp4iResourceOperator.waitForCp4iServicesBindingStatusAndMaybeGetUrl(anyString(), anyString(), anyLong(), anyLong(), any()))
            .thenReturn(Future.succeededFuture(""));

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                Optional<Deployment> adminUI = Optional.ofNullable(mockClient.apps().deployments().inNamespace(NAMESPACE).list())
                    .map(DeploymentList::getItems)
                    .map(list -> list.stream()
                        .filter(deploy -> deploy.getMetadata().getName().equals(CLUSTER_NAME + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME))
                        .findFirst())
                    .map(deployment -> (Deployment) deployment.get());

                assertThat(adminUI.isPresent(), is(true));
                Container uiContainer = adminUI.get().getSpec().getTemplate().getSpec().getContainers().get(0);
                assertThat(uiContainer.getEnv(), hasItem(new EnvVarBuilder().withName(CP4I_ADMIN_UI_ENVAR_NAME).withValue("").build()));
                async.flag();
            })));
    }

    @Test
    public void testDefaultEventStreamsUIEnvVars(VertxTestContext context) {
        cp4iResourceOperator = mockCp4iServicesBinding("", true);
        when(cp4iResourceOperator.waitForCp4iServicesBindingStatusAndMaybeGetUrl(anyString(), anyString(), anyLong(), anyLong(), any()))
            .thenReturn(Future.succeededFuture(""));

        boolean tlsEnabled = AbstractModel.DEFAULT_INTERNAL_TLS.equals(TlsVersion.TLS_V1_2);
        String adminApiService =  "https://" + getInternalServiceName(CLUSTER_NAME, AdminApiModel.COMPONENT_NAME) + "." +  NAMESPACE + ".svc.cluster.local:" + Endpoint.getPodToPodPort(tlsEnabled);
        String schemaRegistryService =  "https://" + getInternalServiceName(CLUSTER_NAME, SchemaRegistryModel.COMPONENT_NAME) + "." +  NAMESPACE + ".svc.cluster.local:" + Endpoint.getPodToPodPort(tlsEnabled);

        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                Optional<Deployment> adminUI = Optional.ofNullable(mockClient.apps().deployments().inNamespace(NAMESPACE).list())
                    .map(DeploymentList::getItems)
                    .map(list -> list.stream()
                        .filter(deploy -> deploy.getMetadata().getName().equals(CLUSTER_NAME + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME))
                        .findFirst())
                    .map(deployment -> (Deployment) deployment.get());

                assertThat(adminUI.isPresent(), is(true));
                Container uiContainer = adminUI.get().getSpec().getTemplate().getSpec().getContainers().get(0);
                EnvVar adminApiEnvVar = new EnvVarBuilder().withName("API_URL").withValue(adminApiService).build();
                EnvVar schemaRegistryEnvVar = new EnvVarBuilder().withName("SCHEMA_REGISTRY_URL").withValue(schemaRegistryService).build();

                assertThat(uiContainer.getEnv(), hasItem(adminApiEnvVar));
                assertThat(uiContainer.getEnv(), hasItem(schemaRegistryEnvVar));
                async.flag();
            })));
    }

    private void updateReplicatorSecretData(Set<HasMetadata> actualResourcesList) {
        actualResourcesList.forEach(item -> {
            if (item instanceof Secret) {
                Secret replicatorSecret = (Secret) item;

                if (replicatorSecret.getMetadata().getName().contains(ReplicatorSecretModel.REPLICATOR_SECRET_NAME)) {
                    Encoder encoder = Base64.getEncoder();
                    String newSecretString = encoder.encodeToString(REPLICATOR_DATA.getBytes(StandardCharsets.UTF_8));
                    Map<String, String> newSecretData = Collections.singletonMap(ReplicatorSecretModel.REPLICATOR_TARGET_CLUSTERS_SECRET_KEY_NAME, newSecretString);
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
                .filter(secret -> secret.getMetadata().getName().contains(ReplicatorSecretModel.REPLICATOR_SECRET_NAME))
                .collect(Collectors.toList()
                );
        assertEquals(1, replicatorSecrets.size(), "Replicator secret Not Found");
        Secret replicatorSecret = replicatorSecrets.get(0);
        Encoder encoder = Base64.getEncoder();
        String newSecretString = encoder.encodeToString(REPLICATOR_DATA.getBytes(StandardCharsets.UTF_8));
        context.verify(() -> assertThat(
                replicatorSecret.getData().get(ReplicatorSecretModel.REPLICATOR_TARGET_CLUSTERS_SECRET_KEY_NAME), is(newSecretString)));
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
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME + "-" + AbstractSecureEndpointsModel.ROUTE_SERVICE_SUFFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME + "-" + AbstractSecureEndpointsModel.ROUTE_SERVICE_SUFFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + AbstractSecureEndpointsModel.ROUTE_SERVICE_SUFFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME + "-" + AbstractSecureEndpointsModel.INTERNAL_SERVICE_SUFFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME + "-" + AbstractSecureEndpointsModel.INTERNAL_SERVICE_SUFFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + AbstractSecureEndpointsModel.INTERNAL_SERVICE_SUFFIX);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME);
        expectedServices.add(clusterName + "-" + APP_NAME + "-" + CollectorModel.COMPONENT_NAME + "-" + AbstractSecureEndpointsModel.INTERNAL_SERVICE_SUFFIX);
        return expectedServices;
    }

    private Set<String> getExpectedRouteNames(String clusterName) {
        Set<String> expectedRoutes = new HashSet<>();
        expectedRoutes.add(UI_ROUTE_NAME);
        expectedRoutes.add(REST_PRODUCER_ROUTE_NAME + "-" + Endpoint.DEFAULT_EXTERNAL_NAME);
        expectedRoutes.add(SCHEMA_REGISTRY_ROUTE_NAME + "-" + Endpoint.DEFAULT_EXTERNAL_NAME);
        expectedRoutes.add(ADMIN_API_ROUTE_NAME + "-" + Endpoint.DEFAULT_EXTERNAL_NAME);
        return expectedRoutes;
    }

    private Set<String> getExpectedNetworkPolicyNames(String clusterName) {
        Set<String> expectedRoutes = new HashSet<>();
        expectedRoutes.add(clusterName + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME);
        expectedRoutes.add(clusterName + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME);
        expectedRoutes.add(clusterName + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME);
        expectedRoutes.add(clusterName + "-" + APP_NAME + "-" + CollectorModel.COMPONENT_NAME);
        expectedRoutes.add(clusterName + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME);
        expectedRoutes.add(clusterName + "-" + APP_NAME + "-" + "kafka"); // TODO reference KafkaCluster.APPLICATION_NAME
        return expectedRoutes;
    }

    private Set<String> getExpectedSecretNames(String clusterName) {
        Set<String> expectedSecrets = new HashSet<>();
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + MessageAuthenticationModel.SECRET_SUFFIX);
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + ReplicatorSecretModel.REPLICATOR_SECRET_NAME);
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME + "-" + CertificateSecretModel.CERT_SECRET_NAME_POSTFIX);
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME + "-" + CertificateSecretModel.CERT_SECRET_NAME_POSTFIX);
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + CertificateSecretModel.CERT_SECRET_NAME_POSTFIX);
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + CollectorModel.COMPONENT_NAME + "-" + CertificateSecretModel.CERT_SECRET_NAME_POSTFIX);
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + ClusterSecretsModel.COMPONENT_NAME);
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + ReplicatorSourceUsersModel.SOURCE_CONNECTOR_KAFKA_USER_NAME);

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
        expectedKafkaUsers.add(clusterName + "-" + APP_NAME + "-" + ReplicatorSourceUsersModel.SOURCE_CONNECTOR_KAFKA_USER_NAME);
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
            default:
                System.out.println("Unexpected type " + type);
        }
        return result;
    }

    private EventStreams createDefaultEventStreams(String namespace, String clusterName) {
        KafkaSpecBuilder kafka = new KafkaSpecBuilder()
            .editOrNewKafka()
                .withReplicas(3)
                .withNewListeners()
                    .withNewPlain()
                    .endPlain()
                    .withNewTls()
                        .withNewKafkaListenerAuthenticationTlsAuth()
                        .endKafkaListenerAuthenticationTlsAuth()
                    .endTls()
                    .withNewKafkaListenerExternalRoute()
                        .withNewKafkaListenerAuthenticationTlsAuth()
                        .endKafkaListenerAuthenticationTlsAuth()
                    .endKafkaListenerExternalRoute()
                .endListeners()
            .endKafka()
            .editOrNewZookeeper()
                .withReplicas(3)
            .endZookeeper();

        return createEventStreamsWithStrimziOverrides(namespace, clusterName, kafka.build());
    }

    private EventStreams createMinimalESInstance() {
        return new EventStreamsBuilder()
            .withMetadata(new ObjectMetaBuilder()
                .withNewName(CLUSTER_NAME)
                .withNewNamespace(NAMESPACE)
                .build())
            .withNewSpec()
            .withNewLicense()
                .withAccept(true)
            .endLicense()
            .withNewVersion(DEFAULT_VERSION)
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .withReplicas(1)
                .withNewListeners()
                .withNewPlain()
                .endPlain()
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
    }

    private EventStreams createMinimalNoTLSESInstance() {
        return new EventStreamsBuilder()
            .withMetadata(new ObjectMetaBuilder()
                .withNewName(CLUSTER_NAME)
                .withNewNamespace(NAMESPACE)
                .build())
            .withNewSpec()
            .withNewLicense()
            .withAccept(true)
            .endLicense()
            .withNewSecurity()
            .withInternalTls(TlsVersion.NONE)
            .endSecurity()
            .withNewVersion(DEFAULT_VERSION)
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .withReplicas(1)
                .withNewListeners()
                .withNewPlain()
                .endPlain()
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

        return createEventStreamsWithStrimziOverrides(namespace, clusterName, kafka.build());
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

        return createEventStreamsWithStrimziOverrides(namespace, clusterName, kafka.build());
    }

    private EventStreams createEventStreamsWithStrimziOverrides(String namespace, String clusterName, KafkaSpec kafka) {
        return new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(clusterName).withNamespace(namespace).build())
                .withNewSpec()
                    .withNewLicense()
                        .withAccept(true)
                    .endLicense()
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
                    .withStrimziOverrides(kafka)
                    .withVersion(DEFAULT_VERSION)
                .endSpec()
                .build();
    }

    private String formatRouteHost(String name) {
        return String.format("%s.%s", name, ROUTE_HOST_POSTFIX);
    }

    private void createRoutesInMockClient() {
        List<Route> routes = new ArrayList<>();
        routes.add(createRoute(UI_ROUTE_NAME, NAMESPACE));
        routes.add(createRoute(REST_PRODUCER_ROUTE_NAME + "-" + Endpoint.DEFAULT_EXTERNAL_NAME, NAMESPACE));
        routes.add(createRoute(SCHEMA_REGISTRY_ROUTE_NAME + "-" + Endpoint.DEFAULT_EXTERNAL_NAME, NAMESPACE));
        routes.add(createRoute(ADMIN_API_ROUTE_NAME + "-" + Endpoint.DEFAULT_EXTERNAL_NAME, NAMESPACE));

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

    private Cp4iServicesBindingResourceOperator mockCp4iServicesBinding(String headerUrl, Boolean crdPresent) {
        // mock Cp4iServicesBinding Crd
        CustomResourceDefinitionList crdList;
        if (crdPresent) {
            ObjectMeta meta = new ObjectMetaBuilder().withName(EventStreamsOperator.CP4I_SERVICES_BINDING_NAME).build();
            CustomResourceDefinition crd = new CustomResourceDefinitionBuilder().withMetadata(meta).build();
            crdList = new CustomResourceDefinitionListBuilder().addToItems(crd).build();
        } else {
            crdList = new CustomResourceDefinitionListBuilder().build();
        }

        when(mockClient.customResourceDefinitions().list()).thenReturn(crdList);
        cp4iResourceOperator = mock(Cp4iServicesBindingResourceOperator.class);

        return cp4iResourceOperator;
    }

    @Test
    public void testDefaultEndpointRouteLabelsChangeWhenCustomEndpointsProvided(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        EndpointSpec secureRouteMutualTls = new EndpointSpecBuilder()
            .withName("secure-mutual")
            .withContainerPort(9999)
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withType(EndpointServiceType.ROUTE)
            .withAuthenticationMechanisms(Collections.singletonList(TLS_LABEL))
            .build();

        String shortRouteName = AdminApiModel.COMPONENT_NAME + "-" + Endpoint.DEFAULT_EXTERNAL_NAME;
        String longRouteName = String.format("%s-ibm-es-%s-%s", CLUSTER_NAME, AdminApiModel.COMPONENT_NAME, Endpoint.DEFAULT_EXTERNAL_NAME);
        String expectedLongRouteName = String.format("%s-ibm-es-%s-%s", CLUSTER_NAME, AdminApiModel.COMPONENT_NAME, secureRouteMutualTls.getName());

        EventStreams defaultInstance = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), defaultInstance)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                Route route = routeOperator.get(NAMESPACE,  longRouteName);

                assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + IAM_BEARER_LABEL, "true"));
                assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + SCRAM_SHA_512_LABEL, "true"));
                assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_PROTOCOL_LABEL, "https"));
            })))
            .map(v -> {
                ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(updatedEventStreams.capture());
                context.verify(() -> assertThat(updatedEventStreams.getValue().getStatus().getRoutes().get(shortRouteName), is(notNullValue())));

                // Get the default CR that was used and return the updated CR with secure endpoints. In the next compose we use this
                // updated CR which has custom endpoints.
                EventStreams customEventStreams = new EventStreamsBuilder(defaultInstance)
                    .editSpec()
                    .withNewAdminApi()
                        .withReplicas(1)
                        .withEndpoints(secureRouteMutualTls)
                    .endAdminApi()
                    .endSpec()
                    .build();
                return customEventStreams;
            })
            .compose(customEventStreams -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), customEventStreams))
            .onComplete(context.succeeding(v -> context.verify(() -> {
                Route route = routeOperator.get(NAMESPACE, expectedLongRouteName);

                assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + TLS_LABEL, "true"));
                assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_PROTOCOL_LABEL, "https"));
                async.flag();
            })));
    }

    @Test
    public void testEndpointRouteLabelsChangeWhenEndpointsUpdated(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);

        EndpointSpec secureRouteBearer = new EndpointSpecBuilder()
            .withName("secure-bearer")
            .withContainerPort(9999)
            .withType(EndpointServiceType.ROUTE)
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withAuthenticationMechanisms(Collections.singletonList(IAM_BEARER_LABEL))
            .build();

        EndpointSpec secureRouteMutualTls = new EndpointSpecBuilder()
            .withName("secure-mutual")
            .withContainerPort(9998)
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withType(EndpointServiceType.ROUTE)
            .withAuthenticationMechanisms(Collections.singletonList(TLS_LABEL))
            .build();

        EndpointSpec insecureRouteMutualTls = new EndpointSpecBuilder()
            .withName("insecure-mutual")
            .withContainerPort(9999)
            .withTlsVersion(TlsVersion.NONE)
            .withType(EndpointServiceType.ROUTE)
            .withAuthenticationMechanisms(Collections.singletonList(TLS_LABEL))
            .build();

        EndpointSpec insecureRouteBearer = new EndpointSpecBuilder()
            .withName("insecure-bearer")
            .withContainerPort(9998)
            .withTlsVersion(TlsVersion.NONE)
            .withType(EndpointServiceType.ROUTE)
            .withAuthenticationMechanisms(Collections.singletonList(IAM_BEARER_LABEL))
            .build();

        EventStreams secureInstance = new EventStreamsBuilder()
            .withMetadata(new ObjectMetaBuilder()
                .withNewName(CLUSTER_NAME)
                .withNewNamespace(NAMESPACE)
                .build())
            .withNewSpec()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.TLS_V1_2)
                .build())
            .withNewAdminApi()
                .withReplicas(1)
                .withEndpoints(new ArrayList<>(Arrays.asList(secureRouteBearer, secureRouteMutualTls)))
            .endAdminApi()
            .withNewLicense()
                .withAccept(true)
            .endLicense()
            .withNewVersion(DEFAULT_VERSION)
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

        String secureMutualTlsRouteLongName = String.format("%s-ibm-es-%s-%s", CLUSTER_NAME, AdminApiModel.COMPONENT_NAME, secureRouteMutualTls.getName());
        String secureBearerRouteLongName = String.format("%s-ibm-es-%s-%s", CLUSTER_NAME, AdminApiModel.COMPONENT_NAME, secureRouteBearer.getName());
        String insecureMutualTlsRouteLongName = String.format("%s-ibm-es-%s-%s", CLUSTER_NAME, AdminApiModel.COMPONENT_NAME, insecureRouteMutualTls.getName());
        String insecureBearerRouteLongName = String.format("%s-ibm-es-%s-%s", CLUSTER_NAME, AdminApiModel.COMPONENT_NAME, insecureRouteBearer.getName());

        Checkpoint async = context.checkpoint();

        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), secureInstance)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                Route secureMutualTlsRoute = routeOperator.get(NAMESPACE, secureMutualTlsRouteLongName);
                Route secureBearerRoute = routeOperator.get(NAMESPACE, secureBearerRouteLongName);

                assertThat(secureMutualTlsRoute.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + TLS_LABEL, "true"));
                assertThat(secureMutualTlsRoute.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_PROTOCOL_LABEL, "https"));
                assertThat(secureBearerRoute.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + IAM_BEARER_LABEL, "true"));
                assertThat(secureBearerRoute.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_PROTOCOL_LABEL, "https"));
            })))
            .map(v -> {
                ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(2)).updateEventStreamsStatus(updatedEventStreams.capture());
                context.verify(() -> assertThat(updatedEventStreams.getValue().getStatus().getRoutes().get("admapi-secure-mutual"), is(notNullValue())));
                context.verify(() -> assertThat(updatedEventStreams.getValue().getStatus().getRoutes().get("admapi-secure-bearer"), is(notNullValue())));

                // Get the CR used previously and return the updated CR with insecure endpoints.
                EventStreams insecureInstance = new EventStreamsBuilder(secureInstance)
                    .editSpec()
                    .withSecurity(new SecuritySpecBuilder()
                        .withInternalTls(TlsVersion.NONE)
                        .build())
                    .withNewAdminApi()
                        .withReplicas(1)
                        .withEndpoints(new ArrayList<>(Arrays.asList(insecureRouteBearer, insecureRouteMutualTls)))
                    .endAdminApi()
                    .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                            .withReplicas(1)
                            .withNewListeners()
                                .withNewPlain()
                                .endPlain()
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
                return insecureInstance;
            })
            .compose(insecureInstance -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), insecureInstance))
            .onComplete(context.succeeding(v -> context.verify(() -> {
                Route insecureMutualTlsRoute = routeOperator.get(NAMESPACE, insecureMutualTlsRouteLongName);
                Route insecureBearerRoute = routeOperator.get(NAMESPACE, insecureBearerRouteLongName);

                assertThat(insecureMutualTlsRoute.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + TLS_LABEL, "true"));
                assertThat(insecureMutualTlsRoute.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_PROTOCOL_LABEL, "http"));
                assertThat(insecureBearerRoute.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + IAM_BEARER_LABEL, "true"));
                assertThat(insecureBearerRoute.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_PROTOCOL_LABEL, "http"));

                ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
                verify(esResourceOperator, times(3)).updateEventStreamsStatus(updatedEventStreams.capture());
                assertThat(updatedEventStreams.getValue().getStatus().getRoutes().get("admapi-insecure-mutual"), is(notNullValue()));
                assertThat(updatedEventStreams.getValue().getStatus().getRoutes().get("admapi-insecure-bearer"), is(notNullValue()));
                async.flag();
            })));
    }

    @Test
    public void testDefaultEventStreamsSchemaRegistryHasKafkaPrincipal(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, esReplicatorResourceOperator, kafkaUserOperator, imageConfig, routeOperator, metricsProvider, kafkaStatusReadyTimeoutMs);
        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
            .onComplete(context.succeeding(v -> context.verify(() -> {
                Optional<Deployment> schemaRegistry = Optional.ofNullable(mockClient.apps().deployments().inNamespace(NAMESPACE).list())
                    .map(DeploymentList::getItems)
                    .map(list -> list.stream()
                        .filter(deploy -> deploy.getMetadata().getName().equals(CLUSTER_NAME + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME))
                        .findFirst())
                    .map(deployment -> deployment.get());

                assertThat(schemaRegistry.isPresent(), is(true));

                Optional<KafkaUser> kafkaUser = Optional.ofNullable(mockClient.customResources(io.strimzi.api.kafka.Crds.kafkaUser(), KafkaUser.class, KafkaUserList.class, DoneableKafkaUser.class).inNamespace(NAMESPACE).list())
                    .map(KafkaUserList::getItems)
                    .map(list -> list.stream()
                        .filter(ku -> ku.getMetadata().getName().equals(InternalKafkaUserModel.getInternalKafkaUserName(CLUSTER_NAME)))
                        .findFirst())
                    .map(ku -> ku.get());

                Container schemaRegistryContainer = schemaRegistry.get().getSpec().getTemplate().getSpec().getContainers().get(2);
                EnvVar expectedKafkaPrincipal = new EnvVarBuilder().withName("KAFKA_PRINCIPAL").withValue(kafkaUser.get().getStatus().getUsername()).build();

                assertThat(schemaRegistryContainer.getEnv(), hasItem(expectedKafkaPrincipal));
                async.flag();
            })));
    }
}
