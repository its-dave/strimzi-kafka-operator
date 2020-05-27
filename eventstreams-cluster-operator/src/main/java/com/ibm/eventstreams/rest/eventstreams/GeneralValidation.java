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
import com.ibm.eventstreams.api.spec.ComponentSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.SchemaRegistrySpec;
import com.ibm.eventstreams.controller.models.StatusCondition;
import io.strimzi.api.kafka.model.CruiseControlSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.storage.EphemeralStorage;
import io.strimzi.api.kafka.model.storage.Storage;
import io.strimzi.operator.cluster.model.StorageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks for potential problems in the configuration requested by the user, to provide
 * warnings and share best practice. The intent is this class will generate warnings about
 * configurations that aren't necessarily illegal or invalid, but that could potentially
 * lead to problems.
 */
public class GeneralValidation {
    public static final String INSTALLING_IN_LONG_NAMESPACE_REASON = "RouteInLongNameSpace";
    public static final String INSTALLING_IN_LONG_NAMESPACE_MESSAGE = "Creating routes in namespace '%s' may cause problems. "
        + "Automatically generated route hosts will be created appropriately because valid Openshift Route hosts less than 64 characters. "
        + "To fix this issue, provide a valid hostname override to endpoints with type 'Route' in spec.adminApi.endpoints, spec.schemaRegistry.endpoints, spec.restProducer.endpoints and spec.adminUi.host.";

    public static final String CRUISE_CONTROL_REASON = "CruiseControlEnabled";
    public static final String CRUISE_CONTROL_MESSAGE = "Technology Preview features are available to evaluate potential upcoming features. " +
        "These features are intended for testing purposes only and not for production use, and are not supported by IBM.";

    public static final String EPHEMERAL_SCHEMA_REGISTRY_REASON = "SchemaRegistryStorage";
    public static final String EPHEMERAL_SCHEMA_REGISTRY_MESSAGE = "A Schema Registry with ephemeral storage will lose schemas after any restart or rolling update. "
        + "To avoid losing data, edit spec.schemaRegistry.storage to provide a persistent storage class.";

    private static final int LONG_NAMESPACE_CHARACTER_LIMIT = 60;

    public List<StatusCondition> validateCr(EventStreams spec) {
        List<StatusCondition> conditions = new ArrayList<>();
        checkSchemaRegistryStorage(spec, conditions);
        checkCruiseControl(spec, conditions);
        checkLongNamespace(spec.getMetadata().getNamespace(), conditions);
        return conditions;
    }

    private List<StatusCondition> checkSchemaRegistryStorage(EventStreams spec, List<StatusCondition> conditions) {
        Optional<SchemaRegistrySpec> schemaRegistrySpec = Optional.ofNullable(spec).map(EventStreams::getSpec).map(EventStreamsSpec::getSchemaRegistry);

        if (schemaRegistrySpec.isPresent()) {
            int replicas = schemaRegistrySpec.map(ComponentSpec::getReplicas).orElse(SchemaRegistryModel.DEFAULT_REPLICAS);
            Storage storage = schemaRegistrySpec.map(SchemaRegistrySpec::getStorage).orElseGet(EphemeralStorage::new);

            if (replicas > 0 && StorageUtils.usesEphemeral(storage)) {
                conditions.add(StatusCondition.createWarningCondition(
                    EPHEMERAL_SCHEMA_REGISTRY_REASON,
                    EPHEMERAL_SCHEMA_REGISTRY_MESSAGE));
            }
        }
        return conditions;
    }

    private void checkCruiseControl(EventStreams spec, List<StatusCondition> conditions) {
        Optional<CruiseControlSpec> cruiseControlSpec = Optional.ofNullable(spec)
                                                            .map(EventStreams::getSpec)
                                                            .map(EventStreamsSpec::getStrimziOverrides)
                                                            .map(KafkaSpec::getCruiseControl);
        if (cruiseControlSpec.isPresent()) {
            conditions.add(StatusCondition.createWarningCondition(
                CRUISE_CONTROL_REASON,
                CRUISE_CONTROL_MESSAGE));
        }
    }

    private void checkLongNamespace(String namespace, List<StatusCondition> conditions) {
        if (namespace.length() >= LONG_NAMESPACE_CHARACTER_LIMIT) {
            conditions.add(StatusCondition.createWarningCondition(INSTALLING_IN_LONG_NAMESPACE_REASON, String.format(INSTALLING_IN_LONG_NAMESPACE_MESSAGE, namespace)));
        }
    }
}
