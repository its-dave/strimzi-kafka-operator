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

import com.ibm.commonservices.CommonServicesConfig;
import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.TlsVersion;
import com.ibm.eventstreams.api.model.utils.CustomMatchers;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRuleBuilder;
import io.fabric8.openshift.api.model.Route;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.status.ListenerStatusBuilder;
import io.strimzi.api.kafka.model.template.PodTemplateBuilder;
import io.strimzi.operator.common.model.Labels;
import org.hamcrest.collection.IsMapWithSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RestProducerModelTest {

    private final CommonServicesConfig mockCommonServicesConfig = new CommonServicesConfig("mycluster", "ingress", "consoleHost", "443");
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

    private EventStreamsBuilder createEventStreamsWithAuth() {
        return ModelUtils.createEventStreamsWithAuthentication(instanceName)
            .editSpec()
            .withNewRestProducer()
            .withReplicas(defaultReplicas)
            .endRestProducer()
            .endSpec();
    }

    private RestProducerModel createDefaultRestProducerModel() {
        EventStreams instance = createDefaultEventStreams().build();
        return new RestProducerModel(instance, imageConfig, listeners, mockCommonServicesConfig);
    }

    @Test
    public void testDefaultRestProducerModel() {
        RestProducerModel restProducerModel = createDefaultRestProducerModel();

        Deployment restProducerDeployment = restProducerModel.getDeployment();
        assertThat(restProducerDeployment.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(restProducerDeployment.getSpec().getReplicas(), is(defaultReplicas));

        Service adminApiExternalService = restProducerModel.getSecurityService(EndpointServiceType.ROUTE);
        String expectedExternalServiceName = componentPrefix + "-" + AbstractSecureEndpointsModel.ROUTE_SERVICE_SUFFIX;
        assertThat(adminApiExternalService.getMetadata().getName(), is(expectedExternalServiceName));

        Map<String, Route> restProducerRoutes = restProducerModel.getRoutes();
        assertThat(restProducerRoutes, IsMapWithSize.aMapWithSize(1));
        restProducerRoutes.forEach((key, route) -> {
            assertThat(route.getMetadata().getName(), startsWith(componentPrefix));
            assertThat(route.getMetadata().getName(), containsString(key));
        });
    }

    @Test
    public void testDefaultResourceRequirements() {
        RestProducerModel restProducerModel = createDefaultRestProducerModel();

        ResourceRequirements resourceRequirements = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getResources();
        assertThat(resourceRequirements.getRequests().get("cpu").getAmount(), is("250m"));
        assertThat(resourceRequirements.getRequests().get("memory").getAmount(), is("1Gi"));
        assertThat(resourceRequirements.getLimits().get("cpu").getAmount(), is("1000m"));
        assertThat(resourceRequirements.getLimits().get("memory").getAmount(), is("1Gi"));
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
        RestProducerModel restProducerModel = new RestProducerModel(eventStreamsResource, imageConfig, listeners, mockCommonServicesConfig);

        ResourceRequirements resourceRequirements = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getResources();
        assertThat(resourceRequirements.getRequests().get("cpu").getAmount(), is("250m"));
        assertThat(resourceRequirements.getRequests().get("memory").getAmount(), is("3Gi"));
        assertThat(resourceRequirements.getLimits().get("cpu").getAmount(), is("100m"));
        assertThat(resourceRequirements.getLimits().get("memory").getAmount(), is("1Gi"));
    }

    @Test
    public void testVolumeMounts() {
        RestProducerModel restProducerModel = createDefaultRestProducerModel();

        List<VolumeMount> volumeMounts = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts();

        assertThat(volumeMounts.size(), is(5));

        assertThat(volumeMounts.get(0).getName(), is(RestProducerModel.IBMCLOUD_CA_VOLUME_MOUNT_NAME));
        assertThat(volumeMounts.get(0).getReadOnly(), is(true));
        assertThat(volumeMounts.get(0).getMountPath(), is(RestProducerModel.IBMCLOUD_CA_CERTIFICATE_PATH));

        assertThat(volumeMounts.get(1).getName(), is(AbstractSecureEndpointsModel.CERTS_VOLUME_MOUNT_NAME));
        assertThat(volumeMounts.get(1).getReadOnly(), is(true));
        assertThat(volumeMounts.get(1).getMountPath(), is(AbstractSecureEndpointsModel.CERTIFICATE_PATH));

        assertThat(volumeMounts.get(2).getName(), is(AbstractSecureEndpointsModel.CLUSTER_CA_VOLUME_MOUNT_NAME));
        assertThat(volumeMounts.get(2).getReadOnly(), is(true));
        assertThat(volumeMounts.get(2).getMountPath(), is(AbstractSecureEndpointsModel.CLUSTER_CERTIFICATE_PATH));

        assertThat(volumeMounts.get(3).getName(), is(AbstractSecureEndpointsModel.CLIENT_CA_VOLUME_MOUNT_NAME));
        assertThat(volumeMounts.get(3).getReadOnly(), is(true));
        assertThat(volumeMounts.get(3).getMountPath(), is(AbstractSecureEndpointsModel.CLIENT_CA_CERTIFICATE_PATH));

        assertThat(volumeMounts.get(4).getName(), is(AbstractSecureEndpointsModel.KAFKA_USER_SECRET_VOLUME_NAME));
        assertThat(volumeMounts.get(4).getReadOnly(), is(true));
        assertThat(volumeMounts.get(4).getMountPath(), is(AbstractSecureEndpointsModel.KAFKA_USER_CERTIFICATE_PATH));

    }

    @Test
    public void testVolumes() {
        RestProducerModel restProducerModel = createDefaultRestProducerModel();

        List<Volume> volumes = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getVolumes();

        assertThat(volumes.size(), is(5));

        assertThat(volumes.get(0).getName(), is(AbstractSecureEndpointsModel.CERTS_VOLUME_MOUNT_NAME));
        assertThat(volumes.get(1).getName(), is(AbstractSecureEndpointsModel.CLUSTER_CA_VOLUME_MOUNT_NAME));
        assertThat(volumes.get(2).getName(), is(AbstractSecureEndpointsModel.CLIENT_CA_VOLUME_MOUNT_NAME));
        assertThat(volumes.get(3).getName(), is(AbstractSecureEndpointsModel.KAFKA_USER_SECRET_VOLUME_NAME));
        assertThat(volumes.get(4).getName(), is(RestProducerModel.IBMCLOUD_CA_VOLUME_MOUNT_NAME));
    }

    @Test
    public void testNetworkPolicy() {
        RestProducerModel restProducerModel = createDefaultRestProducerModel();

        NetworkPolicy restProducerNetworkPolicy = restProducerModel.getNetworkPolicy();
        assertThat(restProducerNetworkPolicy.getMetadata().getName(), is(componentPrefix));
        assertThat(restProducerNetworkPolicy.getKind(), is("NetworkPolicy"));

        assertThat(restProducerNetworkPolicy.getSpec().getIngress().size(), is(restProducerModel.getEndpoints().size()));

        NetworkPolicyIngressRule defaultP2PIngressRule = new NetworkPolicyIngressRuleBuilder()
                .addNewPort()
                    .withPort(new IntOrString(7443))
                .endPort()
                .addNewFrom()
                    .withNewPodSelector()
                        .addToMatchLabels(Labels.KUBERNETES_INSTANCE_LABEL, instanceName)
                        .addToMatchLabels(Labels.KUBERNETES_MANAGED_BY_LABEL, AbstractModel.OPERATOR_NAME)
                    .endPodSelector()
                .endFrom()
                .build();

        NetworkPolicyIngressRule defaultEndpointRule = new NetworkPolicyIngressRuleBuilder()
                .addNewPort()
                    .withPort(new IntOrString(9443))
                .endPort()
                .build();

        assertThat(restProducerNetworkPolicy.getSpec().getIngress(), CustomMatchers.containsIngressRulesInAnyOrder(defaultEndpointRule, defaultP2PIngressRule));

        assertThat(restProducerNetworkPolicy.getSpec().getPodSelector().getMatchLabels(), allOf(
                aMapWithSize(2),
                hasEntry(Labels.KUBERNETES_NAME_LABEL, RestProducerModel.APPLICATION_NAME),
                hasEntry(Labels.KUBERNETES_INSTANCE_LABEL, instanceName)
                ));
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

        List<Container> containers = new RestProducerModel(instance, imageConfig, listeners, mockCommonServicesConfig).getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testOperatorImagePullSecretOverride() {
        LocalObjectReference imagePullSecret = new LocalObjectReferenceBuilder()
                .withName("operator-image-pull-secret")
                .build();
        when(imageConfig.getPullSecrets()).thenReturn(Collections.singletonList(imagePullSecret));

        assertThat(createDefaultRestProducerModel().getServiceAccount().getImagePullSecrets(),
                   contains(imagePullSecret));
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

        List<Container> containers = new RestProducerModel(instance, imageConfig, listeners, mockCommonServicesConfig).getDeployment().getSpec().getTemplate()
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

        assertThat(new RestProducerModel(eventStreams, imageConfig, listeners, mockCommonServicesConfig).getServiceAccount()
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

        assertThat(new RestProducerModel(eventStreams, imageConfig, listeners, mockCommonServicesConfig).getServiceAccount()
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

        assertThat(new RestProducerModel(eventStreams, imageConfig, listeners, mockCommonServicesConfig).getServiceAccount()
                        .getImagePullSecrets(), containsInAnyOrder(globalPullSecretOverride, componentPullSecretOverride));
    }

    @Test
    public void testContainerHasDefaultKafkaBootstrapEnvironmentVariables() {
        EventStreams defaultEs = createDefaultEventStreams().build();
        RestProducerModel restProducerModel = new RestProducerModel(defaultEs, imageConfig, listeners, mockCommonServicesConfig);

        String runasKafkaBootstrap = instanceName + "-kafka-bootstrap." + restProducerModel.getNamespace() + ".svc:" + EventStreamsKafkaModel.KAFKA_RUNAS_PORT;
        EnvVar authentication = new EnvVarBuilder().withName("AUTHENTICATION").withValue("9443:RUNAS-ANONYMOUS,7443:RUNAS-ANONYMOUS").build();
        EnvVar endpoints = new EnvVarBuilder().withName("ENDPOINTS").withValue("9443:external,7443:p2ptls").build();
        EnvVar tlsVersion = new EnvVarBuilder().withName("TLS_VERSION").withValue("9443:TLSv1.2,7443:TLSv1.2").build();
        EnvVar runasKafkaBootstrapUrlEnv = new EnvVarBuilder().withName("RUNAS_KAFKA_BOOTSTRAP_SERVERS").withValue(runasKafkaBootstrap).build();
        Container adminApiContainer = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(adminApiContainer.getEnv(), hasItem(runasKafkaBootstrapUrlEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(authentication));
        assertThat(adminApiContainer.getEnv(), hasItem(endpoints));
        assertThat(adminApiContainer.getEnv(), hasItem(tlsVersion));

    }

    @Test
    public void testContainerHasDefaultKafkaBootstrapEnvironmentVariablesWithAuth() {
        EventStreams defaultEs = createEventStreamsWithAuth().build();
        RestProducerModel restProducerModel = new RestProducerModel(defaultEs, imageConfig, listeners, mockCommonServicesConfig);

        String runasKafkaBootstrap = instanceName + "-kafka-bootstrap." + restProducerModel.getNamespace() + ".svc:" + EventStreamsKafkaModel.KAFKA_RUNAS_PORT;
        EnvVar authentication = new EnvVarBuilder().withName("AUTHENTICATION").withValue("9443:TLS;SCRAM-SHA-512,7443:RUNAS-ANONYMOUS").build();
        EnvVar endpoints = new EnvVarBuilder().withName("ENDPOINTS").withValue("9443:external,7443:p2ptls").build();
        EnvVar tlsVersion = new EnvVarBuilder().withName("TLS_VERSION").withValue("9443:TLSv1.2,7443:TLSv1.2").build();
        EnvVar runasKafkaBootstrapUrlEnv = new EnvVarBuilder().withName("RUNAS_KAFKA_BOOTSTRAP_SERVERS").withValue(runasKafkaBootstrap).build();
        Container adminApiContainer = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(adminApiContainer.getEnv(), hasItem(runasKafkaBootstrapUrlEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(authentication));
        assertThat(adminApiContainer.getEnv(), hasItem(endpoints));
        assertThat(adminApiContainer.getEnv(), hasItem(tlsVersion));
    }

    @Test
    public void testContainerHasPlainKafkaStatusKafkaBootstrapEnvironmentVariables() {
        final String hostName = "plainHost";
        final Integer port = 1234;

        EventStreams defaultEs = createDefaultEventStreams().build();

        ListenerStatus listener = new ListenerStatusBuilder()
                .withNewType("tls")
                .addNewAddress()
                .withHost(hostName)
                .withPort(port)
                .endAddress()
                .build();

        List<ListenerStatus> listeners = new ArrayList<>();
        listeners.add(listener);

        RestProducerModel restProducerModel = new RestProducerModel(defaultEs, imageConfig, listeners, mockCommonServicesConfig);
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
                .withInternalTls(TlsVersion.TLS_V1_2)
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

        RestProducerModel restProducerModel = new RestProducerModel(defaultEs, imageConfig, listeners, mockCommonServicesConfig);
        String expectedKafkaBootstrap = hostName + ":" + port;

        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(expectedKafkaBootstrap).build();
        Container adminApiContainer = restProducerModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapUrlEnv));
    }

    @Test
    public void testCreateRestProducerRouteWithTlsEncryption() {
        EventStreams eventStreams = createDefaultEventStreams()
                .editSpec()
                    .withNewSecurity()
                        .withInternalTls(TlsVersion.TLS_V1_2)
                    .endSecurity()
                .endSpec()
                .build();

        RestProducerModel restProducerModel = new RestProducerModel(eventStreams, imageConfig, listeners, mockCommonServicesConfig);
        Map<String, Route> routes = restProducerModel.getRoutes();
        assertThat(routes, IsMapWithSize.aMapWithSize(1));
        assertThat(routes.get(restProducerModel.getRouteName(Endpoint.DEFAULT_EXTERNAL_NAME)).getSpec().getTls().getTermination(), is("passthrough"));
    }

    @Test
    public void testGenerationIdLabelOnDeployment() {
        EventStreams eventStreams = createDefaultEventStreams().build();
        RestProducerModel restProducerModel = new RestProducerModel(eventStreams, imageConfig, null, mockCommonServicesConfig);

        assertThat(restProducerModel.getDeployment("newID").getMetadata().getLabels().containsKey(AbstractSecureEndpointsModel.CERT_GENERATION_KEY), is(true));
        assertThat(restProducerModel.getDeployment("newID").getMetadata().getLabels().get(AbstractSecureEndpointsModel.CERT_GENERATION_KEY), is("newID"));
        assertThat(restProducerModel.getDeployment("newID").getSpec().getTemplate().getMetadata().getLabels().containsKey(AbstractSecureEndpointsModel.CERT_GENERATION_KEY), is(true));
        assertThat(restProducerModel.getDeployment("newID").getSpec().getTemplate().getMetadata().getLabels().get(AbstractSecureEndpointsModel.CERT_GENERATION_KEY), is("newID"));
    }

    @Test
    public void testCheckIfEnabled() {
        EventStreams eventStreams = createDefaultEventStreams().build();
        assertThat(RestProducerModel.isRestProducerEnabled(eventStreams), is(true));
    }

    @Test
    public void testCheckIfDisabled() {
        EventStreams eventStreams = ModelUtils.createDefaultEventStreams(instanceName).build();
        assertThat(RestProducerModel.isRestProducerEnabled(eventStreams), is(false));
    }
}