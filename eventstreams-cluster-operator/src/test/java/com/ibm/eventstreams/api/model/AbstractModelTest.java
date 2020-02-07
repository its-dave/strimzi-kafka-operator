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
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.HTTPHeaderBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorage;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorageBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


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

    public void testCreatePersistentVolumeClaimWithValidStorage() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance);

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

        PersistentVolumeClaim pvc = model.createPersistentVolumeClaim("test-pvc", storage);

        assertThat(pvc.getSpec().getStorageClassName(), is(storageClass));
        assertThat(pvc.getSpec().getResources().getRequests(), is(expectedStorageRequest));
        assertThat(pvc.getSpec().getSelector(), is(new LabelSelector(new ArrayList<>(), selector)));
        assertThat("Owner Reference should be empty by default so that pvcs are not deleted",
                pvc.getMetadata().getOwnerReferences(), is(new ArrayList<>()));
    }

    @Test
    public void testCreatePersistentVolumeClaimWithDeleteClaim() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance);

        PersistentClaimStorage storage = new PersistentClaimStorageBuilder()
                .withDeleteClaim(true)
                .build();

        PersistentVolumeClaim pvc = model.createPersistentVolumeClaim("test-pvc", storage);
        assertThat("Owner Reference should be empty by default so that pvcs are not deleted",
                pvc.getMetadata().getOwnerReferences(),
                is(Collections.singletonList(model.getEventStreamsOwnerReference())));
    }
}