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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.spec.AdminApiSpec;
import com.ibm.eventstreams.api.spec.EndpointSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.SchemaRegistrySpec;
import com.ibm.eventstreams.api.spec.SecurityComponentSpec;
import com.ibm.eventstreams.rest.ValidationResponsePayload.ValidationResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternal;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;


public class EndpointValidation extends AbstractValidation {

    private static final Logger log = LogManager.getLogger(EndpointValidation.class.getName());

    public static final String ADMIN_API_SPEC_NAME = "adminApi";
    public static final String REST_PRODUCER_SPEC_NAME = "restProducer";
    public static final String SCHEMA_REGISTRY_SPEC_NAME = "schemaRegistry";
    public static final String FAILURE_REASON = "InvalidEndpoints";
    public static final int ENDPOINT_NAME_MAX_LENGTH = 16;

    public static void rejectInvalidEndpoint(RoutingContext routingContext) {

        EventStreams instance = getSpecFromRequest(routingContext);
        ValidationResponsePayload outcome = validateEndpoints(instance);
        routingContext
                .response()
                .setStatusCode(200)
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(outcome.getResponse() == null ? ValidationResponsePayload.createSuccessResponsePayload() : outcome));
        log.traceExit();
    }

    public static ValidationResponsePayload validateEndpoints(EventStreams instance) {
        // get the spec that contain endpoints
        Optional<List<EndpointSpec>> adminApiEndpoints = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getAdminApi).map(AdminApiSpec::getEndpoints);
        Optional<List<EndpointSpec>> restProdEndpoints = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getRestProducer).map(SecurityComponentSpec::getEndpoints);
        Optional<List<EndpointSpec>> schemaRegistryEndpoints = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getSchemaRegistry).map(SchemaRegistrySpec::getEndpoints);
        Optional<KafkaListeners> listeners = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getStrimziOverrides).map(KafkaSpec::getKafka).map(KafkaClusterSpec::getListeners);
        ValidationResponsePayload outcome = new ValidationResponsePayload(null);
        if (adminApiEndpoints.isPresent()) {
            checkNoEndpointsOnReservedPorts(outcome, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
            checkNameTooLong(outcome, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
            checkUniqueNames(outcome, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
            checkUniquePorts(outcome, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
            checkValidTypes(outcome, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
        }
        if (restProdEndpoints.isPresent()) {
            checkNoEndpointsOnReservedPorts(outcome, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkNameTooLong(outcome, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkUniqueNames(outcome, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkUniquePorts(outcome, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkValidTypes(outcome, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
        }
        if (schemaRegistryEndpoints.isPresent()) {
            checkNoEndpointsOnReservedPorts(outcome, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
            checkNameTooLong(outcome, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
            checkUniqueNames(outcome, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
            checkUniquePorts(outcome, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
            checkValidTypes(outcome, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
        }
        checkKafkaListenersValidTypes(outcome, listeners);
        return outcome;
    }

    // TODO what kafka listener types are actually invalid
    private static void checkKafkaListenersValidTypes(ValidationResponsePayload outcome, Optional<KafkaListeners> listeners) {
        if (listeners.map(KafkaListeners::getExternal).map(KafkaListenerExternal::getType).isPresent() &&  !listeners.map(KafkaListeners::getExternal).map(KafkaListenerExternal::getType).get().equals("route")) {
            outcome.setResponse(invalidKafkaListenerResponse("external"));
        }
    }

    private static ValidationResponse invalidKafkaListenerResponse(String listener) {
        return ValidationResponsePayload.createFailureResponse("Invalid " + listener + " kafka listener type, kafka listener can only have type 'route'",
            FAILURE_REASON);
    }

    private static void checkNoEndpointsOnReservedPorts(ValidationResponsePayload outcome, String specName, List<EndpointSpec> endpoints) {
        if (hasEndpointsOnReservedPorts(endpoints)) {
            outcome.setResponse(reservedEndpointResponse(specName));
        }
    }

    private static boolean hasEndpointsOnReservedPorts(List<EndpointSpec> endpoints) {
        return endpoints.stream()
            .filter(endpoint -> endpoint.getAccessPort().intValue() == Endpoint.DEFAULT_P2P_PLAIN_PORT || endpoint.getAccessPort().intValue() == Endpoint.DEFAULT_P2P_TLS_PORT)
            .findAny()
            .isPresent();
    }

    private static ValidationResponse reservedEndpointResponse(String spec) {
        return ValidationResponsePayload.createFailureResponse(
            spec + " endpoint configuration has requested access on a reserved port 7080/7443",
            FAILURE_REASON);
    }


    private static void checkNameTooLong(ValidationResponsePayload outcome, String specName, List<EndpointSpec> endpoints) {
        if (hasNameTooLong(endpoints)) {
            outcome.setResponse(nameTooLongResponse(specName));
        }
    }

    private static boolean hasNameTooLong(List<EndpointSpec> endpoints) {
        return endpoints.stream()
            .filter(endpoint -> endpoint.getName().length() > ENDPOINT_NAME_MAX_LENGTH)
            .findAny()
            .isPresent();
    }

    private static ValidationResponse nameTooLongResponse(String spec) {
        return ValidationResponsePayload.createFailureResponse(
            spec + " endpoint configuration has an endpoint with a too long name, name cannot be longer than " + ENDPOINT_NAME_MAX_LENGTH + " characters",
            FAILURE_REASON);
    }


    private static void checkUniqueNames(ValidationResponsePayload outcome, String specName, List<EndpointSpec> endpoints) {
        if (!hasUniqueNames(endpoints)) {
            outcome.setResponse(nonUniqueNameResponse(specName));
        }
    }

    private static boolean hasUniqueNames(List<EndpointSpec> endpoints) {
        Set<String> names = new HashSet<String>();
        endpoints.forEach(endpoint -> names.add(endpoint.getName()));
        return names.size() == endpoints.size();
    }

    private static ValidationResponse nonUniqueNameResponse(String spec) {
        return ValidationResponsePayload.createFailureResponse(
            spec + " endpoint configuration has endpoints with the same name",
            FAILURE_REASON);
    }

    private static void checkUniquePorts(ValidationResponsePayload outcome, String specName, List<EndpointSpec> endpoints) {
        if (!hasUniquePorts(endpoints)) {
            outcome.setResponse(nonUniquePortResponse(specName));
        }
    }
    private static boolean hasUniquePorts(List<EndpointSpec> endpoints) {
        Set<Integer> ports = new HashSet<Integer>();
        endpoints.forEach(endpoint -> ports.add(endpoint.getAccessPort()));
        return ports.size() == endpoints.size();
    }

    private static ValidationResponse nonUniquePortResponse(String spec) {
        return ValidationResponsePayload.createFailureResponse(
            spec + " endpoint configuration has endpoints with the same accessPort",
            FAILURE_REASON);
    }

    private static void checkValidTypes(ValidationResponsePayload outcome, String specName, List<EndpointSpec> endpoints) {
        if (hasInvalidTypes(endpoints)) {
            outcome.setResponse(invalidTypeResponse(specName));
        }
    }
    private static boolean hasInvalidTypes(List<EndpointSpec> endpoints) {
        return endpoints.stream()
            .filter(endpoint -> {
                return endpoint.getType() != null &&
                    (endpoint.getType().equals(EndpointServiceType.NODE_PORT) ||
                    endpoint.getType().equals(EndpointServiceType.INGRESS) ||
                    endpoint.getType().equals(EndpointServiceType.LOAD_BALANCER));
            })
            .findAny()
            .isPresent();
    }

    private static ValidationResponse invalidTypeResponse(String spec) {
        return ValidationResponsePayload.createFailureResponse(
            spec + " endpoint configuration has endpoints with invalid types. Acceptable types are 'Route' and 'Internal'",
            FAILURE_REASON);
    }
}
