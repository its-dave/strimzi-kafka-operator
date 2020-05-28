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
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.controller.models.ConditionType;
import com.ibm.eventstreams.controller.models.StatusCondition;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.storage.EphemeralStorage;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorage;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class GeneralValidationTest {
    private final String instanceName = "test-instance";
    private final String longNamespace = "d2lsbERhbGUtZXZlbi1jaGVjay10aGlzLXdoby1rbm93cy1idXQtSS1rbm93LVNhbS13aWxsLWRlZmluaXRlbHktY2hlY2stdGhpcwo=";
    private final String shortNamespace = "Wonder-what-that-string-is";

    @Test
    public void TestWarnsWhenCruiseControlEnabled() {
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
    public void TestWarnsWhenEphemeralSchemaRegistry() {
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
    public void TestWarnsWhenLongNamespace() {
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

    @Test
    public void TestErrorsWhenNoAdminApiWithUI() {
        EventStreams instance =  new EventStreamsBuilder()
            .withNewSpec()
                .withNewAdminUI()
                .endAdminUI()
                .withNewRestProducer()
                .endRestProducer()
                .withNewSchemaRegistry()
                    .withStorage(new PersistentClaimStorage())
                .endSchemaRegistry()
                .withStrimziOverrides(new KafkaSpecBuilder()
                    .withNewKafka()
                    .endKafka()
                    .build())
            .endSpec()
            .build();

        List<StatusCondition> responses = new GeneralValidation().validateCr(instance);

        assertThat(responses, hasSize(1));
        assertThat(responses.get(0).getReason(), is(GeneralValidation.UI_REQUIRES_REST_COMPONENTS_REASON));
        assertThat(responses.get(0).getMessage(), is("One of the following components have not been enabled: adminApi. The UI requires Schema Registry, Admin API and Rest Producer to be enabled to allow for its capabilities. Edit spec.adminApi.replicas to enable the component with more than 1 replica."));
        assertThat(responses.get(0).getType(), is(ConditionType.ERROR));
    }

    @Test
    public void TestErrorsWhenNoSchemaRegistryWithUI() {
        EventStreams instance =  new EventStreamsBuilder()
            .withNewSpec()
            .withNewAdminUI()
            .endAdminUI()
            .withNewAdminApi()
            .endAdminApi()
            .withNewRestProducer()
            .endRestProducer()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .endKafka()
                .build())
            .endSpec()
            .build();

        List<StatusCondition> responses = new GeneralValidation().validateCr(instance);

        assertThat(responses, hasSize(1));
        assertThat(responses.get(0).getReason(), is(GeneralValidation.UI_REQUIRES_REST_COMPONENTS_REASON));
        assertThat(responses.get(0).getMessage(), is("One of the following components have not been enabled: schemaRegistry. The UI requires Schema Registry, Admin API and Rest Producer to be enabled to allow for its capabilities. Edit spec.schemaRegistry.replicas to enable the component with more than 1 replica."));
        assertThat(responses.get(0).getType(), is(ConditionType.ERROR));
    }

    @Test
    public void TestErrorsWhenNoRestProducerWithUI() {
        EventStreams instance =  new EventStreamsBuilder()
            .withNewSpec()
            .withNewAdminUI()
            .endAdminUI()
            .withNewAdminApi()
            .endAdminApi()
            .withNewSchemaRegistry()
                .withStorage(new PersistentClaimStorage())
            .endSchemaRegistry()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .endKafka()
                .build())
            .endSpec()
            .build();

        List<StatusCondition> responses = new GeneralValidation().validateCr(instance);

        assertThat(responses, hasSize(1));
        assertThat(responses.get(0).getReason(), is(GeneralValidation.UI_REQUIRES_REST_COMPONENTS_REASON));
        assertThat(responses.get(0).getMessage(), is("One of the following components have not been enabled: restProducer. The UI requires Schema Registry, Admin API and Rest Producer to be enabled to allow for its capabilities. Edit spec.restProducer.replicas to enable the component with more than 1 replica."));
        assertThat(responses.get(0).getType(), is(ConditionType.ERROR));
    }

    @Test
    public void TestErrorsWhenNoComponentsEnabledWithUI() {
        EventStreams instance =  new EventStreamsBuilder()
            .withNewSpec()
            .withNewAdminUI()
            .endAdminUI()
            .withNewAdminApi()
                .withReplicas(0)
            .endAdminApi()
            .withNewSchemaRegistry()
                .withReplicas(0)
                .withStorage(new PersistentClaimStorage())
            .endSchemaRegistry()
            .withNewRestProducer()
                .withReplicas(0)
            .endRestProducer()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                .endKafka()
                .build())
            .endSpec()
            .build();

        List<StatusCondition> responses = new GeneralValidation().validateCr(instance);

        assertThat(responses, hasSize(1));
        assertThat(responses.get(0).getReason(), is(GeneralValidation.UI_REQUIRES_REST_COMPONENTS_REASON));
        assertThat(responses.get(0).getMessage(), is("One of the following components have not been enabled: adminApi, schemaRegistry, restProducer. The UI requires Schema Registry, Admin API and Rest Producer to be enabled to allow for its capabilities. Edit spec.adminApi.replicas, spec.schemaRegistry.replicas, spec.restProducer.replicas to enable the component with more than 1 replica."));
        assertThat(responses.get(0).getType(), is(ConditionType.ERROR));
    }
}
