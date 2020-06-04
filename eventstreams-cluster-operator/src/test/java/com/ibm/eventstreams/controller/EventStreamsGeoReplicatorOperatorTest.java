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

import com.ibm.eventstreams.api.ProductUse;
import com.ibm.eventstreams.api.model.GeoReplicatorDestinationUsersModel;
import com.ibm.eventstreams.api.model.InternalKafkaUserModel;
import com.ibm.eventstreams.api.model.utils.MockEventStreamsKube;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicator;
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicatorBuilder;
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicatorSpec;
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicatorSpecBuilder;
import com.ibm.eventstreams.api.status.EventStreamsGeoReplicatorStatusBuilder;
import com.ibm.eventstreams.api.status.EventStreamsVersions;
import com.ibm.eventstreams.controller.models.PhaseState;
import com.ibm.eventstreams.controller.utils.ConditionUtils;
import com.ibm.eventstreams.controller.utils.MetricsUtils;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.DoneableSecret;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.KafkaMirrorMaker2List;
import io.strimzi.api.kafka.KafkaUserList;
import io.strimzi.api.kafka.model.DoneableKafkaMirrorMaker2;
import io.strimzi.api.kafka.model.DoneableKafkaUser;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserBuilder;
import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.api.kafka.model.status.KafkaUserStatusBuilder;
import io.strimzi.operator.KubernetesVersion;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.strimzi.operator.common.MetricsProvider;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.operator.resource.ReconcileResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.ibm.eventstreams.api.model.AbstractModel.APP_NAME;
import static com.ibm.eventstreams.api.model.InternalKafkaUserModel.getInternalKafkaUserName;
import static com.ibm.eventstreams.controller.EventStreamsGeoReplicatorOperator.DEPLOYMENT_FAILED_REASON;
import static com.ibm.eventstreams.controller.EventStreamsGeoReplicatorOperator.EVENT_STREAMS_INSTANCE_NOT_FOUND_REASON;
import static com.ibm.eventstreams.rest.replicator.ReplicatorKafkaListenerValidation.INVALID_EXTERNAL_KAFKA_LISTENER_MESSAGE;
import static com.ibm.eventstreams.rest.replicator.ReplicatorKafkaListenerValidation.INVALID_EXTERNAL_KAFKA_LISTENER_REASON;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@SuppressWarnings({"checkstyle:ClassFanOutComplexity", "checkstyle:ClassDataAbstractionCoupling"})
@ExtendWith(VertxExtension.class)
public class EventStreamsGeoReplicatorOperatorTest {

    private static final Logger LOGGER = LogManager.getLogger(EventStreamsGeoReplicatorOperatorTest.class);

    private static final String NAMESPACE = "test-namespace";
    private static final String CLUSTER_NAME = "my-es";
    private static final String DEFAULT_VERSION = "2020.1.1";

    private static Vertx vertx;
    private KubernetesClient mockClient;
    private EventStreamsGeoReplicatorResourceOperator esReplicatorResourceOperator;
    private EventStreamsResourceOperator esResourceOperator;
    private KafkaUserOperator kafkaUserOperator;
    private MetricsProvider metricsProvider;

    private PlatformFeaturesAvailability pfa;


    public enum KubeResourceType {
        DEPLOYMENTS,
        SECRETS,
        NETWORK_POLICYS,
        KAFKA_USERS,
        KAFKA_MIRROR_MAKER_2S
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
        initialSecrets.addAll(ModelUtils.generateClusterCa(NAMESPACE, CLUSTER_NAME, APP_NAME, ModelUtils.Certificates.CLUSTER_CA, ModelUtils.Keys.CLUSTER_CA_KEY));
        initialSecrets.addAll(ModelUtils.generateGeoReplicatorConnectSecrets(NAMESPACE, CLUSTER_NAME, APP_NAME, ModelUtils.Certificates.CLUSTER_CA, ModelUtils.Keys.CLUSTER_CA_KEY));

        mockClient = new MockEventStreamsKube()
                .withInitialSecrets(initialSecrets)
                .build();
        when(mockClient.getNamespace()).thenReturn(NAMESPACE);

