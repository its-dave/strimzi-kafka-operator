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

import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.controller.models.StatusCondition;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.storage.EphemeralStorage;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class GeneralValidationTest {
    private final String instanceName = "test-instance";
    private final String longNamespace = "d2lsbERhbGUtZXZlbi1jaGVjay10aGlzLXdoby1rbm93cy1idXQtSS1rbm93LVNhbS13aWxsLWRlZmluaXRlbHktY2hlY2stdGhpcwo=";
    private final String shortNamespace = "Wonder-what-that-string-is";

    @Test
    public void warnsWhenCruiseControlEnabled() {
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
                    .withNewCruiseControl()
                    .endCruiseControl()
                .build())
            .endSpec()
            .build();

        instance.getMetadata().setNamespace(shortNamespace);
        List<StatusCondition> responses = new GeneralValidation().validateCr(instance);
        assertThat(responses, hasSize(1));

        assertThat(responses.get(0).getReason(), Matchers.is(GeneralValidation.CRUISE_CONTROL_REASON));
        assertThat(responses.get(0).getMessage(), Matchers.is(GeneralValidation.CRUISE_CONTROL_MESSAGE));
    }

    @Test
    public void warnsWhenEphemeralSchemaRegistry() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName)
            .editSpec()
            .withNewSchemaRegistry()
                .withStorage(new EphemeralStorage())
            .endSchemaRegistry()
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

        instance.getMetadata().setNamespace(shortNamespace);
        List<StatusCondition> responses = new GeneralValidation().validateCr(instance);
        assertThat(responses, hasSize(1));

        assertThat(responses.get(0).getReason(), Matchers.is(GeneralValidation.EPHEMERAL_SCHEMA_REGISTRY_REASON));
        assertThat(responses.get(0).getMessage(), Matchers.is(GeneralValidation.EPHEMERAL_SCHEMA_REGISTRY_MESSAGE));
    }

    @Test
    public void warnsWhenLongNamespace() {
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

        instance.getMetadata().setNamespace(longNamespace);
        List<StatusCondition> responses = new GeneralValidation().validateCr(instance);
        assertThat(responses, hasSize(1));

        assertThat(responses.get(0).getReason(), Matchers.is(GeneralValidation.INSTALLING_IN_LONG_NAMESPACE_REASON));
        assertThat(responses.get(0).getMessage(), Matchers.is(String.format(GeneralValidation.INSTALLING_IN_LONG_NAMESPACE_MESSAGE, longNamespace)));
    }

}
