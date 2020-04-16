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

import com.ibm.eventstreams.api.ListenerAuthentication;
import com.ibm.eventstreams.api.ListenerType;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpecBuilder;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ListenerTypeTest {
    private static String instanceName = "test-instance";

    @Test
    public void testNoListeners() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        assertThat(ListenerAuthentication.getAuthentication(instance, ListenerType.PLAIN), is(ListenerAuthentication.NONE));
        assertThat(ListenerAuthentication.getAuthentication(instance, ListenerType.TLS), is(ListenerAuthentication.NONE));
        assertThat(ListenerAuthentication.getAuthentication(instance, ListenerType.EXTERNAL), is(ListenerAuthentication.NONE));
    }

    @Test
    public void testTlsListenerWithTlsAuth() {
        EventStreams tlsListener = ModelUtils.createEventStreams(instanceName, new EventStreamsSpecBuilder()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .withNewListeners()
                .withNewTls()
                .withNewKafkaListenerAuthenticationTlsAuth()
                .endKafkaListenerAuthenticationTlsAuth()
                .endTls()
                .endListeners()
                .endKafka()
                .build())
            .build())
            .build();

        assertThat(ListenerAuthentication.getAuthentication(tlsListener, ListenerType.TLS), is(ListenerAuthentication.TLS));
    }

    @Test
    public void testExternalListenerWithTlsAuth() {
        EventStreams externalListener = ModelUtils.createEventStreams(instanceName, new EventStreamsSpecBuilder()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .withNewListeners()
                .withNewKafkaListenerExternalRoute()
                .withNewKafkaListenerAuthenticationTlsAuth()
                .endKafkaListenerAuthenticationTlsAuth()
                .endKafkaListenerExternalRoute()
                .endListeners()
                .endKafka()
                .build())
            .build())
            .build();

        assertThat(ListenerAuthentication.getAuthentication(externalListener, ListenerType.EXTERNAL), is(ListenerAuthentication.TLS));
    }

    @Test
    public void testPlainListenerWithOauth() {
        EventStreams plainListenerInstance = ModelUtils.createEventStreams(instanceName, new EventStreamsSpecBuilder()
                .withStrimziOverrides(new KafkaSpecBuilder()
                    .withNewKafka()
                        .withNewListeners()
                            .withNewPlain()
                                .withNewKafkaListenerAuthenticationOAuth()
                                .endKafkaListenerAuthenticationOAuth()
                            .endPlain()
                        .endListeners()
                    .endKafka()
                .build())
            .build())
            .build();

        assertThat(ListenerAuthentication.getAuthentication(plainListenerInstance, ListenerType.PLAIN), is(ListenerAuthentication.OAUTH));
    }

    @Test
    public void testTlsListenerWithOauth() {
        EventStreams tlsListener = ModelUtils.createEventStreams(instanceName, new EventStreamsSpecBuilder()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .withNewListeners()
                .withNewTls()
                .withNewKafkaListenerAuthenticationOAuth()
                .endKafkaListenerAuthenticationOAuth()
                .endTls()
                .endListeners()
                .endKafka()
                .build())
            .build())
            .build();

        assertThat(ListenerAuthentication.getAuthentication(tlsListener, ListenerType.TLS), is(ListenerAuthentication.OAUTH));
    }

    @Test
    public void testExternalListenerWithOauth() {
        EventStreams externalListener = ModelUtils.createEventStreams(instanceName, new EventStreamsSpecBuilder()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .withNewListeners()
                .withNewKafkaListenerExternalRoute()
                .withNewKafkaListenerAuthenticationOAuth()
                .endKafkaListenerAuthenticationOAuth()
                .endKafkaListenerExternalRoute()
                .endListeners()
                .endKafka()
                .build())
            .build())
            .build();

        assertThat(ListenerAuthentication.getAuthentication(externalListener, ListenerType.EXTERNAL), is(ListenerAuthentication.OAUTH));
    }

    @Test
    public void testPlainListenerWithScram() {
        EventStreams plainListenerInstance = ModelUtils.createEventStreams(instanceName, new EventStreamsSpecBuilder()
                .withStrimziOverrides(new KafkaSpecBuilder()
                    .withNewKafka()
                        .withNewListeners()
                            .withNewPlain()
                                .withNewKafkaListenerAuthenticationScramSha512Auth()
                                .endKafkaListenerAuthenticationScramSha512Auth()
                            .endPlain()
                        .endListeners()
                    .endKafka()
                .build())
            .build())
            .build();

        assertThat(ListenerAuthentication.getAuthentication(plainListenerInstance, ListenerType.PLAIN), is(ListenerAuthentication.SCRAM_SHA_512));
    }

    @Test
    public void testTlsListenerWithScram() {
        EventStreams tlsListener = ModelUtils.createEventStreams(instanceName, new EventStreamsSpecBuilder()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .withNewListeners()
                .withNewTls()
                .withNewKafkaListenerAuthenticationScramSha512Auth()
                .endKafkaListenerAuthenticationScramSha512Auth()
                .endTls()
                .endListeners()
                .endKafka()
                .build())
            .build())
            .build();

        assertThat(ListenerAuthentication.getAuthentication(tlsListener, ListenerType.TLS), is(ListenerAuthentication.SCRAM_SHA_512));
    }


    @Test
    public void testExternalListenerWithScram() {
        EventStreams externalListener = ModelUtils.createEventStreams(instanceName, new EventStreamsSpecBuilder()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .withNewListeners()
                .withNewKafkaListenerExternalRoute()
                .withNewKafkaListenerAuthenticationScramSha512Auth()
                .endKafkaListenerAuthenticationScramSha512Auth()
                .endKafkaListenerExternalRoute()
                .endListeners()
                .endKafka()
                .build())
            .build())
            .build();

        assertThat(ListenerAuthentication.getAuthentication(externalListener, ListenerType.EXTERNAL), is(ListenerAuthentication.SCRAM_SHA_512));
    }
}
