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

package com.ibm.eventstreams.controller;

import io.strimzi.operator.cluster.ClusterOperatorConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class EventStreamsOperatorConfigTest {

    @Test
    public void testCreatesImageLookupFromEnvMap() {

        Map<String, String> envMap = Stream
            .of(new String[][]{
                {EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_ADMIN_API_IMAGE, "api"},
                {EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_ADMIN_UI_IMAGE, "ui"},
                {EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_ADMIN_UI_REDIS_IMAGE, "ui-redis" },
                {EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_COLLECTOR_IMAGE, "collector"},
                {EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_REST_PRODUCER_IMAGE, "rest_producer"},
                {EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_AVRO_IMAGE, "schema_avro"},
                {EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_IMAGE, "schema"}
            })
            .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));

        EventStreamsOperatorConfig eventStreamsOperatorConfig = EventStreamsOperatorConfig.fromMap(envMap);

        EventStreamsOperatorConfig.ImageLookup imageConfig = eventStreamsOperatorConfig.getImages();

        assertThat(imageConfig.getAdminApiImage().get(),
                   is(envMap.get(EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_ADMIN_API_IMAGE)));
        assertThat(imageConfig.getAdminUIImage().get(),
                   is(envMap.get(EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_ADMIN_UI_IMAGE)));
        assertThat(imageConfig.getAdminUIRedisImage().get(),
                   is(envMap.get(EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_ADMIN_UI_REDIS_IMAGE)));
        assertThat(imageConfig.getCollectorImage().get(),
                   is(envMap.get(EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_COLLECTOR_IMAGE)));
        assertThat(imageConfig.getRestProducerImage().get(),
                   is(envMap.get(EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_REST_PRODUCER_IMAGE)));
        assertThat(imageConfig.getSchemaRegistryAvroImage().get(),
                   is(envMap.get(EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_AVRO_IMAGE)));
        assertThat(imageConfig.getSchemaRegistryImage().get(),
                   is(envMap.get(EventStreamsOperatorConfig.EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_IMAGE)));
    }

    @Test
    public void testImagesAreEmptyIfNotInEnvMap() {
        EventStreamsOperatorConfig eventStreamsOperatorConfig = EventStreamsOperatorConfig.fromMap(
            Collections.emptyMap());

        EventStreamsOperatorConfig.ImageLookup imageConfig = eventStreamsOperatorConfig.getImages();

        assertThat(imageConfig.getAdminApiImage(), is(Optional.empty()));
        assertThat(imageConfig.getAdminUIImage(), is(Optional.empty()));
        assertThat(imageConfig.getAdminUIRedisImage(), is(Optional.empty()));
        assertThat(imageConfig.getCollectorImage(), is(Optional.empty()));
        assertThat(imageConfig.getRestProducerImage(), is(Optional.empty()));
        assertThat(imageConfig.getSchemaRegistryAvroImage(), is(Optional.empty()));
        assertThat(imageConfig.getSchemaRegistryImage(), is(Optional.empty()));
    }

    @Test
    public void testEmptyPullSecretsIfNotProvided() {
        EventStreamsOperatorConfig eventStreamsOperatorConfig = EventStreamsOperatorConfig.fromMap(Collections.emptyMap());
        EventStreamsOperatorConfig.ImageLookup imageConfig = eventStreamsOperatorConfig.getImages();

        assertThat(imageConfig.getPullSecrets(), is(empty()));
    }

    @Test
    public void testPullSecretsIfProvided() {
        Map<String, String> envMap = new HashMap<>();
        envMap.put(ClusterOperatorConfig.STRIMZI_IMAGE_PULL_SECRETS, "first,second,third");
        EventStreamsOperatorConfig eventStreamsOperatorConfig = EventStreamsOperatorConfig.fromMap(envMap);
        EventStreamsOperatorConfig.ImageLookup imageConfig = eventStreamsOperatorConfig.getImages();

        assertThat(imageConfig.getPullSecrets(), hasSize(3));
        assertThat(imageConfig.getPullSecrets(), contains(hasProperty("name", is("first")),
                                                          hasProperty("name", is("second")),
                                                          hasProperty("name", is("third"))));
    }

    @Test
    public void testDefaultDependencyStatusChecks() {
        Map<String, String> envMap = new HashMap<>();
        EventStreamsOperatorConfig eventStreamsOperatorConfig = EventStreamsOperatorConfig.fromMap(envMap);

        assertThat(eventStreamsOperatorConfig.getDependencyStatusChecks(), hasSize(1));
        assertThat(eventStreamsOperatorConfig.getDependencyStatusChecks(), hasItems("iamstatus"));
    }

    @Test
    public void testEmptyDependencyStatusChecks() {
        Map<String, String> envMap = new HashMap<>();
        envMap.put(EventStreamsOperatorConfig.EVENTSTREAMS_DEPENDENCY_STATUS_CHECKS, "");
        EventStreamsOperatorConfig eventStreamsOperatorConfig = EventStreamsOperatorConfig.fromMap(envMap);

        assertThat(eventStreamsOperatorConfig.getDependencyStatusChecks(), hasSize(0));
    }

    @Test
    public void testCustomDependencyStatusChecks() {
        Map<String, String> envMap = new HashMap<>();
        envMap.put(EventStreamsOperatorConfig.EVENTSTREAMS_DEPENDENCY_STATUS_CHECKS, "iamstatus,monitoringstatus");
        EventStreamsOperatorConfig eventStreamsOperatorConfig = EventStreamsOperatorConfig.fromMap(envMap);

        assertThat(eventStreamsOperatorConfig.getDependencyStatusChecks(), hasSize(2));
        assertThat(eventStreamsOperatorConfig.getDependencyStatusChecks(), hasItems("iamstatus", "monitoringstatus"));
    }
}
