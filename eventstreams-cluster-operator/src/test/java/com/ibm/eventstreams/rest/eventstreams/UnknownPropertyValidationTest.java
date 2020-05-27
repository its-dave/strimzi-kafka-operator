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

import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.controller.models.ConditionType;
import com.ibm.eventstreams.controller.models.StatusCondition;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.ibm.eventstreams.rest.eventstreams.UnknownPropertyValidation.KAFKA_CONFIG_FORBIDDEN_PREFIX_MESSAGE;
import static com.ibm.eventstreams.rest.eventstreams.UnknownPropertyValidation.KAFKA_JMX_OPTIONS_FORBIDDEN_CONFIG_PREFIX_MESSAGE;
import static com.ibm.eventstreams.rest.eventstreams.UnknownPropertyValidation.KAFKA_JMX_OPTIONS_FORBIDDEN_CONFIG_PREFIX_REASON;
import static com.ibm.eventstreams.rest.eventstreams.UnknownPropertyValidation.KAFKA_UNKNOWN_PROPERTY_MESSAGE;
import static com.ibm.eventstreams.rest.eventstreams.UnknownPropertyValidation.SCHEMA_REGISTRY_UNKNOWN_PROPERTY_MESSAGE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

public class UnknownPropertyValidationTest {
    @Test
    public void testAdditionalNestedKeysInSchemaRegistryWarns() {
        JsonObject schemaJson = new JsonObject()
            .put("spec", new JsonObject()
                .put("schemaRegistry", new JsonObject()
                    .put("storage", new JsonObject()
                        .put("type", "ephemeral")
                        .put("da", new JsonObject()
                            .put("ba", "dee"))
                        .put("storageClassName", "rook-ceph-cephfs-internal"))));

        EventStreams instance = schemaJson.mapTo(EventStreams.class);
        List<StatusCondition> conditions = new UnknownPropertyValidation().validateCr(instance);

        assertThat(conditions, hasSize(1));
        assertThat(conditions.get(0).getReason(), is(UnknownPropertyValidation.SCHEMA_REGISTRY_UNKNOWN_PROPERTY_REASON));
        assertThat(conditions.get(0).getMessage(), is(formatWarningMessage(SCHEMA_REGISTRY_UNKNOWN_PROPERTY_MESSAGE, "spec.schemaRegistry", "da", "storageClassName")));
        assertThat(conditions.get(0).getType(), is(ConditionType.WARNING));
    }

    @Test
    public void testValidSchemaRegistryDoesNotWarn() {
        JsonObject schemaJson = new JsonObject()
            .put("spec", new JsonObject()
                .put("schemaRegistry", new JsonObject()
                    .put("storage", new JsonObject()
                        .put("type", "ephemeral"))));

        EventStreams instance = schemaJson.mapTo(EventStreams.class);
        List<StatusCondition> conditions = new UnknownPropertyValidation().validateCr(instance);

        assertThat(conditions, hasSize(0));
    }

    @Test
    public void testUnknownPropertiesInKafkaSpecWarns() {
        JsonObject kafkaJson = new JsonObject()
            .put("spec", new JsonObject()
                .put("strimziOverrides", new JsonObject()
                    .put("kafka", new JsonObject()
                        .put("config", new JsonObject()
                            .put("interceptor.class.names", "com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor"))
                        .put("prop1", "val1")
                        .put("prop2", "val2")
                        .put("prop3", "val3"))));

        EventStreams instance = kafkaJson.mapTo(EventStreams.class);
        List<StatusCondition> conditions = new UnknownPropertyValidation().validateCr(instance);

        assertThat(conditions, hasSize(1));
        assertThat(conditions.get(0).getReason(), is(UnknownPropertyValidation.KAFKA_UNKNOWN_PROPERTY_REASON));
        assertThat(conditions.get(0).getMessage(), is(formatWarningMessage(KAFKA_UNKNOWN_PROPERTY_MESSAGE, "spec.strimziOverrides.kafka", "prop2", "prop1", "prop3")));
        assertThat(conditions.get(0).getType(), is(ConditionType.WARNING));
    }

    @Test
    public void testNoUnknownPropertiesInStrimziOverridesDoesNotWarns() {
        JsonObject kafkaJson = new JsonObject()
            .put("spec", new JsonObject()
                .put("strimziOverrides", new JsonObject()
                    .put("kafka", new JsonObject()
                        .put("config", new JsonObject()
                            .put("interceptor.class.names", "com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor")))));

        EventStreams instance = kafkaJson.mapTo(EventStreams.class);
        List<StatusCondition> conditions = new UnknownPropertyValidation().validateCr(instance);

        assertThat(conditions, hasSize(0));
    }

    @Test
    public void testForbiddenPrefixesInKafkaConfig() {
        JsonObject kafkaJson = new JsonObject()
            .put("spec", new JsonObject()
                .put("strimziOverrides", new JsonObject()
                    .put("kafka", new JsonObject()
                        .put("config", new JsonObject()
                            .put("interceptor.class.names", "com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor")
                            .put("forbidden", "1")
                            .put("value.with.warned", "2")
                            .put("this.should.be.fine", "3")))));

        EventStreams instance = kafkaJson.mapTo(EventStreams.class);
        List<StatusCondition> conditions = new ArrayList<>();
        new UnknownPropertyValidation().checkForForbiddenKafkaConfigs(instance, Arrays.asList("forbidden", "value"), conditions);

        assertThat(conditions, hasSize(1));
        assertThat(conditions.get(0).getReason(), is(UnknownPropertyValidation.KAFKA_CONFIG_FORBIDDEN_PREFIX_REASON));
        assertThat(conditions.get(0).getMessage(), is(formatWarningMessage(KAFKA_CONFIG_FORBIDDEN_PREFIX_MESSAGE, "spec.strimziOverrides.kafka.config", "forbidden", "value.with.warned")));
        assertThat(conditions.get(0).getType(), is(ConditionType.ERROR));
    }

    @Test
    public void testForbiddenPrefixesInKafkaJmxOptions() {
        JsonObject kafkaJson = new JsonObject()
            .put("spec", new JsonObject()
                .put("strimziOverrides", new JsonObject()
                    .put("kafka", new JsonObject()
                        .put("jvmOptions", new JsonObject()
                            .put("interceptor.class.names", "com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor")
                            .put("forbidden", "1")
                            .put("value.with.suffix", "2")
                            .put("this.should.be.fine", "3")))));

        EventStreams instance = kafkaJson.mapTo(EventStreams.class);
        List<StatusCondition> conditions = new ArrayList<>();
        new UnknownPropertyValidation().checkForForbiddenKafkaJmxOptions(instance, Arrays.asList("forbidden", "value"), conditions);

        assertThat(conditions, hasSize(1));
        assertThat(conditions.get(0).getReason(), is(KAFKA_JMX_OPTIONS_FORBIDDEN_CONFIG_PREFIX_REASON));
        assertThat(conditions.get(0).getMessage(), is(formatWarningMessage(KAFKA_JMX_OPTIONS_FORBIDDEN_CONFIG_PREFIX_MESSAGE, "spec.strimziOverrides.kafka.jmxOptions", "value.with.suffix", "forbidden")));
        assertThat(conditions.get(0).getType(), is(ConditionType.ERROR));
    }

    private static String formatWarningMessage(String startingString, String specPath, String... properties) {
        String warningMessage = startingString + ": ";
        for (String property : properties) {
            warningMessage = warningMessage.concat(String.format("%s.%s, ", specPath, property));
        }
        return warningMessage.substring(0, warningMessage.length() - 2) + ".";
    }
}
