/*
 * IBM Confidential
 * OCO Source Materials
 *
 * 5737-H33
 *
 * (C) Copyright IBM Corp. 2020  All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 */
package com.ibm.eventstreams.controller;

import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenersBuilder;
import io.strimzi.api.kafka.model.storage.EphemeralStorage;
import io.strimzi.test.k8s.KubeClusterResource;
import io.strimzi.test.k8s.exceptions.KubeClusterException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static io.strimzi.test.k8s.KubeClusterResource.cmdKubeClient;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * This integration test verifies the installation of various forms of the Event Streams custom resource.
 *
 * NOTE: These tests need a running environment.
 */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventStreamsIT {

    private static final String NAMESPACE = "es-it";
    private static final String EVENTSTREAMS_CUSTOM_RESOURCE_DEFINITION = System.getProperty("installDirectory") + "/cluster-operator/140-Crd-EventStreams.yaml";
    private static final String API_VERSION = "eventstreams.ibm.com/v1beta1";
    private static final String NAME = "my-es";
    private static final String ARCHITECTURE = "amd64";

    private KafkaSpec validKafkaSpec;
    private KubeClusterResource cluster = KubeClusterResource.getInstance();

    private void createDeleteResource(EventStreams instance) {
        RuntimeException createResourceException = null;
        RuntimeException deleteResourceException = null;

        String resourceName = instance.toString();

        try {
            cmdKubeClient().applyContent(resourceName);
        } catch (RuntimeException t) {
            createResourceException = t;
        } finally {
            try {
                cmdKubeClient().deleteContent(resourceName);
            } catch (RuntimeException t) {
                deleteResourceException = t;
            }
        }

        if (createResourceException != null) {
            if (deleteResourceException != null) {
                createResourceException.addSuppressed(deleteResourceException);
            }
            throw createResourceException;
        } else if (deleteResourceException != null) {
            throw deleteResourceException;
        }
    }


    @BeforeAll
    void setupEnvironment() {
        cluster.createNamespace(NAMESPACE);
        cluster.createCustomResources(EVENTSTREAMS_CUSTOM_RESOURCE_DEFINITION);

        validKafkaSpec = new KafkaSpecBuilder()
            .withNewKafka()
                .withReplicas(1)
                .withListeners(new KafkaListenersBuilder().withNewPlain().endPlain().withNewTls().endTls().build())
                .withEphemeralStorage(new EphemeralStorage())
            .endKafka()
            .withNewZookeeper()
                .withReplicas(1)
                .withEphemeralStorage(new EphemeralStorage())
            .endZookeeper()
            .build();
    }

    @AfterAll
    void tearDownEnvironment() {
        cluster.deleteCustomResources();
        cluster.deleteNamespaces();
    }

    @Test
    void testMinimalValidPods() {
        EventStreams instance = new EventStreamsBuilder()
            .withApiVersion(API_VERSION)
            .withMetadata(new ObjectMetaBuilder().withName(NAME).build())
            .withNewSpec()
                .withArchitecture(ARCHITECTURE)
            .endSpec()
            .build();

        createDeleteResource(instance);
    }

    @Test
    void testValidAllPods() {
        EventStreams instance = new EventStreamsBuilder()
            .withApiVersion(API_VERSION)
            .withMetadata(new ObjectMetaBuilder().withName(NAME).build())
            .withNewSpec()
                .withArchitecture(ARCHITECTURE)
                .withNewAdminApi()
                    .withReplicas(1)
                .endAdminApi()
                .withNewAdminProxy()
                    .withReplicas(1)
                .endAdminProxy()
                .withNewRestProducer()
                    .withReplicas(1)
                .endRestProducer()
                .withNewAdminUI()
                    .withReplicas(1)
                .endAdminUI()
                .withNewSchemaRegistry()
                    .withReplicas(1)
                .endSchemaRegistry()
                .withNewCollector()
                    .withReplicas(1)
                .endCollector()
                .withStrimziOverrides(validKafkaSpec)
            .endSpec()
            .build();

        createDeleteResource(instance);
    }

    @Test
    void testKafkaZeroReplicas() {
        KafkaSpec invalidKafkaReplicasKafkaSpec = new KafkaSpecBuilder()
            .withNewKafka()
                .withReplicas(0)
                .withListeners(new KafkaListenersBuilder().withNewPlain().endPlain().withNewTls().endTls().build())
                .withEphemeralStorage(new EphemeralStorage())
            .endKafka()
            .build();

        EventStreams instance = new EventStreamsBuilder()
            .withApiVersion(API_VERSION)
            .withMetadata(new ObjectMetaBuilder().withName(NAME).build())
            .withNewSpec()
                .withArchitecture(ARCHITECTURE)
                .withStrimziOverrides(invalidKafkaReplicasKafkaSpec)
            .endSpec()
            .build();

        assertThrows(KubeClusterException.InvalidResource.class, () -> createDeleteResource(instance));
    }

    @Test
    void testZooKeeperZeroReplicas() {
        KafkaSpec invalidZookeeperReplicasKafkaSpec = new KafkaSpecBuilder()
            .withNewZookeeper()
                .withReplicas(0)
                .withEphemeralStorage(new EphemeralStorage())
            .endZookeeper()
            .build();

        EventStreams instance = new EventStreamsBuilder()
            .withApiVersion(API_VERSION)
            .withMetadata(new ObjectMetaBuilder().withName(NAME).build())
            .withNewSpec()
                .withArchitecture(ARCHITECTURE)
                .withStrimziOverrides(invalidZookeeperReplicasKafkaSpec)
            .endSpec()
            .build();

        assertThrows(KubeClusterException.InvalidResource.class, () -> createDeleteResource(instance));
    }

    @Test
    void testAdminApiZeroReplicas() {
        EventStreams instance = new EventStreamsBuilder()
            .withApiVersion(API_VERSION)
            .withMetadata(new ObjectMetaBuilder().withName(NAME).build())
            .withNewSpec()
                .withArchitecture(ARCHITECTURE)
                .withNewAdminApi()
                    .withReplicas(0)
                .endAdminApi()
            .endSpec()
            .build();

        assertThrows(KubeClusterException.InvalidResource.class, () -> createDeleteResource(instance));
    }

    @Test
    void testRestProducerZeroReplicas() {
        EventStreams instance = new EventStreamsBuilder()
            .withApiVersion(API_VERSION)
            .withMetadata(new ObjectMetaBuilder().withName(NAME).build())
            .withNewSpec()
                .withArchitecture(ARCHITECTURE)
                .withNewRestProducer()
                    .withReplicas(0)
                .endRestProducer()
            .endSpec()
            .build();

        assertThrows(KubeClusterException.InvalidResource.class, () -> createDeleteResource(instance));
    }

    @Test
    void testAdminProxyZeroReplicas() {
        EventStreams instance = new EventStreamsBuilder()
            .withApiVersion(API_VERSION)
            .withMetadata(new ObjectMetaBuilder().withName(NAME).build())
            .withNewSpec()
                .withArchitecture(ARCHITECTURE)
                .withNewAdminProxy()
                    .withReplicas(0)
                .endAdminProxy()
            .endSpec()
            .build();

        assertThrows(KubeClusterException.InvalidResource.class, () -> createDeleteResource(instance));
    }

    @Test
    void testAdminUIZeroReplicas() {
        EventStreams instance = new EventStreamsBuilder()
            .withApiVersion(API_VERSION)
            .withMetadata(new ObjectMetaBuilder().withName(NAME).build())
            .withNewSpec()
                .withArchitecture(ARCHITECTURE)
                .withNewAdminUI()
                    .withReplicas(0)
                .endAdminUI()
            .endSpec()
            .build();

        assertThrows(KubeClusterException.InvalidResource.class, () -> createDeleteResource(instance));
    }

    @Test
    void testSchemaRegistryZeroReplicas() {
        EventStreams instance = new EventStreamsBuilder()
            .withApiVersion(API_VERSION)
            .withMetadata(new ObjectMetaBuilder().withName(NAME).build())
            .withNewSpec()
                .withArchitecture(ARCHITECTURE)
                .withNewSchemaRegistry()
                    .withReplicas(0)
                .endSchemaRegistry()
            .endSpec()
            .build();

        assertThrows(KubeClusterException.InvalidResource.class, () -> createDeleteResource(instance));
    }

    @Test
    void testCollectorZeroReplicas() {
        EventStreams instance = new EventStreamsBuilder()
            .withApiVersion(API_VERSION)
            .withMetadata(new ObjectMetaBuilder().withName(NAME).build())
            .withNewSpec()
                .withArchitecture(ARCHITECTURE)
                .withNewCollector()
                    .withReplicas(0)
                .endCollector()
            .endSpec()
            .build();

        assertThrows(KubeClusterException.InvalidResource.class, () -> createDeleteResource(instance));
    }

}