        KafkaMirrorMaker2 mockKafkaMM2 = new KafkaMirrorMaker2();
        mockKafkaMM2.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build());
        Optional<KafkaMirrorMaker2> mockMM2Instance = Optional.of(mockKafkaMM2);

        esReplicatorResourceOperator = mock(EventStreamsGeoReplicatorResourceOperator.class);
        when(esReplicatorResourceOperator.createOrUpdate(any(EventStreamsGeoReplicator.class))).thenReturn(Future.succeededFuture());
        when(esReplicatorResourceOperator.getReplicatorMirrorMaker2Instance(anyString(), anyString())).thenReturn(mockMM2Instance);

        metricsProvider = MetricsUtils.createMockMetricsProvider();

        esResourceOperator = mock(EventStreamsResourceOperator.class);
        when(esResourceOperator.kafkaCRHasStoppedDeploying(anyString(), anyString(), anyLong(), anyLong())).thenReturn(Future.succeededFuture());
        when(esResourceOperator.createOrUpdate(any(EventStreams.class))).thenReturn(Future.succeededFuture());

        pfa = mock(PlatformFeaturesAvailability.class);
        when(pfa.hasRoutes()).thenReturn(true);

        // mock ICP Config Map is present
        Map<String, String> configMapData = new HashMap<>();
        configMapData.put("cluster_address", "0.0.0.0");
        ConfigMap testICPConfigMap = new ConfigMap();
        testICPConfigMap.setData(configMapData);
        Resource<ConfigMap, DoneableConfigMap> mockResource = mock(Resource.class);
        when(mockResource.get()).thenReturn(testICPConfigMap);

        // mock ICP cluster ca cert
        Map<String, String> secretData = new HashMap<>();
        secretData.put("ca.crt", "QnJOY0twdXdjaUxiCg==");
        Secret ibmCloudClusterCaCert = new Secret();
        ibmCloudClusterCaCert.setData(secretData);
        Resource<Secret, DoneableSecret> mockSecret = mock(Resource.class);
        when(mockSecret.get()).thenReturn(ibmCloudClusterCaCert);

