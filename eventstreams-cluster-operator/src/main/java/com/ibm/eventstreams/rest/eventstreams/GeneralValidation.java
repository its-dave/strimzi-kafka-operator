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
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.strimzi.api.kafka.model.CruiseControlSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.storage.EphemeralStorage;
import io.strimzi.api.kafka.model.storage.Storage;
import io.strimzi.operator.cluster.model.StorageUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Checks for potential problems in the configuration requested by the user, to provide
 * warnings and share best practice. The intent is this class will generate warnings about
 * configurations that aren't necessarily illegal or invalid, but that could potentially
 * lead to problems.
 */
public class GeneralValidation implements EventStreamsValidation {
    private static final Logger log = LogManager.getLogger(GeneralValidation.class.getName());

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

    public final static String UI_REQUIRES_REST_COMPONENTS_REASON = "UIRequiresRestComponents";
    public final static String UI_REQUIRES_REST_COMPONENTS_MESSAGE = "One of the following components have not been enabled: %s. "
        + "The UI requires Schema Registry, Admin API and Rest Producer to be enabled to allow for its capabilities. "
        + "Edit %s to enable the component with more than 1 replica.";

    private static final int LONG_NAMESPACE_CHARACTER_LIMIT = 60;

    public List<StatusCondition> validateCr(EventStreams spec) {
        log.traceEntry(() -> spec);
        List<StatusCondition> conditions = new ArrayList<>();
        String namespace = Optional.ofNullable(spec)
            .map(EventStreams::getMetadata)
            .map(ObjectMeta::getNamespace)
            .orElse("");
        checkSchemaRegistryStorage(spec, conditions);
        checkCruiseControl(spec, conditions);
        checkLongNamespace(namespace, conditions);
        checkUiHasRequiredComponents(spec, conditions);
        return log.traceExit(conditions);
    }

    private void checkSchemaRegistryStorage(EventStreams spec, List<StatusCondition> conditions) {
        log.traceEntry(() -> conditions);
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
        log.traceExit();
    }

    private void checkCruiseControl(EventStreams spec, List<StatusCondition> conditions) {
        log.traceEntry(() -> conditions);
        Optional<CruiseControlSpec> cruiseControlSpec = Optional.ofNullable(spec)
                                                            .map(EventStreams::getSpec)
                                                            .map(EventStreamsSpec::getStrimziOverrides)
                                                            .map(KafkaSpec::getCruiseControl);
        if (cruiseControlSpec.isPresent()) {
            conditions.add(StatusCondition.createWarningCondition(
                CRUISE_CONTROL_REASON,
                CRUISE_CONTROL_MESSAGE));
        }
        log.traceExit();
    }

    private void checkLongNamespace(String namespace, List<StatusCondition> conditions) {
        log.traceEntry(() -> namespace);
        if (namespace.length() >= LONG_NAMESPACE_CHARACTER_LIMIT) {
            conditions.add(StatusCondition.createWarningCondition(INSTALLING_IN_LONG_NAMESPACE_REASON, String.format(INSTALLING_IN_LONG_NAMESPACE_MESSAGE, namespace)));
        }
        log.traceExit();
    }

    private void checkUiHasRequiredComponents(EventStreams spec, List<StatusCondition> conditions) {
        log.traceEntry(() -> conditions);
        boolean isUiEnabled = isComponentEnabled(Optional.ofNullable(spec)
                .map(EventStreams::getSpec)
                .map(EventStreamsSpec::getAdminUI));

        if (isUiEnabled) {
            List<String> missingComponents = new ArrayList<>();

            boolean isAdminApiEnabled = isComponentEnabled(Optional.ofNullable(spec)
                .map(EventStreams::getSpec)
                .map(EventStreamsSpec::getAdminApi));

            boolean isSchemaRegistryEnabled = isComponentEnabled(Optional.ofNullable(spec)
                .map(EventStreams::getSpec)
                .map(EventStreamsSpec::getSchemaRegistry));

            boolean isRestProducerEnabled = isComponentEnabled(Optional.ofNullable(spec)
                .map(EventStreams::getSpec)
                .map(EventStreamsSpec::getRestProducer));

            if (!isAdminApiEnabled) {
                missingComponents.add(ADMIN_API_SPEC_NAME);
            }

            if (!isSchemaRegistryEnabled) {
                missingComponents.add(SCHEMA_REGISTRY_SPEC_NAME);
            }

            if (!isRestProducerEnabled) {
                missingComponents.add(REST_PRODUCER_SPEC_NAME);
            }

            if (missingComponents.size() > 0) {
                conditions.add(StatusCondition.createErrorCondition(UI_REQUIRES_REST_COMPONENTS_REASON, getUiRequiresRestComponentsMessage(missingComponents)));
            }
        }
        log.traceExit();
    }

    private <T extends ComponentSpec> boolean isComponentEnabled(Optional<T> spec) {
        log.traceEntry(() -> spec);
        return log.traceExit(spec.isPresent()
            ? spec
            .map(ComponentSpec::getReplicas)
            .map(replica -> replica > 0)
            .orElse(true)
            : false);
    }

    private String getUiRequiresRestComponentsMessage(List<String> missingComponents) {
        log.traceEntry(() -> missingComponents);
        List<String> specPath = missingComponents.stream().map(missingComponent -> String.format("spec.%s.replicas", missingComponent)).collect(Collectors.toList());
        return log.traceExit(String.format(UI_REQUIRES_REST_COMPONENTS_MESSAGE, String.join(", ", missingComponents), String.join(", ", specPath)));
    }
}
