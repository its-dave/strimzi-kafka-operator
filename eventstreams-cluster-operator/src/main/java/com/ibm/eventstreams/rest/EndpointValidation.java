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

import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.spec.AdminUISpec;
import com.ibm.eventstreams.api.spec.EndpointSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.SchemaRegistrySpec;
import com.ibm.eventstreams.api.spec.SecurityComponentSpec;
import com.ibm.eventstreams.rest.ValidationResponsePayload.ValidationResponse;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternal;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;


public class EndpointValidation extends AbstractValidation {

    private static final Logger log = LogManager.getLogger(EndpointValidation.class.getName());

    public static final String ADMIN_API_SPEC_NAME = "adminApi";
    public static final String REST_PRODUCER_SPEC_NAME = "restProducer";
    public static final String SCHEMA_REGISTRY_SPEC_NAME = "schemaRegistry";
    public static final String ADMIN_UI_SPEC_NAME = "adminUi";

    public static final String INVALID_PORT_REASON = "InvalidEndpointPort";
    public static final String INVALID_ENDPOINT_NAME_REASON = "InvalidEndpointName";
    public static final String DUPLICATE_ENDPOINT_NAME_REASON = "DuplicateEndpointNames";
    public static final String DUPLICATE_ENDPOINT_PORTS_REASON = "DuplicateEndpointPorts";
    public static final String INVALID_ENDPOINT_TYPE_REASON = "InvalidEndpointType";
    public static final String INVALID_EXTERNAL_KAFKA_LISTENER_TYPE = "InvalidExternalKafkaListenerType";
    public static final String UNSUPPORTED_ENDPOINT_AUTHENTICATION_MECHANISM_REASON = "UnsupportedEndpointAuthenticationMechanism";
    public static final String INVALID_HOSTNAME_REASON = "InvalidHostNames";
    public static final String DUPLICATE_HOST_NAMES_REASON = "DuplicateHostNames";

    public static final int ENDPOINT_NAME_MAX_LENGTH = 16;
    public static final int ROUTE_HOST_NAME_MAX_LENGTH = 64;
    private static final String VALID_NAME_REGEX = "^[a-z][-a-z0-9]*$";
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile(VALID_NAME_REGEX);
    private static final String VALID_HOST_REGEX = "^[a-z][-a-z0-9.]*$";
    private static final Pattern VALID_HOST_PATTERN = Pattern.compile(VALID_HOST_REGEX);

    public static void rejectInvalidEndpoint(RoutingContext routingContext) {
        EventStreams instance = getSpecFromRequest(routingContext);
        List<ValidationResponse> responses = validateEndpoints(instance);
        routingContext
            .response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json; charset=utf-8")
            // Get the first outcome when checking the CR
            .end(Json.encodePrettily(responses.size() == 0 ? ValidationResponsePayload.createSuccessResponsePayload() : new ValidationResponsePayload(responses.get(0))));
        log.traceExit();
    }

