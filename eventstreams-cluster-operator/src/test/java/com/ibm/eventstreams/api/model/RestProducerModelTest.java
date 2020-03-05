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
import com.ibm.eventstreams.api.spec.SecuritySpec;
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
import io.fabric8.openshift.api.model.Route;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.status.ListenerStatusBuilder;
import io.strimzi.api.kafka.model.template.PodTemplateBuilder;

import org.hamcrest.collection.IsMapWithSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterableOf;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsInAnyOrder;

@ExtendWith(MockitoExtension.class)
public class RestProducerModelTest {

    private final String instanceName = "test-instance";
    private final String componentPrefix = instanceName + "-" + AbstractModel.APP_NAME + "-" + RestProducerModel.COMPONENT_NAME;
    private final int defaultReplicas = 1;
    private List<ListenerStatus> listeners = new ArrayList<>();

    @Mock
    private EventStreamsOperatorConfig.ImageLookup imageConfig;

    private EventStreamsBuilder createDefaultEventStreams() {
        return ModelUtils.createDefaultEventStreams(instanceName)
                .editSpec()
                    .withNewRestProducer()
                        .withReplicas(defaultReplicas)
                    .endRestProducer()
                .endSpec();
    }

    private RestProducerModel createDefaultRestProducerModel() {
        EventStreams instance = createDefaultEventStreams().build();
        return new RestProducerModel(instance, imageConfig, listeners);
    }

