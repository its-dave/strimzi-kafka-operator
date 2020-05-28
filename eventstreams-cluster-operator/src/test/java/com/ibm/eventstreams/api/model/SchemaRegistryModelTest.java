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

import com.ibm.commonservices.CommonServices;
import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.model.utils.CustomMatchers;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.NodeSelectorRequirement;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PreferredSchedulingTerm;
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
import io.strimzi.api.kafka.model.ExternalLogging;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.storage.EphemeralStorageBuilder;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorage;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorageBuilder;
import io.strimzi.api.kafka.model.template.PodTemplateBuilder;
import io.strimzi.operator.common.model.Labels;
import org.hamcrest.collection.IsMapContaining;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SchemaRegistryModelTest {

    private final String instanceName = "test-instance";
    private final String componentPrefix = instanceName + "-" + AbstractModel.APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME;
    private final int defaultReplicas = 1;
    private final CommonServices mockCommonServices = new CommonServices(instanceName, ModelUtils.mockCommonServicesClusterData());
    private final String kafkaPrincipal = InternalKafkaUserModel.getInternalKafkaUserName(instanceName);

    @Mock
    private EventStreamsOperatorConfig.ImageLookup imageConfig;

    private EventStreamsBuilder createDefaultEventStreams() {
        return ModelUtils.createDefaultEventStreams(instanceName)
                .editSpec()
                    .withNewSchemaRegistry()
                        .withReplicas(defaultReplicas)
                        .withNewAvro()
                        .endAvro()
                        .withNewProxy()
                        .endProxy()
                        .withStorage(new EphemeralStorageBuilder().build())
                    .endSchemaRegistry()
                .endSpec();
    }

    private EventStreamsBuilder createEventStreamsWithAuthorization() {
        return ModelUtils.createEventStreamsWithAuthorization(instanceName)
            .editSpec()
            .withNewSchemaRegistry()
            .withReplicas(defaultReplicas)
            .withNewAvro()
            .endAvro()
            .withNewProxy()
            .endProxy()
            .withStorage(new EphemeralStorageBuilder().build())
            .endSchemaRegistry()
            .endSpec();
    }

    private SchemaRegistryModel createDefaultSchemaRegistryModel() {
        EventStreams eventStreamsResource = createDefaultEventStreams().build();
        return new SchemaRegistryModel(eventStreamsResource, imageConfig, null, mockCommonServices, kafkaPrincipal);
    }

    @Test
    public void testDefaultBuilder() {
        SchemaRegistryModel schemaRegistryModel = createDefaultSchemaRegistryModel();

        Deployment schemaRegistryDeployment = schemaRegistryModel.getDeployment();
        assertThat(schemaRegistryDeployment.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(schemaRegistryDeployment.getSpec().getReplicas(), is(defaultReplicas));

        Service schemaRegistryInternalService = schemaRegistryModel.getSecurityService(EndpointServiceType.INTERNAL);
        assertThat(schemaRegistryInternalService.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(schemaRegistryInternalService.getMetadata().getName(), endsWith(AbstractSecureEndpointsModel.INTERNAL_SERVICE_SUFFIX));

        Service schemaRegistryExternalService = schemaRegistryModel.getSecurityService(EndpointServiceType.ROUTE);
        assertThat(schemaRegistryExternalService.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(schemaRegistryExternalService.getMetadata().getName(), endsWith(AbstractSecureEndpointsModel.ROUTE_SERVICE_SUFFIX));

        NetworkPolicy schemaRegistryNetworkPolicy = schemaRegistryModel.getNetworkPolicy();
        assertThat(schemaRegistryNetworkPolicy.getMetadata().getName(), is(componentPrefix));
        assertThat(schemaRegistryNetworkPolicy.getKind(), is("NetworkPolicy"));

        assertThat(schemaRegistryNetworkPolicy.getSpec().getIngress().size(), is(schemaRegistryModel.getEndpoints().size()));

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

        assertThat(schemaRegistryNetworkPolicy.getSpec().getIngress(), CustomMatchers.containsIngressRulesInAnyOrder(defaultEndpointRule, defaultP2PIngressRule));

        assertThat(schemaRegistryNetworkPolicy.getSpec().getEgress().size(), is(0));

        assertThat(schemaRegistryNetworkPolicy.getSpec().getPodSelector().getMatchLabels(), allOf(
                aMapWithSize(2),
                hasEntry(Labels.KUBERNETES_NAME_LABEL, SchemaRegistryModel.APPLICATION_NAME),
                hasEntry(Labels.KUBERNETES_INSTANCE_LABEL, instanceName)
            ));

        Map<String, Route> schemaRegistryRoutes = schemaRegistryModel.getRoutes();
        schemaRegistryRoutes.forEach((key, route) -> {
            assertThat(route.getMetadata().getName(), startsWith(componentPrefix));
            assertThat(route.getMetadata().getName(), containsString(key));
        });
    }

    @Test
    public void testDefaultResourceRequirements() {
        SchemaRegistryModel schemaRegistryModel = createDefaultSchemaRegistryModel();
        List<Container> containerList = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers();
        containerList.forEach(container -> {
            assertThat(container.getResources().getRequests().get("cpu").getAmount(), is("500m"));
            assertThat(container.getResources().getLimits().get("cpu").getAmount(), is("500m"));
            if (container.getName().equals(SchemaRegistryModel.COMPONENT_NAME)) {
                assertThat(container.getResources().getRequests().get("memory").getAmount(), is("256Mi"));
                assertThat(container.getResources().getLimits().get("memory").getAmount(), is("256Mi"));
            } else {
                assertThat(container.getResources().getRequests().get("memory").getAmount(), is("1Gi"));
                assertThat(container.getResources().getLimits().get("memory").getAmount(), is("1Gi"));
            }
        });
    }

    @Test
    public void testCustomResourceRequirements() {
        ResourceRequirements customResourceRequirements = new ResourceRequirementsBuilder()
                .addToRequests("memory", new Quantity("450Mi"))
                .addToLimits("cpu", new Quantity("100m"))
                .build();
        ResourceRequirements customAvroResourceRequirements = new ResourceRequirementsBuilder()
                .addToRequests("cpu", new Quantity("100m"))
                .addToLimits("memory", new Quantity("50Mi"))
                .build();
        ResourceRequirements customSchemaProxyResourceRequirements = new ResourceRequirementsBuilder()
                .addToRequests("cpu", new Quantity("150m"))
                .addToLimits("memory", new Quantity("70Mi"))
                .build();

        EventStreams eventStreamsResource = createDefaultEventStreams()
                .editSpec()
                    .editSchemaRegistry()
                        .withResources(customResourceRequirements)
                        .editAvro()
                            .withResources(customAvroResourceRequirements)
                        .endAvro()
                        .editProxy()
                            .withResources(customSchemaProxyResourceRequirements)
                        .endProxy()
                    .endSchemaRegistry()
                .endSpec()
                .build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(eventStreamsResource, Mockito.mock(
            EventStreamsOperatorConfig.ImageLookup.class), null, mockCommonServices, kafkaPrincipal);

        List<Container> containerList = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers();

        Map<String, ResourceRequirements> resourceRequirements = containerList.stream()
                .collect(Collectors.toMap(Container::getName, Container::getResources));


        ResourceRequirements schemaResources = resourceRequirements.get(SchemaRegistryModel.COMPONENT_NAME);
        assertThat(schemaResources.getRequests().get("cpu").getAmount(), is("500m"));
        assertThat(schemaResources.getRequests().get("memory").getAmount(), is("450Mi"));
        assertThat(schemaResources.getLimits().get("cpu").getAmount(), is("100m"));
        assertThat(schemaResources.getLimits().get("memory").getAmount(), is("256Mi"));

        ResourceRequirements avroResources = resourceRequirements.get(SchemaRegistryModel.AVRO_SERVICE_CONTAINER_NAME);
        assertThat(avroResources.getRequests().get("cpu").getAmount(), is("100m"));
        assertThat(avroResources.getRequests().get("memory").getAmount(), is("1Gi"));
        assertThat(avroResources.getLimits().get("cpu").getAmount(), is("500m"));
        assertThat(avroResources.getLimits().get("memory").getAmount(), is("50Mi"));

        ResourceRequirements schemaProxyResources = resourceRequirements.get(SchemaRegistryModel.SCHEMA_REGISTRY_PROXY_CONTAINER_NAME);
        assertThat(schemaProxyResources.getRequests().get("cpu").getAmount(), is("150m"));
        assertThat(schemaProxyResources.getRequests().get("memory").getAmount(), is("1Gi"));
        assertThat(schemaProxyResources.getLimits().get("cpu").getAmount(), is("500m"));
        assertThat(schemaProxyResources.getLimits().get("memory").getAmount(), is("70Mi"));
    }

    @Test
    public void testImageOverride() {
        String schemaImage = "schema-image:latest";
        String avroImage = "avro-image:latest";
        String schemaRegistryProxyImage = "schema-proxy-image:latest";

        EventStreams instance = createDefaultEventStreams()
                .editSpec()
                    .editSchemaRegistry()
                        .withImage(schemaImage)
                        .withNewAvro()
                            .withImage(avroImage)
                        .endAvro()
                        .withNewProxy()
                            .withImage(schemaRegistryProxyImage)
                        .endProxy()
                    .endSchemaRegistry()
                .endSpec()
                .build();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(SchemaRegistryModel.COMPONENT_NAME, schemaImage);
        expectedImages.put(SchemaRegistryModel.AVRO_SERVICE_CONTAINER_NAME, avroImage);
        expectedImages.put(SchemaRegistryModel.SCHEMA_REGISTRY_PROXY_CONTAINER_NAME, schemaRegistryProxyImage);

        List<Container> containers = new SchemaRegistryModel(instance, imageConfig, null, mockCommonServices, kafkaPrincipal).getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testOperatorImageOverride() {
        String schemaImage = "component-schema-image:latest";
        String avroImage = "component-avro-image:latest";
        String proxyImage = "component-schema-proxy-image:latest";

        when(imageConfig.getSchemaRegistryImage()).thenReturn(Optional.of(schemaImage));
        when(imageConfig.getSchemaRegistryAvroImage()).thenReturn(Optional.of(avroImage));
        when(imageConfig.getSchemaRegistryProxyImage()).thenReturn(Optional.of(proxyImage));

        SchemaRegistryModel model = createDefaultSchemaRegistryModel();
        List<Container> containers = model.getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(SchemaRegistryModel.COMPONENT_NAME, schemaImage);
        expectedImages.put(SchemaRegistryModel.AVRO_SERVICE_CONTAINER_NAME, avroImage);
        expectedImages.put(SchemaRegistryModel.SCHEMA_REGISTRY_PROXY_CONTAINER_NAME, proxyImage);

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testOperatorImageOverrideTakesPrecedenceOverComponentLevelOverride() {
        String schemaImage = "component-schema-image:latest";
        String avroImage = "component-avro-image:latest";
        String proxyImage = "component-proxy-image:latest";

        String schemaImageFromEnv = "env-schema-image:latest";
        String avroImageFromEnv = "env-avro-image:latest";
        String proxyImageFromEnv = "env-proxy-image:latest";

        when(imageConfig.getSchemaRegistryImage()).thenReturn(Optional.of(schemaImageFromEnv));
        when(imageConfig.getSchemaRegistryAvroImage()).thenReturn(Optional.of(avroImageFromEnv));
        when(imageConfig.getSchemaRegistryProxyImage()).thenReturn(Optional.of(proxyImageFromEnv));
        EventStreams instance = createDefaultEventStreams()
                .editSpec()
                    .editSchemaRegistry()
                        .withImage(schemaImage)
                        .withNewAvro()
                            .withImage(avroImage)
                        .endAvro()
                        .withNewProxy()
                            .withImage(proxyImage)
                        .endProxy()
                    .endSchemaRegistry()
                .endSpec()
                .build();

        List<Container> containers = new SchemaRegistryModel(instance, imageConfig, null, mockCommonServices, kafkaPrincipal).getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(SchemaRegistryModel.COMPONENT_NAME, schemaImage);
        expectedImages.put(SchemaRegistryModel.AVRO_SERVICE_CONTAINER_NAME, avroImage);
        expectedImages.put(SchemaRegistryModel.SCHEMA_REGISTRY_PROXY_CONTAINER_NAME, proxyImage);

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testPodServiceAccountContainsUserSuppliedPullSecret() {

        EventStreamsBuilder defaultEs = createDefaultEventStreams();
        LocalObjectReference imagePullSecretOverride = new LocalObjectReferenceBuilder()
            .withName("component-image-secret")
            .build();

        EventStreams eventStreams = defaultEs
            .editSpec()
                .editSchemaRegistry()
                    .editOrNewTemplate()
                        .withPod(new PodTemplateBuilder()
                                        .withImagePullSecrets(imagePullSecretOverride)
                                        .build()
                        )
                    .endTemplate()
                .endSchemaRegistry()
            .endSpec()
            .build();

        assertThat(new SchemaRegistryModel(eventStreams, imageConfig, null, mockCommonServices, kafkaPrincipal).getServiceAccount()
                        .getImagePullSecrets(), contains(imagePullSecretOverride));
    }

    @Test
    public void testPodServiceAccountContainsGlobalSuppliedPullSecret() {

        EventStreamsBuilder defaultEs = createDefaultEventStreams();
        LocalObjectReference imagePullSecretOverride = new LocalObjectReferenceBuilder()
            .withName("global-image-secret")
            .build();

        EventStreams eventStreams = defaultEs
            .editSpec()
                .withNewImages()
                    .withPullSecrets(imagePullSecretOverride)
                .endImages()
            .endSpec()
            .build();

        assertThat(new SchemaRegistryModel(eventStreams, imageConfig, null, mockCommonServices, kafkaPrincipal).getServiceAccount()
                        .getImagePullSecrets(), contains(imagePullSecretOverride));
    }

    @Test
    public void testOperatorImagePullSecretOverride() {
        LocalObjectReference imagePullSecret = new LocalObjectReferenceBuilder()
                .withName("operator-image-pull-secret")
                .build();
        when(imageConfig.getPullSecrets()).thenReturn(Collections.singletonList(imagePullSecret));

        assertThat(createDefaultSchemaRegistryModel().getServiceAccount().getImagePullSecrets(),
                   contains(imagePullSecret));
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
                .editSchemaRegistry()
                    .withNewTemplate()
                        .withPod(new PodTemplateBuilder().withImagePullSecrets(componentPullSecretOverride).build())
                    .endTemplate()
                .endSchemaRegistry()
            .endSpec()
            .build();

        assertThat(new SchemaRegistryModel(eventStreams, imageConfig, null, mockCommonServices, kafkaPrincipal).getServiceAccount()
                        .getImagePullSecrets(), containsInAnyOrder(globalPullSecretOverride, componentPullSecretOverride));
    }

    @Test
    public void testSchemaRegistryProxyAuthenticationEnvVarsSetWithNoAuth() {
        EventStreams defaultEs = createDefaultEventStreams().build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(defaultEs, imageConfig, null, mockCommonServices, kafkaPrincipal);

        EnvVar authentication = new EnvVarBuilder().withName("AUTHENTICATION").withValue("9443:runas-anonymous,7443:runas-anonymous").build();
        EnvVar endpoints = new EnvVarBuilder().withName("ENDPOINTS").withValue("9443:external,7443:p2ptls").build();
        EnvVar tlsVersion = new EnvVarBuilder().withName("TLS_VERSION").withValue("9443:TLSv1.2,7443:TLSv1.2").build();
        EnvVar authEnabled  = new EnvVarBuilder().withName("AUTHORIZATION_ENABLED").withValue("false").build();

        List<EnvVar> envVars = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().stream().filter(container -> SchemaRegistryModel.SCHEMA_REGISTRY_PROXY_CONTAINER_NAME.equals(container.getName())).findFirst().get().getEnv();

        assertThat(envVars, hasItems(authentication, endpoints, tlsVersion, authEnabled));
    }

    @Test
    public void testSchemaRegistryProxyAuthenticationEnvVarsWithAuth() {
        EventStreams defaultEs = createEventStreamsWithAuthorization().build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(defaultEs, imageConfig, null, mockCommonServices, kafkaPrincipal);

        EnvVar authentication = new EnvVarBuilder().withName("AUTHENTICATION").withValue("9443:iam-bearer;tls;scram-sha-512,7443:iam-bearer;mac").build();
        EnvVar endpoints = new EnvVarBuilder().withName("ENDPOINTS").withValue("9443:external,7443:p2ptls").build();
        EnvVar tlsVersion = new EnvVarBuilder().withName("TLS_VERSION").withValue("9443:TLSv1.2,7443:TLSv1.2").build();
        EnvVar authEnabled  = new EnvVarBuilder().withName("AUTHORIZATION_ENABLED").withValue("true").build();

        List<EnvVar> envVars = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().stream().filter(container -> SchemaRegistryModel.SCHEMA_REGISTRY_PROXY_CONTAINER_NAME.equals(container.getName())).findFirst().get().getEnv();

        assertThat(envVars, hasItems(authentication, endpoints, tlsVersion, authEnabled));
    }

    @Test
    public void testDefaultLogging() {
        EventStreams defaultEs = createDefaultEventStreams().build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(defaultEs, imageConfig, null, mockCommonServices, kafkaPrincipal);

        EnvVar expectedEnvVar = new EnvVarBuilder()
                .withName(SchemaRegistryModel.LOG_LEVEL_ENV_NAME)
                .withValue("INFO")
                .build();
        List<EnvVar> envVars = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().stream().filter(container -> SchemaRegistryModel.COMPONENT_NAME.equals(container.getName())).findFirst().get().getEnv();

        assertThat(envVars, hasItem(expectedEnvVar));
    }

    @Test
    public void testOverrideLoggingInLine() {
        Map<String, String> loggers = new HashMap<>();
        loggers.put("logger.one", "DEBUG");
        loggers.put("logger.two", "TRACE");
        InlineLogging logging = new InlineLogging();
        logging.setLoggers(loggers);

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                    .editSchemaRegistry()
                        .withLogging(logging)
                    .endSchemaRegistry()
                .endSpec()
                .build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(defaultEs, imageConfig, null, mockCommonServices, kafkaPrincipal);

        EnvVar expectedEnvVar = new EnvVarBuilder()
                .withName(SchemaRegistryModel.LOG_LEVEL_ENV_NAME)
                .withValue("DEBUG")
                .build();
        List<EnvVar> envVars = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().stream().filter(container -> SchemaRegistryModel.COMPONENT_NAME.equals(container.getName())).findFirst().get().getEnv();

        assertThat(envVars, hasItem(expectedEnvVar));
    }

    @Test
    public void testUsesDefaultLoggingIfNoLoggers() {
        InlineLogging logging = new InlineLogging();

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                    .editSchemaRegistry()
                        .withLogging(logging)
                    .endSchemaRegistry()
                .endSpec()
                .build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(defaultEs, imageConfig, null, mockCommonServices, kafkaPrincipal);

        EnvVar expectedEnvVar = new EnvVarBuilder()
                .withName(SchemaRegistryModel.LOG_LEVEL_ENV_NAME)
                .withValue("INFO")
                .build();
        List<EnvVar> envVars = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().stream().filter(container -> SchemaRegistryModel.COMPONENT_NAME.equals(container.getName())).findFirst().get().getEnv();

        assertThat(envVars, hasItem(expectedEnvVar));
    }

    @Test
    public void testOverrideLoggingExternalIsIgnored() {
        ExternalLogging logging = new ExternalLogging();

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                    .editSchemaRegistry()
                        .withLogging(logging)
                    .endSchemaRegistry()
                .endSpec()
                .build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(defaultEs, imageConfig, null, mockCommonServices, kafkaPrincipal);

        EnvVar expectedEnvVar = new EnvVarBuilder()
                .withName(SchemaRegistryModel.LOG_LEVEL_ENV_NAME)
                .withValue("INFO")
                .build();
        List<EnvVar> envVars = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().stream().filter(container -> SchemaRegistryModel.COMPONENT_NAME.equals(container.getName())).findFirst().get().getEnv();

        assertThat(envVars, hasItem(expectedEnvVar));
    }

    @Test
    public void testAvroDefaultLogging() {
        EventStreams defaultEs = createDefaultEventStreams().build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(defaultEs, imageConfig, null, mockCommonServices, kafkaPrincipal);

        EnvVar expectedEnvVar = new EnvVarBuilder()
                .withName(SchemaRegistryModel.AVRO_LOG_LEVEL_ENV_NAME)
                .withValue("info")
                .build();
        List<EnvVar> envVars = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().stream().filter(container -> SchemaRegistryModel.AVRO_SERVICE_CONTAINER_NAME.equals(container.getName())).findFirst().get().getEnv();

        assertThat(envVars, hasItem(expectedEnvVar));
    }

    @Test
    public void testOverrideAvroLoggingInLine() {
        Map<String, String> loggers = new HashMap<>();
        loggers.put("logger.one", "TRACE");
        loggers.put("logger.two", "INFO");
        InlineLogging logging = new InlineLogging();
        logging.setLoggers(loggers);

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                    .editSchemaRegistry()
                        .editAvro()
                            .withLogging(logging)
                        .endAvro()
                    .endSchemaRegistry()
                .endSpec()
                .build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(defaultEs, imageConfig, null, mockCommonServices, kafkaPrincipal);

        EnvVar expectedEnvVar = new EnvVarBuilder()
                .withName(SchemaRegistryModel.AVRO_LOG_LEVEL_ENV_NAME)
                .withValue("TRACE")
                .build();
        List<EnvVar> envVars = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().stream().filter(container -> SchemaRegistryModel.AVRO_SERVICE_CONTAINER_NAME.equals(container.getName())).findFirst().get().getEnv();

        assertThat(envVars, hasItem(expectedEnvVar));
    }

    @Test
    public void testUsesAvroDefaultLoggingIfNoLoggers() {
        InlineLogging logging = new InlineLogging();

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                    .editSchemaRegistry()
                        .editAvro()
                            .withLogging(logging)
                        .endAvro()
                    .endSchemaRegistry()
                .endSpec()
                .build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(defaultEs, imageConfig, null, mockCommonServices, kafkaPrincipal);

        EnvVar expectedEnvVar = new EnvVarBuilder()
                .withName(SchemaRegistryModel.AVRO_LOG_LEVEL_ENV_NAME)
                .withValue("info")
                .build();
        List<EnvVar> envVars = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().stream().filter(container -> SchemaRegistryModel.AVRO_SERVICE_CONTAINER_NAME.equals(container.getName())).findFirst().get().getEnv();

        assertThat(envVars, hasItem(expectedEnvVar));
    }

    @Test
    public void testOverrideAvroLoggingExternalIsIgnored() {
        ExternalLogging logging = new ExternalLogging();

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                    .editSchemaRegistry()
                        .editAvro()
                            .withLogging(logging)
                        .endAvro()
                    .endSchemaRegistry()
                .endSpec()
                .build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(defaultEs, imageConfig, null, mockCommonServices, kafkaPrincipal);

        EnvVar expectedEnvVar = new EnvVarBuilder()
                .withName(SchemaRegistryModel.AVRO_LOG_LEVEL_ENV_NAME)
                .withValue("info")
                .build();
        List<EnvVar> envVars = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().stream().filter(container -> SchemaRegistryModel.AVRO_SERVICE_CONTAINER_NAME.equals(container.getName())).findFirst().get().getEnv();

        assertThat(envVars, hasItem(expectedEnvVar));
    }

    @Test
    public void testCreateSchemaRegistryRouteWithTlsEncryption() {
        EventStreams eventStreams = createDefaultEventStreams().build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(eventStreams, imageConfig, null, mockCommonServices, kafkaPrincipal);
        String expectedRouteName = instanceName + "-" + AbstractModel.APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME + "-" + Endpoint.DEFAULT_EXTERNAL_NAME;
        assertThat(schemaRegistryModel.getRoutes(), IsMapContaining.hasKey(expectedRouteName));
        assertThat(schemaRegistryModel.getRoutes().get(expectedRouteName).getSpec().getTls().getTermination(),  is("passthrough"));
    }

    @Test
    public void testGenerationIdLabelOnDeployment() {
        EventStreams eventStreams = createDefaultEventStreams().build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(eventStreams, imageConfig, null, mockCommonServices, kafkaPrincipal);

        assertThat(schemaRegistryModel.generateDeployment("newID", eventStreams).getMetadata().getLabels().containsKey(AbstractSecureEndpointsModel.CERT_GENERATION_KEY), is(true));
        assertThat(schemaRegistryModel.generateDeployment("newID", eventStreams).getMetadata().getLabels().get(AbstractSecureEndpointsModel.CERT_GENERATION_KEY), is("newID"));
        assertThat(schemaRegistryModel.generateDeployment("newID", eventStreams).getSpec().getTemplate().getMetadata().getLabels().containsKey(AbstractSecureEndpointsModel.CERT_GENERATION_KEY), is(true));
        assertThat(schemaRegistryModel.generateDeployment("newID", eventStreams).getSpec().getTemplate().getMetadata().getLabels().get(AbstractSecureEndpointsModel.CERT_GENERATION_KEY), is("newID"));
    }

    @Test
    public void testVolumeMounts() {
        SchemaRegistryModel schemaRegistryModel = createDefaultSchemaRegistryModel();

        List<VolumeMount> volumeMounts = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts();

        assertThat(volumeMounts.size(), is(2));

        assertThat(volumeMounts.get(0).getName(), is(SchemaRegistryModel.TEMP_DIR_NAME));
        assertThat(volumeMounts.get(0).getMountPath(), is("/var/lib/tmp"));

        assertThat(volumeMounts.get(1).getName(), is(SchemaRegistryModel.SHARED_VOLUME_MOUNT_NAME));
        assertThat(volumeMounts.get(1).getMountPath(), is("/var/lib/schemas"));

        // Test mounts for proxy
        List<VolumeMount> volumeMountsProxy = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(2).getVolumeMounts();

        assertThat(volumeMountsProxy.size(), is(7));

        assertThat(volumeMountsProxy.get(0).getName(), is("hmac-secret"));
        assertThat(volumeMountsProxy.get(0).getReadOnly(), is(true));
        assertThat(volumeMountsProxy.get(0).getMountPath(), is("/env/hmac"));

        assertThat(volumeMountsProxy.get(1).getName(), is(AbstractSecureEndpointsModel.CERTS_VOLUME_MOUNT_NAME));
        assertThat(volumeMountsProxy.get(1).getReadOnly(), is(true));
        assertThat(volumeMountsProxy.get(1).getMountPath(), is(AbstractSecureEndpointsModel.CERTIFICATE_PATH));

        assertThat(volumeMountsProxy.get(2).getName(), is(AbstractSecureEndpointsModel.CLUSTER_CA_VOLUME_MOUNT_NAME));
        assertThat(volumeMountsProxy.get(2).getReadOnly(), is(true));
        assertThat(volumeMountsProxy.get(2).getMountPath(), is(AbstractSecureEndpointsModel.CLUSTER_CERTIFICATE_PATH));

        assertThat(volumeMountsProxy.get(3).getName(), is(AbstractSecureEndpointsModel.CLIENT_CA_VOLUME_MOUNT_NAME));
        assertThat(volumeMountsProxy.get(3).getReadOnly(), is(true));
        assertThat(volumeMountsProxy.get(3).getMountPath(), is(AbstractSecureEndpointsModel.CLIENT_CA_CERTIFICATE_PATH));

        assertThat(volumeMountsProxy.get(4).getName(), is(AbstractSecureEndpointsModel.KAFKA_USER_SECRET_VOLUME_NAME));
        assertThat(volumeMountsProxy.get(4).getReadOnly(), is(true));
        assertThat(volumeMountsProxy.get(4).getMountPath(), is(AbstractSecureEndpointsModel.KAFKA_USER_CERTIFICATE_PATH));

        assertThat(volumeMountsProxy.get(5).getName(), is(CommonServices.IBMCLOUD_CA_VOLUME_MOUNT_NAME));
        assertThat(volumeMountsProxy.get(5).getReadOnly(), is(true));
        assertThat(volumeMountsProxy.get(5).getMountPath(), is(CommonServices.IBMCLOUD_CA_CERTIFICATE_PATH));

        assertThat(volumeMountsProxy.get(6).getName(), is("oidc-secret"));
        assertThat(volumeMountsProxy.get(6).getReadOnly(), is(true));
        assertThat(volumeMountsProxy.get(6).getMountPath(), is("/env/commonServices"));
    }

    @Test
    public void testVolumes() {
        SchemaRegistryModel schemaRegistryModel = createDefaultSchemaRegistryModel();

        List<Volume> volumes = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getVolumes();

        assertThat(volumes.size(), is(9));

        assertThat(volumes.get(0).getName(), is(SchemaRegistryModel.TEMP_DIR_NAME));
        assertThat(volumes.get(1).getName(), is(CommonServices.IBMCLOUD_CA_VOLUME_MOUNT_NAME));
        assertThat(volumes.get(2).getName(), is("oidc-secret"));
        assertThat(volumes.get(3).getName(), is(SchemaRegistryModel.SHARED_VOLUME_MOUNT_NAME));
        assertThat(volumes.get(4).getName(), is(AbstractSecureEndpointsModel.CERTS_VOLUME_MOUNT_NAME));
        assertThat(volumes.get(5).getName(), is(AbstractSecureEndpointsModel.CLUSTER_CA_VOLUME_MOUNT_NAME));
        assertThat(volumes.get(6).getName(), is(AbstractSecureEndpointsModel.CLIENT_CA_VOLUME_MOUNT_NAME));
        assertThat(volumes.get(7).getName(), is(AbstractSecureEndpointsModel.KAFKA_USER_SECRET_VOLUME_NAME));
        assertThat(volumes.get(8).getName(), is("hmac-secret"));

    }

    @Test
    public void testCreateSchemaRegistryPersistentVolumeClaimWithDeleteClaim() {
    
        PersistentClaimStorage storage = new PersistentClaimStorageBuilder()
            .withDeleteClaim(true)
            .build();
        
        EventStreams eventStreams = createDefaultEventStreams()
            .editOrNewSpec()
                .editOrNewSchemaRegistry()
                    .withStorage(storage)
                .endSchemaRegistry()
            .endSpec()
            .build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(eventStreams, imageConfig, null, mockCommonServices, kafkaPrincipal);

        PersistentVolumeClaim pvc = schemaRegistryModel.getPersistentVolumeClaim();
        assertThat("Owner Reference should be empty by default so that pvcs are not deleted",
                pvc.getMetadata().getOwnerReferences(),
                is(Collections.singletonList(schemaRegistryModel.getEventStreamsOwnerReference())));
    }

    @Test
    public void testCreatePersistentVolumeClaimWithValidStorage() {

        final String storageClass = "a-storage-class";
        final String size = "some-size";
        Map<String, String> selector = new HashMap<>();
        selector.put("key", "value");

        PersistentClaimStorage storage = new PersistentClaimStorageBuilder()
                .withNewStorageClass(storageClass)
                .withNewSize(size)
                .addToSelector(selector)
                .build();

        Map<String, Quantity> expectedStorageRequest = new HashMap<String, Quantity>();
        expectedStorageRequest.put("storage", new Quantity(size));

        EventStreams eventStreams = createDefaultEventStreams()
            .editOrNewSpec()
                .editOrNewSchemaRegistry()
                    .withStorage(storage)
                .endSchemaRegistry()
            .endSpec()
            .build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(eventStreams, imageConfig, null, mockCommonServices, kafkaPrincipal);

        PersistentVolumeClaim pvc = schemaRegistryModel.getPersistentVolumeClaim();

        assertThat(pvc.getSpec().getStorageClassName(), is(storageClass));
        assertThat(pvc.getSpec().getResources().getRequests(), is(expectedStorageRequest));
        assertThat(pvc.getSpec().getSelector(), is(new LabelSelector(new ArrayList<>(), selector)));
        assertThat("Owner Reference should be empty by default so that pvcs are not deleted",
                pvc.getMetadata().getOwnerReferences(), is(new ArrayList<>()));
    }

    @Test
    public void testWhenICPClusterDataEnvironmentVariablesSet() {
        String ingressEndpoint = "https://ingress-endpoint.cs-ns.svc:443";
        String clusterName = "test-cluster";
        Map<String, String> data = new HashMap<>();
        data.put("cluster_name", clusterName);
        data.put("cluster_endpoint", ingressEndpoint);
        data.put("cluster_address", "consoleHost");
        data.put("cluster_router_https_port", "443");

        CommonServices commonServices = new CommonServices(instanceName, data);

        EventStreams eventStreams = createDefaultEventStreams().build();
        SchemaRegistryModel schemaRegistryModel = new SchemaRegistryModel(eventStreams, imageConfig, null, commonServices, kafkaPrincipal);

        EnvVar expectedEnvVarIAMClusterName = new EnvVarBuilder().withName("IAM_CLUSTER_NAME").withValue(clusterName).build();
        EnvVar expectedEnvVarIAMClusterEndpoint = new EnvVarBuilder().withName("IAM_SERVER_URL").withValue(ingressEndpoint).build();
        EnvVar expectedEnvVarKafkaPrincipal = new EnvVarBuilder().withName("KAFKA_PRINCIPAL").withValue(kafkaPrincipal).build();

        List<EnvVar> actualSchemaProxyEnvVars = schemaRegistryModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(2).getEnv();
        System.out.println(actualSchemaProxyEnvVars);
        assertThat(actualSchemaProxyEnvVars, hasItems(expectedEnvVarIAMClusterName, expectedEnvVarIAMClusterEndpoint, expectedEnvVarKafkaPrincipal));
    }

    @Test
    public void testDefaultLabelsAndAnnotations() {
        SchemaRegistryModel schemaRegistry = createDefaultSchemaRegistryModel();

        Map<String, String> computedAnnotations = schemaRegistry.getDeployment().getSpec().getTemplate().getMetadata().getAnnotations();
        ModelUtils.assertMeteringAnnotationsPresent(computedAnnotations);

        Map<String, String> computedLabels = schemaRegistry.getDeployment().getSpec().getTemplate().getMetadata().getLabels();
        ModelUtils.assertEventStreamsLabelsPresent(computedLabels);
    }

    @Test
    public void testCustomAnnotations() {
        Map<String, String> customAnnotations = new HashMap<>();
        customAnnotations.put("mycustomannotation", "alpha");
        customAnnotations.put("multipleannotations", "beta");
        customAnnotations.put("finalannotation", "delta");

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                .editSchemaRegistry()
                .withNewTemplate()
                .withPod(new PodTemplateBuilder()
                        .withNewMetadata()
                        .withAnnotations(customAnnotations)
                        .endMetadata()
                        .build())
                .endTemplate()
                .endSchemaRegistry()
                .endSpec()
                .build();
        SchemaRegistryModel schemaRegistry = new SchemaRegistryModel(defaultEs, imageConfig, null, mockCommonServices, kafkaPrincipal);

        Map<String, String> computedAnnotations = schemaRegistry.getDeployment().getSpec().getTemplate().getMetadata().getAnnotations();
        ModelUtils.assertMeteringAnnotationsPresent(computedAnnotations);
        assertThat(computedAnnotations, hasEntry("mycustomannotation", "alpha"));
        assertThat(computedAnnotations, hasEntry("multipleannotations", "beta"));
        assertThat(computedAnnotations, hasEntry("finalannotation", "delta"));
    }

    @Test
    public void testCustomLabels() {
        Map<String, String> customLabels = new HashMap<>();
        customLabels.put("mycustomlabel", "alpha");
        customLabels.put("multiplelabels", "beta");
        customLabels.put("finallabel", "delta");

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                .editSchemaRegistry()
                .withNewTemplate()
                .withPod(new PodTemplateBuilder()
                        .withNewMetadata()
                        .withLabels(customLabels)
                        .endMetadata()
                        .build())
                .endTemplate()
                .endSchemaRegistry()
                .endSpec()
                .build();
        SchemaRegistryModel schemaRegistry = new SchemaRegistryModel(defaultEs, imageConfig, null, mockCommonServices, kafkaPrincipal);

        Map<String, String> computedLabels = schemaRegistry.getDeployment().getSpec().getTemplate().getMetadata().getLabels();
        ModelUtils.assertEventStreamsLabelsPresent(computedLabels);
        assertThat(computedLabels, hasEntry("mycustomlabel", "alpha"));
        assertThat(computedLabels, hasEntry("multiplelabels", "beta"));
        assertThat(computedLabels, hasEntry("finallabel", "delta"));
    }

    @Test
    public void testDefaultAffinity() {
        SchemaRegistryModel schemaRegistry = createDefaultSchemaRegistryModel();
        Affinity schemaRegistryAffinity = schemaRegistry.getDeployment().getSpec().getTemplate().getSpec().getAffinity();
        assertNull(schemaRegistryAffinity);
    }

    @Test
    public void testCustomAffinity() {
        Affinity affinity = new AffinityBuilder()
                .withNewNodeAffinity()
                    .addNewPreferredDuringSchedulingIgnoredDuringExecution()
                        .withNewPreference()
                            .addNewMatchExpression()
                                .withNewKey("custom-schema-key")
                                .withNewOperator("custom-schema-operator")
                                .addNewValue("custom-schema-value")
                            .endMatchExpression()
                        .endPreference()
                    .endPreferredDuringSchedulingIgnoredDuringExecution()
                .endNodeAffinity()
            .build();

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                    .editSchemaRegistry()
                        .withNewTemplate()
                            .withPod(new PodTemplateBuilder().withAffinity(affinity).build())
                        .endTemplate()
                    .endSchemaRegistry()
                .endSpec()
            .build();
        SchemaRegistryModel schemaRegistry = new SchemaRegistryModel(defaultEs, imageConfig, null, mockCommonServices, kafkaPrincipal);

        Affinity schemaRegAffinity = schemaRegistry.getDeployment().getSpec().getTemplate().getSpec().getAffinity();
        PreferredSchedulingTerm computedNodeSelector = schemaRegAffinity.getNodeAffinity().getPreferredDuringSchedulingIgnoredDuringExecution().get(0);
        NodeSelectorRequirement computedMatchExpression = computedNodeSelector.getPreference().getMatchExpressions().get(0);
        assertThat(computedMatchExpression.getKey(), is("custom-schema-key"));
    }

    @Test
    public void testCheckIfEnabled() {
        EventStreams eventStreams = createDefaultEventStreams().build();
        assertThat(SchemaRegistryModel.isSchemaRegistryEnabled(eventStreams), is(true));
    }

    @Test
    public void testCheckIfDisabled() {
        EventStreams eventStreams = ModelUtils.createDefaultEventStreams(instanceName).build();
        assertThat(SchemaRegistryModel.isSchemaRegistryEnabled(eventStreams), is(false));
    }
}
