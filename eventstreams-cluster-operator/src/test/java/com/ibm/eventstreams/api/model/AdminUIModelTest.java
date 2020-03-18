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

import static com.ibm.eventstreams.api.model.AbstractSecureEndpointModel.INTERNAL_SERVICE_SUFFIX;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterableOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ibm.eventstreams.Main;
import com.ibm.eventstreams.api.Labels;
import com.ibm.eventstreams.api.Listener;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.ExternalAccess;
import com.ibm.eventstreams.api.spec.ExternalAccessBuilder;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeer;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.openshift.api.model.Route;
import io.strimzi.api.kafka.model.ExternalLogging;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.template.PodTemplateBuilder;

import java.util.Collections;

@ExtendWith(MockitoExtension.class)
public class AdminUIModelTest {

    private final String instanceName = "test-instance";
    private final String componentPrefix = instanceName + "-" + AbstractModel.APP_NAME + "-" + AdminUIModel.COMPONENT_NAME;
    private final int defaultReplicas = 1;
    private final String namespace = "test-namespace";

    @Mock
    private EventStreamsOperatorConfig.ImageLookup imageConfig;

    private EventStreamsBuilder createDefaultEventStreams() {
        return ModelUtils.createDefaultEventStreams(instanceName)
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(instanceName)
                        .withNewNamespace(namespace)
                        .build())
                .editSpec()
                    .withNewAdminUI()
                        .withReplicas(defaultReplicas)
                    .endAdminUI()
                .endSpec();
    }

    private EventStreamsBuilder createDefaultEventStreamsWithExternalAccess(String type) {
        ExternalAccess externalAccess = new ExternalAccessBuilder().withNewType(type).build();

        return ModelUtils.createDefaultEventStreams(instanceName)
                .withMetadata(new ObjectMetaBuilder()
                        .withNewName(instanceName)
                        .withNewNamespace(namespace)
                        .build())
                .editSpec()
                    .withNewAdminUI()
                        .withReplicas(defaultReplicas)
                        .withExternalAccess(externalAccess)
                    .endAdminUI()
                .endSpec();
    }

    private AdminUIModel createDefaultAdminUIModel() {
        EventStreams instance = createDefaultEventStreams().build();
        return new AdminUIModel(instance, imageConfig, false, null);
    }

    private AdminUIModel createAdminUIModelWithICPCM(Map<String, String> configMap) {
        EventStreams instance = createDefaultEventStreams().build();
        return new AdminUIModel(instance, imageConfig, true, configMap);
    }

    @Test
    public void testDefaultAdminUIModel() {
        AdminUIModel adminUIModel = createDefaultAdminUIModel();

        Deployment userInterfaceDeployment = adminUIModel.getDeployment();
        assertThat(userInterfaceDeployment.getMetadata().getName(), startsWith(componentPrefix));
        assertThat(userInterfaceDeployment.getSpec().getReplicas(), is(defaultReplicas));

        final ObjectMeta uiPodMetadata = userInterfaceDeployment.getSpec().getTemplate().getMetadata();
        final PodSpec uiPodSpec = userInterfaceDeployment.getSpec().getTemplate().getSpec();
        final List<Container> uiContainers = uiPodSpec.getContainers();

        final RoleBinding createdRoleBinding = adminUIModel.getRoleBinding();

        assertThat(createdRoleBinding.getSubjects().size(), is(1));
        assertThat(createdRoleBinding.getMetadata().getName(), is(componentPrefix));

        final Subject createdSubject = createdRoleBinding.getSubjects().get(0);
        assertThat(createdSubject.getKind(), is("ServiceAccount"));
        assertThat(createdSubject.getName(), is(componentPrefix));
        assertThat(createdSubject.getNamespace(), is(namespace));

        final RoleRef createdRoleRef = createdRoleBinding.getRoleRef();
        assertThat(createdRoleRef.getKind(), is("ClusterRole"));
        assertThat(createdRoleRef.getName(), is("eventstreams-ui-clusterrole"));
        assertThat(createdRoleRef.getApiGroup(), is("rbac.authorization.k8s.io"));

        // confirm label 'release' present - so the UI can discover it for status
        assertThat(uiPodMetadata.getLabels().get("release"), is(instanceName));

        // confirm ui container has required envars


        String adminApiService = "http://" + instanceName + "-" + AbstractModel.APP_NAME + "-" + AdminApiModel.COMPONENT_NAME + "-" + INTERNAL_SERVICE_SUFFIX + "." +  namespace + ".svc." + Main.CLUSTER_NAME + ":" + Listener.podToPodListener(false).getPort();
        String schemaRegistryService = "http://" + instanceName + "-" + AbstractModel.APP_NAME + "-" + SchemaRegistryModel.COMPONENT_NAME + "-" + INTERNAL_SERVICE_SUFFIX + "." +  namespace + ".svc." + Main.CLUSTER_NAME + ":" + Listener.podToPodListener(false).getPort();

        assertThat(uiContainers.get(0).getEnv(), hasItems(
                new EnvVarBuilder().withName("ID").withValue(instanceName).build(),
                new EnvVarBuilder().withName("ESFF_SECURITY_AUTH").withValue("true").build(),
                new EnvVarBuilder().withName("ESFF_SECURITY_AUTHZ").withValue("true").build(),
                new EnvVarBuilder().withName("API_URL").withValue(adminApiService).build(),
                new EnvVarBuilder().withName("ICP_USER_MGMT_IP").withValue("icp-management-ingress.kube-system").build(),
                new EnvVarBuilder().withName("ICP_USER_MGMT_PORT").withValue("443").build(),
                new EnvVarBuilder().withName("GEOREPLICATION_ENABLED").withValue("true").build(),
                new EnvVarBuilder().withName("SCHEMA_REGISTRY_URL").withValue(schemaRegistryService).build(),
                new EnvVarBuilder().withName("ICP_USER_MGMT_HIGHEST_ROLE_FOR_CRN").withValue("idmgmt/identity/api/v1/teams/highestRole").build()));

        Service userInterfaceService = adminUIModel.getService();
        assertThat(userInterfaceService.getMetadata().getName(), startsWith(componentPrefix));

        Route userInterfaceRoute = adminUIModel.getRoute();
        assertThat(userInterfaceRoute.getMetadata().getName(), startsWith(componentPrefix));
    }

    @Test
    public void testAdminUIModelWithICPCM() {
        Map<String, String> icpCM = new HashMap<>();
        String clusterAddress = "mycluster.apps.ibm.com";
        String clusterPort = "443";
        String clusterName = "mycluster";
        icpCM.put(AdminUIModel.ICP_CM_CLUSTER_ADDRESS_KEY, clusterAddress);
        icpCM.put(AdminUIModel.ICP_CM_CLUSTER_ROUTER_PORT_KEY, clusterPort);
        icpCM.put(AdminUIModel.ICP_CM_CLUSTER_NAME_KEY, clusterName);
        AdminUIModel adminUIModel = createAdminUIModelWithICPCM(icpCM);
        Deployment userInterfaceDeployment = adminUIModel.getDeployment();
        final PodSpec uiPodSpec = userInterfaceDeployment.getSpec().getTemplate().getSpec();
        final List<Container> uiContainers = uiPodSpec.getContainers();
        assertThat(uiContainers.get(0).getEnv(), hasItems(
            new EnvVarBuilder().withName("CLUSTER_EXTERNAL_IP").withValue(clusterAddress).build(),
            new EnvVarBuilder().withName("CLUSTER_EXTERNAL_PORT").withValue(clusterPort).build(),
            new EnvVarBuilder().withName("IAM_CLUSTER_NAME").withValue(clusterName).build()));
    }

    @Test
    public void testDefaultNetworkPolicy() {
        AdminUIModel adminUIModel = createDefaultAdminUIModel();

        NetworkPolicy userInterfaceNetworkPolicy = adminUIModel.getNetworkPolicy();
        assertThat(userInterfaceNetworkPolicy.getMetadata().getName(), is(componentPrefix));
        assertThat(userInterfaceNetworkPolicy.getKind(), is("NetworkPolicy"));

        assertThat(userInterfaceNetworkPolicy.getSpec().getPodSelector().getMatchLabels().size(), is(1));
        assertThat(userInterfaceNetworkPolicy
            .getSpec()
            .getPodSelector()
            .getMatchLabels()
            .get(Labels.COMPONENT_LABEL), is(AdminUIModel.COMPONENT_NAME));

        assertThat(userInterfaceNetworkPolicy.getSpec().getIngress().size(), is(1));
        assertThat(userInterfaceNetworkPolicy.getSpec().getIngress().get(0).getFrom(), is(emptyIterableOf(NetworkPolicyPeer.class)));
        assertThat(userInterfaceNetworkPolicy.getSpec().getIngress().get(0).getPorts().size(), is(1));
        assertThat(userInterfaceNetworkPolicy.getSpec().getIngress().get(0).getPorts().get(0).getPort().getIntVal(), is(AdminUIModel.UI_SERVICE_PORT));
    }

    @Test
    public void testDefaultResourceRequirements() {
        AdminUIModel adminUIModel = createDefaultAdminUIModel();

        List<Container> containerList = adminUIModel.getDeployment().getSpec().getTemplate().getSpec().getContainers();

        Map<String, ResourceRequirements> resourceRequirements = containerList.stream()
                .collect(Collectors.toMap(Container::getName, Container::getResources));

        ResourceRequirements userInterfaceResources = resourceRequirements.get(AdminUIModel.COMPONENT_NAME);
        assertThat(userInterfaceResources.getRequests().get("cpu").getAmount(), is("1000m"));
        assertThat(userInterfaceResources.getRequests().get("memory").getAmount(), is("1Gi"));
        assertThat(userInterfaceResources.getLimits().get("cpu").getAmount(), is("1000m"));
        assertThat(userInterfaceResources.getLimits().get("memory").getAmount(), is("1Gi"));

        ResourceRequirements redisResources = resourceRequirements.get(AdminUIModel.REDIS_CONTAINER_NAME);
        assertThat(redisResources.getRequests().get("cpu").getAmount(), is("100m"));
        assertThat(redisResources.getRequests().get("memory").getAmount(), is("100Mi"));
        assertThat(redisResources.getLimits().get("cpu").getAmount(), is("100m"));
        assertThat(redisResources.getLimits().get("memory").getAmount(), is("100Mi"));
    }

    @Test
    public void testCustomResourceRequirements() {
        ResourceRequirements customResourceRequirements = new ResourceRequirementsBuilder()
                .addToRequests("memory", new Quantity("450Mi"))
                .addToLimits("cpu", new Quantity("100m"))
                .build();

        EventStreams eventStreamsResource = createDefaultEventStreams()
                .editSpec()
                    .editAdminUI()
                        .withResources(customResourceRequirements)
                    .endAdminUI()
                .endSpec()
                .build();
        AdminUIModel adminUIModel = new AdminUIModel(eventStreamsResource, imageConfig, false, null);

        List<Container> containerList = adminUIModel.getDeployment().getSpec().getTemplate().getSpec().getContainers();

        Map<String, ResourceRequirements> resourceRequirements = containerList.stream()
                .collect(Collectors.toMap(Container::getName, Container::getResources));


        ResourceRequirements schemaResources = resourceRequirements.get(AdminUIModel.COMPONENT_NAME);
        assertThat(schemaResources.getRequests().get("cpu").getAmount(), is("1000m"));
        assertThat(schemaResources.getRequests().get("memory").getAmount(), is("450Mi"));
        assertThat(schemaResources.getLimits().get("cpu").getAmount(), is("100m"));
        assertThat(schemaResources.getLimits().get("memory").getAmount(), is("1Gi"));

        ResourceRequirements avroResources = resourceRequirements.get(AdminUIModel.REDIS_CONTAINER_NAME);
        assertThat(avroResources.getRequests().get("cpu").getAmount(), is("100m"));
        assertThat(avroResources.getRequests().get("memory").getAmount(), is("100Mi"));
        assertThat(avroResources.getLimits().get("cpu").getAmount(), is("100m"));
        assertThat(avroResources.getLimits().get("memory").getAmount(), is("100Mi"));
    }

    @Test
    public void testImageOverride() {
        String uiImage = "ui-image:latest";
        String redisImage = "redis-image:latest";

        EventStreams instance = createDefaultEventStreams()
                .editSpec()
                    .editAdminUI()
                        .withImage(uiImage)
                        .withNewRedis()
                            .withImage(redisImage)
                        .endRedis()
                    .endAdminUI()
                .endSpec()
                .build();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(AdminUIModel.COMPONENT_NAME, uiImage);
        expectedImages.put(AdminUIModel.REDIS_CONTAINER_NAME, redisImage);

        List<Container> containers = new AdminUIModel(instance, imageConfig, false, null).getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }


    @Test
    public void testExternalAccessOverrideWithNodePort() {
        EventStreams instance = createDefaultEventStreamsWithExternalAccess(ExternalAccess.TYPE_NODEPORT).build();
        AdminUIModel adminUIModelK8s = new AdminUIModel(instance, imageConfig, false, null);
        assertThat(adminUIModelK8s.getService().getSpec().getType(), is("NodePort"));

        AdminUIModel adminUIModelOpenShift = new AdminUIModel(instance, imageConfig, true, null);
        assertThat(adminUIModelOpenShift.getService().getSpec().getType(), is("NodePort"));
    }

    @Test
    public void testExternalAccessOverrideWithRoutes() {
        EventStreams instance = createDefaultEventStreamsWithExternalAccess(ExternalAccess.TYPE_ROUTE).build();
        AdminUIModel adminUIModelK8s = new AdminUIModel(instance, imageConfig, false, null);
        assertThat(adminUIModelK8s.getService().getSpec().getType(), is("ClusterIP"));

        AdminUIModel adminUIModelOpenShift = new AdminUIModel(instance, imageConfig, true, null);
        assertThat(adminUIModelOpenShift.getService().getSpec().getType(), is("ClusterIP"));
    }

    @Test
    public void testDefaultServiceType() {
        EventStreams instance = createDefaultEventStreams().build();
        AdminUIModel adminUIModelK8s = new AdminUIModel(instance, imageConfig, false, null);
        assertThat(adminUIModelK8s.getService().getSpec().getType(), is("ClusterIP"));

        AdminUIModel adminUIModelOpenShift = new AdminUIModel(instance, imageConfig, true, null);
        assertThat(adminUIModelOpenShift.getService().getSpec().getType(), is("ClusterIP"));
    }

    @Test
    public void testOperatorImageOverride() {
        String uiImage = "ui-image:latest";
        String redisImage = "redis-image:latest";

        when(imageConfig.getAdminUIImage()).thenReturn(Optional.of(uiImage));
        when(imageConfig.getAdminUIRedisImage()).thenReturn(Optional.of(redisImage));

        AdminUIModel adminUI = createDefaultAdminUIModel();
        List<Container> containers = adminUI.getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(AdminUIModel.COMPONENT_NAME, uiImage);
        expectedImages.put(AdminUIModel.REDIS_CONTAINER_NAME, redisImage);

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testOperatorImageOverrideTakesPrecedenceOverComponentLevelOverride() {
        String uiImage = "component-ui-image:latest";
        String redisImage = "component-redis-image:latest";

        String uiImageFromEnv = "env-ui-image:latest";
        String redisImageFromEnv = "env-redis-image:latest";

        when(imageConfig.getAdminUIImage()).thenReturn(Optional.of(uiImageFromEnv));
        when(imageConfig.getAdminUIRedisImage()).thenReturn(Optional.of(redisImageFromEnv));

        EventStreams instance = createDefaultEventStreams()
                .editSpec()
                    .editAdminUI()
                        .withImage(uiImage)
                        .withNewRedis()
                            .withImage(redisImage)
                        .endRedis()
                    .endAdminUI()
                .endSpec()
                .build();


        AdminUIModel adminUIModel = new AdminUIModel(instance, imageConfig, false, null);
        assertThat(adminUIModel.getImage(), is(uiImage));
        assertTrue(adminUIModel.getCustomImage());

        List<Container> containers = adminUIModel.getDeployment().getSpec().getTemplate().getSpec().getContainers();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(AdminUIModel.COMPONENT_NAME, uiImage);
        expectedImages.put(AdminUIModel.REDIS_CONTAINER_NAME, redisImage);

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
                .editAdminUI()
                    .editOrNewTemplate()
                        .withPod(new PodTemplateBuilder()
                                        .withImagePullSecrets(imagePullSecretOverride)
                                        .build()
                        )
                    .endTemplate()
                .endAdminUI()
            .endSpec()
            .build();

        assertThat(new AdminUIModel(eventStreams, imageConfig, false, null).getServiceAccount()
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

        assertThat(new AdminUIModel(eventStreams, imageConfig, false, null).getServiceAccount()
                        .getImagePullSecrets(), contains(imagePullSecretOverride));
    }

    @Test
    public void testOperatorImagePullSecretOverride() {
        LocalObjectReference imagePullSecret = new LocalObjectReferenceBuilder()
                .withName("operator-image-pull-secret")
                .build();
        when(imageConfig.getPullSecrets()).thenReturn(Collections.singletonList(imagePullSecret));

        assertThat(createDefaultAdminUIModel().getServiceAccount().getImagePullSecrets(),
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
                .editAdminUI()
                    .withNewTemplate()
                        .withPod(new PodTemplateBuilder().withImagePullSecrets(componentPullSecretOverride).build())
                    .endTemplate()
                .endAdminUI()
            .endSpec()
            .build();

        assertThat(new AdminUIModel(eventStreams, imageConfig, false, null).getServiceAccount()
                        .getImagePullSecrets(), containsInAnyOrder(globalPullSecretOverride, componentPullSecretOverride));
    }


    @Test
    public void testCreateAdminUIRouteWithDefaultTlsEncryption() {
        EventStreams eventStreams = createDefaultEventStreams().build();

        AdminUIModel ui = new AdminUIModel(eventStreams, imageConfig, false, null);

        String expectedServiceCertName = "test-instance-ibm-es-admin-ui-service-cert";

        assertThat(ui.getRoute().getSpec().getTls().getTermination(), is("reencrypt"));
        assertThat(ui.getService().getMetadata().getAnnotations(),
                is(Collections.singletonMap("service.beta.openshift.io/serving-cert-secret-name", expectedServiceCertName)));
        assertThat(ui.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv(),
                hasItems(new EnvVarBuilder()
                                .withName("TLS_CERT")
                                .withNewValueFrom()
                                .withNewSecretKeyRef()
                                .withName(expectedServiceCertName)
                                .withKey("tls.crt")
                                .endSecretKeyRef()
                                .endValueFrom()
                                .build(),
                        new EnvVarBuilder()
                                .withName("TLS_KEY")
                                .withNewValueFrom()
                                .withNewSecretKeyRef()
                                .withName(expectedServiceCertName)
                                .withKey("tls.key")
                                .endSecretKeyRef()
                                .endValueFrom()
                                .build()));
    }

    @Test
    public void testDefaultLogging() {
        EventStreams eventStreams = createDefaultEventStreams().build();

        List<EnvVar> envVars = new AdminUIModel(eventStreams, imageConfig, false, null).getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        EnvVar traceEnvVar = envVars.stream().filter(var -> var.getName() == AdminUIModel.TRACE_STATE).findFirst().orElseGet(EnvVar::new);
        assertThat(traceEnvVar.getValue(), is("ExpressApp;INFO,Simulated;INFO,KubernetesClient;INFO"));
    }

    @Test
    public void testOverrideLoggingInline() {
        Map<String, String> loggers = new HashMap<>();
        loggers.put("logger.one", "INFO");
        loggers.put("logger.two", "DEBUG");
        InlineLogging logging = new InlineLogging();
        logging.setLoggers(loggers);

        EventStreams eventStreams = createDefaultEventStreams()
                .editSpec()
                    .editAdminUI()
                        .withLogging(logging)
                    .endAdminUI()
                .endSpec()
                .build();

        List<EnvVar> envVars = new AdminUIModel(eventStreams, imageConfig, false, null).getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        EnvVar traceEnvVar = envVars.stream().filter(var -> var.getName() == AdminUIModel.TRACE_STATE).findFirst().orElseGet(EnvVar::new);
        assertThat(traceEnvVar.getValue(), is("logger.one;INFO,logger.two;DEBUG"));
    }

    @Test
    public void testOverrideLoggingExternalIsIgnored() {
        ExternalLogging logging = new ExternalLogging();

        EventStreams eventStreams = createDefaultEventStreams()
                .editSpec()
                    .editAdminUI()
                        .withLogging(logging)
                    .endAdminUI()
                .endSpec()
                .build();

        List<EnvVar> envVars = new AdminUIModel(eventStreams, imageConfig, false, null).getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        EnvVar traceEnvVar = envVars.stream().filter(var -> var.getName() == AdminUIModel.TRACE_STATE).findFirst().orElseGet(EnvVar::new);
        assertThat(traceEnvVar.getValue(), is("ExpressApp;INFO,Simulated;INFO,KubernetesClient;INFO"));
    }

    @Test
    public void testMetricsEnvVarTrueWhenUsingKafkaInterceptor() {
        Map<String, Object> config = new HashMap<>();
        config.put("interceptor.class.names", "com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor");
        
        EventStreams eventStreams = createDefaultEventStreams()
            .editSpec()
                .withStrimziOverrides(
                    new KafkaSpecBuilder()
                        .withNewKafka()
                            .withMetrics(new HashMap<>())
                            .withConfig(config)
                        .endKafka()
                    .build())
            .endSpec()
            .build();

        List<EnvVar> envVars = new AdminUIModel(eventStreams, imageConfig, false, null).getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        EnvVar producerMetricsEnvVar = envVars.stream().filter(var -> var.getName() == "PRODUCER_METRICS_ENABLED").findFirst().orElseGet(EnvVar::new);
        EnvVar metricsEnvVar = envVars.stream().filter(var -> var.getName() == "METRICS_ENABLED").findFirst().orElseGet(EnvVar::new);
        assertThat(producerMetricsEnvVar.getValue(), is("true"));
        assertThat(metricsEnvVar.getValue(), is("true"));


        
    }
    @Test
    public void testMetricsEnvVarFalseTrueWhenNoKafkaInterceptor() {
        EventStreams eventStreams = createDefaultEventStreams()
            .build();

        List<EnvVar> envVars = new AdminUIModel(eventStreams, imageConfig, false, null).getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        EnvVar producerMetricsEnvVar = envVars.stream().filter(var -> var.getName() == "PRODUCER_METRICS_ENABLED").findFirst().orElseGet(EnvVar::new);
        EnvVar metricsEnvVar = envVars.stream().filter(var -> var.getName() == "METRICS_ENABLED").findFirst().orElseGet(EnvVar::new);
        assertThat(producerMetricsEnvVar.getValue(), is("false"));
        assertThat(metricsEnvVar.getValue(), is("false"));
     
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
}