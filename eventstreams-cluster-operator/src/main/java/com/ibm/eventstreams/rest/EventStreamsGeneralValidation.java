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
package com.ibm.eventstreams.rest;

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
public class EventStreamsGeneralValidation implements Validation {

    @Override
    public List<StatusCondition> validateCr(EventStreams spec) {
        List<StatusCondition> conditions = new ArrayList<>();
        checkSchemaRegistryStorage(spec, conditions);
        checkCruiseControl(spec, conditions);
        return conditions;
    }

    private List<StatusCondition> checkSchemaRegistryStorage(EventStreams spec, List<StatusCondition> conditions) {
        Optional<SchemaRegistrySpec> schemaRegistrySpec = Optional.ofNullable(spec).map(EventStreams::getSpec).map(EventStreamsSpec::getSchemaRegistry);

        if (schemaRegistrySpec.isPresent()) {
            int replicas = schemaRegistrySpec.map(ComponentSpec::getReplicas).orElse(SchemaRegistryModel.DEFAULT_REPLICAS);
            Storage storage = schemaRegistrySpec.map(SchemaRegistrySpec::getStorage).orElseGet(EphemeralStorage::new);

            if (replicas > 0 && StorageUtils.usesEphemeral(storage)) {
                conditions.add(StatusCondition.createWarningCondition(
                    "SchemaRegistryStorage",
                    "A Schema Registry with ephemeral storage will lose schemas after any restart or rolling update."
                        + "To avoid losing data, edit spec.schemaRegistry.storage to provide a persistent storage class."));
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
                    "CruiseControlEnabled",
                    "Technology Preview features are available to evaluate potential upcoming features. " +
                            "These features are intended for testing purposes only and not for production use, and " +
                            "are not supported by IBM."));
        }
    }
}
