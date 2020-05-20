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
import com.ibm.eventstreams.api.spec.EndpointSpec;
import com.ibm.eventstreams.api.spec.EndpointSpecBuilder;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.EventStreamsSpecBuilder;
import com.ibm.eventstreams.api.spec.SecurityComponentSpecBuilder;
import com.ibm.eventstreams.api.spec.SecuritySpecBuilder;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.openshift.api.model.Route;
import io.strimzi.api.kafka.model.ExternalLogging;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.status.ListenerStatusBuilder;
import io.strimzi.api.kafka.model.template.PodTemplateBuilder;
import io.strimzi.operator.common.model.Labels;
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

import static com.ibm.eventstreams.api.model.AbstractSecureEndpointsModel.INTERNAL_SERVICE_SUFFIX;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminApiModelTest {

    private final CommonServicesConfig mockCommonServicesConfig = new CommonServicesConfig("mycluster", "ingress", "consoleHost", "443");
    private final String instanceName = "test-instance";
    private final String componentPrefix = instanceName + "-" + AbstractModel.APP_NAME + "-" + AdminApiModel.COMPONENT_NAME;
    private final String apiVersion = "apiVersion";
    private final int defaultReplicas = 1;
    private final String kafkaPrincipal = InternalKafkaUserModel.getInternalKafkaUserName(instanceName);

    @Mock
    private EventStreamsOperatorConfig.ImageLookup imageConfig;
    private List<ListenerStatus> listeners = new ArrayList<>();

    private EventStreamsBuilder createDefaultEventStreams() {
        return ModelUtils.createDefaultEventStreams(instanceName)
                .withApiVersion(apiVersion)
                .editSpec()
                    .withNewAdminApi()
                        .withReplicas(defaultReplicas)
                    .endAdminApi()
                .endSpec();
    }

    private EventStreamsBuilder createEventStreamsWithAuthentication() {
        return ModelUtils.createEventStreamsWithAuthentication(instanceName)
            .withApiVersion(apiVersion)
            .editSpec()
            .withNewAdminApi()
                .withReplicas(defaultReplicas)
            .endAdminApi()
            .endSpec();
    }

    public EventStreamsBuilder createDefaultEventStreamsWithNoInternalTLS() {
        return ModelUtils.createDefaultEventStreams(instanceName)
            .withApiVersion(apiVersion)
            .editSpec()
            .withNewSecurity()
                .withInternalTls(TlsVersion.NONE)
            .endSecurity()
            .withNewAdminApi()
                .withReplicas(defaultReplicas)
            .endAdminApi()
            .endSpec();
    }

    private EventStreamsBuilder createEventStreams(EventStreamsSpec eventStreamsSpec) {
        return ModelUtils.createEventStreams(instanceName, eventStreamsSpec)
                .editSpec()
                    .withNewAdminApi()
                        .withReplicas(defaultReplicas)
                    .endAdminApi()
                .endSpec();
    }

    private EventStreamsBuilder createEventStreamsWithReplicator(EventStreamsSpec eventStreamsSpec) {
        return ModelUtils.createEventStreams(instanceName, eventStreamsSpec)
            .editSpec()
            .withNewAdminApi()
            .withReplicas(defaultReplicas)
            .endAdminApi()
            .endSpec();
    }

    private AdminApiModel createDefaultAdminApiModel() {
        EventStreams eventStreamsResource = createDefaultEventStreams().build();
        return new AdminApiModel(eventStreamsResource, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal);
    }

    private AdminApiModel createAdminApiModelWithAuthentication() {
        EventStreams eventStreamsResource = createEventStreamsWithAuthentication().build();
        return new AdminApiModel(eventStreamsResource, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal);
    }

    @Test
    public void testDefaultAdminApi() {
        AdminApiModel adminApiModel = createDefaultAdminApiModel();
        Deployment adminApiDeployment = adminApiModel.getDeployment();
        assertThat(adminApiDeployment.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(adminApiDeployment.getSpec().getReplicas(), is(defaultReplicas));

        Service adminApiInternalService = adminApiModel.getSecurityService(EndpointServiceType.INTERNAL);
        assertThat(adminApiInternalService.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(adminApiInternalService.getMetadata().getName(), endsWith(AbstractSecureEndpointsModel.INTERNAL_SERVICE_SUFFIX));

        Service adminApiExternalService = adminApiModel.getSecurityService(EndpointServiceType.ROUTE);
        assertThat(adminApiExternalService.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(adminApiExternalService.getMetadata().getName(), endsWith(AbstractSecureEndpointsModel.ROUTE_SERVICE_SUFFIX));

        Map<String, Route> routes = adminApiModel.getRoutes();
        routes.forEach((routeName, route) -> {
            assertThat(route.getMetadata().getName(), startsWith(componentPrefix));
            assertThat(route.getMetadata().getName(), is(routeName));
        });
    }

    @Test
    public void testDefaultAdminApiEnvVars() {
        AdminApiModel adminApiModel = createDefaultAdminApiModel();

        String schemaRegistryEndpoint = instanceName  + "-" + AbstractModel.APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME + "-" + INTERNAL_SERVICE_SUFFIX  + "." + adminApiModel.getNamespace() + ".svc:" + Endpoint.getPodToPodPort(adminApiModel.tlsEnabled());
        String zookeeperEndpoint = instanceName + "-" + EventStreamsKafkaModel.ZOOKEEPER_COMPONENT_NAME + "-client." + adminApiModel.getNamespace() + ".svc:" + EventStreamsKafkaModel.ZOOKEEPER_PORT;
        String kafkaConnectRestEndpoint = "http://" + instanceName  + "-mirrormaker2-api." + adminApiModel.getNamespace() + ".svc:" + GeoReplicatorModel.REPLICATOR_PORT;

        EnvVar schemaRegistryUrlEnv = new EnvVarBuilder().withName("SCHEMA_REGISTRY_URL").withValue(schemaRegistryEndpoint).build();
        EnvVar zkConnectEnv = new EnvVarBuilder().withName("ZOOKEEPER_CONNECT").withValue(zookeeperEndpoint).build();
        EnvVar kafkaStsEnv = new EnvVarBuilder().withName("KAFKA_STS_NAME").withValue(instanceName + "-" + EventStreamsKafkaModel.KAFKA_COMPONENT_NAME).build();
        EnvVar clientCaCertPath = new EnvVarBuilder().withName("CLIENT_CA_PATH").withValue("/certs/client/ca.crt").build();
        EnvVar authentication = new EnvVarBuilder().withName("AUTHENTICATION").withValue("9443:RUNAS-ANONYMOUS,7443:RUNAS-ANONYMOUS").build();
        EnvVar endpoints = new EnvVarBuilder().withName("ENDPOINTS").withValue("9443:external,7443:p2ptls").build();
        EnvVar tlsVersion = new EnvVarBuilder().withName("TLS_VERSION").withValue("9443:TLSv1.2,7443:TLSv1.2").build();
        EnvVar kafkaConnectRestApiEnv = new EnvVarBuilder().withName("KAFKA_CONNECT_REST_API_ADDRESS").withValue(kafkaConnectRestEndpoint).build();
        EnvVar apiVersionEnv = new EnvVarBuilder().withName("EVENTSTREAMS_API_GROUP").withValue(apiVersion).build();
        EnvVar geoRepEnabledEnv = new EnvVarBuilder().withName("GEOREPLICATION_ENABLED").withValue("false").build();
        EnvVar geoRepSecretNameEnv = new EnvVarBuilder().withName("GEOREPLICATION_SECRET_NAME").withValue(instanceName  + "-" + AbstractModel.APP_NAME + "-" + GeoReplicatorSecretModel.REPLICATOR_SECRET_NAME).build();
        EnvVar geoRepInternalClientAuthEnv = new EnvVarBuilder().withName("GEOREPLICATION_INTERNAL_CLIENT_AUTH_ENABLED").withValue("false").build();
        EnvVar geoRepExternalClientAuthEnv = new EnvVarBuilder().withName("GEOREPLICATION_EXTERNAL_CLIENT_AUTH_ENABLED").withValue("false").build();
        EnvVar geoRepInternalServerAuthEnv = new EnvVarBuilder().withName("GEOREPLICATION_INTERNAL_SERVER_AUTH_ENABLED").withValue("false").build();
        EnvVar geoRepExternalServerAuthEnv = new EnvVarBuilder().withName("GEOREPLICATION_EXTERNAL_SERVER_AUTH_ENABLED").withValue("false").build();
        EnvVar geoRepInternalClientAuthTypeEnv = new EnvVarBuilder().withName("GEOREPLICATION_INTERNAL_CLIENT_AUTH_TYPE").withValue("NONE").build();

        Container adminApiContainer = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);
        List<EnvVar> defaultEnvVars = adminApiContainer.getEnv();
        assertThat(defaultEnvVars, hasItem(zkConnectEnv));
        assertThat(defaultEnvVars, hasItem(schemaRegistryUrlEnv));
        assertThat(defaultEnvVars, hasItem(kafkaStsEnv));
        assertThat(defaultEnvVars, hasItem(clientCaCertPath));
        assertThat(defaultEnvVars, hasItem(authentication));
        assertThat(defaultEnvVars, hasItem(endpoints));
        assertThat(defaultEnvVars, hasItem(tlsVersion));
        assertThat(defaultEnvVars, hasItem(kafkaConnectRestApiEnv));
        assertThat(defaultEnvVars, hasItem(apiVersionEnv));
        assertThat(defaultEnvVars, hasItem(geoRepEnabledEnv));
        assertThat(defaultEnvVars, hasItem(geoRepSecretNameEnv));
        assertThat(defaultEnvVars, hasItem(geoRepInternalClientAuthEnv));
        assertThat(defaultEnvVars, hasItem(geoRepExternalClientAuthEnv));
        assertThat(defaultEnvVars, hasItem(geoRepInternalServerAuthEnv));
        assertThat(defaultEnvVars, hasItem(geoRepInternalServerAuthEnv));
        assertThat(defaultEnvVars, hasItem(geoRepInternalClientAuthTypeEnv));
    }

    @Test
    public void testAdminApiEnvVarsWithAuthentication() {
        AdminApiModel adminApiModel = createAdminApiModelWithAuthentication();

        String schemaRegistryEndpoint = instanceName  + "-" + AbstractModel.APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME + "-" + INTERNAL_SERVICE_SUFFIX  + "." + adminApiModel.getNamespace() + ".svc:" + Endpoint.getPodToPodPort(adminApiModel.tlsEnabled());
        String zookeeperEndpoint = instanceName + "-" + EventStreamsKafkaModel.ZOOKEEPER_COMPONENT_NAME + "-client." + adminApiModel.getNamespace() + ".svc:" + EventStreamsKafkaModel.ZOOKEEPER_PORT;
        String kafkaConnectRestEndpoint = "http://" + instanceName + "-mirrormaker2-api." + adminApiModel.getNamespace() + ".svc:" + GeoReplicatorModel.REPLICATOR_PORT;

        EnvVar schemaRegistryUrlEnv = new EnvVarBuilder().withName("SCHEMA_REGISTRY_URL").withValue(schemaRegistryEndpoint).build();
        EnvVar zkConnectEnv = new EnvVarBuilder().withName("ZOOKEEPER_CONNECT").withValue(zookeeperEndpoint).build();
        EnvVar kafkaStsEnv = new EnvVarBuilder().withName("KAFKA_STS_NAME").withValue(instanceName + "-" + EventStreamsKafkaModel.KAFKA_COMPONENT_NAME).build();
        EnvVar clientCaCertPath = new EnvVarBuilder().withName("CLIENT_CA_PATH").withValue("/certs/client/ca.crt").build();
        EnvVar authentication = new EnvVarBuilder().withName("AUTHENTICATION").withValue("9443:IAM-BEARER;TLS;SCRAM-SHA-512,7443:IAM-BEARER").build();
        EnvVar endpoints = new EnvVarBuilder().withName("ENDPOINTS").withValue("9443:external,7443:p2ptls").build();
        EnvVar tlsVersion = new EnvVarBuilder().withName("TLS_VERSION").withValue("9443:TLSv1.2,7443:TLSv1.2").build();
        EnvVar kafkaPrincipalEnv = new EnvVarBuilder().withName("KAFKA_PRINCIPAL").withValue(kafkaPrincipal).build();
        EnvVar kafkaConnectRestApiEnv = new EnvVarBuilder().withName("KAFKA_CONNECT_REST_API_ADDRESS").withValue(kafkaConnectRestEndpoint).build();
        EnvVar apiVersionEnv = new EnvVarBuilder().withName("EVENTSTREAMS_API_GROUP").withValue(apiVersion).build();
        EnvVar geoRepEnabledEnv = new EnvVarBuilder().withName("GEOREPLICATION_ENABLED").withValue("false").build();
        EnvVar geoRepSecretNameEnv = new EnvVarBuilder().withName("GEOREPLICATION_SECRET_NAME").withValue(instanceName  + "-" + AbstractModel.APP_NAME + "-" + GeoReplicatorSecretModel.REPLICATOR_SECRET_NAME).build();
        EnvVar geoRepInternalClientAuthEnv = new EnvVarBuilder().withName("GEOREPLICATION_INTERNAL_CLIENT_AUTH_ENABLED").withValue("false").build();
        EnvVar geoRepExternalClientAuthEnv = new EnvVarBuilder().withName("GEOREPLICATION_EXTERNAL_CLIENT_AUTH_ENABLED").withValue("false").build();
        EnvVar geoRepInternalServerAuthEnv = new EnvVarBuilder().withName("GEOREPLICATION_INTERNAL_SERVER_AUTH_ENABLED").withValue("false").build();
        EnvVar geoRepExternalServerAuthEnv = new EnvVarBuilder().withName("GEOREPLICATION_EXTERNAL_SERVER_AUTH_ENABLED").withValue("false").build();
        EnvVar geoRepInternalClientAuthTypeEnv = new EnvVarBuilder().withName("GEOREPLICATION_INTERNAL_CLIENT_AUTH_TYPE").withValue("NONE").build();

        EnvVarSource esCaCertEnvVarSource = new EnvVarSourceBuilder().withSecretKeyRef(new SecretKeySelector("ca.crt", instanceName + "-cluster-ca-cert", true)).build();
        EnvVar esCaCertEnv = new EnvVarBuilder().withName("ES_CACERT").withValueFrom(esCaCertEnvVarSource).build();

        Container adminApiContainer = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);
        List<EnvVar> defaultEnvVars = adminApiContainer.getEnv();
        assertThat(defaultEnvVars, hasItem(zkConnectEnv));
        assertThat(defaultEnvVars, hasItem(schemaRegistryUrlEnv));
        assertThat(defaultEnvVars, hasItem(kafkaStsEnv));
        assertThat(defaultEnvVars, hasItem(clientCaCertPath));
        assertThat(defaultEnvVars, hasItem(authentication));
        assertThat(defaultEnvVars, hasItem(endpoints));
        assertThat(defaultEnvVars, hasItem(esCaCertEnv));
        assertThat(defaultEnvVars, hasItem(tlsVersion));
        assertThat(defaultEnvVars, hasItem(kafkaPrincipalEnv));
        assertThat(defaultEnvVars, hasItem(kafkaConnectRestApiEnv));
        assertThat(defaultEnvVars, hasItem(apiVersionEnv));
        assertThat(defaultEnvVars, hasItem(geoRepEnabledEnv));
        assertThat(defaultEnvVars, hasItem(geoRepSecretNameEnv));
        assertThat(defaultEnvVars, hasItem(geoRepInternalClientAuthEnv));
        assertThat(defaultEnvVars, hasItem(geoRepExternalClientAuthEnv));
        assertThat(defaultEnvVars, hasItem(geoRepInternalServerAuthEnv));
        assertThat(defaultEnvVars, hasItem(geoRepExternalServerAuthEnv));
        assertThat(defaultEnvVars, hasItem(geoRepInternalClientAuthTypeEnv));
    }

    @Test
    public void testAdminApiEnvVarsWithEventStreamsGeoReplicator() {
        EventStreams eventStreams = createDefaultEventStreams().build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockCommonServicesConfig, true, kafkaPrincipal);

        EnvVar geoRepEnabledEnv = new EnvVarBuilder().withName("GEOREPLICATION_ENABLED").withValue("true").build();

        Container adminApiContainer = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);
        List<EnvVar> defaultEnvVars = adminApiContainer.getEnv();
        assertThat(defaultEnvVars, hasItem(geoRepEnabledEnv));
    }

    @Test
    public void testDefaultResourceRequirements() {
        AdminApiModel adminApiModel = createDefaultAdminApiModel();

        ResourceRequirements resourceRequirements = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getResources();
        assertThat(resourceRequirements.getRequests().get("cpu").getAmount(), is("500m"));
        assertThat(resourceRequirements.getRequests().get("memory").getAmount(), is("1Gi"));
        assertThat(resourceRequirements.getLimits().get("cpu").getAmount(), is("2000m"));
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
        AdminApiModel adminApiModel = new AdminApiModel(eventStreamsResource, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal);

        ResourceRequirements resourceRequirements = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getResources();
        assertThat(resourceRequirements.getRequests().get("cpu").getAmount(), is("200m"));
        assertThat(resourceRequirements.getRequests().get("memory").getAmount(), is("1Gi"));
        assertThat(resourceRequirements.getLimits().get("cpu").getAmount(), is("2000m"));
        assertThat(resourceRequirements.getLimits().get("memory").getAmount(), is("3Gi"));
    }

    @Test
    public void testDefaultAdminApiNetworkPolicy() {
        AdminApiModel adminApiModel = createDefaultAdminApiModel();

        NetworkPolicy networkPolicy = adminApiModel.getNetworkPolicy();
        assertThat(networkPolicy.getMetadata().getName(), is(componentPrefix));
        assertThat(networkPolicy.getKind(), is("NetworkPolicy"));

        assertThat(networkPolicy.getSpec().getIngress().size(), is(adminApiModel.getEndpoints().size()));

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

        assertThat(networkPolicy.getSpec().getIngress(), CustomMatchers.containsIngressRulesInAnyOrder(defaultEndpointRule, defaultP2PIngressRule));
    }

    @Test
    public void testAdminApiIngressNetworkPolicyWithTLS() {
        EventStreams eventStreams = createDefaultEventStreams()
                .editSpec()
                .withSecurity(new SecuritySpecBuilder().withInternalTls(TlsVersion.TLS_V1_2).build())
                .withAdminApi(new SecurityComponentSpecBuilder()
                    .withEndpoints(
                        new EndpointSpecBuilder()
                            .withName("first-endpoint")
                            .withContainerPort(9999)
                            .build(),
                        new EndpointSpecBuilder()
                            .withName("second-endpoint")
                            .withContainerPort(9998)
                            .build())
                    .build())
                .endSpec()
                .build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal);

        NetworkPolicy networkPolicy = adminApiModel.getNetworkPolicy();
        assertThat(networkPolicy.getMetadata().getName(), is(componentPrefix));
        assertThat(networkPolicy.getKind(), is("NetworkPolicy"));

        assertThat(networkPolicy.getSpec().getIngress().size(), is(adminApiModel.endpoints.size()));

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

        NetworkPolicyIngressRule customEndpointRule1 = new NetworkPolicyIngressRuleBuilder()
                .addNewPort()
                    .withPort(new IntOrString(9999))
                .endPort()
                .build();

        NetworkPolicyIngressRule customEndpointRule2 = new NetworkPolicyIngressRuleBuilder()
                .addNewPort()
                    .withPort(new IntOrString(9998))
                .endPort()
                .build();

        assertThat(networkPolicy.getSpec().getIngress(), CustomMatchers.containsIngressRulesInAnyOrder(customEndpointRule1, customEndpointRule2, defaultP2PIngressRule));
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
        expectedImages.put(AdminApiModel.ADMIN_API_CONTAINER_NAME, adminApiImage);

        List<Container> containers = new AdminApiModel(instance, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal).getDeployment().getSpec().getTemplate()
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

        AdminApiModel adminApiModel = new AdminApiModel(instance, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal);
        assertThat(adminApiModel.getImage(), is(adminApiImage));
        assertTrue(adminApiModel.getCustomImage());

        List<Container> containers = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers();

        Map<String, String> expectedImages = new HashMap<>();
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

        assertThat(new AdminApiModel(eventStreams, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal).getServiceAccount()
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

        assertThat(new AdminApiModel(eventStreams, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal).getServiceAccount()
                        .getImagePullSecrets(), contains(imagePullSecretOverride));
    }

    @Test
    public void testOperatorImagePullSecretOverride() {
        LocalObjectReference imagePullSecret = new LocalObjectReferenceBuilder()
                .withName("operator-image-pull-secret")
                .build();
        when(imageConfig.getPullSecrets()).thenReturn(Collections.singletonList(imagePullSecret));

        assertThat(createDefaultAdminApiModel().getServiceAccount().getImagePullSecrets(),
                   contains(imagePullSecret));
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

        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal);
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

        assertThat(new AdminApiModel(eventStreams, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal).getServiceAccount()
                        .getImagePullSecrets(), containsInAnyOrder(globalPullSecretOverride, componentPullSecretOverride));
    }

    @Test
    public void testAdminApiContainerHasRoleBinding() {
        EventStreams defaultEs = createDefaultEventStreams().build();
        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal);

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
    public void testAdminApiContainerWithNoInternalTLSAndPlainListenerHasPlainKafkaStatusKafkaBootstrapEnvironmentVariables() {
        final String kafkaPlainHost = "plainHost";
        final Integer kafkaPlainPort = 1234;

        final String kafkaTlsHost = "tlsHost";
        final Integer kafkaTlsPort = 5678;

        final String externalHost = "externalHost";
        final Integer externalPort = 9876;

        EventStreams defaultEs = createDefaultEventStreamsWithNoInternalTLS().build();

        ListenerStatus internalPlainListener = new ListenerStatusBuilder()
                .withNewType("plain")
                .addNewAddress()
                    .withHost(kafkaPlainHost)
                    .withPort(kafkaPlainPort)
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
        listeners.add(internalPlainListener);
        listeners.add(externalListener);

        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal);

        EnvVar kafkaBootstrapServersEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(kafkaPlainHost + ":" + kafkaPlainPort).build();
        EnvVar kafkaBootstrapInternalPlainUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_INTERNAL_PLAIN_URL").withValue(kafkaPlainHost + ":" + kafkaPlainPort).build();
        EnvVar kafkaBootstrapInternalTlsUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_INTERNAL_TLS_URL").withValue(kafkaTlsHost + ":" + kafkaTlsPort).build();
        EnvVar kafkaBootstrapExternalUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_EXTERNAL_URL").withValue(externalHost + ":" + externalPort).build();

        Container adminApiContainer = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapServersEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapInternalPlainUrlEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapExternalUrlEnv));

        assertThat(adminApiContainer.getEnv(), not(hasItem(kafkaBootstrapInternalTlsUrlEnv)));
    }

    @Test
    public void testAdminApiContainerWithTlsListenerHasTlsKafkaStatusKafkaBootstrapEnvironmentVariables() {
        final String kafkaPlainHost = "plainHost";
        final Integer kafkaPlainPort = 1234;

        final String kafkaTlsHost = "tlsHost";
        final Integer kafkaTlsPort = 5678;

        final String externalHost = "externalHost";
        final Integer externalPort = 9876;

        EventStreams defaultEs = createDefaultEventStreams()
            .editSpec()
                .editOrNewSecurity()
                    .withInternalTls(TlsVersion.TLS_V1_2)
                .endSecurity()
            .endSpec()
            .build();

        ListenerStatus internalTlsListener = new ListenerStatusBuilder()
            .withNewType("tls")
            .addNewAddress()
            .withHost(kafkaTlsHost)
            .withPort(kafkaTlsPort)
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
        listeners.add(internalTlsListener);
        listeners.add(externalListener);

        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal);

        EnvVar kafkaBootstrapServersEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(kafkaTlsHost + ":" + kafkaTlsPort).build();
        EnvVar kafkaBootstrapInternalPlainUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_INTERNAL_PLAIN_URL").withValue(kafkaPlainHost + ":" + kafkaPlainPort).build();
        EnvVar kafkaBootstrapInternalTlsUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_INTERNAL_TLS_URL").withValue(kafkaTlsHost + ":" + kafkaTlsPort).build();
        EnvVar kafkaBootstrapExternalUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_EXTERNAL_URL").withValue(externalHost + ":" + externalPort).build();

        Container adminApiContainer = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapServersEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapInternalTlsUrlEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapExternalUrlEnv));

        assertThat(adminApiContainer.getEnv(), not(hasItem(kafkaBootstrapInternalPlainUrlEnv)));
    }

    @Test
    public void testAdminApiContainerWithPlainAndTlsListenerHasTlsKafkaStatusKafkaBootstrapEnvironmentVariables() {
        final String kafkaPlainHost = "plainHost";
        final Integer kafkaPlainPort = 1234;

        final String kafkaTlsHost = "tlsHost";
        final Integer kafkaTlsPort = 5678;

        final String externalHost = "externalHost";
        final Integer externalPort = 9876;

        EventStreams defaultEs = createDefaultEventStreams()
            .editSpec()
            .editOrNewSecurity()
            .withInternalTls(TlsVersion.TLS_V1_2)
            .endSecurity()
            .endSpec()
            .build();

        ListenerStatus internalPlainListener = new ListenerStatusBuilder()
            .withNewType("plain")
            .addNewAddress()
            .withHost(kafkaPlainHost)
            .withPort(kafkaPlainPort)
            .endAddress()
            .build();

        ListenerStatus internalTlsListener = new ListenerStatusBuilder()
            .withNewType("tls")
            .addNewAddress()
            .withHost(kafkaTlsHost)
            .withPort(kafkaTlsPort)
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
        listeners.add(internalPlainListener);
        listeners.add(internalTlsListener);
        listeners.add(externalListener);

        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal);

        EnvVar kafkaBootstrapServersEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(kafkaTlsHost + ":" + kafkaTlsPort).build();
        EnvVar kafkaBootstrapInternalPlainUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_INTERNAL_PLAIN_URL").withValue(kafkaPlainHost + ":" + kafkaPlainPort).build();
        EnvVar kafkaBootstrapInternalTlsUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_INTERNAL_TLS_URL").withValue(kafkaTlsHost + ":" + kafkaTlsPort).build();
        EnvVar kafkaBootstrapExternalUrlEnv = new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_EXTERNAL_URL").withValue(externalHost + ":" + externalPort).build();

        Container adminApiContainer = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapServersEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapInternalPlainUrlEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapInternalTlsUrlEnv));
        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapExternalUrlEnv));
    }

    @Test
    public void testAdminApiContainerHasRunAsKafkaBootstrapEnvironmentVariablesWhenSecurityEnabled() {
        final String kafkaHost = "plainHost";
        final Integer kafkaPort = 1234;

        final String externalHost = "externalHost";
        final Integer externalPort = 9876;

        final Integer runasPort = 8091;

        EventStreams defaultEs = createDefaultEventStreams()
            .editSpec()
                .editOrNewSecurity()
                    .withInternalTls(TlsVersion.TLS_V1_2)
                .endSecurity()
            .endSpec()
            .build();

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

        List<ListenerStatus> listeners = new ArrayList<>();
        listeners.add(kafkaListener);
        listeners.add(externalListener);

        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, listeners, mockCommonServicesConfig, false, kafkaPrincipal);
        String expectedRunAsKafkaBootstrap = adminApiModel.getInstanceName() + "-kafka-bootstrap." + adminApiModel.getNamespace() + ".svc:" + runasPort;

        EnvVar kafkaBootstrapUrlEnv = new EnvVarBuilder().withName("RUNAS_KAFKA_BOOTSTRAP_SERVERS").withValue(expectedRunAsKafkaBootstrap).build();

        Container adminApiContainer = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(adminApiContainer.getEnv(), hasItem(kafkaBootstrapUrlEnv));
    }

    @Test
    public void testDefaultLogging() {
        EventStreams defaultEs = createDefaultEventStreams().build();
        AdminApiModel adminApiModel = new AdminApiModel(defaultEs, imageConfig, null, mockCommonServicesConfig, false, kafkaPrincipal);

        EnvVar expectedTraceSpecEnvVar = new EnvVarBuilder().withName("TRACE_SPEC").withValue("info").build();
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
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockCommonServicesConfig, false, kafkaPrincipal);

        EnvVar expectedTraceSpecEnvVar = new EnvVarBuilder().withName("TRACE_SPEC").withValue("logger.one:info,logger.two:debug").build();
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
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockCommonServicesConfig, false, kafkaPrincipal);

        EnvVar expectedTraceSpecEnvVar = new EnvVarBuilder().withName("TRACE_SPEC").withValue("info").build();
        List<EnvVar> actualEnvVars = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();

        assertThat(actualEnvVars, hasItem(expectedTraceSpecEnvVar));
    }

    @Test
    public void testSSLTrustAndKeystoreEnvVars() {

        final String userCertPath = "/certs/user";
        final String clusterCertPath = "/certs/cluster";

        EventStreams eventStreams = createDefaultEventStreams().build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockCommonServicesConfig, false, kafkaPrincipal);

        EnvVar expectedTruststoreP12Path = new EnvVarBuilder().withName("SSL_TRUSTSTORE_P12_PATH").withValue(clusterCertPath + File.separator + "ca.p12").build();
        EnvVar expectedTruststoreCrtPath = new EnvVarBuilder().withName("SSL_TRUSTSTORE_CRT_PATH").withValue(clusterCertPath + File.separator + "ca.crt").build();
        EnvVar expectedEnvVarKeyStorePath = new EnvVarBuilder().withName("SSL_KEYSTORE_PATH").withValue(userCertPath + File.separator + "user.p12").build();
        List<EnvVar> actualEnvVars = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        assertThat(actualEnvVars, hasItem(expectedTruststoreP12Path));
        assertThat(actualEnvVars, hasItem(expectedTruststoreCrtPath));
        assertThat(actualEnvVars, hasItem(expectedEnvVarKeyStorePath));
    }

    @Test
    public void testICPClusterDataEnvironmentVariablesCorrectlySet() {
        String clusterAddress = "0.0.0.0";
        String clusterPort = "9080";
        String caCert = "abcdef";
        String clusterName = "test-cluster";
        CommonServicesConfig commonServicesConfig = new CommonServicesConfig(clusterName, "ingress", clusterAddress, clusterPort);
        commonServicesConfig.setCaCerts("", caCert);

        EventStreams eventStreams = createDefaultEventStreams().build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, commonServicesConfig, false, kafkaPrincipal);

        EnvVar expectedEnvVarPrometheusHost = new EnvVarBuilder().withName("PROMETHEUS_HOST").withValue(clusterAddress).build();
        EnvVar expectedEnvVarPrometheusPort = new EnvVarBuilder().withName("PROMETHEUS_PORT").withValue(clusterPort).build();
        EnvVar expectedEnvVarIAMClusterName = new EnvVarBuilder().withName("IAM_CLUSTER_NAME").withValue(clusterName).build();
        List<EnvVar> actualEnvVars = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        assertThat(actualEnvVars, hasItem(expectedEnvVarPrometheusHost));
        assertThat(actualEnvVars, hasItem(expectedEnvVarPrometheusPort));
        List<EnvVar> actualAdminAPIEnvVars = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        assertThat(actualAdminAPIEnvVars, hasItem(expectedEnvVarIAMClusterName));

    }

    @Test
    public void testCreateAdminApiRouteWithTlsEncryptionHasARoutesWithTls() {
        EventStreams eventStreams = createDefaultEventStreams()
                .editSpec()
                    .withNewSecurity()
                        .withInternalTls(TlsVersion.TLS_V1_2)
                    .endSecurity()
                .endSpec()
                .build();

        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockCommonServicesConfig, false, kafkaPrincipal);
        assertThat(adminApiModel.getRoutes().get(adminApiModel.getRouteName("external")).getSpec().getTls().getTermination(), is("passthrough"));
    }

    @Test
    public void testCreateAdminApiRouteWithoutTlsEncryptionHasRoutesWithoutTls() {
        EventStreams eventStreams = createDefaultEventStreams().build();

        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockCommonServicesConfig, false, kafkaPrincipal);
        assertThat(adminApiModel.getRoutes().size(), is(1));
        assertThat(adminApiModel.getRoutes().get(adminApiModel.getRouteName(Endpoint.DEFAULT_EXTERNAL_NAME)).getSpec().getTls(), is(notNullValue()));
    }

    @Test
    public void testCreateAdminApiRouteWithNonTlsOverridesHaveRoute() {
        String routeName = "Non-tls-route";
        EndpointSpec endpointSpec = new EndpointSpecBuilder()
            .withName(routeName)
            .withContainerPort(9999)
            .withTlsVersion(TlsVersion.NONE)
            .build();

        EventStreams eventStreams = createDefaultEventStreams().build();

        eventStreams.getSpec().getAdminApi().setEndpoints(Collections.singletonList(endpointSpec));

        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockCommonServicesConfig, false, kafkaPrincipal);
        assertThat(adminApiModel.getRoutes().size(), is(1));
        assertThat(adminApiModel.getRoutes().get(adminApiModel.getRouteName(routeName)).getSpec().getTls(), is(nullValue()));
    }

    @Test
    public void testGenerationIdLabelOnDeployment() {
        EventStreams eventStreams = createDefaultEventStreams().build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockCommonServicesConfig, false, kafkaPrincipal);

        assertThat(adminApiModel.getDeployment("newID").getMetadata().getLabels(), hasKey(AbstractSecureEndpointsModel.CERT_GENERATION_KEY));
        assertThat(adminApiModel.getDeployment("newID").getMetadata().getLabels(), hasEntry(AbstractSecureEndpointsModel.CERT_GENERATION_KEY, "newID"));
        assertThat(adminApiModel.getDeployment("newID").getSpec().getTemplate().getMetadata().getLabels(), hasKey(AbstractSecureEndpointsModel.CERT_GENERATION_KEY));
        assertThat(adminApiModel.getDeployment("newID").getSpec().getTemplate().getMetadata().getLabels(), hasEntry(AbstractSecureEndpointsModel.CERT_GENERATION_KEY, "newID"));
    }

    @Test
    public void testVolumeMounts() {
        EventStreams eventStreams = createDefaultEventStreams().build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockCommonServicesConfig, false, kafkaPrincipal);

        List<VolumeMount> volumeMounts = adminApiModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts();

        assertThat(volumeMounts.size(), is(7));

        assertThat(volumeMounts.get(0).getName(), is(GeoReplicatorSecretModel.REPLICATOR_SECRET_NAME));
        assertThat(volumeMounts.get(0).getReadOnly(), is(true));
        assertThat(volumeMounts.get(0).getMountPath(), is(GeoReplicatorModel.REPLICATOR_SECRET_MOUNT_PATH));

        assertThat(volumeMounts.get(1).getName(), is(AdminApiModel.KAFKA_CONFIGMAP_MOUNT_NAME));
        assertThat(volumeMounts.get(1).getReadOnly(), is(true));
        assertThat(volumeMounts.get(1).getMountPath(), is("/etc/kafka-cm"));

        assertThat(volumeMounts.get(2).getName(), is(AdminApiModel.IBMCLOUD_CA_VOLUME_MOUNT_NAME));
        assertThat(volumeMounts.get(2).getReadOnly(), is(true));
        assertThat(volumeMounts.get(2).getMountPath(), is(AdminApiModel.IBMCLOUD_CA_CERTIFICATE_PATH));

        assertThat(volumeMounts.get(3).getName(), is(AdminApiModel.CERTS_VOLUME_MOUNT_NAME));
        assertThat(volumeMounts.get(3).getReadOnly(), is(true));
        assertThat(volumeMounts.get(3).getMountPath(), is(AdminApiModel.CERTIFICATE_PATH));

        assertThat(volumeMounts.get(4).getName(), is(AdminApiModel.CLUSTER_CA_VOLUME_MOUNT_NAME));
        assertThat(volumeMounts.get(4).getReadOnly(), is(true));
        assertThat(volumeMounts.get(4).getMountPath(), is(AdminApiModel.CLUSTER_CERTIFICATE_PATH));

        assertThat(volumeMounts.get(5).getName(), is(AdminApiModel.CLIENT_CA_VOLUME_MOUNT_NAME));
        assertThat(volumeMounts.get(5).getReadOnly(), is(true));
        assertThat(volumeMounts.get(5).getMountPath(), is(AdminApiModel.CLIENT_CA_CERTIFICATE_PATH));

        assertThat(volumeMounts.get(6).getName(), is(AdminApiModel.KAFKA_USER_SECRET_VOLUME_NAME));
        assertThat(volumeMounts.get(6).getReadOnly(), is(true));
        assertThat(volumeMounts.get(6).getMountPath(), is(AdminApiModel.KAFKA_USER_CERTIFICATE_PATH));
    }


    @Test
    public void testOwnerReferenceCorrectForRoutes() {
        EventStreams eventStreams = createDefaultEventStreams()
            .withApiVersion("test-api-version")
            .withMetadata(new ObjectMetaBuilder()
                .withName("test-name")
                .withUid("test-uid")
                .build())
            .build();
        AdminApiModel adminApiModel = new AdminApiModel(eventStreams, imageConfig, null, mockCommonServicesConfig, false, kafkaPrincipal);

        OwnerReference ownerReference = adminApiModel.getRoutes().get(adminApiModel.getRouteName(Endpoint.DEFAULT_EXTERNAL_NAME)).getMetadata().getOwnerReferences().get(0);
        assertThat(ownerReference.getUid(), is("test-uid"));
        assertThat(ownerReference.getName(), is("test-name"));
        assertThat(ownerReference.getApiVersion(), is("test-api-version"));
    }

    private EventStreamsSpec createStrimziOverrides() {
        return new EventStreamsSpecBuilder()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .withReplicas(1)
                .withNewListeners()
                .withNewKafkaListenerExternalRoute()
                .withNewKafkaListenerAuthenticationTlsAuth()
                .endKafkaListenerAuthenticationTlsAuth()
                .endKafkaListenerExternalRoute()
                .withNewTls()
                .withNewKafkaListenerAuthenticationTlsAuth()
                .endKafkaListenerAuthenticationTlsAuth()
                .endTls()
                .endListeners()
                .withNewTemplate()
                .withNewPod()
                .withNewMetadata()
                .endMetadata()
                .endPod()
                .endTemplate()
                .endKafka()
                .build()
            )
            .build();
    }
}
