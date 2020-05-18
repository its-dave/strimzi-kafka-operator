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

//import static com.ibm.eventstreams.api.model.AbstractModel.APP_NAME;
//import static org.hamcrest.CoreMatchers.containsString;
//import static org.hamcrest.CoreMatchers.is;
//import static org.hamcrest.CoreMatchers.not;
//import static org.hamcrest.MatcherAssert.assertThat;
//import static org.hamcrest.Matchers.aMapWithSize;
//import static org.hamcrest.Matchers.contains;
//import static org.hamcrest.Matchers.hasItem;
//import static org.hamcrest.Matchers.hasKey;
//import static org.hamcrest.Matchers.hasSize;
//import static org.hamcrest.core.IsNull.notNullValue;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyLong;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//import java.nio.charset.StandardCharsets;
//import java.security.cert.X509Certificate;
//import java.text.SimpleDateFormat;
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Base64;
//import java.util.Base64.Encoder;
//import java.util.Collections;
//import java.util.Date;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.Set;
//import java.util.concurrent.atomic.AtomicReference;
//import java.util.stream.Collectors;
//
//import com.ibm.eventstreams.api.Labels;
//import com.ibm.eventstreams.api.Listener;
//import com.ibm.eventstreams.api.model.AbstractSecureEndpointModel;
//import com.ibm.eventstreams.api.model.AdminApiModel;
//import com.ibm.eventstreams.api.model.AdminUIModel;
//import com.ibm.eventstreams.api.model.CertificateSecretModel;
//import com.ibm.eventstreams.api.model.ClusterSecretsModel;
//import com.ibm.eventstreams.api.model.CollectorModel;
//import com.ibm.eventstreams.api.model.MessageAuthenticationModel;
//import com.ibm.eventstreams.api.model.InternalKafkaUserModel;
//import com.ibm.eventstreams.api.model.ReplicatorSecretModel;
//import com.ibm.eventstreams.api.model.ReplicatorDestinationUsersModel;
//import com.ibm.eventstreams.api.model.ReplicatorSourceUsersModel;
//import com.ibm.eventstreams.api.model.RestProducerModel;
//import com.ibm.eventstreams.api.model.SchemaRegistryModel;
//import com.ibm.eventstreams.api.model.utils.MockEventStreamsKube;
//import com.ibm.eventstreams.api.model.utils.ModelUtils;
//import com.ibm.eventstreams.api.spec.EventStreams;
//import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
//import com.ibm.eventstreams.api.spec.EventStreamsReplicator;
//import com.ibm.eventstreams.api.spec.EventStreamsReplicatorBuilder;
//import com.ibm.eventstreams.api.spec.ReplicatorSpec;
//import com.ibm.eventstreams.api.spec.ReplicatorSpecBuilder;
//import com.ibm.eventstreams.api.status.EventStreamsAvailableVersions;
//import com.ibm.eventstreams.api.status.EventStreamsEndpoint;
//import com.ibm.eventstreams.api.status.EventStreamsEndpointBuilder;
//import com.ibm.eventstreams.api.status.EventStreamsStatus;
//import com.ibm.eventstreams.api.status.EventStreamsStatusBuilder;
//import com.ibm.eventstreams.api.status.EventStreamsVersions;
//import com.ibm.eventstreams.controller.utils.ControllerUtils;
//import com.ibm.iam.api.spec.Cp4iServicesBinding;
//import com.ibm.iam.api.spec.Cp4iServicesBindingDoneable;
//import com.ibm.iam.api.spec.Cp4iServicesBindingList;
//import com.ibm.iam.api.controller.Cp4iServicesBindingResourceOperator;
//
////import io.strimzi.api.kafka.KafkaMirrorMaker2List;
////import io.strimzi.api.kafka.model.DoneableKafkaMirrorMaker2;
////import io.strimzi.api.kafka.model.KafkaMirrorMaker2;
//import io.strimzi.api.kafka.KafkaMirrorMaker2List;
//import io.strimzi.api.kafka.model.KafkaMirrorMaker2;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.hamcrest.Matchers;
//import org.junit.jupiter.api.AfterAll;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeAll;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//
//import io.fabric8.kubernetes.api.model.ConfigMap;
//import io.fabric8.kubernetes.api.model.Container;
//import io.fabric8.kubernetes.api.model.DoneableConfigMap;
//import io.fabric8.kubernetes.api.model.DoneableSecret;
//import io.fabric8.kubernetes.api.model.EnvVar;
//import io.fabric8.kubernetes.api.model.EnvVarBuilder;
//import io.fabric8.kubernetes.api.model.HasMetadata;
//import io.fabric8.kubernetes.api.model.ObjectMeta;
//import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
//import io.fabric8.kubernetes.api.model.Secret;
//import io.fabric8.kubernetes.api.model.Service;
//import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
//import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
//import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionList;
//import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionListBuilder;
//import io.fabric8.kubernetes.api.model.apps.Deployment;
//import io.fabric8.kubernetes.api.model.apps.DeploymentList;
//import io.fabric8.kubernetes.client.KubernetesClient;
//import io.fabric8.kubernetes.client.KubernetesClientException;
//import io.fabric8.kubernetes.client.dsl.MixedOperation;
//import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
//import io.fabric8.kubernetes.client.dsl.Resource;
//import io.fabric8.openshift.api.model.Route;
//import io.fabric8.openshift.api.model.RouteBuilder;
//import io.fabric8.openshift.client.OpenShiftClient;
//import io.strimzi.api.kafka.KafkaList;
//import io.strimzi.api.kafka.KafkaUserList;
//import io.strimzi.api.kafka.model.CertAndKeySecretSourceBuilder;
//import io.strimzi.api.kafka.model.DoneableKafka;
//import io.strimzi.api.kafka.model.DoneableKafkaUser;
//import io.strimzi.api.kafka.model.Kafka;
//import io.strimzi.api.kafka.model.KafkaSpec;
//import io.strimzi.api.kafka.model.KafkaSpecBuilder;
//import io.strimzi.api.kafka.model.KafkaUser;
//import io.strimzi.api.kafka.model.listener.KafkaListenerExternalConfigurationBuilder;
//import io.strimzi.api.kafka.model.listener.KafkaListenerExternalRouteBuilder;
//import io.strimzi.api.kafka.model.listener.KafkaListenerTlsBuilder;
//import io.strimzi.api.kafka.model.listener.KafkaListenersBuilder;
//import io.strimzi.api.kafka.model.listener.TlsListenerConfigurationBuilder;
//import io.strimzi.api.kafka.model.status.StatusCondition;
//import io.strimzi.api.kafka.model.status.ConditionBuilder;
//import io.strimzi.api.kafka.model.status.KafkaStatusBuilder;
//import io.strimzi.api.kafka.model.status.ListenerAddressBuilder;
//import io.strimzi.api.kafka.model.status.ListenerStatus;
//import io.strimzi.api.kafka.model.status.ListenerStatusBuilder;
//import io.strimzi.certs.CertAndKey;
//import io.strimzi.operator.KubernetesVersion;
//import io.strimzi.operator.PlatformFeaturesAvailability;
//import io.strimzi.operator.common.Reconciliation;
//import io.strimzi.operator.common.operator.resource.ReconcileResult;
//import io.strimzi.operator.common.operator.resource.RouteOperator;
//import io.vertx.core.CompositeFuture;
//import io.vertx.core.Future;
//import io.vertx.core.Vertx;
//import io.vertx.junit5.Checkpoint;
//import io.vertx.junit5.VertxExtension;
//import io.vertx.junit5.VertxTestContext;