    @Test
    public void testDefaultRestProducerModel() {
        RestProducerModel restProducerModel = createDefaultRestProducerModel();

        Deployment restProducerDeployment = restProducerModel.getDeployment();
        assertThat(restProducerDeployment.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(restProducerDeployment.getSpec().getReplicas(), is(defaultReplicas));

        Service adminApiInternalService = restProducerModel.getInternalService();
        String expectedInternalServiceName = componentPrefix + "-" + AbstractSecureEndpointModel.INTERNAL_SERVICE_SUFFIX;
        assertThat(adminApiInternalService.getMetadata().getName(), is(expectedInternalServiceName));

        Service adminApiExternalService = restProducerModel.getExternalService();
        String expectedExternalServiceName = componentPrefix + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_SUFFIX;
        assertThat(adminApiExternalService.getMetadata().getName(), is(expectedExternalServiceName));

        Map<String, Route> restProducerRoutes = restProducerModel.getRoutes();
        assertThat(restProducerRoutes, IsMapWithSize.aMapWithSize(2));
        restProducerRoutes.forEach((key, route) -> {
            assertThat(route.getMetadata().getName(), startsWith(componentPrefix));
            assertThat(route.getMetadata().getName(), containsString(key));
        });
    }

    @Test
    public void testDefaultResourceRequirements() {
        RestProducerModel restProducerModel = createDefaultRestProducerModel();

        ResourceRequirements resourceRequirements = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getResources();
        assertThat(resourceRequirements.getRequests().get("cpu").getAmount(), is("500m"));
        assertThat(resourceRequirements.getRequests().get("memory").getAmount(), is("1Gi"));
        assertThat(resourceRequirements.getLimits().get("cpu").getAmount(), is("4000m"));
        assertThat(resourceRequirements.getLimits().get("memory").getAmount(), is("2Gi"));
    }

    @Test
    public void testCustomResourceRequirements() {
        ResourceRequirements customResourceRequirements = new ResourceRequirementsBuilder()
                .addToRequests("memory", new Quantity("3Gi"))
                .addToLimits("cpu", new Quantity("100m"))
                .build();
        EventStreams eventStreamsResource = createDefaultEventStreams()
                .editSpec()
                    .editRestProducer()
                        .withResources(customResourceRequirements)
                    .endRestProducer()
                .endSpec()
                .build();
        RestProducerModel restProducerModel = new RestProducerModel(eventStreamsResource, imageConfig, listeners);

        ResourceRequirements resourceRequirements = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getResources();
        assertThat(resourceRequirements.getRequests().get("cpu").getAmount(), is("500m"));
        assertThat(resourceRequirements.getRequests().get("memory").getAmount(), is("3Gi"));
        assertThat(resourceRequirements.getLimits().get("cpu").getAmount(), is("100m"));
        assertThat(resourceRequirements.getLimits().get("memory").getAmount(), is("2Gi"));
    }

    @Test
    public void testNetworkPolicy() {
        RestProducerModel restProducerModel = createDefaultRestProducerModel();

        NetworkPolicy restProducerNetworkPolicy = restProducerModel.getNetworkPolicy();
        String expectedNetworkPolicyName = componentPrefix;
        assertThat(restProducerNetworkPolicy.getMetadata().getName(), is(expectedNetworkPolicyName));
        assertThat(restProducerNetworkPolicy.getKind(), is("NetworkPolicy"));

        int numberOfPodToPodListeners = 1;
        int expectNumberOfIngresses = Listener.enabledListeners().size() + numberOfPodToPodListeners;
        assertThat(restProducerNetworkPolicy.getSpec().getIngress().size(), is(expectNumberOfIngresses));
        List<Listener> listeners = Listener.enabledListeners();
        listeners.add(Listener.podToPodListener(false));
        List<Integer> listenerPorts = listeners.stream().map(Listener::getPort).collect(Collectors.toList());
        restProducerNetworkPolicy.getSpec().getIngress().forEach(ingress -> {
            assertThat(ingress.getFrom(), is(emptyIterableOf(NetworkPolicyPeer.class)));
            assertThat(ingress.getPorts().size(), is(1));
            assertThat(listenerPorts, hasItem(ingress.getPorts().get(0).getPort().getIntVal()));
        });

        assertThat(restProducerNetworkPolicy.getSpec().getEgress().size(), is(2));
        assertThat(restProducerNetworkPolicy.getSpec().getEgress().get(0).getPorts().size(), is(1));
        assertThat(restProducerNetworkPolicy
            .getSpec()
            .getEgress()
            .get(0)
            .getPorts()
            .get(0)
            .getPort()
            .getIntVal(), is(EventStreamsKafkaModel.KAFKA_PORT));
        assertThat(restProducerNetworkPolicy.getSpec().getEgress().get(0).getTo().size(), is(1));
        assertThat(restProducerNetworkPolicy
            .getSpec()
            .getEgress()
            .get(0)
            .getTo()
            .get(0)
            .getPodSelector()
            .getMatchLabels()
            .size(), is(1));
        assertThat(restProducerNetworkPolicy
            .getSpec()
            .getEgress()
            .get(0)
            .getTo()
            .get(0)
            .getPodSelector()
            .getMatchLabels()
            .get(Labels.COMPONENT_LABEL), is(EventStreamsKafkaModel.KAFKA_COMPONENT_NAME));

        assertThat(restProducerNetworkPolicy.getSpec().getEgress().get(1).getTo().size(), is(1));
        assertThat(restProducerNetworkPolicy.getSpec().getEgress().get(1).getPorts().size(), is(1));
        assertThat(restProducerNetworkPolicy
            .getSpec()
            .getEgress()
            .get(1)
            .getPorts()
            .get(0)
            .getPort()
            .getIntVal(), is(Listener.podToPodListener(false).getPort()));
        assertThat(restProducerNetworkPolicy
            .getSpec()
            .getEgress()
            .get(1)
            .getTo()
            .get(0)
            .getPodSelector()
            .getMatchLabels()
            .size(), is(1));
        assertThat(restProducerNetworkPolicy
            .getSpec()
            .getEgress()
            .get(1)
            .getTo()
            .get(0)
            .getPodSelector()
            .getMatchLabels()
            .get(Labels.COMPONENT_LABEL), is(SchemaRegistryModel.COMPONENT_NAME));

        assertThat(restProducerNetworkPolicy.getSpec().getPodSelector().getMatchLabels().size(), is(1));
        assertThat(restProducerNetworkPolicy
            .getSpec()
            .getPodSelector()
            .getMatchLabels()
            .get(Labels.COMPONENT_LABEL), is(RestProducerModel.COMPONENT_NAME));
    }

    @Test
    public void testImageOverride() {
        String restProducerImage = "rest-producer-image:latest";

        EventStreams instance = createDefaultEventStreams()
                .editSpec()
                    .editRestProducer()
                        .withImage(restProducerImage)
                    .endRestProducer()
                .endSpec()
                .build();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(RestProducerModel.COMPONENT_NAME, restProducerImage);

        List<Container> containers = new RestProducerModel(instance, imageConfig, listeners).getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testOperatorImageOverride() {
        String restProducerImage = "rest-producer-image:latest";

        when(imageConfig.getRestProducerImage()).thenReturn(Optional.of(restProducerImage));

        RestProducerModel model = createDefaultRestProducerModel();
        List<Container> containers = model.getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(RestProducerModel.COMPONENT_NAME, restProducerImage);

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testOperatorImageOverrideTakesPrecedenceOverComponentLevelOverride() {
        String restProducerImage = "rest-producer-image:latest";
        String restProducerImageFromEnv = "rest-producer-image:latest";

        when(imageConfig.getRestProducerImage()).thenReturn(Optional.of(restProducerImageFromEnv));

        EventStreams instance = createDefaultEventStreams()
                .editSpec()
                    .editRestProducer()
                        .withImage(restProducerImage)
                    .endRestProducer()
                .endSpec()
                .build();

        List<Container> containers = new RestProducerModel(instance, imageConfig, listeners).getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(RestProducerModel.COMPONENT_NAME, restProducerImage);

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testPodServiceAccountContainsUserSuppliedPullSecret() {

        EventStreamsBuilder defaultEs = createDefaultEventStreams();
        LocalObjectReference imagePullSecretOverride = new LocalObjectReferenceBuilder()
            .withName("test-image")
            .build();

        EventStreams eventStreams = defaultEs
            .editSpec()
                .editRestProducer()
                    .editOrNewTemplate()
                        .withPod(new PodTemplateBuilder()
                                        .withImagePullSecrets(imagePullSecretOverride)
                                        .build())
                    .endTemplate()
                .endRestProducer()
            .endSpec()
            .build();

        assertThat(new RestProducerModel(eventStreams, imageConfig, listeners).getServiceAccount()
                        .getImagePullSecrets(), contains(imagePullSecretOverride));
    }

    @Test
    public void testPodServiceAccountContainsGlobalSuppliedPullSecret() {

        EventStreamsBuilder defaultEs = createDefaultEventStreams();
        LocalObjectReference imagePullSecretOverride = new LocalObjectReferenceBuilder()
            .withName("test-image")
            .build();

        EventStreams eventStreams = defaultEs
            .editSpec()
                .withNewImages()
                    .withPullSecrets(imagePullSecretOverride)
                .endImages()
            .endSpec()
            .build();

        assertThat(new RestProducerModel(eventStreams, imageConfig, listeners).getServiceAccount()
                        .getImagePullSecrets(), contains(imagePullSecretOverride));
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
                .editRestProducer()
                    .withNewTemplate()
                        .withPod(new PodTemplateBuilder().withImagePullSecrets(componentPullSecretOverride).build())
                    .endTemplate()
                .endRestProducer()
            .endSpec()
            .build();

        assertThat(new RestProducerModel(eventStreams, imageConfig, listeners).getServiceAccount()
                        .getImagePullSecrets(), containsInAnyOrder(globalPullSecretOverride, componentPullSecretOverride));
    }

    @Test
    public void testContainerHasDefaultKafkaBootstrapEnvironmentVariables() {
        EventStreams defaultEs = createDefaultEventStreams().build();
        RestProducerModel restProducerModel = new RestProducerModel(defaultEs, imageConfig, listeners);

        String kafkaBootstrap = instanceName + "-kafka-bootstrap." + restProducerModel.getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.KAFKA_PORT;
        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(kafkaBootstrap).build();
        Container adminApiContainer = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapUrlEnv));
    }

    @Test
    public void testContainerHasPlainKafkaStatusKafkaBootstrapEnvironmentVariables() {
        final String hostName = "plainHost";
        final Integer port = 1234;

        EventStreams defaultEs = createDefaultEventStreams().build();

        ListenerStatus listener = new ListenerStatusBuilder()
                .withNewType("plain")
                .addNewAddress()
                .withHost(hostName)
                .withPort(port)
                .endAddress()
                .build();

        List<ListenerStatus> listeners = new ArrayList<>();
        listeners.add(listener);

        RestProducerModel restProducerModel = new RestProducerModel(defaultEs, imageConfig, listeners);
        String expectedKafkaBootstrap = hostName + ":" + port;

        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(expectedKafkaBootstrap).build();
        Container adminApiContainer = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapUrlEnv));
    }

