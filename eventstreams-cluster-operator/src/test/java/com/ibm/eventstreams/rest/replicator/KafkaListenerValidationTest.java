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
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import org.junit.jupiter.api.Test;

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
                            .withNewTls()
                                .withNewKafkaListenerAuthenticationOAuth()
                                .endKafkaListenerAuthenticationOAuth()
                            .endTls()
                        .endListeners()
                    .endKafka()
                .build())
            .endSpec()
            .build();

        ReplicatorKafkaListenerValidation validation = new ReplicatorKafkaListenerValidation(test);

        assertThat(validation.getConditions(), hasSize(1));

        assertThat(validation.getConditions().stream()
            .filter(condition -> condition.getReason().equals(ReplicatorKafkaListenerValidation.INVALID_KAFKA_LISTENER_REASON))
            .findFirst().get().getMessage(), is(String.format(ReplicatorKafkaListenerValidation.INVALID_KAFKA_LISTENER_MESSAGE, ListenerAuthentication.OAUTH.toValue(), ListenerType.TLS.toValue(), ListenerType.TLS.toValue())));
    }

    @Test
    public void testInvalidExternalKafkaListener() {
        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
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
            .endSpec()
            .build();

        ReplicatorKafkaListenerValidation validation = new ReplicatorKafkaListenerValidation(test);

        assertThat(validation.getConditions(), hasSize(1));

        assertThat(validation.getConditions().stream()
            .filter(condition -> condition.getReason().equals(ReplicatorKafkaListenerValidation.INVALID_KAFKA_LISTENER_REASON))
            .findFirst().get().getMessage(), is(String.format(ReplicatorKafkaListenerValidation.INVALID_KAFKA_LISTENER_MESSAGE, ListenerAuthentication.OAUTH.toValue(), ListenerType.EXTERNAL.toValue(), ListenerType.EXTERNAL.toValue())));
    }

    @Test
    public void testUnauthenticatedTlsListenerButAuthenticatedExternalListenerThrows() {
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

        ReplicatorKafkaListenerValidation validation = new ReplicatorKafkaListenerValidation(test);

        assertThat(validation.getConditions(), hasSize(1));

        assertThat(validation.getConditions().stream()
            .filter(condition -> condition.getReason().equals(ReplicatorKafkaListenerValidation.MISMATCHED_INTERNAL_AND_EXTERNAL_AUTHENTICATION_REASON))
            .findFirst().get().getMessage(), is(ReplicatorKafkaListenerValidation.UNAUTHENTICATED_TLS_BUT_AUTHENTICATED_EXTERNAL_MESSAGE));
    }

    @Test
    public void testAuthenticatedTlsListenerButUnauthenticatedExternalListenerThrows() {

    }
}
