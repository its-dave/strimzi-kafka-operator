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

import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.strimzi.api.kafka.model.ExternalLogging;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.template.PodTemplateBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CollectorModelTest {

    private final String instanceName = "test";
    private final int defaultReplicas = 1;

    @Mock
    private EventStreamsOperatorConfig.ImageLookup imageConfig;

    private EventStreamsBuilder createDefaultEventStreams() {
        return ModelUtils.createDefaultEventStreams(instanceName)
                .editSpec()
                    .withNewCollector()
                        .withReplicas(defaultReplicas)
                    .endCollector()
                .endSpec();
    }

    private CollectorModel createDefaultCollectorModel() {
        EventStreams eventStreamsResource = createDefaultEventStreams().build();
        return new CollectorModel(eventStreamsResource, imageConfig);
    }

    @Test
    public void testDefaultResourceRequirements() {
        CollectorModel collectorModel = createDefaultCollectorModel();

        ResourceRequirements resourceRequirements = collectorModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getResources();
        assertThat(resourceRequirements.getRequests().get("cpu").getAmount(), is("100m"));
        assertThat(resourceRequirements.getRequests().get("memory").getAmount(), is("50Mi"));
        assertThat(resourceRequirements.getLimits().get("cpu").getAmount(), is("100m"));
        assertThat(resourceRequirements.getLimits().get("memory").getAmount(), is("50Mi"));
    }

    @Test
    public void testCustomResourceRequirements() {
        ResourceRequirements customResourceRequirements = new ResourceRequirementsBuilder()
                .addToRequests("memory", new Quantity("450Mi"))
                .addToLimits("cpu", new Quantity("100m"))
                .build();
        EventStreams eventStreamsResource = createDefaultEventStreams()
                .editSpec()
                    .editCollector()
                        .withResources(customResourceRequirements)
                    .endCollector()
                .endSpec()
                .build();
        CollectorModel collectorModel = new CollectorModel(eventStreamsResource, imageConfig);

        ResourceRequirements resourceRequirements = collectorModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getResources();
        assertThat(resourceRequirements.getRequests().get("cpu").getAmount(), is("100m"));
        assertThat(resourceRequirements.getRequests().get("memory").getAmount(), is("450Mi"));
        assertThat(resourceRequirements.getLimits().get("cpu").getAmount(), is("100m"));
        assertThat(resourceRequirements.getLimits().get("memory").getAmount(), is("50Mi"));
    }

    @Test
    public void testImageOverride() {
        String collectorImage = "collector-image:latest";

        EventStreams instance = createDefaultEventStreams()
                .editSpec()
                    .editCollector()
                        .withImage(collectorImage)
                    .endCollector()
                .endSpec()
                .build();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(CollectorModel.COMPONENT_NAME, collectorImage);

        List<Container> containers = new CollectorModel(instance, imageConfig).getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testOperatorImageOverride() {
        String collectorImage = "collector-image:latest";

        when(imageConfig.getCollectorImage()).thenReturn(Optional.of(collectorImage));

        CollectorModel model = createDefaultCollectorModel();
        List<Container> containers = model.getDeployment().getSpec().getTemplate()
                .getSpec().getContainers();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(CollectorModel.COMPONENT_NAME, collectorImage);

        ModelUtils.assertCorrectImageOverridesOnContainers(containers, expectedImages);
    }

    @Test
    public void testOperatorImageOverrideTakesPrecedenceOverComponentLevelOverride() {
        String collectorImage = "collector-image:latest";
        String collectorImageFromEnv = "env-collector-image:latest";

        when(imageConfig.getCollectorImage()).thenReturn(Optional.of(collectorImageFromEnv));

        EventStreams instance = createDefaultEventStreams()
                .editSpec()
                    .editCollector()
                        .withImage(collectorImage)
                    .endCollector()
                .endSpec()
                .build();

        CollectorModel collectorModel = new CollectorModel(instance, imageConfig);
        assertThat(collectorModel.getImage(), is(collectorImage));
        assertTrue(collectorModel.getCustomImage());

        List<Container> containers = collectorModel.getDeployment().getSpec().getTemplate().getSpec().getContainers();

        Map<String, String> expectedImages = new HashMap<>();
        expectedImages.put(CollectorModel.COMPONENT_NAME, collectorImage);

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
                .editCollector()
                    .editOrNewTemplate()
                        .withPod(new PodTemplateBuilder()
                                        .withImagePullSecrets(imagePullSecretOverride)
                                        .build()
                        )
                    .endTemplate()
                .endCollector()
            .endSpec()
            .build();

        assertThat(new CollectorModel(eventStreams, imageConfig).getServiceAccount()
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

        assertThat(new CollectorModel(eventStreams, imageConfig).getServiceAccount()
                        .getImagePullSecrets(), contains(imagePullSecretOverride));
    }

    @Test
    public void testOperatorImagePullSecretOverride() {
        LocalObjectReference imagePullSecret = new LocalObjectReferenceBuilder()
                .withName("operator-image-pull-secret")
                .build();
        when(imageConfig.getPullSecrets()).thenReturn(Collections.singletonList(imagePullSecret));

        assertThat(createDefaultCollectorModel().getServiceAccount().getImagePullSecrets(),
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
                .editCollector()
                    .withNewTemplate()
                        .withPod(new PodTemplateBuilder().withImagePullSecrets(componentPullSecretOverride).build())
                    .endTemplate()
                .endCollector()
            .endSpec()
            .build();

        assertThat(new CollectorModel(eventStreams, imageConfig).getServiceAccount()
                        .getImagePullSecrets(), containsInAnyOrder(globalPullSecretOverride, componentPullSecretOverride));
    }

    @Test
    public void testTlsVersionEnvValue() {
        EventStreams defaultEs = createDefaultEventStreams().build();
        CollectorModel collectorModel = new CollectorModel(defaultEs, imageConfig);

        EnvVar expectedEnvVar = new EnvVarBuilder()
            .withName(AbstractModel.TLS_VERSION_ENV_KEY)
            .withValue("TLSv1.2")
            .build();

        List<EnvVar> envVars = collectorModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        assertThat(envVars, hasItem(expectedEnvVar));
    }

    @Test
    public void testDefaultLogging() {
        EventStreams defaultEs = createDefaultEventStreams().build();
        CollectorModel collectorModel = new CollectorModel(defaultEs, imageConfig);

        EnvVar expectedEnvVar = new EnvVarBuilder()
                .withName("TRACE_LEVEL")
                .withValue("0")
                .build();

        List<EnvVar> envVars = collectorModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        assertThat(envVars, hasItem(expectedEnvVar));
    }

    @Test
    public void testOverrideLoggingInLine() {
        Map<String, String> loggers = new HashMap<>();
        loggers.put("logger.one", "2");
        loggers.put("logger.two", "0");
        InlineLogging logging = new InlineLogging();
        logging.setLoggers(loggers);

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                    .editCollector()
                        .withLogging(logging)
                    .endCollector()
                .endSpec()
                .build();
        CollectorModel collectorModel = new CollectorModel(defaultEs, imageConfig);

        EnvVar expectedEnvVar = new EnvVarBuilder()
                .withName("TRACE_LEVEL")
                .withValue("2")
                .build();

        List<EnvVar> envVars = collectorModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        assertThat(envVars, hasItem(expectedEnvVar));
    }

    @Test
    public void testUsesDefaultLoggingIfNoLoggers() {
        InlineLogging logging = new InlineLogging();

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                    .editCollector()
                        .withLogging(logging)
                    .endCollector()
                .endSpec()
                .build();
        CollectorModel collectorModel = new CollectorModel(defaultEs, imageConfig);

        EnvVar expectedEnvVar = new EnvVarBuilder()
                .withName("TRACE_LEVEL")
                .withValue("0")
                .build();

        List<EnvVar> envVars = collectorModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        assertThat(envVars, hasItem(expectedEnvVar));
    }

    @Test
    public void testOverrideLoggingExternalIsIgnored() {
        ExternalLogging logging =  new ExternalLogging();

        EventStreams defaultEs = createDefaultEventStreams()
                .editSpec()
                    .editCollector()
                        .withLogging(logging)
                    .endCollector()
                .endSpec()
                .build();
        CollectorModel collectorModel = new CollectorModel(defaultEs, imageConfig);

        EnvVar expectedEnvVar = new EnvVarBuilder()
                .withName("TRACE_LEVEL")
                .withValue("0")
                .build();

        List<EnvVar> envVars = collectorModel.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        assertThat(envVars, hasItem(expectedEnvVar));
    }
    @Test
    public void testAnnotationsPresentWhenKafkaIsUsingInterceptor() {
        Map<String, Object> config = new HashMap<>();
        config.put("interceptor.class.names", "com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor");
        
        EventStreams es = createDefaultEventStreams()
            .editSpec()
                .withStrimziOverrides(
                    new KafkaSpecBuilder()
                        .withNewKafka()
                            .withConfig(config)
                        .endKafka()
                    .build())
            .endSpec()
            .build();

        CollectorModel collector = new CollectorModel(es, imageConfig);
        assertThat(collector.getService().getMetadata().getAnnotations().get("prometheus.io/scrape"), is("true"));
        assertThat(collector.getService().getMetadata().getAnnotations().get("prometheus.io/port"), is(String.valueOf(CollectorModel.METRICS_PORT)));
        assertThat(collector.getService().getMetadata().getAnnotations().get("prometheus.io/path"), is("/metrics"));
    }
    @Test
    public void testAnnotationsNotPresentWhenNoKafkaInterceptor() {
        EventStreams es = createDefaultEventStreams()
            .build();

        CollectorModel collector = new CollectorModel(es, imageConfig);
        assertThat(collector.getService().getMetadata().getAnnotations().get("prometheus.io/scrape"), is(nullValue()));
        assertThat(collector.getService().getMetadata().getAnnotations().get("prometheus.io/port"), is(nullValue()));
        assertThat(collector.getService().getMetadata().getAnnotations().get("prometheus.io/path"), is(nullValue()));
    }
}
