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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ibm.eventstreams.api.Labels;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;

import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserSpec;
import io.strimzi.api.kafka.model.KafkaUserSpecBuilder;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.HTTPHeaderBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;


public class AbstractModelTest {

    private final String instanceName = "test-instance";

    // Extend AbstractModel to test the abstract class
    private class ComponentModel extends AbstractModel {
        private static final String COMPONENT_NAME = "test-component";

        public ComponentModel(EventStreams instance) {
            super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), COMPONENT_NAME);
            setArchitecture(instance.getSpec().getArchitecture());
            setOwnerReference(instance);
        }
    }

    @Test
    public void testDefaultGetArchitecture() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance);
        assertThat(model.getArchitecture(), is("amd64"));
    }

    @Test
    public void testCustomGetArchitecture() {
        String customArchitecture = "Z80";
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName)
                .editSpec()
                    .withArchitecture(customArchitecture)
                .endSpec()
                .build();
        ComponentModel model = new ComponentModel(instance);

        assertThat(model.getArchitecture(), is(customArchitecture));
    }

    @Test
    public void testGetResourcePrefix() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance);
        assertThat(model.getResourcePrefix(), is("test-instance-ibm-es"));
    }

    @Test
    public void testGetEventStreamsOwnerReference() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance);

        OwnerReference expectedOwnerReference = new OwnerReferenceBuilder()
                .withApiVersion(instance.getApiVersion())
                .withKind(instance.getKind())
                .withName(instance.getMetadata().getName())
                .withUid(null)
                .withBlockOwnerDeletion(false)
                .withController(false)
                .build();

        assertThat(model.getEventStreamsOwnerReference(), is(expectedOwnerReference));
    }
    @Test
    public void testCreateDeploymentHasRequiredLabels() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance);

        Map<String, String> expectedLabels = new HashMap<>();
        expectedLabels.put(Labels.APP_LABEL, AbstractModel.APP_NAME);
        expectedLabels.put(Labels.COMPONENT_LABEL, ComponentModel.COMPONENT_NAME);
        expectedLabels.put(Labels.INSTANCE_LABEL, instanceName);
        expectedLabels.put(Labels.RELEASE_LABEL, instanceName);
        expectedLabels.put(Labels.KUBERNETES_NAME_LABEL, Labels.KUBERNETES_NAME);
        expectedLabels.put(Labels.KUBERNETES_INSTANCE_LABEL, instanceName);
        expectedLabels.put(Labels.KUBERNETES_MANAGED_BY_LABEL, Labels.KUBERNETES_MANAGED_BY);
        expectedLabels.put(Labels.NAME_LABEL, instanceName + "-" + AbstractModel.APP_NAME + "-" + ComponentModel.COMPONENT_NAME);
        expectedLabels.put(Labels.SERVICE_SELECTOR_LABEL, ComponentModel.COMPONENT_NAME);

        Deployment deployment = model.createDeployment(new ArrayList<>(), null);
        assertThat(deployment.getSpec().getTemplate().getMetadata().getLabels(), is(expectedLabels));
    }

    @Test
    public void testCreateDeploymentTemplatesContainers() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance);

        List<Container> containers = new ArrayList<>();
        containers.add(new ContainerBuilder()
                .withNewName("a-container")
                .withNewImage("a-container-image")
                .build());
        containers.add(new ContainerBuilder()
                .withNewName("another-container")
                .withNewImage("another-container-image")
                .build());

        Deployment deployment = model.createDeployment(containers, null);
        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers(), is(containers));
    }

    @Test
    public void testCreateDeploymentTemplatesVolumes() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance);

        List<Volume> volumes = new ArrayList<>();
        volumes.add(new VolumeBuilder()
                .withNewName("a-volume")
                .build());
        volumes.add(new VolumeBuilder()
                .withNewName("another-volume")
                .build());

        Deployment deployment = model.createDeployment(null, volumes);
        assertThat(deployment.getSpec().getTemplate().getSpec().getVolumes(), is(volumes));
    }

    @Test
    public void testCreateKafkaUserReturnsValidKafkaUser() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance);

        String kafkaUserName = "a-user";
        KafkaUserSpec spec = new KafkaUserSpecBuilder().build();

        KafkaUser kafkaUser = model.createKafkaUser(kafkaUserName, spec);

        assertThat(kafkaUser.getSpec(), is(spec));

        Map<String, String> labels = kafkaUser.getMetadata().getLabels();
        for (Map.Entry<String, String> label : labels.entrySet()) {
            if (!label.getKey().equals(io.strimzi.operator.common.model.Labels.STRIMZI_CLUSTER_LABEL)) {
                assertThat(label.getKey(), not(containsString(io.strimzi.operator.common.model.Labels.STRIMZI_DOMAIN)));
            }
        }
    }

    @Test
    public void testCombineProbeDefinitionsWithNullOverridesReturnsSame() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance);

        Probe probe = new ProbeBuilder()
                .withNewHttpGet()
                    .withPath("/liveness_path")
                    .withNewPort(1000)
                    .withScheme("HTTP")
                    .withHttpHeaders(new HTTPHeaderBuilder()
                            .withName("Accept")
                            .withValue("*/*")
                            .build())
                .endHttpGet()
                .withPeriodSeconds(2)
                .withSuccessThreshold(4)
                .withFailureThreshold(5)
                .build();

        Probe expectedProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/liveness_path")
                .withNewPort(1000)
                .withScheme("HTTP")
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withPeriodSeconds(2)
                .withSuccessThreshold(4)
                .withFailureThreshold(5)
                // Strimzi Defaults
                .withInitialDelaySeconds(15)
                .withTimeoutSeconds(5)
                .build();

        io.strimzi.api.kafka.model.Probe emptyProbe = new io.strimzi.api.kafka.model.Probe();

        Probe newCombinedProbe = model.combineProbeDefinitions(probe, emptyProbe);
        assertThat(newCombinedProbe, is(expectedProbe));
    }

    @Test
    public void testCombineProbeDefinitionsWithSeveralFields() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance);

        Probe probe = new ProbeBuilder()
                .withNewHttpGet()
                    .withPath("/liveness_path")
                    .withNewPort(1000)
                    .withScheme("HTTP")
                    .withHttpHeaders(new HTTPHeaderBuilder()
                            .withName("Accept")
                            .withValue("*/*")
                            .build())
                .endHttpGet()
                .withInitialDelaySeconds(1)
                .withPeriodSeconds(2)
                .withTimeoutSeconds(3)
                .withSuccessThreshold(4)
                .withFailureThreshold(5)
                .build();

        io.strimzi.api.kafka.model.Probe overridesProbe = new io.strimzi.api.kafka.model.ProbeBuilder()
                .withInitialDelaySeconds(101)
                .withPeriodSeconds(102)
                .withTimeoutSeconds(103)
                .build();

        Probe expectedProbe = new ProbeBuilder()
                .withNewHttpGet()
                    .withPath("/liveness_path")
                    .withNewPort(1000)
                    .withScheme("HTTP")
                    .withHttpHeaders(new HTTPHeaderBuilder()
                            .withName("Accept")
                            .withValue("*/*")
                            .build())
                .endHttpGet()
                .withInitialDelaySeconds(101)
                .withPeriodSeconds(102)
                .withTimeoutSeconds(103)
                .withSuccessThreshold(4)
                .withFailureThreshold(5)
                .build();

        Probe newCombinedProbe = model.combineProbeDefinitions(probe, overridesProbe);
        assertThat(newCombinedProbe, is(expectedProbe));
    }

    @Test
    public void testCombineProbeDefinitionsWithAllFields() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance);

        Probe probe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/liveness_path")
                .withNewPort(1000)
                .withScheme("HTTP")
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(1)
                .withPeriodSeconds(2)
                .withTimeoutSeconds(3)
                .withSuccessThreshold(4)
                .withFailureThreshold(5)
                .build();

        io.strimzi.api.kafka.model.Probe overridesProbe = new io.strimzi.api.kafka.model.ProbeBuilder()
                .withInitialDelaySeconds(101)
                .withPeriodSeconds(102)
                .withTimeoutSeconds(103)
                .withSuccessThreshold(104)
                .withFailureThreshold(105)
                .build();

        Probe expectedProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/liveness_path")
                .withNewPort(1000)
                .withScheme("HTTP")
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(101)
                .withPeriodSeconds(102)
                .withTimeoutSeconds(103)
                .withSuccessThreshold(104)
                .withFailureThreshold(105)
                .build();

        Probe newCombinedProbe = model.combineProbeDefinitions(probe, overridesProbe);
        assertThat(newCombinedProbe, is(expectedProbe));
    }
}