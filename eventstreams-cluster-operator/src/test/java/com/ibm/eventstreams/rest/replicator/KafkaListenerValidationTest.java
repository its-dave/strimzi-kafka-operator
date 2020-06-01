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
package com.ibm.eventstreams.rest.replicator;

import com.ibm.eventstreams.api.ListenerAuthentication;
import com.ibm.eventstreams.api.ListenerType;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.controller.models.StatusCondition;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class KafkaListenerValidationTest {

    @Test
    public void testInvalidTlsKafkaListener() {
        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                    .withNewKafka()
                        .withNewListeners()
                            .withNewKafkaListenerExternalRoute()
                                .withNewKafkaListenerAuthenticationTlsAuth()
                                .endKafkaListenerAuthenticationTlsAuth()
                            .endKafkaListenerExternalRoute()
                            .withNewTls()
                                .withNewKafkaListenerAuthenticationOAuth()
                                .endKafkaListenerAuthenticationOAuth()
                            .endTls()
                        .endListeners()
                    .endKafka()
                .build())
            .endSpec()
            .build();

        List<StatusCondition> conditions = new ReplicatorKafkaListenerValidation().validateCr(test);

        assertThat(conditions, hasSize(1));

        assertThat(conditions.stream()
            .filter(condition -> condition.getReason().equals(ReplicatorKafkaListenerValidation.INVALID_INTERNAL_KAFKA_LISTENER_REASON))
            .findFirst().get().getMessage(), is(String.format(ReplicatorKafkaListenerValidation.INVALID_INTERNAL_KAFKA_LISTENER_MESSAGE, ListenerAuthentication.OAUTH.toValue(), ListenerType.TLS.toValue(), ListenerType.TLS.toValue())));
    }

    @Test
    public void testInvalidExternalKafkaListenerOauth() {
        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                    .withNewKafka()
                        .withNewListeners()
                            .withNewKafkaListenerExternalRoute()
                                .withNewKafkaListenerAuthenticationOAuth()
                                .endKafkaListenerAuthenticationOAuth()
                            .endKafkaListenerExternalRoute()
                        .withNewTls()
                            .withNewKafkaListenerAuthenticationTlsAuth()
                            .endKafkaListenerAuthenticationTlsAuth()
                        .endTls()
                        .endListeners()
                    .endKafka()
                .build())
            .endSpec()
            .build();

        List<StatusCondition> conditions = new ReplicatorKafkaListenerValidation().validateCr(test);

        assertThat(conditions, hasSize(1));

        assertThat(conditions.stream()
            .filter(condition -> condition.getReason().equals(ReplicatorKafkaListenerValidation.INVALID_EXTERNAL_KAFKA_LISTENER_REASON))
            .findFirst().get().getMessage(), is(String.format(ReplicatorKafkaListenerValidation.INVALID_EXTERNAL_KAFKA_LISTENER_MESSAGE, ListenerAuthentication.OAUTH.toValue(), ListenerType.EXTERNAL.toValue(), ListenerType.EXTERNAL.toValue())));
    }


    @Test
    public void testMissingExternalKafkaListenerAuth() {
        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                    .withNewListeners()
                        .withNewKafkaListenerExternalRoute()
                        .endKafkaListenerExternalRoute()
                        .withNewTls()
                            .withNewKafkaListenerAuthenticationTlsAuth()
                            .endKafkaListenerAuthenticationTlsAuth()
                        .endTls()
                    .endListeners()
                .endKafka()
                .build())
            .endSpec()
            .build();

        List<StatusCondition> conditions = new ReplicatorKafkaListenerValidation().validateCr(test);

        assertThat(conditions, hasSize(1));

        assertThat(conditions.stream()
            .filter(condition -> condition.getReason().equals(ReplicatorKafkaListenerValidation.INVALID_EXTERNAL_KAFKA_LISTENER_REASON))
            .findFirst().get().getMessage(), is(String.format(ReplicatorKafkaListenerValidation.INVALID_EXTERNAL_KAFKA_LISTENER_MESSAGE, ListenerAuthentication.NONE.toValue())));
    }


    @Test
    public void testMissingInternallKafkaListener() {
        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
                .editOrNewSpec()
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
                .endSpec()
                .build();

        List<StatusCondition> conditions = new ReplicatorKafkaListenerValidation().validateCr(test);

        assertThat(conditions, hasSize(1));

        assertThat(conditions.stream()
                .filter(condition -> condition.getReason().equals(ReplicatorKafkaListenerValidation.INVALID_INTERNAL_KAFKA_LISTENER_REASON))
                .findFirst().get().getMessage(), is(String.format(ReplicatorKafkaListenerValidation.INVALID_INTERNAL_KAFKA_LISTENER_MESSAGE, ListenerAuthentication.NONE.toValue(), ListenerType.TLS.toValue(), ListenerType.TLS.toValue())));
    }

    @Test
    public void testMissingInternallKafkaListenerAuth() {
        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
                .editOrNewSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                        .withNewListeners()
                            .withNewKafkaListenerExternalRoute()
                                .withNewKafkaListenerAuthenticationTlsAuth()
                                .endKafkaListenerAuthenticationTlsAuth()
                            .endKafkaListenerExternalRoute()
                        .withNewTls()
                        .endTls()
                        .endListeners()
                        .endKafka()
                        .build())
                .endSpec()
                .build();

        List<StatusCondition> conditions = new ReplicatorKafkaListenerValidation().validateCr(test);

        assertThat(conditions, hasSize(1));

        assertThat(conditions.stream()
            .filter(condition -> condition.getReason().equals(ReplicatorKafkaListenerValidation.INVALID_INTERNAL_KAFKA_LISTENER_REASON))
            .findFirst().get().getMessage(), is(String.format(ReplicatorKafkaListenerValidation.INVALID_INTERNAL_KAFKA_LISTENER_MESSAGE, "none", "tls", "tls")));
    }


    @Test
    public void testPlainListenerFails() {
        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
                .editOrNewSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                        .withNewKafka()
                        .withNewListeners()
                        .withNewKafkaListenerExternalRoute()
                        .withNewKafkaListenerAuthenticationTlsAuth()
                        .endKafkaListenerAuthenticationTlsAuth()
                        .endKafkaListenerExternalRoute()
                        .withNewPlain()
                            .withNewKafkaListenerAuthenticationScramSha512Auth()
                            .endKafkaListenerAuthenticationScramSha512Auth()
                        .endPlain()
                        .endListeners()
                        .endKafka()
                        .build())
                .endSpec()
                .build();

        List<StatusCondition> conditions = new ReplicatorKafkaListenerValidation().validateCr(test);

        assertThat(conditions, hasSize(1));

        assertThat(conditions.stream()
                .filter(condition -> condition.getReason().equals(ReplicatorKafkaListenerValidation.INVALID_INTERNAL_KAFKA_LISTENER_REASON))
                .findFirst().get().getMessage(), is(String.format(ReplicatorKafkaListenerValidation.INVALID_INTERNAL_KAFKA_LISTENER_MESSAGE,  ListenerAuthentication.NONE.toValue(), ListenerType.TLS.toValue(), ListenerType.TLS.toValue())));
    }

}
