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
package com.ibm.eventstreams.controller;

import com.ibm.eventstreams.api.model.SchemaRegistryModel;
import com.ibm.eventstreams.api.spec.ComponentSpec;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.SchemaRegistrySpec;
import io.strimzi.api.kafka.model.CruiseControlSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.api.kafka.model.status.ConditionBuilder;
import io.strimzi.api.kafka.model.storage.EphemeralStorage;
import io.strimzi.api.kafka.model.storage.Storage;
import io.strimzi.operator.cluster.model.ModelUtils;
import io.strimzi.operator.cluster.model.StorageUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Checks for potential problems in the configuration requested by the user, to provide
 * warnings and share best practice. The intent is this class will generate warnings about
 * configurations that aren't necessarily illegal or invalid, but that could potentially
 * lead to problems.
 */
public class EventStreamsSpecChecker {

    private EventStreamsSpec spec;
    private String timestamp;

    public EventStreamsSpecChecker(Supplier<Date> dateSupplier, EventStreamsSpec spec) {
        this.spec = spec;
        this.timestamp = ModelUtils.formatTimestamp(dateSupplier.get());
    }

    public List<Condition> run() {
        List<Condition> warnings = new ArrayList<>();
        checkSchemaRegistryStorage(warnings);
        checkCruiseControl(warnings);
        return warnings;
    }


    private void checkSchemaRegistryStorage(List<Condition> warnings) {
        Optional<SchemaRegistrySpec> schemaRegistrySpec = Optional.ofNullable(spec).map(EventStreamsSpec::getSchemaRegistry);

        if (schemaRegistrySpec.isPresent()) {
            int replicas = schemaRegistrySpec.map(ComponentSpec::getReplicas).orElse(SchemaRegistryModel.DEFAULT_REPLICAS);
            Storage storage = schemaRegistrySpec.map(SchemaRegistrySpec::getStorage).orElseGet(EphemeralStorage::new);

            if (replicas > 0 && StorageUtils.usesEphemeral(storage)) {
                warnings.add(buildCondition(
                        "SchemaRegistryStorage",
                        "A Schema Registry with ephemeral storage will lose schemas after any restart or rolling update."));
            }
        }
    }

    private void checkCruiseControl(List<Condition> warnings) {
        Optional<CruiseControlSpec> cruiseControlSpec = Optional.ofNullable(spec)
                                                            .map(EventStreamsSpec::getStrimziOverrides)
                                                            .map(KafkaSpec::getCruiseControl);
        if (cruiseControlSpec.isPresent()) {
            warnings.add(buildCondition(
                    "CruiseControlEnabled",
                    "Technology Preview features are available to evaluate potential upcoming features. " +
                            "These features are intended for testing purposes only and not for production use, and " +
                            "are not supported by IBM."));
        }
    }

    private Condition buildCondition(String reason, String message) {
        return new ConditionBuilder()
                .withLastTransitionTime(timestamp)
                .withType("Warning")
                .withStatus("True")
                .withReason(reason)
                .withMessage(message)
                .build();
    }
}