//@SuppressWarnings({"checkstyle:ClassFanOutComplexity", "checkstyle:ClassDataAbstractionCoupling"})
//@ExtendWith(VertxExtension.class)
public class EventStreamsReplicatorOperatorTest {

//    private static final Logger LOGGER = LogManager.getLogger(EventStreamsReplicatorOperatorTest.class);
//
//    private static final String NAMESPACE = "test-namespace";
//    private static final String CLUSTER_NAME = "my-es";
//    private static final int EXPECTED_DEFAULT_REPLICAS = 1;
//    private static final String REPLICATOR_DATA = "[replicatorTestData]";
//    private static final String DEFAULT_VERSION = "2020.1.1";
//    private static final int TWO_YEARS_PLUS_IN_SECONDS = 70000000;
//
//    private static Vertx vertx;
//    private KubernetesClient mockClient;
//    private EventStreamsOperator esOperator;
//    private EventStreamsReplicatorResourceOperator esReplicatorResourceOperator;
//    private EventStreamsResourceOperator esResourceOperator;
//    private EventStreamsReplicatorOperator esReplicatorOperator;
//    private PlatformFeaturesAvailability pfa;
//
//    private long kafkaStatusReadyTimeoutMs = 0;
//
//    public enum KubeResourceType {
//        DEPLOYMENTS,
//        SECRETS,
//        NETWORK_POLICYS,
//        KAFKA_USERS,
//        KAFKA_MIRROR_MAKER_2S
//    }
//
//    @BeforeAll
//    public static void setup() {
//        vertx = Vertx.vertx();
//    }
//
//    @AfterAll
//    public static void closeVertxInstance() {
//        vertx.close();
//    }
//
//    @BeforeEach
//    public void init() {
//        // setting up a mock Kubernetes client
//        Set<Secret> initialSecrets = new HashSet<>();
//        ModelUtils.generateClusterCa(NAMESPACE, CLUSTER_NAME, APP_NAME, ModelUtils.Certificates.CLUSTER_CA, ModelUtils.Keys.CLUSTER_CA_KEY).forEach(s -> initialSecrets.add(s));
//        ModelUtils.generateReplicatorConnectSecrets(NAMESPACE, CLUSTER_NAME, APP_NAME, ModelUtils.Certificates.CLUSTER_CA, ModelUtils.Keys.CLUSTER_CA_KEY).forEach(s -> initialSecrets.add(s));
//
//        mockClient = new MockEventStreamsKube()
//                .withInitialSecrets(initialSecrets)
//                .build();
//        when(mockClient.getNamespace()).thenReturn(NAMESPACE);
//
//        KafkaMirrorMaker2 mockKafkaMM2 = new KafkaMirrorMaker2();
//        mockKafkaMM2.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build());
//      //  mockKafkaMM2.setStatus(new KafkaStatusBuilder().build());
//        Optional<KafkaMirrorMaker2> mockMM2Instance = Optional.of(mockKafkaMM2);
//
//        esOperator = mock(EventStreamsOperator.class);
//
//        esReplicatorResourceOperator = mock(EventStreamsReplicatorResourceOperator.class);
//        when(esReplicatorResourceOperator.replicatorMirrorMaker2CRHasReadyStatus(anyString(), anyString(), anyLong(), anyLong())).thenReturn(Future.succeededFuture());
//        when(esReplicatorResourceOperator.createOrUpdate(any(EventStreamsReplicator.class))).thenReturn(Future.succeededFuture());
//        when(esReplicatorResourceOperator.getReplicatorMirrorMaker2Instance(anyString(), anyString())).thenReturn(mockMM2Instance);
//
//        Kafka mockKafka = new Kafka();
//        mockKafka.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build());
//        mockKafka.setStatus(new KafkaStatusBuilder().build());
//        Optional<Kafka> mockKafkaInstance = Optional.of(mockKafka);
//
//        esResourceOperator = mock(EventStreamsResourceOperator.class);
//        when(esResourceOperator.kafkaCRHasReadyStatus(anyString(), anyString(), anyLong(), anyLong())).thenReturn(Future.succeededFuture());
//        when(esResourceOperator.createOrUpdate(any(EventStreams.class))).thenReturn(Future.succeededFuture());
//        when(esResourceOperator.getKafkaInstance(anyString(), anyString())).thenReturn(mockKafkaInstance);
//
//        Cp4iServicesBinding mockCp4i = new Cp4iServicesBinding();
//        mockCp4i.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build());
//
//
//        NonNamespaceOperation mockNamespaceOperation = mock(NonNamespaceOperation.class);
//        Resource<Cp4iServicesBinding, Cp4iServicesBindingDoneable> res = mock(Resource.class);
//        when(mockCp4iCr.inNamespace(anyString())).thenReturn(mockNamespaceOperation);
//        when(mockCp4iCr.inNamespace(anyString()).withName(anyString())).thenReturn(res);
//
//        pfa = mock(PlatformFeaturesAvailability.class);
//        when(pfa.hasRoutes()).thenReturn(true);
//
//        // mock ICP Config Map is present
//        Map<String, String> configMapData = new HashMap<>();
//        configMapData.put("cluster_address", "0.0.0.0");
//        ConfigMap testICPConfigMap = new ConfigMap();
//        testICPConfigMap.setData(configMapData);
//        Resource<ConfigMap, DoneableConfigMap> mockResource = mock(Resource.class);
//        when(mockClient.configMaps().inNamespace("kube-public")).thenReturn(mockNamespaceOperation);
//        when(mockNamespaceOperation.withName("ibmcloud-cluster-info")).thenReturn(mockResource);
//        when(mockResource.get()).thenReturn(testICPConfigMap);
//
//        // mock ICP cluster ca cert
//        Map<String, String> secretData = new HashMap<>();
//        secretData.put("ca.crt", "QnJOY0twdXdjaUxiCg==");
//        Secret ibmCloudClusterCaCert = new Secret();
//        ibmCloudClusterCaCert.setData(secretData);
//        Resource<Secret, DoneableSecret> mockSecret = mock(Resource.class);
//        when(mockClient.secrets().inNamespace("kube-public")).thenReturn(mockNamespaceOperation);
//        when(mockNamespaceOperation.withName("ibmcloud-cluster-ca-cert")).thenReturn(mockSecret);
//        when(mockSecret.get()).thenReturn(ibmCloudClusterCaCert);
//
//
//    }
//
//    @AfterEach
//    public void closeMockClient() {
//        mockClient.close();
//    }
//
//    @Test
//    public void testCreateDefaultEventStreamsInstanceOpenShift(VertxTestContext context) {
//        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
//
//        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
//
//        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
//        EventStreamsReplicator esReplicatorCluster = createDefaultEventStreamsReplicator(NAMESPACE, CLUSTER_NAME);
//        Map<String, Integer> expectedResourcesWithReplicas = getExpectedResourcesWithReplicas(CLUSTER_NAME);
//        Set<String> expectedResources = expectedResourcesWithReplicas.keySet();
//        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);
//        Set<String> expectedKafkaUsers = getExpectedKafkaUsers(CLUSTER_NAME);
//
//        Checkpoint async = context.checkpoint();
//        esReplicatorOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
//                .onComplete(context.succeeding(v -> context.verify(() -> {
//                    verifyHasOnlyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
//                    verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
//                    verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
//
//                    Set<HasMetadata> mm2s = getResources(NAMESPACE, KubeResourceType.KAFKA_MIRROR_MAKER_2S);
//                    mm2s.forEach(mm2 -> {
//                        for (Map.Entry<String, String> label: mm2.getMetadata().getLabels().entrySet()) {
//                            assertThat("MirrorMaker2 Custom Resources should not contain reserved domain labels", label.getKey(), not(containsString(io.strimzi.operator.common.model.Labels.STRIMZI_DOMAIN)));
//                        }
//                    });
//
//                    verifyHasOnlyResources(context, expectedKafkaUsers, KubeResourceType.KAFKA_USERS);
//                    Set<HasMetadata> kafkaUsers = getResources(NAMESPACE, KubeResourceType.KAFKA_USERS);
//                    kafkaUsers.forEach(user -> {
//                        for (Map.Entry<String, String> label: user.getMetadata().getLabels().entrySet()) {
//                            if (!label.getKey().equals(io.strimzi.operator.common.model.Labels.STRIMZI_CLUSTER_LABEL)) {
//                                assertThat("KafkaUser Custom Resources should not contain reserved domain labels, with the exception of the cluster label", label.getKey(), not(containsString(io.strimzi.operator.common.model.Labels.STRIMZI_DOMAIN)));
//                            }
//                        }
//                    });
//
//                    async.flag();
//                })));
//    }
//
//    @Test
//    public void testCreateDefaultEventStreamsInstanceK8s(VertxTestContext context) {
//        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
//        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator,
//                cp4iResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
//
//        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
//        Map<String, Integer> expectedResourcesWithReplicas = getExpectedResourcesWithReplicas(CLUSTER_NAME);
//        Set<String> expectedResources = expectedResourcesWithReplicas.keySet();
//        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);
//
//        Checkpoint async = context.checkpoint();
//        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
//                .onComplete(context.succeeding(v -> context.verify(() -> {
//                    verifyHasOnlyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
//                    verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
//                    verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
//                    async.flag();
//                })));
//    }
//
//    @Test
//    public void testStatusHasCorrectVersions(VertxTestContext context) {
//        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
//
//        esReplicatorOperator = new EventStreamsReplicatorOperator(vertx, mockClient, EventStreamsReplicator.RESOURCE_KIND, pfa, esReplicatorResourceOperator, esResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
//        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
//
//        Checkpoint async = context.checkpoint();
//        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
//                .onComplete(context.succeeding(v -> context.verify(() -> {
//                    ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
//                    verify(esResourceOperator).createOrUpdate(argument.capture());
//                    assertThat(argument.getValue().getStatus().getVersions().getInstalled(), is(DEFAULT_VERSION));
//                    assertThat(argument.getValue().getStatus().getVersions().getAvailable().getVersions(), contains(DEFAULT_VERSION));
//                    assertThat(argument.getValue().getStatus().getVersions().getAvailable().getChannels(), contains("2020.1"));
//                    async.flag();
//                })));
//    }
//
//
//
//    @Test
//    public void testCustomImagesOverride(VertxTestContext context) {
//        mockRoutes();
//
//        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(false, KubernetesVersion.V1_9);
//        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
//        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
//        //TODO swap for Replicator Image
//        esCluster.getSpec().getAdminUI().setImage("adminUi-image:test");
//        Checkpoint async = context.checkpoint();
//        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
//                .onComplete(context.succeeding(v -> context.verify(() -> {
//                    ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
//                    verify(esResourceOperator).createOrUpdate(argument.capture());
//                    assertThat(argument.getValue().getStatus().isCustomImages(), is(true));
//                    async.flag();
//                })));
//    }
//
//    @Test
//    public void testEventStreamsNameTooLongThrows(VertxTestContext context) {
//
//        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
//
//        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
//
//        // 17 Characters long
//        String clusterName = "long-instancename";
//
//        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, clusterName);
//        ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
//        Checkpoint async = context.checkpoint();
//
//        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, clusterName), esCluster)
//                .onComplete(context.failing(e -> context.verify(() -> {
//                    assertThat(e.getMessage(), is("Invalid Custom Resource: check status"));
//                    // check status
//                    verify(esResourceOperator).createOrUpdate(updatedEventStreams.capture());
//                    assertThat("Status is incorrect, found status : " + updatedEventStreams.getValue().getStatus(), updatedEventStreams.getValue().getStatus().getConditions().get(0).getMessage(), is("Invalid custom resource: EventStreams metadata name too long. Maximum length is 16"));
//                    async.flag();
//                })));
//    }
//
//    @Test void testMultipleReplicatorInstancesSucceedes(Vertx context) {
//
//    }
//
//    @Test
//    public void testOauthSecurityErrorsReplicator(VertxTestContext context) {
//
//    }
//
//    @Test
//    public void testIncorrectEventStreamsInstanceErrors(VertxTestContext context) {
//
//    }
//
//    @Test
//    public void testUpdateEventStreamsInstanceOpenShiftNoChanges(VertxTestContext context) {
//        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
//
//        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator,
//                cp4iResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
//
//        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
//        Map<String, Integer> expectedResourcesWithReplicas = getExpectedResourcesWithReplicas(CLUSTER_NAME);
//        Set<String> expectedResources = expectedResourcesWithReplicas.keySet();
//        Set<String> expectedServices = getExpectedServiceNames(CLUSTER_NAME);
//        Set<String> expectedRoutes = getExpectedRouteNames(CLUSTER_NAME);
//        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);
//
//        // Create a cluster
//        Checkpoint async = context.checkpoint();
//
//        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
//                .onComplete(context.succeeding(v -> {
////                verifyHasOnlyResources(context, expectedConfigMaps, KubeResourceType.CONFIG_MAPS);
//                    verifyHasOnlyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
//                    verifyHasOnlyResources(context, expectedServices, KubeResourceType.SERVICES);
//                    verifyHasOnlyResources(context, expectedRoutes, KubeResourceType.ROUTES);
//                    verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
//                    verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
//                    LOGGER.debug("Start updating cluster");
//                }))
//                // update the cluster
//                .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster))
//                .onComplete(context.succeeding(v -> {
////                verifyHasOnlyResources(context, expectedConfigMaps, KubeResourceType.CONFIG_MAPS);
//                    verifyHasOnlyResources(context, expectedResources, KubeResourceType.DEPLOYMENTS);
//                    verifyHasOnlyResources(context, expectedServices, KubeResourceType.SERVICES);
//                    verifyHasOnlyResources(context, expectedRoutes, KubeResourceType.ROUTES);
//                    verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
//                    verifyReplicasInDeployments(context, expectedResourcesWithReplicas);
//                    async.flag();
//                }));
//    }
//
//
//
//    @Test
//    public void testStatusIsCorrectlyDisplayed(VertxTestContext context) {
//        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
//        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
//        EventStreams esCluster = createDefaultEventStreams(NAMESPACE, CLUSTER_NAME);
//
//
//        Checkpoint async = context.checkpoint();
//        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), esCluster)
//                .onComplete(context.succeeding(v -> context.verify(() -> {
//                    ArgumentCaptor<EventStreams> argument = ArgumentCaptor.forClass(EventStreams.class);
//                    verify(esResourceOperator).createOrUpdate(argument.capture());
//                    assertThat(argument.getValue().getStatus().isCustomImages(), is(false));
//                    assertThat(esCluster.getStatus().getVersions().getInstalled(), is(EventStreamsVersions.OPERAND_VERSION));
//                    assertThat(esCluster.getStatus().getVersions().getAvailable().getChannels(), is(EventStreamsAvailableVersions.CHANNELS));
//                    assertThat(esCluster.getStatus().getVersions().getAvailable().getVersions(), is(EventStreamsAvailableVersions.VERSIONS));
//                    context.completeNow();
//                    async.flag();
//                })));
//    }
//
//
//
//   @Test
//   public void testZeroReplicatorReplicas(VertxTestContext context) {
//
//   }
//    @Test
//    public void testReplicatorComponentCreatedAndDeletedWhenAddedAndRemovedFromCR(VertxTestContext context) {
//        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
//        esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);
//
//        EventStreams minimalInstance = new EventStreamsBuilder()
//                .withMetadata(new ObjectMetaBuilder()
//                        .withNewName(CLUSTER_NAME)
//                        .withNewNamespace(NAMESPACE)
//                        .build())
//                .withNewSpec()
//                .withLicenseAccept(true)
//                .withNewVersion(DEFAULT_VERSION)
//                .withStrimziOverrides(new KafkaSpecBuilder()
//                        .withNewKafka()
//                            .withReplicas(1)
//                            .withNewEphemeralStorage()
//                            .endEphemeralStorage()
//                        .endKafka()
//                        .withNewZookeeper()
//                            .withReplicas(1)
//                            .withNewEphemeralStorage()
//                            .endEphemeralStorage()
//                        .endZookeeper()
//                        .build())
//                .endSpec()
//                .build();
//
//        EventStreams instance = new EventStreamsBuilder(minimalInstance)
//                .editSpec()
//                    .withStrimziOverrides(new KafkaSpecBuilder(minimalInstance.getSpec().getStrimziOverrides())
//                            .editOrNewKafka()
//                                .withListeners(ModelUtils.getMutualTLSOnBothInternalAndExternalListenerSpec())
//                            .endKafka()
//                            .build())
//                    .withNewReplicator()
//                        .withReplicas(1)
//                    .endReplicator()
//                .endSpec()
//                .build();
//
//        String defaultComponentResourceName = CLUSTER_NAME + "-" + APP_NAME + "-" + ReplicatorModel.COMPONENT_NAME;
//
//        String kafkaMirrorMaker2Name = defaultComponentResourceName;
//        String networkPolicyName = defaultComponentResourceName;
//
//        String secretName = defaultComponentResourceName + "-secret";
//        String replicatorConnectUserName = CLUSTER_NAME + "-" + APP_NAME + "-rep-connect-user";
//        String replicatorTargetConnectorUser = CLUSTER_NAME + "-" + APP_NAME + "-rep-target-user";
//        String replicatorSourceConnectorUser = CLUSTER_NAME + "-" + APP_NAME + "-rep-source-user";
//
//        Set<String> kafkaUserNames = new HashSet<>();
//        kafkaUserNames.add(replicatorConnectUserName);
//        kafkaUserNames.add(replicatorTargetConnectorUser);
//        kafkaUserNames.add(replicatorSourceConnectorUser);
//
//
//        Boolean secretNotOptional = true;
//
//        Checkpoint async = context.checkpoint(3);
//
//        esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance)
//                .onComplete(context.succeeding(v -> {
//                    verifyContainsResource(context, kafkaMirrorMaker2Name, KubeResourceType.KAFKA_MIRROR_MAKER_2S, false);
//                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
//                    verifyContainsResources(context, kafkaUserNames, KubeResourceType.KAFKA_USERS, false);
//                    verifyContainsResource(context, secretName, KubeResourceType.SECRETS, secretNotOptional);
//                    async.flag();
//                }))
//                .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), instance))
//                .onComplete(context.succeeding(v -> {
//                    verifyContainsResource(context, kafkaMirrorMaker2Name, KubeResourceType.KAFKA_MIRROR_MAKER_2S, false);
//                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, true);
//                    verifyContainsResources(context, kafkaUserNames, KubeResourceType.KAFKA_USERS, true);
//                    verifyContainsResource(context, secretName, KubeResourceType.SECRETS, secretNotOptional);
//                    async.flag();
//                }))
//                .compose(v -> esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), minimalInstance))
//                .onComplete(context.succeeding(v -> {
//                    verifyContainsResource(context, kafkaMirrorMaker2Name, KubeResourceType.KAFKA_MIRROR_MAKER_2S, false);
//                    verifyContainsResource(context, networkPolicyName, KubeResourceType.NETWORK_POLICYS, false);
//                    verifyContainsResources(context, kafkaUserNames, KubeResourceType.KAFKA_USERS, false);
//                    verifyContainsResource(context, secretName, KubeResourceType.SECRETS, secretNotOptional);
//                    async.flag();
//                }));
//    }
//
//    private void verifyReplicasInDeployments(VertxTestContext context, Map<String, Integer> expectedResourcesWithReplicas) {
//        Set<HasMetadata> capturedDeployments = getActualResources(expectedResourcesWithReplicas.keySet(), KubeResourceType.DEPLOYMENTS);
//        Set<String> capturedDeploymentNames = capturedDeployments.stream().map(deploy -> deploy.getMetadata().getName()).collect(Collectors.toSet());
//        for (String deploymentName : capturedDeploymentNames) {
//            Integer actualReplicas = getActualReplicas(deploymentName, expectedResourcesWithReplicas.get(deploymentName));
//            LOGGER.debug("Deployment name {} set {} replicas", deploymentName, actualReplicas);
//            context.verify(() -> assertThat("For deployment " + deploymentName, mockClient.apps().deployments().inNamespace(NAMESPACE).withName(deploymentName).get().getSpec().getReplicas(),
//                    is(expectedResourcesWithReplicas.get(deploymentName))));
//        }
//    }
//
//    private void verifyHasOnlyResources(VertxTestContext context, Set<String> expectedResources, KubeResourceType type) {
//        Set<HasMetadata> actualResources =  getActualResources(expectedResources, type);
//        Set<String> actualResourceNames = actualResources.stream().map(res -> res.getMetadata().getName()).collect(Collectors.toSet());
//        context.verify(() -> assertThat(actualResourceNames, is(expectedResources)));
//    }
//
//    private void verifyContainsResources(VertxTestContext context, Set<String> resources, KubeResourceType type, boolean shouldExist) {
//        Set<HasMetadata> actualResources =  getResources(NAMESPACE, type);
//        Set<String> actualResourceNames = actualResources.stream().map(res -> res.getMetadata().getName()).collect(Collectors.toSet());
//        if (shouldExist) {
//            context.verify(() -> assertTrue(actualResourceNames.containsAll(resources), "for type: " + type + " expected: " + actualResourceNames.toString() + " to contain: " + resources.toString()));
//        } else {
//            context.verify(() -> assertFalse(actualResourceNames.containsAll(resources), "for type: " + type + " expected: " + actualResourceNames.toString() + " to not contain: " + resources.toString()));
//        }
//    }
//
//    private void verifyContainsResource(VertxTestContext context, String resource, KubeResourceType type, boolean shouldExist) {
//        Set<HasMetadata> actualResources =  getResources(NAMESPACE, type);
//        Set<String> actualResourceNames = actualResources.stream().map(res -> res.getMetadata().getName()).collect(Collectors.toSet());
//        if (shouldExist) {
//            context.verify(() -> assertTrue(actualResourceNames.contains(resource), "for type: " + type + " expected: " + actualResourceNames.toString() + " to contain: " + resource));
//        } else {
//            context.verify(() -> assertFalse(actualResourceNames.contains(resource), "for type: " + type + " expected: " + actualResourceNames.toString() + " to not contain: " + resource));
//        }
//    }
//
//    private Set<HasMetadata> getActualResources(Set<String> expectedResources, KubeResourceType type) {
//        int retryCount = 0;
//        int maxRetry = 3;
//        Set<HasMetadata> actualResources = new HashSet<>();
//        while (retryCount < maxRetry) {
//            actualResources = getResources(NAMESPACE, type);
//            LOGGER.debug("Actual resource count " + actualResources.size() + " for type " + type);
//            LOGGER.debug("Expected resource count " + expectedResources.size() + " for type " + type);
//            if (actualResources.size() == expectedResources.size()) {
//                break;
//            } else {
//                retryCount++;
//                try {
//                    LOGGER.debug("Waiting in retry loop " + retryCount);
//                    Thread.sleep(2000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//        return actualResources;
//    }
//
//    private Integer getActualReplicas(String deploymentName, Integer expectedReplica) {
//        int retryCount = 0;
//        int maxRetry = 5;
//        Integer actualReplica = 0;
//        while (retryCount < maxRetry) {
//            actualReplica = mockClient.apps().deployments().inNamespace(NAMESPACE).withName(deploymentName).get().getSpec().getReplicas();
//            LOGGER.debug("Actual replica for " + deploymentName + " is " + actualReplica);
//            LOGGER.debug("Expected replica for " + deploymentName + " is " + expectedReplica);
//            if (expectedReplica == actualReplica) {
//                break;
//            } else {
//                retryCount++;
//                try {
//                    LOGGER.debug("Waiting in retry loop " + retryCount);
//                    Thread.sleep(3000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//        return actualReplica;
//    }
//
//    private Map<String, Integer> getExpectedResourcesWithReplicas(String clusterName) {
//        Map<String, Integer> expectedDeployments = new HashMap<>();
//       // expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
//      //  expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + RestProducerModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
//        expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + AdminApiModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
//       // expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + AdminUIModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
//      //  expectedDeployments.put(clusterName + "-" + APP_NAME + "-" + CollectorModel.COMPONENT_NAME, EXPECTED_DEFAULT_REPLICAS);
//        return expectedDeployments;
//    }
//
//    private Set<String> getExpectedSecretNames(String clusterName) {
//        Set<String> expectedSecrets = new HashSet<>();
//        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + ReplicatorDestinationUsersModel.CONNECT_KAFKA_USER_NAME);
//        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + ReplicatorDestinationUsersModel.TARGET_CONNECTOR_KAFKA_USER_NAME);
////TODO add source one back in  and replicator secret too
//        expectedSecrets.add(clusterName + "-cluster-ca");
//        expectedSecrets.add(clusterName + "-cluster-ca-cert");
//
//        return expectedSecrets;
//    }
//
//    private Set<String> getExpectedKafkaUsers(String clusterName) {
//        Set<String> expectedKafkaUsers = new HashSet<>();
//        expectedKafkaUsers.add(clusterName + "-" + APP_NAME + "-" + ReplicatorDestinationUsersModel.CONNECT_KAFKA_USER_NAME);
//        expectedKafkaUsers.add(clusterName + "-" + APP_NAME + "-" + ReplicatorSourceUsersModel.SOURCE_CONNECTOR_KAFKA_USER_NAME);
//        expectedKafkaUsers.add(clusterName + "-" + APP_NAME + "-" + ReplicatorDestinationUsersModel.TARGET_CONNECTOR_KAFKA_USER_NAME);
//        expectedKafkaUsers.add(clusterName + "-" + APP_NAME + "-" + InternalKafkaUserModel.COMPONENT_NAME);
//
//        return expectedKafkaUsers;
//    }
//
//    private Set<HasMetadata> getResources(String namespace, KubeResourceType type) {
//        Set<HasMetadata> result = new HashSet<>();
//        switch (type) {
//            case DEPLOYMENTS:
//                result = new HashSet<>(mockClient.apps().deployments().inNamespace(namespace).list().getItems());
//                break;
//            case SECRETS:
//                result = new HashSet<>(mockClient.secrets().inNamespace(namespace).list().getItems());
//                break;
//            case NETWORK_POLICYS:
//                result = new HashSet<>(mockClient.network().networkPolicies().inNamespace(namespace).list().getItems());
//                break;
//            case KAFKA_USERS:
//                result = new HashSet<>(mockClient.customResources(io.strimzi.api.kafka.Crds.kafkaUser(), KafkaUser.class, KafkaUserList.class, DoneableKafkaUser.class).inNamespace(namespace).list().getItems());
//                break;
//            case KAFKA_MIRROR_MAKER_2S:
//                result = new HashSet<>(mockClient.customResources(io.strimzi.api.kafka.Crds.kafkaMirrorMaker2(), KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class, DoneableKafkaMirrorMaker2.class).inNamespace(namespace).list().getItems());
//                break;
//            default:
//                System.out.println("Unexpected type " + type);
//        }
//        return result;
//    }
//
//    private EventStreams createDefaultEventStreams(String namespace, String clusterName) {
//        KafkaSpecBuilder kafka = new KafkaSpecBuilder()
//                .editOrNewKafka()
//                .withReplicas(3)
//                .withNewListeners()
//                .withNewTls()
//                .withNewKafkaListenerAuthenticationTlsAuth()
//                .endKafkaListenerAuthenticationTlsAuth()
//                .endTls()
//                .withNewKafkaListenerExternalRoute()
//                .withNewKafkaListenerAuthenticationTlsAuth()
//                .endKafkaListenerAuthenticationTlsAuth()
//                .endKafkaListenerExternalRoute()
//                .endListeners()
//                .endKafka()
//                .editOrNewZookeeper()
//                .withReplicas(3)
//                .endZookeeper();
//
//        return createEventStreamsWithStrimziOverrides(namespace, clusterName, kafka.build());
//    }
//
//    private EventStreamsReplicator createDefaultEventStreamsReplicator(String namespace, String clusterName){
//        ReplicatorSpec replicatorSpec = new ReplicatorSpecBuilder().withReplicas(1).build();
//
//        return createEventStreamsReplicator(namespace, clusterName, replicatorSpec);
//
//    }
//
//    private EventStreamsReplicator createEventStreamsReplicator(String namespace, String clusterName, ReplicatorSpec replicatorSpec){
//        return new EventStreamsReplicatorBuilder()
//                .withMetadata(new ObjectMetaBuilder().withName(clusterName).withNamespace(namespace).build())
//                .withNewSpecLike(replicatorSpec)
//                .endSpec()
//                .build();
//    }
//
//
//    private EventStreams createEventStreamsWithStrimziOverrides(String namespace, String clusterName, KafkaSpec kafka) {
//        return new EventStreamsBuilder()
//                .withMetadata(new ObjectMetaBuilder().withName(clusterName).withNamespace(namespace).build())
//                .withNewSpec()
//                .withLicenseAccept(true)
//                .withNewAdminApi()
//                .withReplicas(1)
//                .endAdminApi()
//                .withNewRestProducer()
//                .withReplicas(0)
//                .endRestProducer()
//                .withNewSchemaRegistry()
//                .withReplicas(0)
//                .endSchemaRegistry()
//                .withNewAdminUI()
//                .withReplicas(0)
//                .endAdminUI()
//                .withNewCollector()
//                .withReplicas(0)
//                .endCollector()
//                .withStrimziOverrides(kafka)
//                .withVersion(DEFAULT_VERSION)
//                .endSpec()
//                .build();
//    }

}