    public static List<ValidationResponse> validateEndpoints(EventStreams instance) {
        List<ValidationResponse> responses = new ArrayList<>();
        // get the spec that contain endpoints
        Optional<List<EndpointSpec>> adminApiEndpoints = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getAdminApi).map(SecurityComponentSpec::getEndpoints);
        Optional<List<EndpointSpec>> restProdEndpoints = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getRestProducer).map(SecurityComponentSpec::getEndpoints);
        Optional<List<EndpointSpec>> schemaRegistryEndpoints = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getSchemaRegistry).map(SchemaRegistrySpec::getEndpoints);
        Optional<String> adminUiHost = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getAdminUI).map(AdminUISpec::getHost);
        Optional<KafkaListeners> listeners = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getStrimziOverrides).map(KafkaSpec::getKafka).map(KafkaClusterSpec::getListeners);
        if (adminApiEndpoints.isPresent()) {
            checkNoEndpointsOnReservedPorts(responses, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
            checkValidNames(responses, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
            checkUniqueNames(responses, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
            checkUniquePorts(responses, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
            checkValidTypes(responses, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
        }
        if (restProdEndpoints.isPresent()) {
            checkNoEndpointsOnReservedPorts(responses, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkValidNames(responses, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkUniqueNames(responses, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkUniquePorts(responses, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkValidTypes(responses, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkNoIAMBearer(responses, restProdEndpoints.get());
        }
        if (schemaRegistryEndpoints.isPresent()) {
            checkNoEndpointsOnReservedPorts(responses, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
            checkValidNames(responses, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
            checkUniqueNames(responses, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
            checkUniquePorts(responses, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
            checkValidTypes(responses, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
        }
        checkKafkaListenersValidTypes(responses, listeners);
        checkHasUniqueHosts(responses, adminApiEndpoints, restProdEndpoints, schemaRegistryEndpoints, adminUiHost);
        checkValidHostNames(responses, adminApiEndpoints, restProdEndpoints, schemaRegistryEndpoints, adminUiHost);
        return responses;
    }

    // TODO what kafka listener types are actually invalid
    private static void checkKafkaListenersValidTypes(List<ValidationResponse> responses, Optional<KafkaListeners> listeners) {
        if (listeners.map(KafkaListeners::getExternal).map(KafkaListenerExternal::getType).isPresent() &&  !listeners.map(KafkaListeners::getExternal).map(KafkaListenerExternal::getType).get().equals("route")) {
            responses.add(invalidExternalListenerType());
        }
    }

    private static void checkValidHostNames(List<ValidationResponse> responses, Optional<List<EndpointSpec>> adminApiEndpoints, Optional<List<EndpointSpec>> restProducerEndpoints, Optional<List<EndpointSpec>> schemaRegistryEndpoints, Optional<String> adminUiHost) {
        checkEndpointSpecsHasValidHosts(responses, adminApiEndpoints, ADMIN_API_SPEC_NAME);
        checkEndpointSpecsHasValidHosts(responses, restProducerEndpoints, REST_PRODUCER_SPEC_NAME);
        checkEndpointSpecsHasValidHosts(responses, schemaRegistryEndpoints, SCHEMA_REGISTRY_SPEC_NAME);
        adminUiHost.ifPresent(host -> checkHostIsValid(responses, host, ADMIN_UI_SPEC_NAME));
    }

    private static void checkEndpointSpecsHasValidHosts(List<ValidationResponse> responses, Optional<List<EndpointSpec>> spec, String component) {
        spec.ifPresent(endpoints ->
            endpoints.stream().map(EndpointSpec::getHost)
                .filter(Objects::nonNull)
                .forEach(host -> checkHostIsValid(responses, host, component)));
    }

    private static void checkHostIsValid(List<ValidationResponse> responses, String host, String spec) {
        if (!VALID_HOST_PATTERN.matcher(host).matches()) {
            responses.add(invalidHostNameResponse(host, spec));
        } else if (doesExceedMaxHostLengthLimit(host)) {
            responses.add(invalidHostNameLengthResponse(host, spec));
        }
    }

    private static ValidationResponse invalidHostNameLengthResponse(String hostname, String spec) {
        return ValidationResponsePayload.createFailureResponse(String.format("%s host '%s' is an invalid hostname. A valid hostname cannot be longer than 64 characters. Edit spec.%s.endpoints to provide a valid hostname.", spec, hostname, spec),
            INVALID_HOSTNAME_REASON);
    }

    private static boolean doesExceedMaxHostLengthLimit(String host) {
        return host.length() > ROUTE_HOST_NAME_MAX_LENGTH;
    }

    private static ValidationResponse invalidHostNameResponse(String hostname, String spec) {
        return ValidationResponsePayload.createFailureResponse(String.format("%s host '%s' is an invalid hostname. A valid hostname contains lowercase alphanumeric characters and full stops (%s). Edit spec.%s.endpoints to provide a valid hostname.", spec, hostname, VALID_NAME_REGEX, spec),
            INVALID_HOSTNAME_REASON);
    }

    private static void checkHasUniqueHosts(List<ValidationResponse> responses, Optional<List<EndpointSpec>> adminApiEndpoints, Optional<List<EndpointSpec>> restProducerEndpoints, Optional<List<EndpointSpec>> schemaRegistryEndpoints, Optional<String> adminUiHost) {
        if (!hasUniqueHosts(adminApiEndpoints, restProducerEndpoints, schemaRegistryEndpoints, adminUiHost)) {
            responses.add(nonUniqueHostNamesResponse());
        }
    }

    private static boolean hasUniqueHosts(Optional<List<EndpointSpec>> adminApiEndpoints, Optional<List<EndpointSpec>> restProducerEndpoints, Optional<List<EndpointSpec>> schemaRegistryEndpoints, Optional<String> adminUiHost) {
        Set<String> hosts = new HashSet<>();
        AtomicInteger numOfDefinedHosts = new AtomicInteger(0);

        addHostsToSetAndIncrementCount(adminApiEndpoints, hosts, numOfDefinedHosts);
        addHostsToSetAndIncrementCount(restProducerEndpoints, hosts, numOfDefinedHosts);
        addHostsToSetAndIncrementCount(schemaRegistryEndpoints, hosts, numOfDefinedHosts);
        adminUiHost.ifPresent(host -> addHostToSetAndIncrement(host, hosts, numOfDefinedHosts));

        return hosts.size() == numOfDefinedHosts.get();
    }

    private static ValidationResponse nonUniqueHostNamesResponse() {
        return ValidationResponsePayload.createFailureResponse(String.format("There are two or more hosts that have the same value. Each host must have a unique value. To provide unique hostnames, edit spec.%s.endpoints, spec.%s.endpoints, spec.%s.endpoints, and spec.%s.host.",
            ADMIN_API_SPEC_NAME, REST_PRODUCER_SPEC_NAME, SCHEMA_REGISTRY_SPEC_NAME, ADMIN_UI_SPEC_NAME),
            DUPLICATE_HOST_NAMES_REASON);
    }

    private static void addHostsToSetAndIncrementCount(Optional<List<EndpointSpec>> endpointSpecs, Set<String> hosts, AtomicInteger numOfDefinedHosts) {
        endpointSpecs.ifPresent(endpoints -> endpoints.stream()
            .map(EndpointSpec::getHost)
            .filter(Objects::nonNull)
            .forEachOrdered(host -> addHostToSetAndIncrement(host, hosts, numOfDefinedHosts)));
    }

    private static void addHostToSetAndIncrement(String host, Set<String> hosts, AtomicInteger numOfDefinedHosts) {
        hosts.add(host);
        numOfDefinedHosts.incrementAndGet();
    }

    private static ValidationResponse invalidExternalListenerType() {
        return ValidationResponsePayload.createFailureResponse("spec.strimziOverrides.kafka.listener.external.type is an invalid listener type. Edit spec.strimziOverrides.kafka.listener.external.type to set 'route' as the value.",
            INVALID_EXTERNAL_KAFKA_LISTENER_TYPE);
    }

    private static void checkNoEndpointsOnReservedPorts(List<ValidationResponse> responses, String specName, List<EndpointSpec> endpoints) {
        if (hasEndpointsOnReservedPorts(endpoints)) {
            responses.add(reservedEndpointResponse(specName));
        }
    }

    private static boolean hasEndpointsOnReservedPorts(List<EndpointSpec> endpoints) {
        return endpoints.stream()
            .anyMatch(endpoint -> endpoint.getContainerPort() >= 7000 && endpoint.getContainerPort() <= 7999); // reserve 7000 - 7999 to give us some space
    }

    private static ValidationResponse reservedEndpointResponse(String spec) {
        return ValidationResponsePayload.createFailureResponse(
            String.format("%s has an endpoint that requested access on a reserved port between 7000 and 7999, inclusive. Edit spec.%s.endpoints to choose a port number outside of that range.", spec, spec),
            INVALID_PORT_REASON);
    }

    private static void checkValidNames(List<ValidationResponse> responses, String specName, List<EndpointSpec> endpoints) {
        if (hasInvalidName(endpoints)) {
            responses.add(invalidEndpointNameResponse(specName));
        } else if (hasNameTooLong(endpoints)) {
            responses.add(nameTooLongResponse(specName));
        }
    }

    private static boolean hasInvalidName(List<EndpointSpec> endpoints) {
        return endpoints.stream()
            .anyMatch(endpoint -> !VALID_NAME_PATTERN.matcher(endpoint.getName()).matches());
    }

    private static ValidationResponse invalidEndpointNameResponse(String spec) {
        return ValidationResponsePayload.createFailureResponse(
            String.format("%s has an endpoint with an invalid name. Acceptable names are lowercase alphanumeric with dashes (%s). Edit spec.%s.endpoints to provide a valid endpoint names.", spec, VALID_NAME_REGEX, spec),
            INVALID_ENDPOINT_NAME_REASON);
    }

    private static boolean hasNameTooLong(List<EndpointSpec> endpoints) {
        return endpoints.stream()
            .anyMatch(endpoint -> endpoint.getName().length() > ENDPOINT_NAME_MAX_LENGTH);
    }

    private static ValidationResponse nameTooLongResponse(String spec) {
        return ValidationResponsePayload.createFailureResponse(
            String.format("%s has an endpoint with an invalid name. Names cannot be longer than %d characters. Edit spec.%s.endpoints to provide a valid endpoint name.", spec, ENDPOINT_NAME_MAX_LENGTH, spec),
            INVALID_ENDPOINT_NAME_REASON);
    }

    private static void checkUniqueNames(List<ValidationResponse> responses, String specName, List<EndpointSpec> endpoints) {
        if (!hasUniqueNames(endpoints)) {
            responses.add(nonUniqueNameResponse(specName));
        }
    }

    private static boolean hasUniqueNames(List<EndpointSpec> endpoints) {
        Set<String> names = new HashSet<String>();
        endpoints.forEach(endpoint -> names.add(endpoint.getName()));
        return names.size() == endpoints.size();
    }

    private static ValidationResponse nonUniqueNameResponse(String spec) {
        return ValidationResponsePayload.createFailureResponse(
            String.format("%s has multiple endpoints with the same name. Edit spec.%s.endpoints to ensure that each endpoint has a unique name.", spec, spec),
            DUPLICATE_ENDPOINT_NAME_REASON);
    }

    private static void checkUniquePorts(List<ValidationResponse> responses, String specName, List<EndpointSpec> endpoints) {
        if (!hasUniquePorts(endpoints)) {
            responses.add(nonUniquePortResponse(specName));
        }
    }
    private static boolean hasUniquePorts(List<EndpointSpec> endpoints) {
        Set<Integer> ports = new HashSet<>();
        endpoints.forEach(endpoint -> ports.add(endpoint.getContainerPort()));
        return ports.size() == endpoints.size();
    }

    private static ValidationResponse nonUniquePortResponse(String spec) {
        return ValidationResponsePayload.createFailureResponse(
            String.format("%s has multiple endpoints with the same containerPort. Edit spec.%s.endpoints to ensure that each endpoint has a unique containerPort.", spec, spec),
            DUPLICATE_ENDPOINT_PORTS_REASON);
    }

    private static void checkValidTypes(List<ValidationResponse> responses, String specName, List<EndpointSpec> endpoints) {
        if (hasInvalidTypes(endpoints)) {
            responses.add(invalidTypeResponse(specName));
        }
    }

    private static boolean hasInvalidTypes(List<EndpointSpec> endpoints) {
        return endpoints.stream()
            .filter(endpoint -> endpoint.getType() != null)
            .anyMatch(endpoint -> endpoint.getType().equals(EndpointServiceType.NODE_PORT) ||
                endpoint.getType().equals(EndpointServiceType.INGRESS) ||
                endpoint.getType().equals(EndpointServiceType.LOAD_BALANCER));
    }

    private static ValidationResponse invalidTypeResponse(String spec) {
        return ValidationResponsePayload.createFailureResponse(
            String.format("%s has an endpoint with an invalid type. Acceptable types are 'Route' and 'Internal'. Edit spec.%s.endpoints to ensure that each endpoint has an acceptable type.", spec, spec),
            INVALID_ENDPOINT_TYPE_REASON);
    }

    private static void checkNoIAMBearer(List<ValidationResponse> responses, List<EndpointSpec> endpoints) {
        if (hasIAMBearerAuth(endpoints)) {
            responses.add(invalidIAMBearerEndpointResponse());
        }
    }

    private static boolean hasIAMBearerAuth(List<EndpointSpec> endpoints) {
        return endpoints.stream()
            .anyMatch(endpoint -> Optional.ofNullable(endpoint.getAuthenticationMechanisms()).orElse(Collections.emptyList()).contains(Endpoint.IAM_BEARER_KEY));
    }

    private static ValidationResponse invalidIAMBearerEndpointResponse() {
        return ValidationResponsePayload.createFailureResponse(
            String.format("restProducer has an endpoint using authentication mechanism '%s' which is not supported. Edit the authenticationMechanisms property in spec.restProducer.endpoints to set '%s', '%s', or both.", Endpoint.IAM_BEARER_KEY, Endpoint.SCRAM_SHA_512_KEY, Endpoint.MAC_KEY),
            UNSUPPORTED_ENDPOINT_AUTHENTICATION_MECHANISM_REASON);
    }
}