        KafkaUser mockKafkaUser = new KafkaUser();
        mockKafkaUser.setMetadata(new ObjectMetaBuilder().withName(getInternalKafkaUserName(CLUSTER_NAME)).withNamespace(NAMESPACE).build());
        mockKafkaUser.setStatus(new KafkaUserStatusBuilder()
            .withConditions(ConditionUtils.getReadyCondition())
            .withNewUsername(InternalKafkaUserModel.getInternalKafkaUserName(CLUSTER_NAME))
            .build());
        Optional<KafkaUser> mockKafkaUserInstance = Optional.of(mockKafkaUser);

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

    }

    @AfterEach
    public void closeMockClient() {
        mockClient.close();
    }

    @Test
    public void testFailsWhenInvalidAuthenticationInEventStreamsSpec(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        EventStreamsGeoReplicator geoReplicatorCluster = createDefaultEventStreamsGeoReplicator(NAMESPACE, CLUSTER_NAME);

        mockGetEventStreamsResource(CLUSTER_NAME, createDefaultEventStreams(NAMESPACE, CLUSTER_NAME));
        mockGetGeoReplicatorResource(CLUSTER_NAME, geoReplicatorCluster);
        mockUpdateGeoReplicatorStatus(null);
        EventStreamsGeoReplicatorOperator geoReplicatorOperator = new EventStreamsGeoReplicatorOperator(
            vertx,
            mockClient,
            EventStreamsGeoReplicator.RESOURCE_KIND,
            pfa,
            esReplicatorResourceOperator,
            esResourceOperator,
            kafkaUserOperator,
            metricsProvider
        );

        Checkpoint async = context.checkpoint();
        geoReplicatorOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), geoReplicatorCluster)
            .onComplete(context.failing(v -> context.verify(() -> {
                ArgumentCaptor<EventStreamsGeoReplicator> argument = ArgumentCaptor.forClass(EventStreamsGeoReplicator.class);
                verify(esReplicatorResourceOperator, times(2)).updateEventStreamsGeoReplicatorStatus(argument.capture());

                EventStreamsGeoReplicator val = argument.getValue();
                assertThat(val.getStatus().getPhase(), is(PhaseState.FAILED));
                List<Condition> conditions = val.getStatus().getConditions();
                assertThat(conditions.stream().filter(condition -> condition.getReason().equals(INVALID_EXTERNAL_KAFKA_LISTENER_REASON)).findFirst().get().getMessage(),
                    is(String.format(INVALID_EXTERNAL_KAFKA_LISTENER_MESSAGE, "none")));
                async.flag();
            })));
    }

    @Test
    public void testConditionsAreNotChangedWhenInstanceDoesNotChange(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        EventStreamsGeoReplicator geoReplicatorCluster = createDefaultEventStreamsGeoReplicator(NAMESPACE, CLUSTER_NAME);

        mockGetEventStreamsResource(CLUSTER_NAME, createDefaultEventStreams(NAMESPACE, CLUSTER_NAME));
        mockGetGeoReplicatorResource(CLUSTER_NAME, geoReplicatorCluster);
        mockUpdateGeoReplicatorStatus(null);
        EventStreamsGeoReplicatorOperator geoReplicatorOperator = new EventStreamsGeoReplicatorOperator(
            vertx,
            mockClient,
            EventStreamsGeoReplicator.RESOURCE_KIND,
            pfa,
            esReplicatorResourceOperator,
            esResourceOperator,
            kafkaUserOperator,
            metricsProvider
        );

        AtomicReference<Condition> initialCondition = new AtomicReference<>();
        AtomicReference<EventStreamsGeoReplicator> geoReplicatorSpec = new AtomicReference<>();
        Checkpoint async = context.checkpoint(2);
        Promise geoReplicatorFirstCreateOrUpdate = Promise.promise();

        geoReplicatorOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), geoReplicatorCluster)
            .onComplete(context.failing(v -> context.verify(() -> {
                ArgumentCaptor<EventStreamsGeoReplicator> argument = ArgumentCaptor.forClass(EventStreamsGeoReplicator.class);
                verify(esReplicatorResourceOperator, times(2)).updateEventStreamsGeoReplicatorStatus(argument.capture());

                EventStreamsGeoReplicator val = argument.getValue();
                assertThat(val.getStatus().getPhase(), is(PhaseState.FAILED));
                List<Condition> conditions = val.getStatus().getConditions();
                initialCondition.set(conditions.stream().filter(condition -> condition.getReason().equals(INVALID_EXTERNAL_KAFKA_LISTENER_REASON)).findFirst().get());
                assertThat(initialCondition.get().getMessage(),
                    is(String.format(INVALID_EXTERNAL_KAFKA_LISTENER_MESSAGE, "none")));
                geoReplicatorFirstCreateOrUpdate.complete();
                ArgumentCaptor<EventStreamsGeoReplicator> geoReplicatorInstanceWithStatus = ArgumentCaptor.forClass(EventStreamsGeoReplicator.class);
                verify(esReplicatorResourceOperator, times(2)).updateEventStreamsGeoReplicatorStatus(geoReplicatorInstanceWithStatus.capture());

                geoReplicatorSpec.set(geoReplicatorInstanceWithStatus.getValue());
                async.flag();
            })));

        geoReplicatorFirstCreateOrUpdate.future().compose(v -> geoReplicatorOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), geoReplicatorSpec.get()))
            .onComplete(context.failing(v -> context.verify(() -> {
                ArgumentCaptor<EventStreamsGeoReplicator> argument = ArgumentCaptor.forClass(EventStreamsGeoReplicator.class);
                verify(esReplicatorResourceOperator, times(4)).updateEventStreamsGeoReplicatorStatus(argument.capture());

                EventStreamsGeoReplicator val = argument.getValue();
                assertThat(val.getStatus().getPhase(), is(PhaseState.FAILED));
                List<Condition> conditions = val.getStatus().getConditions();
                Condition newCondition = conditions.stream().filter(condition -> condition.getReason().equals(INVALID_EXTERNAL_KAFKA_LISTENER_REASON)).findFirst().get();
                assertThat(initialCondition.get().getReason(), is(newCondition.getReason()));
                assertThat(initialCondition.get().getMessage(), is(newCondition.getMessage()));
                assertThat(initialCondition.get().getType(), is(newCondition.getType()));
                assertThat(initialCondition.get().getStatus(), is(newCondition.getStatus()));
                assertThat(initialCondition.get().getLastTransitionTime(), is(newCondition.getLastTransitionTime()));
                async.flag();
            })));
    }

    @Test
    public void testConditionsAreNotAddedToReadyInstances(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        EventStreamsGeoReplicator geoReplicatorCluster = createDefaultEventStreamsGeoReplicator(NAMESPACE, CLUSTER_NAME);
        geoReplicatorCluster.setStatus(new EventStreamsGeoReplicatorStatusBuilder()
                .withPhase(PhaseState.READY)
                .withConditions(Collections.EMPTY_LIST)
                .build());

        mockGetEventStreamsResource(CLUSTER_NAME, createDefaultEventStreamsWithAuthentication(NAMESPACE, CLUSTER_NAME));
        mockGetGeoReplicatorResource(CLUSTER_NAME, geoReplicatorCluster);
        mockUpdateGeoReplicatorStatus(null);
        EventStreamsGeoReplicatorOperator geoReplicatorOperator = new EventStreamsGeoReplicatorOperator(
                vertx,
                mockClient,
                EventStreamsGeoReplicator.RESOURCE_KIND,
                pfa,
                esReplicatorResourceOperator,
                esResourceOperator,
                kafkaUserOperator,
                metricsProvider
        );

        Checkpoint async = context.checkpoint();

        geoReplicatorOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreamsGeoReplicator.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), geoReplicatorCluster)
                .onComplete(context.succeeding(v -> {
                    ArgumentCaptor<EventStreamsGeoReplicator> argument = ArgumentCaptor.forClass(EventStreamsGeoReplicator.class);
                    verify(esReplicatorResourceOperator, times(2)).updateEventStreamsGeoReplicatorStatus(argument.capture());

                    List<EventStreamsGeoReplicator> allUpdates = argument.getAllValues();
                    for (EventStreamsGeoReplicator update : allUpdates) {
                        // none of the status updates should've added a
                        // "georep is being deployed" condition as the
                        // georep instance was already in a ready state
                        assertThat(update.getStatus().getConditions(), is(empty()));
                    }

                    async.flag();
                }));
    }

    @Test
    public void testCreateDefaultGeoReplicatorEventStreamsInstanceOpenShift(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        EventStreamsGeoReplicator geoReplicatorCluster = createDefaultEventStreamsGeoReplicator(NAMESPACE, CLUSTER_NAME);

        mockGetEventStreamsResource(CLUSTER_NAME, createDefaultEventStreamsWithAuthentication(NAMESPACE, CLUSTER_NAME));
        mockGetGeoReplicatorResource(CLUSTER_NAME, geoReplicatorCluster);
        mockUpdateGeoReplicatorStatus(null);
        EventStreamsGeoReplicatorOperator geoReplicatorOperator = new EventStreamsGeoReplicatorOperator(
            vertx,
            mockClient,
            EventStreamsGeoReplicator.RESOURCE_KIND,
            pfa,
            esReplicatorResourceOperator,
            esResourceOperator,
            kafkaUserOperator,
            metricsProvider
        );

        Set<String> expectedSecrets = getExpectedSecretNames(CLUSTER_NAME);
        Set<String> expectedKafkaUsers = getExpectedKafkaUsers(CLUSTER_NAME);

        Checkpoint async = context.checkpoint();
        geoReplicatorOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), geoReplicatorCluster)
                .onComplete(context.succeeding(v -> context.verify(() -> {
                    verifyHasOnlyResources(context, expectedSecrets, KubeResourceType.SECRETS);
                    Set<HasMetadata> mm2s = getResources(NAMESPACE, KubeResourceType.KAFKA_MIRROR_MAKER_2S);
                    mm2s.forEach(mm2 -> {
                        for (Map.Entry<String, String> label: mm2.getMetadata().getLabels().entrySet()) {
                            assertThat("MirrorMaker2 Custom Resources should not contain reserved domain labels", label.getKey(), not(containsString(io.strimzi.operator.common.model.Labels.STRIMZI_DOMAIN)));
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

                    ArgumentCaptor<EventStreamsGeoReplicator> argument = ArgumentCaptor.forClass(EventStreamsGeoReplicator.class);
                    verify(esReplicatorResourceOperator, times(2)).updateEventStreamsGeoReplicatorStatus(argument.capture());
                    EventStreamsGeoReplicator val = argument.getValue();
                    assertThat(val.getStatus().getPhase(), is(PhaseState.READY));
                    List<Condition> conditions = val.getStatus().getConditions();
                    assertThat(conditions.stream().filter(condition -> !EventStreamsGeoReplicatorOperator.GEOREPLICATOR_BEING_DEPLOYED_REASON.matches(condition.getReason())).collect(Collectors.toList()),
                        hasSize(0));
                    async.flag();
                })));
    }

    @Test
    public void testGeoReplicatorFailsWhenNoEventStreamsInstance(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        EventStreamsGeoReplicator geoReplicatorCluster = createDefaultEventStreamsGeoReplicator(NAMESPACE, CLUSTER_NAME);

        mockGetEventStreamsResource(CLUSTER_NAME, null);
        mockGetGeoReplicatorResource(CLUSTER_NAME, geoReplicatorCluster);
        mockUpdateGeoReplicatorStatus(null);
        EventStreamsGeoReplicatorOperator geoReplicatorOperator = new EventStreamsGeoReplicatorOperator(
            vertx,
            mockClient,
            EventStreamsGeoReplicator.RESOURCE_KIND,
            pfa,
            esReplicatorResourceOperator,
            esResourceOperator,
            kafkaUserOperator,
            metricsProvider
        );

        Checkpoint async = context.checkpoint();
        geoReplicatorOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), geoReplicatorCluster)
            .onComplete(context.failing(throwable -> context.verify(() -> {
                assertThat(throwable.getMessage(), is("Could not get ES custom resource"));
                ArgumentCaptor<EventStreamsGeoReplicator> argument = ArgumentCaptor.forClass(EventStreamsGeoReplicator.class);
                verify(esReplicatorResourceOperator, times(1)).updateEventStreamsGeoReplicatorStatus(argument.capture());

                EventStreamsGeoReplicator val = argument.getValue();
                assertThat(val.getStatus().getPhase(), is(PhaseState.FAILED));
                List<Condition> conditions = val.getStatus().getConditions();
                assertThat(conditions.stream().filter(condition -> DEPLOYMENT_FAILED_REASON.equals(condition.getReason())).findFirst().get().getMessage(),
                    is("An unexpected exception was encountered: Could not get ES custom resource. More detail can be found in the Event Streams geo-replication operator log."));

                async.flag();
            })));
    }

    @Test
    public void testGeoReplicatorFailsWhenNullEventStreamsInstanceFound(VertxTestContext context) {
        PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);
        EventStreamsGeoReplicator geoReplicatorCluster = createDefaultEventStreamsGeoReplicator(NAMESPACE, CLUSTER_NAME);

        mockGetNullEventStreamsResource(CLUSTER_NAME);
        mockGetGeoReplicatorResource(CLUSTER_NAME, geoReplicatorCluster);
        mockUpdateGeoReplicatorStatus(null);
        EventStreamsGeoReplicatorOperator geoReplicatorOperator = new EventStreamsGeoReplicatorOperator(
            vertx,
            mockClient,
            EventStreamsGeoReplicator.RESOURCE_KIND,
            pfa,
            esReplicatorResourceOperator,
            esResourceOperator,
            kafkaUserOperator,
            metricsProvider
        );

        Checkpoint async = context.checkpoint();
        geoReplicatorOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, CLUSTER_NAME), geoReplicatorCluster)
            .onComplete(context.failing(throwable -> context.verify(() -> {
                String message = String.format("Could not find Event Streams instance '%s' in namespace '%s'. Geo-replication requires a running Event Streams instance. Create an Event Streams instance before deploying the Event Streams geo-replicator", CLUSTER_NAME, NAMESPACE);
                assertThat(throwable.getMessage(), is(message));
                ArgumentCaptor<EventStreamsGeoReplicator> argument = ArgumentCaptor.forClass(EventStreamsGeoReplicator.class);
                verify(esReplicatorResourceOperator, times(1)).updateEventStreamsGeoReplicatorStatus(argument.capture());

                EventStreamsGeoReplicator val = argument.getValue();
                assertThat(val.getStatus().getPhase(), is(PhaseState.FAILED));
                List<Condition> conditions = val.getStatus().getConditions();

                assertThat(conditions.stream().filter(condition -> EVENT_STREAMS_INSTANCE_NOT_FOUND_REASON.equals(condition.getReason())).findFirst().get().getMessage(),
                    is(message));

                assertThat(conditions.stream().filter(condition -> DEPLOYMENT_FAILED_REASON.equals(condition.getReason())).findFirst().get().getMessage(),
                    is(String.format("An unexpected exception was encountered: %s. More detail can be found in the Event Streams geo-replication operator log.", message)));

                async.flag();
            })));
    }

    private void verifyHasOnlyResources(VertxTestContext context, Set<String> expectedResources, KubeResourceType type) {
        Set<HasMetadata> actualResources =  getActualResources(expectedResources, type);
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

    private Set<HasMetadata> getResources(String namespace, KubeResourceType type) {
        Set<HasMetadata> result = new HashSet<>();
        switch (type) {
            case DEPLOYMENTS:
                result = new HashSet<>(mockClient.apps().deployments().inNamespace(namespace).list().getItems());
                break;
            case SECRETS:
                result = new HashSet<>(mockClient.secrets().inNamespace(namespace).list().getItems());
                break;
            case NETWORK_POLICYS:
                result = new HashSet<>(mockClient.network().networkPolicies().inNamespace(namespace).list().getItems());
                break;
            case KAFKA_USERS:
                result = new HashSet<>(mockClient.customResources(io.strimzi.api.kafka.Crds.kafkaUser(), KafkaUser.class, KafkaUserList.class, DoneableKafkaUser.class).inNamespace(namespace).list().getItems());
                break;
            case KAFKA_MIRROR_MAKER_2S:
                result = new HashSet<>(mockClient.customResources(io.strimzi.api.kafka.Crds.kafkaMirrorMaker2(), KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class, DoneableKafkaMirrorMaker2.class).inNamespace(namespace).list().getItems());
                break;
            default:
                System.out.println("Unexpected type " + type);
        }
        return result;
    }

    private Set<String> getExpectedSecretNames(String clusterName) {
        Set<String> expectedSecrets = new HashSet<>();
        expectedSecrets.add(clusterName + "-" + APP_NAME + "-" + GeoReplicatorDestinationUsersModel.CONNECT_KAFKA_USER_NAME);
        expectedSecrets.add(clusterName + "-cluster-ca");
        expectedSecrets.add(clusterName + "-cluster-ca-cert");

        return expectedSecrets;
    }

    private Set<String> getExpectedKafkaUsers(String clusterName) {
        Set<String> expectedKafkaUsers = new HashSet<>();
        expectedKafkaUsers.add(clusterName + "-" + APP_NAME + "-" + GeoReplicatorDestinationUsersModel.CONNECT_KAFKA_USER_NAME);
        expectedKafkaUsers.add(clusterName + "-" + APP_NAME + "-" + GeoReplicatorDestinationUsersModel.CONNECT_EXTERNAL_KAFKA_USER_NAME);
        expectedKafkaUsers.add(clusterName + "-" + APP_NAME + "-" + GeoReplicatorDestinationUsersModel.TARGET_CONNECTOR_KAFKA_USER_NAME);

        return expectedKafkaUsers;
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

    private EventStreams createDefaultEventStreams(String namespace, String clusterName) {
        KafkaSpecBuilder kafka = new KafkaSpecBuilder()
            .editOrNewKafka()
            .withReplicas(3)
            .withNewListeners()
                .withNewTls()
                .endTls()
                .withNewKafkaListenerExternalRoute()
                .endKafkaListenerExternalRoute()
            .endListeners()
            .endKafka()
            .editOrNewZookeeper()
            .withReplicas(3)
            .endZookeeper();

        return createEventStreamsWithStrimziOverrides(namespace, clusterName, kafka.build());
    }

    private EventStreams createDefaultEventStreamsWithAuthentication(String namespace, String clusterName) {
        KafkaSpecBuilder kafka = new KafkaSpecBuilder()
                .editOrNewKafka()
                .withReplicas(3)
                .withNewListeners()
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

    private EventStreams createEventStreamsWithStrimziOverrides(String namespace, String clusterName, KafkaSpec kafka) {
        return new EventStreamsBuilder()
            .withMetadata(new ObjectMetaBuilder().withName(clusterName).withNamespace(namespace).build())
            .withNewSpec()
            .withNewLicense()
                .withAccept(true)
                .withUse(ProductUse.CP4I_NON_PRODUCTION)
            .endLicense()
            .withNewAdminApi()
            .withReplicas(1)
            .endAdminApi()
            .withNewRestProducer()
            .withReplicas(0)
            .endRestProducer()
            .withNewSchemaRegistry()
            .withReplicas(0)
            .endSchemaRegistry()
            .withNewAdminUI()
            .withReplicas(0)
            .endAdminUI()
            .withNewCollector()
            .withReplicas(0)
            .endCollector()
            .withStrimziOverrides(kafka)
            .withVersion(DEFAULT_VERSION)
            .endSpec()
            .build();
    }

    private EventStreamsGeoReplicator createDefaultEventStreamsGeoReplicator(String namespace, String clusterName) {
        EventStreamsGeoReplicatorSpec replicatorSpec = new EventStreamsGeoReplicatorSpecBuilder()
                .withReplicas(1)
                .withNewVersion(EventStreamsVersions.OPERAND_VERSION)
            .build();

        return createEventStreamsGeoReplicator(namespace, clusterName, replicatorSpec);

    }

    private EventStreamsGeoReplicator createEventStreamsGeoReplicator(String namespace, String clusterName, EventStreamsGeoReplicatorSpec eventStreamsGeoReplicator) {
        return new EventStreamsGeoReplicatorBuilder()
            .withMetadata(new ObjectMetaBuilder()
                .withName(clusterName)
                .withNamespace(namespace)
                .withLabels(Collections.singletonMap(Labels.STRIMZI_CLUSTER_LABEL, clusterName))
                .build())
                .withNewSpecLike(eventStreamsGeoReplicator)
                .endSpec()
                .build();
    }

    private void mockGetEventStreamsResource(String name, EventStreams eventStreams) {
        Future<EventStreams> eventStreamsFuture =  eventStreams != null ? Future.succeededFuture(eventStreams) : Future.failedFuture("Could not get ES custom resource");
        when(esResourceOperator.getAsync(NAMESPACE, name)).thenReturn(eventStreamsFuture);
    }

    private void mockGetNullEventStreamsResource(String name) {
        when(esResourceOperator.getAsync(NAMESPACE, name)).thenReturn(Future.succeededFuture(null));
    }

    private void mockGetGeoReplicatorResource(String name, EventStreamsGeoReplicator geoReplicator) {
        Future<EventStreamsGeoReplicator> eventStreamsFuture =  geoReplicator != null ? Future.succeededFuture(geoReplicator) : Future.failedFuture("Could not get ES geo-replicator custom resource");
        when(esReplicatorResourceOperator.getAsync(NAMESPACE, name)).thenReturn(eventStreamsFuture);
    }

    private void mockUpdateGeoReplicatorStatus(Throwable throwable) {
        when(esReplicatorResourceOperator.updateEventStreamsGeoReplicatorStatus(any())).thenReturn(throwable == null ? Future.succeededFuture() : Future.failedFuture(throwable));
    }

}