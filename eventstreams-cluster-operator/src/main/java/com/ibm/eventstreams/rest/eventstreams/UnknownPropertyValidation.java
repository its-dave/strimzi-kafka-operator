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

import com.ibm.eventstreams.api.model.SchemaRegistryModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.SchemaRegistrySpec;
import com.ibm.eventstreams.controller.models.StatusCondition;
import com.ibm.eventstreams.rest.Validation;
import io.strimzi.api.kafka.model.JvmOptions;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorage;
import io.strimzi.api.kafka.model.storage.Storage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class UnknownPropertyValidation implements Validation {
    private static final Logger log = LogManager.getLogger(UnknownPropertyValidation.class.getName());

    private final static String KAFKA_JMX_OPTIONS_SPEC_PATH = "spec.strimziOverrides.kafka.jmxOptions";
    private final static String KAFKA_CONFIG_SPEC_PATH = "spec.strimziOverrides.kafka.config";

    public static final String SCHEMA_REGISTRY_UNKNOWN_PROPERTY_REASON = "SchemaRegistryUnknownProperty";
    public static final String SCHEMA_REGISTRY_UNKNOWN_PROPERTY_MESSAGE = "There are unknown properties in spec.schemaRegistry. "
        + "Remove the following properties from the YAML";

    public static final String KAFKA_UNKNOWN_PROPERTY_REASON = "KafkaUnknownProperty";
    public static final String KAFKA_UNKNOWN_PROPERTY_MESSAGE = "There are unknown properties in spec.strimziOverrides.kafka. "
        + "Remove the following properties from the YAML";

    public static final String KAFKA_CONFIG_FORBIDDEN_PREFIX_REASON = "KafkaConfigForbiddenPrefix";
    public static final String KAFKA_CONFIG_FORBIDDEN_PREFIX_MESSAGE = String.format("There are forbidden prefixes in %s. ", KAFKA_CONFIG_SPEC_PATH)
        + "Remove the following properties from the YAML";

    public static final String KAFKA_JMX_OPTIONS_FORBIDDEN_CONFIG_PREFIX_REASON = "KafkaJmxOptionsForbiddenPrefix";
    public static final String KAFKA_JMX_OPTIONS_FORBIDDEN_CONFIG_PREFIX_MESSAGE = String.format("There are forbidden prefixes in %s. ", KAFKA_JMX_OPTIONS_SPEC_PATH)
        + "Remove the following properties from the YAML";

    private static final List<String> FORBIDDEN_KAFKA_CONFIG_PREFIXES = Collections.emptyList();
    private static final List<String> FORBIDDEN_KAFKA_JMX_OPTIONS_PREFIXES = Collections.emptyList();

    public List<StatusCondition> validateCr(EventStreams instance) {
        log.traceEntry(() -> instance);
        List<StatusCondition> conditions = new ArrayList<>();
        checkSchemaRegistryForUnknownProperties(instance, conditions);
        checkKafkaForUnknownProperties(instance, conditions);
        checkForForbiddenKafkaConfigs(instance, FORBIDDEN_KAFKA_CONFIG_PREFIXES, conditions);
        checkForForbiddenKafkaJmxOptions(instance, FORBIDDEN_KAFKA_JMX_OPTIONS_PREFIXES, conditions);
        return log.traceExit(conditions);
    }

    private void checkSchemaRegistryForUnknownProperties(EventStreams instance, List<StatusCondition> conditions) {
        log.traceEntry(() -> conditions);
        Optional<Storage> schemaRegistryStorage = Optional.ofNullable(instance)
            .map(EventStreams::getSpec)
            .map(EventStreamsSpec::getSchemaRegistry)
            .map(SchemaRegistrySpec::getStorage);

        schemaRegistryStorage.ifPresent(storage -> {
            final boolean isPersistentStorage = storage instanceof PersistentClaimStorage;
            List<String> unknownKeys = storage.getAdditionalProperties().keySet().stream()
                .filter(property -> !isKnownProperty(isPersistentStorage, property))
                .map(property -> String.format("spec.schemaRegistry.%s", property))
                .collect(Collectors.toList());
            if (!unknownKeys.isEmpty()) {
                conditions.add(StatusCondition.createWarningCondition(SCHEMA_REGISTRY_UNKNOWN_PROPERTY_REASON, getSchemaRegistryUnknownPropertyMessage(unknownKeys)));
            }
        });
        log.traceExit();
    }

    private void checkKafkaForUnknownProperties(EventStreams instance, List<StatusCondition> conditions) {
        log.traceEntry(() -> conditions);
        Optional<Map<String, Object>> kafkaAdditionalProperties = Optional.ofNullable(instance)
            .map(EventStreams::getSpec)
            .map(EventStreamsSpec::getStrimziOverrides)
            .map(KafkaSpec::getKafka)
            .map(KafkaClusterSpec::getAdditionalProperties);

        kafkaAdditionalProperties.ifPresent(properties -> {
            List<String> paths = properties.keySet().stream().map(prop -> String.format("spec.strimziOverrides.kafka.%s", prop)).collect(Collectors.toList());
            if (!paths.isEmpty()) {
                conditions.add(
                    StatusCondition.createWarningCondition(KAFKA_UNKNOWN_PROPERTY_REASON, getKafkaUnknownPropertyMessage(paths))
                );
            }
        });
        log.traceExit();
    }

    // exposed for testing
    public void checkForForbiddenKafkaConfigs(EventStreams instance, List<String> forbiddenPrefixes, List<StatusCondition> conditions) {
        log.traceEntry(() -> forbiddenPrefixes, () -> conditions);
        Optional<Map<String, Object>> kafkaConfig = Optional.ofNullable(instance)
            .map(EventStreams::getSpec)
            .map(EventStreamsSpec::getStrimziOverrides)
            .map(KafkaSpec::getKafka)
            .map(KafkaClusterSpec::getConfig);

        String warningMessage = getForbiddenPropertyString(kafkaConfig, forbiddenPrefixes, KAFKA_CONFIG_SPEC_PATH, KAFKA_CONFIG_FORBIDDEN_PREFIX_MESSAGE);

        if (!warningMessage.isEmpty()) {
            conditions.add(StatusCondition.createErrorCondition(KAFKA_CONFIG_FORBIDDEN_PREFIX_REASON, warningMessage));
        }
        log.traceExit();
    }

    // exposed for testing
    public void checkForForbiddenKafkaJmxOptions(EventStreams instance, List<String> forbiddenPrefixes, List<StatusCondition> conditions) {
        log.traceEntry(() -> forbiddenPrefixes, () -> conditions);
        Optional<Map<String, Object>> kafkaConfig = Optional.ofNullable(instance)
            .map(EventStreams::getSpec)
            .map(EventStreamsSpec::getStrimziOverrides)
            .map(KafkaSpec::getKafka)
            .map(KafkaClusterSpec::getJvmOptions)
            .map(JvmOptions::getAdditionalProperties);

        String warningMessage = getForbiddenPropertyString(kafkaConfig, forbiddenPrefixes, KAFKA_JMX_OPTIONS_SPEC_PATH, KAFKA_JMX_OPTIONS_FORBIDDEN_CONFIG_PREFIX_MESSAGE);

        if (!warningMessage.isEmpty()) {
            conditions.add(StatusCondition.createErrorCondition(KAFKA_JMX_OPTIONS_FORBIDDEN_CONFIG_PREFIX_REASON, warningMessage));
        }
        log.traceExit();
    }

    private String getForbiddenPropertyString(Optional<Map<String, Object>> properties, List<String> forbiddenPrefixes, String specPath, String startingString) {
        log.traceEntry(() -> properties, () -> forbiddenPrefixes, () -> specPath, () -> startingString);
        List<String> forbiddenProperties = new ArrayList<>();
        properties.ifPresent(propertiesMap -> propertiesMap.keySet().stream()
            .filter(key -> forbiddenPrefixes.stream().anyMatch(key::startsWith))
            .forEach(key -> forbiddenProperties.add(String.format("%s.%s", specPath, key))));

        return log.traceExit(forbiddenProperties.isEmpty()
            ? ""
            : String.format("%s: %s.", startingString, String.join(", ", forbiddenProperties)));
    }

    private boolean isKnownProperty(boolean isPersistentStorage, String property) {
        log.traceEntry(() -> isPersistentStorage, () -> property);
        if (isPersistentStorage) {
            return log.traceExit(SchemaRegistryModel.KNOWN_PROPERTIES.contains(property));
        }
        return log.traceExit(false);
    }

    private String getSchemaRegistryUnknownPropertyMessage(List<String> unknownProperties) {
        log.traceEntry(() -> unknownProperties);
        String properties = String.join(", ", unknownProperties);
        return log.traceExit(String.format("%s: %s.", SCHEMA_REGISTRY_UNKNOWN_PROPERTY_MESSAGE, properties));
    }

    private String getKafkaUnknownPropertyMessage(List<String> unknownProperties) {
        log.traceEntry(() -> unknownProperties);
        String properties = String.join(", ", unknownProperties);
        return log.traceExit(String.format("%s: %s.", KAFKA_UNKNOWN_PROPERTY_MESSAGE, properties));
    }
}


