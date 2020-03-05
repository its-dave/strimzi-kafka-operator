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

import com.ibm.eventstreams.api.Labels;
import com.ibm.eventstreams.api.Listener;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import com.ibm.eventstreams.api.spec.SecuritySpecBuilder;
import com.ibm.eventstreams.api.spec.ExternalAccess;
import com.ibm.eventstreams.api.spec.ExternalAccessBuilder;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.openshift.api.model.Route;
import io.strimzi.api.kafka.model.template.PodTemplateBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ibm.eventstreams.api.model.AbstractModel.APP_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminProxyModelTest {

    private final String instanceName = "test-instance";
    private final String componentPrefix = instanceName + "-" + APP_NAME + "-" + AdminProxyModel.COMPONENT_NAME;
    private final int defaultReplicas = 1;

    @Mock
    private EventStreamsOperatorConfig.ImageLookup imageConfig;

    private EventStreamsBuilder createDefaultEventStreams() {
        return ModelUtils.createDefaultEventStreams(instanceName)
                .editSpec()
                    .withNewAdminProxy()
                        .withReplicas(defaultReplicas)
                    .endAdminProxy()
                .endSpec();
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

    private AdminProxyModel createDefaultAdminProxyModel() {
        EventStreams eventStreamsResource = createDefaultEventStreams().build();
        return new AdminProxyModel(eventStreamsResource, imageConfig);
    }

    @Test
    public void testDefaultAdminProxyModel() {
        AdminProxyModel adminProxyModel = createDefaultAdminProxyModel();

        Deployment adminProxyDeployment = adminProxyModel.getDeployment();
        assertThat(adminProxyDeployment.getMetadata().getName(), startsWith(componentPrefix));

        assertThat(adminProxyDeployment.getSpec().getReplicas(), is(defaultReplicas));

        Service adminProxyService = adminProxyModel.getService();
        assertThat(adminProxyService.getMetadata().getName(), startsWith(componentPrefix));

        Route adminProxyRoute = adminProxyModel.getRoute();
        assertThat(adminProxyRoute.getMetadata().getName(), startsWith(componentPrefix));
    }

    @Test
    public void testDefaultNetworkPolicy() {
        AdminProxyModel adminProxyModel = createDefaultAdminProxyModel();

        NetworkPolicy networkPolicy = adminProxyModel.getNetworkPolicy();
        assertThat(networkPolicy.getMetadata().getName(), is(componentPrefix));
        assertThat(networkPolicy.getKind(), is("NetworkPolicy"));

        assertThat(networkPolicy.getSpec().getEgress().size(), is(3));

        assertThat(networkPolicy.getSpec().getEgress().get(0).getPorts().size(), is(1));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(0)
            .getPorts()
            .get(0)
            .getPort()
            .getIntVal(), is(Listener.podToPodListener(false).getPort()));
        assertThat(networkPolicy.getSpec().getEgress().get(0).getTo().size(), is(1));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(0)
            .getTo()
            .get(0)
            .getPodSelector()
            .getMatchLabels()
            .size(), is(1));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(0)
            .getTo()
            .get(0)
            .getPodSelector()
            .getMatchLabels()
            .get(Labels.COMPONENT_LABEL), is(AdminApiModel.COMPONENT_NAME));

        assertThat(networkPolicy.getSpec().getEgress().get(1).getPorts().size(), is(1));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(1)
            .getPorts()
            .get(0)
            .getPort()
            .getIntVal(), is(Listener.podToPodListener(false).getPort()));
        assertThat(networkPolicy.getSpec().getEgress().get(1).getTo().size(), is(1));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(1)
            .getTo()
            .get(0)
            .getPodSelector()
            .getMatchLabels()
            .size(), is(1));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(1)
            .getTo()
            .get(0)
            .getPodSelector()
            .getMatchLabels()
            .get(Labels.COMPONENT_LABEL), is(RestProducerModel.COMPONENT_NAME));

        assertThat(networkPolicy.getSpec().getEgress().get(2).getPorts().size(), is(1));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(2)
            .getPorts()
            .get(0)
            .getPort()
            .getIntVal(), is(Listener.podToPodListener(false).getPort()));
        assertThat(networkPolicy.getSpec().getEgress().get(2).getTo().size(), is(1));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(2)
            .getTo()
            .get(0)
            .getPodSelector()
            .getMatchLabels()
            .size(), is(1));
        assertThat(networkPolicy
            .getSpec()
            .getEgress()
            .get(2)
            .getTo()
            .get(0)
            .getPodSelector()
            .getMatchLabels()
            .get(Labels.COMPONENT_LABEL), is(SchemaRegistryModel.COMPONENT_NAME));

        assertThat(networkPolicy.getSpec().getPodSelector().getMatchLabels().size(), is(1));
        assertThat(networkPolicy
            .getSpec()
            .getPodSelector()
            .getMatchLabels()
            .get(Labels.COMPONENT_LABEL), is(AdminProxyModel.COMPONENT_NAME));

        assertThat(networkPolicy.getSpec().getIngress().size(), is(1));
    }

    @Test
    public void testNetworkPolicyEgressWithTLS() {
        EventStreams eventStreams = createDefaultEventStreams()
                .editSpec()
                .withSecurity(new SecuritySpecBuilder().withEncryption(SecuritySpec.Encryption.TLS).build())
                .endSpec()
                .build();
        AdminProxyModel adminProxyModel = new AdminProxyModel(eventStreams, imageConfig);

        NetworkPolicy networkPolicy = adminProxyModel.getNetworkPolicy();
        assertThat(networkPolicy.getMetadata().getName(), is(componentPrefix));
        assertThat(networkPolicy.getKind(), is("NetworkPolicy"));

        assertThat(networkPolicy.getSpec().getEgress().size(), is(3));

        assertThat(networkPolicy.getSpec().getEgress().get(0).getPorts().size(), is(1));
        assertThat(networkPolicy
                .getSpec()
                .getEgress()
                .get(0)
                .getPorts()
                .get(0)
                .getPort()
                .getIntVal(), is(Listener.podToPodListener(true).getPort()));
        assertThat(networkPolicy.getSpec().getEgress().get(0).getTo().size(), is(1));
        assertThat(networkPolicy
                .getSpec()
                .getEgress()
                .get(0)
                .getTo()
                .get(0)
                .getPodSelector()
                .getMatchLabels()
                .size(), is(1));
        assertThat(networkPolicy
                .getSpec()
                .getEgress()
                .get(0)
                .getTo()
                .get(0)
                .getPodSelector()
                .getMatchLabels()
                .get(Labels.COMPONENT_LABEL), is(AdminApiModel.COMPONENT_NAME));

        assertThat(networkPolicy.getSpec().getPodSelector().getMatchLabels().size(), is(1));
        assertThat(networkPolicy
                .getSpec()
                .getPodSelector()
                .getMatchLabels()
                .get(Labels.COMPONENT_LABEL), is(AdminProxyModel.COMPONENT_NAME));

        assertThat(networkPolicy.getSpec().getIngress().size(), is(1));
    }

    @Test
    public void testDefaultConfigMap() {
        AdminProxyModel adminProxyModel = createDefaultAdminProxyModel();

        ConfigMap configMap = adminProxyModel.getConfigMap();
        assertThat(configMap, is(notNullValue()));
        assertThat(configMap.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(configMap.getData().size(), is(1));
    }

    @Test
    public void testDefaultResourceRequirements() {
        AdminProxyModel adminProxyModel = createDefaultAdminProxyModel();

        ResourceRequirements resourceRequirements = adminProxyModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getResources();

        assertThat(resourceRequirements.getRequests().get("cpu").getAmount(), is("500m"));
        assertThat(resourceRequirements.getRequests().get("memory").getAmount(), is("250Mi"));
        assertThat(resourceRequirements.getLimits().get("cpu").getAmount(), is("500m"));
        assertThat(resourceRequirements.getLimits().get("memory").getAmount(), is("250Mi"));
    }

    @Test
    public void testCustomResourceRequirements() {
        ResourceRequirements customResourceRequirements = new ResourceRequirementsBuilder()
                .addToRequests("memory", new Quantity("450Mi"))
                .addToLimits("cpu", new Quantity("100m"))
                .build();
        EventStreams eventStreamsResource = createDefaultEventStreams()
                .editSpec()
                    .editAdminProxy()
                        .withResources(customResourceRequirements)
                    .endAdminProxy()
                .endSpec()
                .build();
        AdminProxyModel adminProxyModel = new AdminProxyModel(eventStreamsResource, imageConfig);

        ResourceRequirements resourceRequirements = adminProxyModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getResources();
        assertThat(resourceRequirements.getRequests().get("cpu").getAmount(), is("500m"));
        assertThat(resourceRequirements.getRequests().get("memory").getAmount(), is("450Mi"));
        assertThat(resourceRequirements.getLimits().get("cpu").getAmount(), is("100m"));
        assertThat(resourceRequirements.getLimits().get("memory").getAmount(), is("250Mi"));
    }

    @Test
    public void testExternalAccessOverrideWithNodePort() {
        EventStreams instance = createDefaultEventStreamsWithExternalAccess(ExternalAccess.TYPE_NODEPORT).build();
        AdminProxyModel adminProxyModelK8s = new AdminProxyModel(instance, imageConfig, false);
        assertThat(adminProxyModelK8s.getService().getSpec().getType(), is("NodePort"));

        AdminProxyModel adminProxyModelOpenShift = new AdminProxyModel(instance, imageConfig, true);
        assertThat(adminProxyModelOpenShift.getService().getSpec().getType(), is("NodePort"));
    }

    @Test
    public void testExternalAccessOverrideWithRoutes() {
        EventStreams instance = createDefaultEventStreamsWithExternalAccess(ExternalAccess.TYPE_ROUTE).build();
        AdminProxyModel adminProxyModelK8s = new AdminProxyModel(instance, imageConfig, false);
        assertThat(adminProxyModelK8s.getService().getSpec().getType(), is("ClusterIP"));

        AdminProxyModel adminProxyModelOpenShift = new AdminProxyModel(instance, imageConfig, true);
        assertThat(adminProxyModelOpenShift.getService().getSpec().getType(), is("ClusterIP"));
    }

    @Test
    public void testDefaultServiceType() {
        EventStreams instance = createDefaultEventStreams().build();
        AdminProxyModel adminProxyModelK8s = new AdminProxyModel(instance, imageConfig, false);
        assertThat(adminProxyModelK8s.getService().getSpec().getType(), is("ClusterIP"));

        AdminProxyModel adminProxyModelOpenShift = new AdminProxyModel(instance, imageConfig, true);
        assertThat(adminProxyModelOpenShift.getService().getSpec().getType(), is("ClusterIP"));
    }

    @Test
    public void testImageOverride() {
        String proxyImage = "proxy-image:latest";

        EventStreams instance = createDefaultEventStreams()
            .editSpec()
                .editAdminProxy()
                    .withImage(proxyImage)
                .endAdminProxy()
            .endSpec()
            .build();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(AdminProxyModel.COMPONENT_NAME, proxyImage);

        List<Container> containers = new AdminProxyModel(instance, imageConfig).getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testOperatorImageOverride() {
        String proxyImage = "proxy-image:latest";

        when(imageConfig.getAdminProxyImage()).thenReturn(Optional.of(proxyImage));

        AdminProxyModel model = createDefaultAdminProxyModel();
        List<Container> containers = model.getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(AdminProxyModel.COMPONENT_NAME, proxyImage);

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testOperatorImageOverrideTakesPrecedenceOverComponentLevelOverride() {
        String proxyImage = "proxy-image:latest";
        String proxyImageFromEnv = "env-proxy-image:latest";

        when(imageConfig.getAdminProxyImage()).thenReturn(Optional.of(proxyImageFromEnv));

        EventStreams instance = createDefaultEventStreams()
                .editSpec()
                    .editAdminProxy()
                        .withImage(proxyImage)
                    .endAdminProxy()
                .endSpec()
                .build();

        AdminProxyModel adminProxyModel = new AdminProxyModel(instance, imageConfig);
        assertThat(adminProxyModel.getImage(), is(proxyImage));
        assertTrue(adminProxyModel.getCustomImage());

        List<Container> containers = adminProxyModel.getDeployment().getSpec().getTemplate().getSpec().getContainers();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(AdminProxyModel.COMPONENT_NAME, proxyImage);

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testPodServiceAccountContainsUserSuppliedPullSecret() {

        EventStreamsBuilder defaultEs = createDefaultEventStreams();
        LocalObjectReference imagePullSecretOverride = new LocalObjectReferenceBuilder()
            .withName("component-test-image")
            .build();

        EventStreams eventStreams = defaultEs
            .editSpec()
                .editAdminProxy()
                    .editOrNewTemplate()
                        .withPod(new PodTemplateBuilder()
                                        .withImagePullSecrets(imagePullSecretOverride)
                                        .build()
                        )
                    .endTemplate()
                .endAdminProxy()
            .endSpec()
            .build();

        assertThat(new AdminProxyModel(eventStreams, imageConfig).getServiceAccount()
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

        assertThat(new AdminProxyModel(eventStreams, imageConfig).getServiceAccount()
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
                .editAdminProxy()
                    .withNewTemplate()
                        .withPod(new PodTemplateBuilder().withImagePullSecrets(componentPullSecretOverride).build())
                    .endTemplate()
                .endAdminProxy()
            .endSpec()
            .build();

        assertThat(new AdminProxyModel(eventStreams, imageConfig).getServiceAccount()
                        .getImagePullSecrets(), containsInAnyOrder(globalPullSecretOverride, componentPullSecretOverride));
    }

    @Test
    public void testConfigMapAndVolumeMatch() {
        AdminProxyModel adminProxyModel = createDefaultAdminProxyModel();
        Volume adminProxyVolume = adminProxyModel.getDeployment().getSpec().getTemplate().getSpec().getVolumes().get(0);
        assertThat(adminProxyModel.getConfigMap().getMetadata().getName(), is(adminProxyVolume.getConfigMap().getName()));
    }

    @Test
    public void testCreateAdminProxyRouteWithTlsEncryption() {
        EventStreams eventStreams = createDefaultEventStreams()
                .editSpec()
                .withSecurity(new SecuritySpecBuilder().withEncryption(SecuritySpec.Encryption.TLS).build())
                .endSpec()
                .build();

        assertThat(new AdminProxyModel(eventStreams, imageConfig).getRoute().getSpec().getTls().getTermination(), is("passthrough"));
    }
}