    @Test
    public void testContainerHasSecureKafkaStatusKafkaBootstrapEnvironmentVariables() {
        final String hostName = "tlsHost";
        final Integer port = 2345;

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                .editOrNewSecurity()
                .withEncryption(SecuritySpec.Encryption.TLS)
                .endSecurity()
                .endSpec()
                .build();

        ListenerStatus listener = new ListenerStatusBuilder()
                .withNewType("tls")
                .addNewAddress()
                .withHost(hostName)
                .withPort(port)
                .endAddress()
                .build();

        List<ListenerStatus> listeners = new ArrayList<>();
        listeners.add(listener);

        RestProducerModel restProducerModel = new RestProducerModel(defaultEs, imageConfig, listeners);
        String expectedKafkaBootstrap = hostName + ":" + port;

        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(expectedKafkaBootstrap).build();
        Container adminApiContainer = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapUrlEnv));
    }

    @Test
    public void testDefaultBootstrapWhenNoKafkaStatusKafkaBootstrap() {

        EventStreams defaultEs = createDefaultEventStreams().build();
        ListenerStatus listener = new ListenerStatusBuilder().build();

        List<ListenerStatus> listeners = new ArrayList<>();
        listeners.add(listener);

        RestProducerModel restProducerModel = new RestProducerModel(defaultEs, imageConfig, listeners);
        String expectedKafkaBootstrap = instanceName + "-kafka-bootstrap." + restProducerModel.getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.KAFKA_PORT;

        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(expectedKafkaBootstrap).build();
        Container adminApiContainer = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapUrlEnv));
    }

    @Test
    public void testDefaultBootstrapWhenNullListeners() {

        EventStreams defaultEs = createDefaultEventStreams().build();

        RestProducerModel restProducerModel = new RestProducerModel(defaultEs, imageConfig, null);
        String expectedKafkaBootstrap = instanceName + "-kafka-bootstrap." + restProducerModel.getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + EventStreamsKafkaModel.KAFKA_PORT;

        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(expectedKafkaBootstrap).build();
        Container adminApiContainer = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapUrlEnv));
    }

    @Test
    public void testCreateRestProducerRouteWithTlsEncryption() {
        EventStreams eventStreams = createDefaultEventStreams()
                .editSpec()
                    .withNewSecurity()
                        .withEncryption(SecuritySpec.Encryption.TLS)
                    .endSecurity()
                .endSpec()
                .build();

        RestProducerModel restProducerModel = new RestProducerModel(eventStreams, imageConfig, listeners);
        Map<String, Route> routes = restProducerModel.getRoutes();
        assertThat(routes, IsMapWithSize.aMapWithSize(2));
        assertThat(routes.get(restProducerModel.getRouteName(Listener.EXTERNAL_TLS_NAME)).getSpec().getTls().getTermination(), is("passthrough"));
    }
      
    public void testGenerationIdLabelOnDeployment() {
        EventStreams eventStreams = createDefaultEventStreams().build();
        RestProducerModel restProducerModel = new RestProducerModel(eventStreams, imageConfig, null);

        assertThat(restProducerModel.getDeployment("newID").getMetadata().getLabels().containsKey(AbstractSecureEndpointModel.CERT_GENERATION_KEY), is(true));
        assertThat(restProducerModel.getDeployment("newID").getMetadata().getLabels().get(AbstractSecureEndpointModel.CERT_GENERATION_KEY), is("newID"));
        assertThat(restProducerModel.getDeployment("newID").getSpec().getTemplate().getMetadata().getLabels().containsKey(AbstractSecureEndpointModel.CERT_GENERATION_KEY), is(true));
        assertThat(restProducerModel.getDeployment("newID").getSpec().getTemplate().getMetadata().getLabels().get(AbstractSecureEndpointModel.CERT_GENERATION_KEY), is("newID"));
    }
}