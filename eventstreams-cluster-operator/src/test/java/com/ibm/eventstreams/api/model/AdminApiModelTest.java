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
package com.ibm.eventstreams.api.model;

import com.ibm.eventstreams.Main;
import com.ibm.eventstreams.api.Labels;
import com.ibm.eventstreams.api.Listener;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.ExternalAccess;
import com.ibm.eventstreams.api.spec.ExternalAccessBuilder;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import com.ibm.eventstreams.api.spec.SecuritySpecBuilder;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeer;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.openshift.api.model.Route;
import io.strimzi.api.kafka.model.ExternalLogging;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.status.ListenerStatusBuilder;
import io.strimzi.api.kafka.model.template.PodTemplateBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterableOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminApiModelTest {

    private final Map<String, String> mockIcpClusterDataMap = new HashMap<>();
    private final String instanceName = "test-instance";
    private final String componentPrefix = instanceName + "-" + AbstractModel.APP_NAME + "-" + AdminApiModel.COMPONENT_NAME;
    private final int defaultReplicas = 1;
    private final String frontendRestImage = "hyc-qp-stable-docker-local.artifactory.swg-devops.com/eventstreams-rest-icp-linux-amd64:2020-01-17-16.07.02-2c5fca9-exp";
    @Mock
    private EventStreamsOperatorConfig.ImageLookup imageConfig;
    private List<ListenerStatus> listeners = new ArrayList<>();

    private EventStreamsBuilder createDefaultEventStreams() {
        return ModelUtils.createDefaultEventStreams(instanceName)
                .editSpec()
                    .withNewAdminApi()
                        .withReplicas(defaultReplicas)
                    .endAdminApi()
                .endSpec();
    }

    private AdminApiModel createDefaultAdminApiModel() {
        EventStreams eventStreamsResource = createDefaultEventStreams().build();
        return new AdminApiModel(eventStreamsResource, imageConfig, listeners, mockIcpClusterDataMap);
    }

    private EventStreamsBuilder createDefaultEventStreamsWithExternalAccess(String type) {
        ExternalAccess externalAccess = new ExternalAccessBuilder().withNewType(type).build();

        return ModelUtils.createDefaultEventStreams(instanceName)
                .editSpec()
                .withNewAdminProxy()
                .withReplicas(defaultReplicas)
                .withExternalAccess(externalAccess)
                .endAdminProxy()
                .endSpec();
    }

    @Test
    public void testDefaultAdminApi() {
        AdminApiModel adminApiModel = createDefaultAdminApiModel();
        Deployment adminApiDeployment = adminApiModel.getDeployment();
        assertThat(adminApiDeployment.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(adminApiDeployment.getSpec().getReplicas(), is(defaultReplicas));

        Service adminApiService = adminApiModel.getService();
        assertThat(adminApiService.getMetadata().getName(), startsWith(componentPrefix));

        Service adminApiInternalService = adminApiModel.getInternalService();
        assertThat(adminApiInternalService.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(adminApiInternalService.getMetadata().getName(), endsWith(AbstractSecureEndpointModel.INTERNAL_SERVICE_POSTFIX));

        Service adminApiExternalService = adminApiModel.getExternalService();
        assertThat(adminApiExternalService.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(adminApiExternalService.getMetadata().getName(), endsWith(AbstractSecureEndpointModel.EXTERNAL_SERVICE_POSTFIX));

        Route adminAPIRoute = adminApiModel.getRoute();
        assertThat(adminAPIRoute.getMetadata().getName(), startsWith(componentPrefix));

        Map<String, Route> schemaRegistryRoutes = adminApiModel.getRoutes();
        schemaRegistryRoutes.forEach((key, route) -> {
            assertThat(route.getMetadata().getName(), startsWith(componentPrefix));
            assertThat(route.getMetadata().getName(), containsString(key));
        });
    }

    @Test
    public void testDefaultResourceRequirements() {
        AdminApiModel adminApiModel = createDefaultAdminApiModel();

        ResourceRequirements resourceRequirements = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getResources();
        assertThat(resourceRequirements.getRequests().get("cpu").getAmount(), is("500m"));
        assertThat(resourceRequirements.getRequests().get("memory").getAmount(), is("1Gi"));
        assertThat(resourceRequirements.getLimits().get("cpu").getAmount(), is("4000m"));
        assertThat(resourceRequirements.getLimits().get("memory").getAmount(), is("1Gi"));
    }

    @Test
    public void testCustomResourceRequirements() {
        ResourceRequirements customResourceRequirements = new ResourceRequirementsBuilder()
                .addToRequests("cpu", new Quantity("200m"))
                .addToLimits("memory", new Quantity("3Gi"))
                .build();
        EventStreams eventStreamsResource = createDefaultEventStreams()
                .editSpec()
                    .editAdminApi()
                        .withResources(customResourceRequirements)
                    .endAdminApi()
                .endSpec()
                .build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreamsResource, imageConfig, listeners, mockIcpClusterDataMap);

        ResourceRequirements resourceRequirements = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getResources();
        assertThat(resourceRequirements.getRequests().get("cpu").getAmount(), is("200m"));
        assertThat(resourceRequirements.getRequests().get("memory").getAmount(), is("1Gi"));
        assertThat(resourceRequirements.getLimits().get("cpu").getAmount(), is("4000m"));
        assertThat(resourceRequirements.getLimits().get("memory").getAmount(), is("3Gi"));
    }

    @Test
    public void testDefaultAdminApiNetworkPolicy() {
        AdminApiModel adminApiModel = createDefaultAdminApiModel();

        NetworkPolicy networkPolicy = adminApiModel.getNetworkPolicy();
        String expectedNetworkPolicyName = componentPrefix + "-network-policy";
        assertThat(networkPolicy.getMetadata().getName(), is(expectedNetworkPolicyName));
        assertThat(networkPolicy.getKind(), is("NetworkPolicy"));

        // + pod to pod as should be, but also + ingress for traffic to old rest
        assertThat(networkPolicy.getSpec().getIngress().size(), is(Listener.enabledListeners().size() + 2));
        List<Listener> listeners = Listener.enabledListeners();
        listeners.add(Listener.podToPodListener(false));
        List<Integer> listenerPorts = listeners.stream().map(Listener::getPort).collect(Collectors.toList());
        listenerPorts.add(AdminApiModel.getServicePort(false)); //
        networkPolicy.getSpec().getIngress().forEach(ingress -> {
            assertThat(ingress.getFrom(), is(emptyIterableOf(NetworkPolicyPeer.class)));
            assertThat(ingress.getPorts().size(), is(1));
            assertThat(listenerPorts, hasItem(ingress.getPorts().get(0).getPort().getIntVal()));
        });

        assertThat(networkPolicy.getSpec().getEgress().size(), is(7));

        checkNetworkPolicy(networkPolicy, 0, 1, 1, 1, EventStreamsKafkaModel.KAFKA_PORT, EventStreamsKafkaModel.KAFKA_COMPONENT_NAME);
        checkNetworkPolicy(networkPolicy, 1, 1, 1, 1, EventStreamsKafkaModel.KAFKA_RUNAS_PORT, EventStreamsKafkaModel.KAFKA_COMPONENT_NAME);
        checkNetworkPolicy(networkPolicy, 2, 1, 1, 1, Listener.podToPodListener(false).getPort(), SchemaRegistryModel.COMPONENT_NAME);
        checkNetworkPolicy(networkPolicy, 3, 1, 1, 1, EventStreamsKafkaModel.ZOOKEEPER_PORT, EventStreamsKafkaModel.ZOOKEEPER_COMPONENT_NAME);
        checkNetworkPolicy(networkPolicy, 4, 1, 1, 1, ReplicatorModel.REPLICATOR_PORT, ReplicatorModel.COMPONENT_NAME);

        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(5)
            .getPorts()
            .size(), is(2));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(5)
            .getPorts()
            .get(0)
            .getPort()
            .getIntVal(), is(8443));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(5)
            .getPorts()
            .get(1)
            .getPort()
            .getIntVal(), is(443));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(5)
            .getTo()
            .size(), is(0));

        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(6)
            .getPorts()
            .size(), is(1));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(6)
            .getPorts()
            .get(0)
            .getPort()
            .getIntVal(), is(53));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(6)
            .getPorts()
            .get(0)
            .getProtocol(), is("UDP"));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(6)
            .getTo()
            .size(), is(0));

        assertThat(networkPolicy
            .getSpec()
            .getPodSelector()
            .getMatchLabels(), hasEntry(is(Labels.COMPONENT_LABEL), is(AdminApiModel.COMPONENT_NAME)));

        Route adminApiRoute = adminApiModel.getRoute();
        assertThat(adminApiRoute.getMetadata().getName(), startsWith(componentPrefix));
    }

    private void checkNetworkPolicy(NetworkPolicy networkPolicy, int egressIndex, int expectedNumberOfPorts, int expectedGetTo, int expectedMatchLabels, int expectedPort, String expectedComponentName) {
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(egressIndex)
            .getPorts()
            .size(), is(expectedNumberOfPorts));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(egressIndex)
            .getPorts()
            .get(0)
            .getPort()
            .getIntVal(), is(expectedPort));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(egressIndex)
            .getTo()
            .size(), is(expectedGetTo));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(egressIndex)
            .getTo()
            .get(0)
            .getPodSelector()
            .getMatchLabels()
            .size(), is(expectedMatchLabels));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(egressIndex)
            .getTo()
            .get(0)
            .getPodSelector()
            .getMatchLabels()
            .get(Labels.COMPONENT_LABEL), is(expectedComponentName));
    }

    @Test
    public void testAdminApiIngressNetworkPolicyWithTLS() {
        EventStreams eventStreams = createDefaultEventStreams()
                .editSpec()
                .withSecurity(new SecuritySpecBuilder().withEncryption(SecuritySpec.Encryption.TLS).build())
                .endSpec()
                .build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, listeners, mockIcpClusterDataMap);

        NetworkPolicy networkPolicy = adminApiModel.getNetworkPolicy();
        String expectedNetworkPolicyName = componentPrefix + "-network-policy";
        assertThat(networkPolicy.getMetadata().getName(), is(expectedNetworkPolicyName));
        assertThat(networkPolicy.getKind(), is("NetworkPolicy"));

        // + pod to pod as should be, but also + ingress for traffic to old rest
        assertThat(networkPolicy.getSpec().getIngress().size(), is(Listener.enabledListeners().size() + 2));
        List<Listener> listeners = Listener.enabledListeners();
        listeners.add(Listener.podToPodListener(true));
        List<Integer> listenerPorts = listeners.stream().map(Listener::getPort).collect(Collectors.toList());
        listenerPorts.add(AdminApiModel.getServicePort(true)); //
        networkPolicy.getSpec().getIngress().forEach(ingress -> {
            assertThat(ingress.getFrom(), is(emptyIterableOf(NetworkPolicyPeer.class)));
            assertThat(ingress.getPorts().size(), is(1));
            assertThat(listenerPorts, hasItem(ingress.getPorts().get(0).getPort().getIntVal()));
        });
    }

    @Test
    public void testImageOverride() {
        String adminApiImage = "admin-api-image:latest";

        EventStreams instance = createDefaultEventStreams()
                .editSpec()
                    .editAdminApi()
                        .withImage(adminApiImage)
                    .endAdminApi()
                .endSpec()
                .build();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(AdminApiModel.FRONTEND_REST_CONTAINER_NAME, frontendRestImage);
        expectedImages.put(AdminApiModel.ADMIN_API_CONTAINER_NAME, adminApiImage);

        List<Container> containers = new AdminApiModel(instance, imageConfig, listeners, mockIcpClusterDataMap).getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testOperatorImageOverride() {
        String adminApiImage = "admin-api-image:latest";

        when(imageConfig.getAdminApiImage()).thenReturn(Optional.of(adminApiImage));

        AdminApiModel model = createDefaultAdminApiModel();
        List<Container> containers = model.getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();
        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(AdminApiModel.FRONTEND_REST_CONTAINER_NAME, frontendRestImage);
        expectedImages.put(AdminApiModel.ADMIN_API_CONTAINER_NAME, adminApiImage);

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testOperatorImageOverrideTakesPrecedenceOverComponentLevelOverride() {
        String adminApiImageFromEnv = "env-admin-api-image:latest";
        String adminApiImage = "admin-api-image:latest";

        when(imageConfig.getAdminApiImage()).thenReturn(Optional.of(adminApiImageFromEnv));

        EventStreams instance = createDefaultEventStreams()
                .editSpec()
                    .editAdminApi()
                        .withImage(adminApiImage)
                    .endAdminApi()
                .endSpec()
                .build();

        AdminApiModel adminApiModel = new AdminApiModel(instance, imageConfig, listeners, mockIcpClusterDataMap);
        assertThat(adminApiModel.getImage(), is(adminApiImage));
        assertTrue(adminApiModel.getCustomImage());

        List<Container> containers = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(AdminApiModel.FRONTEND_REST_CONTAINER_NAME, frontendRestImage);
        expectedImages.put(AdminApiModel.ADMIN_API_CONTAINER_NAME, adminApiImage);

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testPodServiceAccountContainsUserSuppliedPullSecret() {

        EventStreamsBuilder defaultEs = createDefaultEventStreams();
        LocalObjectReference imagePullSecretOverride = new LocalObjectReferenceBuilder()
            .withName("component-test-image-secret")
            .build();

        EventStreams eventStreams = defaultEs
            .editSpec()
                .editAdminApi()
                    .editOrNewTemplate()
                        .withPod(new PodTemplateBuilder()
                                        .withImagePullSecrets(imagePullSecretOverride)
                                        .build()
                        )
                    .endTemplate()
                .endAdminApi()
            .endSpec()
            .build();

        assertThat(new AdminApiModel(eventStreams, imageConfig, listeners, mockIcpClusterDataMap).getServiceAccount()
                        .getImagePullSecrets(), contains(imagePullSecretOverride));
    }

    @Test
    public void testPodServiceAccountContainsGlobalSuppliedPullSecret() {

        EventStreamsBuilder defaultEs = createDefaultEventStreams();
        LocalObjectReference imagePullSecretOverride = new LocalObjectReferenceBuilder()
            .withName("global-test-image")
            .build();

        EventStreams eventStreams = defaultEs
            .editSpec()
                .withNewImages()
                    .withPullSecrets(imagePullSecretOverride)
                .endImages()
            .endSpec()
            .build();

        assertThat(new AdminApiModel(eventStreams, imageConfig, listeners, mockIcpClusterDataMap).getServiceAccount()
                        .getImagePullSecrets(), contains(imagePullSecretOverride));
    }

    @Test
    public void testGlobalCustomImageOverrideWithDefaultIBMCom() {

        String customImageName = AdminApiModel.DEFAULT_IBMCOM_IMAGE;
        EventStreamsBuilder defaultEs = createDefaultEventStreams();
        EventStreams eventStreams = defaultEs
                .editSpec()
                    .withNewAdminApi()
                        .withImage(customImageName)
                    .endAdminApi()
                .endSpec()
                .build();

        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, listeners, mockIcpClusterDataMap);
        assertThat(adminApiModel.getImage(), is(customImageName));
        assertFalse(adminApiModel.getCustomImage());
    }

    @Test
    public void testPodServiceAccountContainsMergeOfPullSecrets() {

        EventStreamsBuilder defaultEs = createDefaultEventStreams();
        LocalObjectReference globalPullSecretOverride = new LocalObjectReferenceBuilder()
            .withName("global-image-secret")
            .build();

        LocalObjectReference componentPullSecretOverride = new LocalObjectReferenceBuilder()
            .withName("component-image-secret")
            .build();

        EventStreams eventStreams = defaultEs
            .editSpec()
                .withNewImages()
                    .withPullSecrets(globalPullSecretOverride)
                .endImages()
                .editAdminApi()
                    .withNewTemplate()
                        .withPod(new PodTemplateBuilder().withImagePullSecrets(componentPullSecretOverride).build())
                    .endTemplate()
                .endAdminApi()
            .endSpec()
            .build();

        assertThat(new AdminApiModel(eventStreams, imageConfig, listeners, mockIcpClusterDataMap).getServiceAccount()
                        .getImagePullSecrets(), containsInAnyOrder(globalPullSecretOverride, componentPullSecretOverride));
    }
  
    @Test
    public void testAdminApiContainerHasDefaultKafkaBootstrapEnvironmentVariables() {
        EventStreams defaultEs = createDefaultEventStreams().build();
        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, listeners, mockIcpClusterDataMap);

        String kafkaBootstrap = instanceName + "-kafka-bootstrap." + adminApiModel.getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.KAFKA_PORT;
        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_URL").withValue(kafkaBootstrap).build();
        EnvVar kafkaAdvertisedListenerEnv = new EnvVarBuilder().withName("KAFKA_ADVERTISED_LISTENER_BOOTSTRAP_ADDRESS").withValue(kafkaBootstrap).build();

        Container adminApiContainer = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapUrlEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaAdvertisedListenerEnv));
    }

    @Test
    public void testAdminApiContainerHasRoleBinding() {
        EventStreams defaultEs = createDefaultEventStreams().build();
        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, listeners, mockIcpClusterDataMap);

        List<Subject> subjects = adminApiModel.getRoleBinding().getSubjects();
        RoleRef roleReference = adminApiModel.getRoleBinding().getRoleRef();

        assertThat(subjects.size(), is(1));
        assertThat(subjects.get(0).getKind(), is("ServiceAccount"));
        assertThat(subjects.get(0).getName(), is(componentPrefix));
        assertThat(subjects.get(0).getNamespace(), is(adminApiModel.getNamespace()));

        assertThat(roleReference.getKind(), is("ClusterRole"));
        assertThat(roleReference.getName(), is(AdminApiModel.ADMIN_CLUSTERROLE_NAME));
        assertThat(roleReference.getApiGroup(), is("rbac.authorization.k8s.io"));

    }

    @Test
    public void testAdminApiContainerHasPlainKafkaStatusKafkaBootstrapEnvironmentVariables() {
        final String kafkaHost = "plainHost";
        final Integer kafkaPort = 1234;

        final String externalHost = "externalHost";
        final Integer externalPort = 9876;

        final String runasHost = "runasHost";
        final Integer runasPort = 8091;

        EventStreams defaultEs = createDefaultEventStreams().build();

        ListenerStatus kafkaListener = new ListenerStatusBuilder()
                .withNewType("plain")
                .addNewAddress()
                    .withHost(kafkaHost)
                    .withPort(kafkaPort)
                .endAddress()
                .build();

        ListenerStatus externalListener = new ListenerStatusBuilder()
                .withNewType("external")
                .addNewAddress()
                    .withHost(externalHost)
                    .withPort(externalPort)
                .endAddress()
                .build();

        ListenerStatus runasListener = new ListenerStatusBuilder()
            .withNewType("runas")
            .addNewAddress()
            .withHost(runasHost)
            .withPort(runasPort)
            .endAddress()
            .build();

        List<ListenerStatus> listeners = new ArrayList<>();
        listeners.add(kafkaListener);
        listeners.add(externalListener);

        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, listeners, mockIcpClusterDataMap);
        String expectedKafkaBootstrap = kafkaHost + ":" + kafkaPort;
        String expectedExternalBootstrap = externalHost + ":" + externalPort;

        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_URL").withValue(expectedKafkaBootstrap).build();
        EnvVar kafkaAdvertisedListenerEnv = new EnvVarBuilder().withName("KAFKA_ADVERTISED_LISTENER_BOOTSTRAP_ADDRESS").withValue(expectedExternalBootstrap).build();

        Container adminApiContainer = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapUrlEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaAdvertisedListenerEnv));
    }

    @Test
    public void testAdminApiContainerHasRunAsKafkaBootstrapEnvironmentVariables() {
        final String kafkaHost = "plainHost";
        final Integer kafkaPort = 1234;

        final String externalHost = "externalHost";
        final Integer externalPort = 9876;

        final String runasHost = "runasHost";
        final Integer runasPort = 8091;

        EventStreams defaultEs = createDefaultEventStreams().build();

        ListenerStatus kafkaListener = new ListenerStatusBuilder()
            .withNewType("plain")
            .addNewAddress()
            .withHost(kafkaHost)
            .withPort(kafkaPort)
            .endAddress()
            .build();

        ListenerStatus externalListener = new ListenerStatusBuilder()
            .withNewType("external")
            .addNewAddress()
            .withHost(externalHost)
            .withPort(externalPort)
            .endAddress()
            .build();

        ListenerStatus runasListener = new ListenerStatusBuilder()
            .withNewType("runas")
            .addNewAddress()
            .withHost(runasHost)
            .withPort(runasPort)
            .endAddress()
            .build();

        List<ListenerStatus> listeners = new ArrayList<>();
        listeners.add(kafkaListener);
        listeners.add(externalListener);
        listeners.add(runasListener);

        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, listeners, mockIcpClusterDataMap);
        String expectedRunAsKafkaBootstrap = runasHost + ":" + runasPort;

        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(expectedRunAsKafkaBootstrap).build();

        Container adminApiContainer = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(1);

        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapUrlEnv));
    }

    @Test
    public void testAdminApiContainerHasSecureKafkaStatusKafkaBootstrapEnvironmentVariables() {
        final String hostName = "tlsHost";
        final Integer port = 2345;

        final String externalHost = "externalHost";
        final Integer externalPort = 5678;

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                    .editOrNewSecurity()
                        .withEncryption(SecuritySpec.Encryption.TLS)
                    .endSecurity()
                .endSpec()
                .build();

        ListenerStatus kafkaListener = new ListenerStatusBuilder()
                .withNewType("tls")
                .addNewAddress()
                    .withHost(hostName)
                    .withPort(port)
                .endAddress()
                .build();

        ListenerStatus externalListener = new ListenerStatusBuilder()
                .withNewType("external")
                .addNewAddress()
                .withHost(externalHost)
                .withPort(externalPort)
                .endAddress()
                .build();

        List<ListenerStatus> listeners = new ArrayList<>();
        listeners.add(kafkaListener);
        listeners.add(externalListener);

        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, listeners, mockIcpClusterDataMap);
        String expectedKafkaBootstrap = hostName + ":" + port;
        String expectedExternalBootstrap = externalHost + ":" + externalPort;

        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_URL").withValue(expectedKafkaBootstrap).build();
        EnvVar kafkaAdvertisedListenerEnv = new EnvVarBuilder().withName("KAFKA_ADVERTISED_LISTENER_BOOTSTRAP_ADDRESS").withValue(expectedExternalBootstrap).build();

        Container adminApiContainer = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapUrlEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaAdvertisedListenerEnv));
    }

    @Test
    public void testDefaultBootstrapWhenNoKafkaStatusKafkaBootstrap() {

        EventStreams defaultEs = createDefaultEventStreams().build();
        ListenerStatus listener = new ListenerStatusBuilder().build();

        List<ListenerStatus> listeners = new ArrayList<>();
        listeners.add(listener);

        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, listeners, mockIcpClusterDataMap);
        String expectedKafkaBootstrap = instanceName + "-kafka-bootstrap." + adminApiModel.getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.KAFKA_PORT;

        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_URL").withValue(expectedKafkaBootstrap).build();
        EnvVar kafkaAdvertisedListenerEnv = new EnvVarBuilder().withName("KAFKA_ADVERTISED_LISTENER_BOOTSTRAP_ADDRESS").withValue(expectedKafkaBootstrap).build();

        Container adminApiContainer = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapUrlEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaAdvertisedListenerEnv));
    }

    @Test
    public void testDefaultBootstrapWhenNullListeners() {

        EventStreams defaultEs = createDefaultEventStreams().build();

        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, null, mockIcpClusterDataMap);
        String expectedKafkaBootstrap = instanceName + "-kafka-bootstrap." + adminApiModel.getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.KAFKA_PORT;

        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_URL").withValue(expectedKafkaBootstrap).build();
        EnvVar kafkaAdvertisedListenerEnv = new EnvVarBuilder().withName("KAFKA_ADVERTISED_LISTENER_BOOTSTRAP_ADDRESS").withValue(expectedKafkaBootstrap).build();

        Container adminApiContainer = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapUrlEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaAdvertisedListenerEnv));
    }

    @Test
    public void testDefaultLogging() {
        EventStreams defaultEs = createDefaultEventStreams().build();
        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, null, mockIcpClusterDataMap);

        EnvVar expectedTraceSpecEnvVar = new EnvVarBuilder().withName("TRACE_SPEC").withValue("*=info").build();
        List<EnvVar> actualEnvVars = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();

        assertThat(actualEnvVars, hasItem(expectedTraceSpecEnvVar));
    }

    @Test
    public void testOverrideLoggingInLine() {
        Map<String, String> loggers = new HashMap<>();
        loggers.put("logger.one", "info");
        loggers.put("logger.two", "debug");
        InlineLogging logging = new InlineLogging();
        logging.setLoggers(loggers);

        EventStreams eventStreams = createDefaultEventStreams()
                .editSpec()
                    .editAdminApi()
                        .withLogging(logging)
                    .endAdminApi()
                .endSpec()
                .build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockIcpClusterDataMap);

        EnvVar expectedTraceSpecEnvVar = new EnvVarBuilder().withName("TRACE_SPEC").withValue("logger.one=info,logger.two=debug").build();
        List<EnvVar> actualEnvVars = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();

        assertThat(actualEnvVars, hasItem(expectedTraceSpecEnvVar));
    }

    @Test
    public void testOverrideLoggingExternalIsIgnored() {
        ExternalLogging logging = new ExternalLogging();

        EventStreams eventStreams = createDefaultEventStreams()
                .editSpec()
                .editAdminApi()
                .withLogging(logging)
                .endAdminApi()
                .endSpec()
                .build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockIcpClusterDataMap);

        EnvVar expectedTraceSpecEnvVar = new EnvVarBuilder().withName("TRACE_SPEC").withValue("*=info").build();
        List<EnvVar> actualEnvVars = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();

        assertThat(actualEnvVars, hasItem(expectedTraceSpecEnvVar));
    }

    @Test
    public void testSSLTrustAndKeystoreEnvVars() {

        final String userCertPath = "/opt/ibm/adminapi/user";
        final String clusterCertPath = "/opt/ibm/adminapi/cluster";

        EventStreams eventStreams = createDefaultEventStreams().build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockIcpClusterDataMap);

        EnvVar expectedEnvVarTrustStorePath = new EnvVarBuilder().withName("SSL_TRUSTSTORE_PATH").withValue(clusterCertPath + File.separator + "podtls.p12").build();
        EnvVar expectedEnvVarKeyStorePath = new EnvVarBuilder().withName("SSL_KEYSTORE_PATH").withValue(userCertPath + File.separator + "podtls.p12").build();
        List<EnvVar> actualEnvVars = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(1).getEnv();
        assertThat(actualEnvVars, hasItem(expectedEnvVarTrustStorePath));
        assertThat(actualEnvVars, hasItem(expectedEnvVarKeyStorePath));
    }

    @Test
    public void testExternalAccessOverrideWithRoutes() {
        EventStreams instance = createDefaultEventStreamsWithExternalAccess(ExternalAccess.TYPE_ROUTE).build();
        AdminApiModel adminApiModelK8s = new AdminApiModel(instance, imageConfig, null, mockIcpClusterDataMap);
        assertThat(adminApiModelK8s.getService().getSpec().getType(), is("ClusterIP"));

        AdminApiModel adminApiModelOpenShift = new AdminApiModel(instance, imageConfig, Collections.singletonList(new ListenerStatus()), mockIcpClusterDataMap);
        assertThat(adminApiModelOpenShift.getService().getSpec().getType(), is("ClusterIP"));
    }

    @Test
    public void testICPClusterDataEnvironmentVariablesCorrectlySet() {
        String clusterAddress = "0.0.0.0";
        String clusterPort = "9080";
        String caCert = "abcdef";
        String clusterName = "test-cluster";
        mockIcpClusterDataMap.put("cluster_address", clusterAddress);
        mockIcpClusterDataMap.put("cluster_name", clusterName);
        mockIcpClusterDataMap.put("cluster_router_https_port", clusterPort);
        mockIcpClusterDataMap.put("icp_public_cacert", caCert);

        EventStreams eventStreams = createDefaultEventStreams().build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockIcpClusterDataMap);
        
        EnvVar expectedEnvVarPrometheusHost = new EnvVarBuilder().withName("PROMETHEUS_HOST").withValue(clusterAddress).build();
        EnvVar expectedEnvVarPrometheusPort = new EnvVarBuilder().withName("PROMETHEUS_PORT").withValue(clusterPort).build();
        EnvVar expectedEnvVarPrometheusClusterCaCert = new EnvVarBuilder().withName("CLUSTER_CACERT").withValue(caCert).build();
        EnvVar expectedEnvVarIAMClusterName = new EnvVarBuilder().withName("IAM_CLUSTER_NAME").withValue(clusterName).build();
        List<EnvVar> actualEnvVars = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        assertThat(actualEnvVars, hasItem(expectedEnvVarPrometheusHost));
        assertThat(actualEnvVars, hasItem(expectedEnvVarPrometheusPort));
        assertThat(actualEnvVars, hasItem(expectedEnvVarPrometheusClusterCaCert));
        List<EnvVar> actualAdminAPIEnvVars = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(1).getEnv();
        assertThat(actualAdminAPIEnvVars, hasItem(expectedEnvVarIAMClusterName));

    }

    @Test
    public void testCreateAdminApiRouteWithTlsEncryption() {
        EventStreams eventStreams = createDefaultEventStreams()
                .editSpec()
                .withSecurity(new SecuritySpecBuilder().withEncryption(SecuritySpec.Encryption.TLS).build())
                .endSpec()
                .build();

        assertThat(new AdminApiModel(eventStreams, imageConfig, null, mockIcpClusterDataMap).getRoute().getSpec().getTls().getTermination(), is("passthrough"));

        eventStreams = createDefaultEventStreams().build();
        assertThat(new AdminApiModel(eventStreams, imageConfig, null, mockIcpClusterDataMap).getRoutes().get(Listener.EXTERNAL_TLS_NAME).getSpec().getTls().getTermination(), is("passthrough"));
    }

    @Test
    public void testGenerationIdLabelOnDeployment() {
        EventStreams eventStreams = createDefaultEventStreams().build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockIcpClusterDataMap);

        assertThat(adminApiModel.getDeployment("newID").getMetadata().getLabels().containsKey(AbstractSecureEndpointModel.CERT_GENERATION_KEY), is(true));
        assertThat(adminApiModel.getDeployment("newID").getMetadata().getLabels().get(AbstractSecureEndpointModel.CERT_GENERATION_KEY), is("newID"));
        assertThat(adminApiModel.getDeployment("newID").getSpec().getTemplate().getMetadata().getLabels().containsKey(AbstractSecureEndpointModel.CERT_GENERATION_KEY), is(true));
        assertThat(adminApiModel.getDeployment("newID").getSpec().getTemplate().getMetadata().getLabels().get(AbstractSecureEndpointModel.CERT_GENERATION_KEY), is("newID"));
    }
}