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

package com.ibm.eventstreams.rest.eventstreams;

import com.ibm.eventstreams.api.TlsVersion;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.controller.models.StatusCondition;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class GeneralSecurityValidationTest {
    private final String instanceName = "test-instance";

    @Test
    public void TestNonTlsEventStreamsWarns() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName)
            .editSpec()
                .withNewSecurity()
                    .withInternalTls(TlsVersion.NONE)
                .endSecurity()
                .withStrimziOverrides(new KafkaSpecBuilder()
                    .withNewKafka()
                        .withNewKafkaAuthorizationRunAs()
                        .endKafkaAuthorizationRunAs()
                        .withNewListeners()
                            .withNewTls()
                                .withNewKafkaListenerAuthenticationTlsAuth()
                                .endKafkaListenerAuthenticationTlsAuth()
                            .endTls()
                        .endListeners()
                    .endKafka()
                .build())
            .endSpec()
            .build();

        List<StatusCondition> responses = new GeneralSecurityValidation().validateCr(instance);
        assertThat(responses, hasSize(1));
        assertThat(responses.get(0).getReason(), is(GeneralSecurityValidation.EVENTSTREAMS_NO_TLS_REASON));
        assertThat(responses.get(0).getMessage(), is(GeneralSecurityValidation.EVENTSTREAMS_NO_TLS_MESSAGE));
    }

    @Test
    public void TestUnauthenticatedEventStreamsWarns() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName)
            .editSpec()
                .withStrimziOverrides(new KafkaSpecBuilder()
                    .withNewKafka()
                        .withNewKafkaAuthorizationRunAs()
                        .endKafkaAuthorizationRunAs()
                        .withNewListeners()
                            .withNewTls()
                            .endTls()
                        .endListeners()
                    .endKafka()
                .build())
            .endSpec()
            .build();

        List<StatusCondition> responses = new GeneralSecurityValidation().validateCr(instance);
        assertThat(responses, hasSize(1));
        assertThat(responses.get(0).getReason(), is(GeneralSecurityValidation.KAFKA_UNAUTHENTICATED_REASON));
        assertThat(responses.get(0).getMessage(), is(GeneralSecurityValidation.KAFKA_UNAUTHENTICATED_MESSAGE));
    }

    @Test
    public void TestUnauthorizedEventStreamsWarns() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName)
            .editSpec()
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
            .endSpec()
            .build();

        List<StatusCondition> responses = new GeneralSecurityValidation().validateCr(instance);
        assertThat(responses, hasSize(1));
        assertThat(responses.get(0).getReason(), is(GeneralSecurityValidation.KAFKA_UNAUTHORIZED_REASON));
        assertThat(responses.get(0).getMessage(), is(GeneralSecurityValidation.KAFKA_UNAUTHORIZED_MESSAGE));
    }

    @Test
    public void TestAllWarningsWarns() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName)
            .editSpec()
                .withNewSecurity()
                    .withInternalTls(TlsVersion.NONE)
                .endSecurity()
                .withStrimziOverrides(new KafkaSpecBuilder()
                    .withNewKafka()
                        .withNewListeners()
                            .withNewTls()
                            .endTls()
                        .endListeners()
                    .endKafka()
                .build())
            .endSpec()
            .build();

        List<StatusCondition> responses = new GeneralSecurityValidation().validateCr(instance);
        assertThat(responses, hasSize(3));
        assertThat(responses.get(0).getReason(), is(GeneralSecurityValidation.EVENTSTREAMS_NO_TLS_REASON));
        assertThat(responses.get(0).getMessage(), is(GeneralSecurityValidation.EVENTSTREAMS_NO_TLS_MESSAGE));
        assertThat(responses.get(1).getReason(), is(GeneralSecurityValidation.KAFKA_UNAUTHORIZED_REASON));
        assertThat(responses.get(1).getMessage(), is(GeneralSecurityValidation.KAFKA_UNAUTHORIZED_MESSAGE));
        assertThat(responses.get(2).getReason(), is(GeneralSecurityValidation.KAFKA_UNAUTHENTICATED_REASON));
        assertThat(responses.get(2).getMessage(), is(GeneralSecurityValidation.KAFKA_UNAUTHENTICATED_MESSAGE));
    }

}
