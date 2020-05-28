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

import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.spec.AdminUISpec;
import com.ibm.eventstreams.api.spec.EndpointSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.SchemaRegistrySpec;
import com.ibm.eventstreams.api.spec.SecurityComponentSpec;
import com.ibm.eventstreams.controller.models.StatusCondition;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternal;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
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


public class EndpointValidation implements EventStreamsValidation {

    private static final Logger log = LogManager.getLogger(EndpointValidation.class.getName());

    public static final String INVALID_PORT_REASON = "InvalidEndpointPort";
    public static final String INVALID_ENDPOINT_NAME_REASON = "InvalidEndpointName";
    public static final String DUPLICATE_ENDPOINT_NAME_REASON = "DuplicateEndpointNames";
    public static final String DUPLICATE_ENDPOINT_PORTS_REASON = "DuplicateEndpointPorts";
    public static final String INVALID_ENDPOINT_TYPE_REASON = "InvalidEndpointType";
    public static final String INVALID_EXTERNAL_KAFKA_LISTENER_TYPE = "InvalidExternalKafkaListenerType";
    public static final String UNSUPPORTED_ENDPOINT_AUTHENTICATION_MECHANISM_REASON = "UnsupportedEndpointAuthenticationMechanism";
    public static final String ADMIN_API_MISSING_IAM_BEARER_REASON = "AdminApiMissingIamBearerAuthenticationMechanism";
    public static final String INVALID_HOSTNAME_REASON = "InvalidHostNames";
    public static final String DUPLICATE_HOST_NAMES_REASON = "DuplicateHostNames";

    public static final String ADMIN_API_MISSING_IAM_BEARER_MESSAGE = "Admin Api does not have a route with iam-bearer authentication. "
        + "iam-bearer authentication is required to use the Event Streams CLI and to create KafkaUsers through Admin API."
        + "To enable these functionalities, add 'iam-bearer' to an endpoint in spec.adminApi.endpoints";

    public static final int ENDPOINT_NAME_MAX_LENGTH = 16;
    public static final int ROUTE_HOST_NAME_MAX_LENGTH = 64;
    private static final String VALID_NAME_REGEX = "^[a-z][-a-z0-9]*$";
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile(VALID_NAME_REGEX);
    private static final String VALID_HOST_REGEX = "^[a-z][-a-z0-9.]*$";
    private static final Pattern VALID_HOST_PATTERN = Pattern.compile(VALID_HOST_REGEX);

    public List<StatusCondition> validateCr(EventStreams instance) {
        log.traceEntry(() -> instance);
        List<StatusCondition> conditions = new ArrayList<>();
        // get the spec that contain endpoints
        Optional<List<EndpointSpec>> adminApiEndpoints = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getAdminApi).map(SecurityComponentSpec::getEndpoints);
        Optional<List<EndpointSpec>> restProdEndpoints = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getRestProducer).map(SecurityComponentSpec::getEndpoints);
        Optional<List<EndpointSpec>> schemaRegistryEndpoints = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getSchemaRegistry).map(SchemaRegistrySpec::getEndpoints);
        Optional<String> adminUiHost = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getAdminUI).map(AdminUISpec::getHost);
        Optional<KafkaListeners> listeners = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getStrimziOverrides).map(KafkaSpec::getKafka).map(KafkaClusterSpec::getListeners);
        if (adminApiEndpoints.isPresent()) {
            checkNoEndpointsOnReservedPorts(conditions, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
            checkValidNames(conditions, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
            checkUniqueNames(conditions, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
            checkUniquePorts(conditions, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
            checkValidTypes(conditions, ADMIN_API_SPEC_NAME, adminApiEndpoints.get());
            checkAdminApiHasIAMBearer(conditions, adminApiEndpoints.get());
        }
        if (restProdEndpoints.isPresent()) {
            checkNoEndpointsOnReservedPorts(conditions, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkValidNames(conditions, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkUniqueNames(conditions, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkUniquePorts(conditions, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkValidTypes(conditions, REST_PRODUCER_SPEC_NAME, restProdEndpoints.get());
            checkNoIAMBearer(conditions, restProdEndpoints.get());
        }
        if (schemaRegistryEndpoints.isPresent()) {
            checkNoEndpointsOnReservedPorts(conditions, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
            checkValidNames(conditions, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
            checkUniqueNames(conditions, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
            checkUniquePorts(conditions, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
            checkValidTypes(conditions, SCHEMA_REGISTRY_SPEC_NAME, schemaRegistryEndpoints.get());
        }
        checkKafkaListenersValidTypes(conditions, listeners);
        checkHasUniqueHosts(conditions, adminApiEndpoints, restProdEndpoints, schemaRegistryEndpoints, adminUiHost);
        checkValidHostNames(conditions, adminApiEndpoints, restProdEndpoints, schemaRegistryEndpoints, adminUiHost);
        return log.traceExit(conditions);
    }

    // TODO what kafka listener types are actually invalid
    private static void checkKafkaListenersValidTypes(List<StatusCondition> conditions, Optional<KafkaListeners> listeners) {
        log.traceEntry(() -> conditions, () -> listeners);
        if (listeners.map(KafkaListeners::getExternal).map(KafkaListenerExternal::getType).isPresent() &&  !listeners.map(KafkaListeners::getExternal).map(KafkaListenerExternal::getType).get().equals("route")) {
            conditions.add(invalidExternalListenerType());
        }
        log.traceExit();
    }

    private static void checkValidHostNames(List<StatusCondition> conditions, Optional<List<EndpointSpec>> adminApiEndpoints, Optional<List<EndpointSpec>> restProducerEndpoints, Optional<List<EndpointSpec>> schemaRegistryEndpoints, Optional<String> adminUiHost) {
        log.traceEntry(() -> conditions, () -> adminApiEndpoints, () -> restProducerEndpoints, () -> schemaRegistryEndpoints, () -> adminUiHost);
        checkEndpointSpecsHasValidHosts(conditions, adminApiEndpoints, ADMIN_API_SPEC_NAME);
        checkEndpointSpecsHasValidHosts(conditions, restProducerEndpoints, REST_PRODUCER_SPEC_NAME);
        checkEndpointSpecsHasValidHosts(conditions, schemaRegistryEndpoints, SCHEMA_REGISTRY_SPEC_NAME);
        adminUiHost.ifPresent(host -> checkHostIsValid(conditions, host, ADMIN_UI_SPEC_NAME));
        log.traceExit();
    }

    private static void checkEndpointSpecsHasValidHosts(List<StatusCondition> conditions, Optional<List<EndpointSpec>> spec, String component) {
        log.traceEntry(() -> component);
        spec.ifPresent(endpoints ->
            endpoints.stream().map(EndpointSpec::getHost)
                .filter(Objects::nonNull)
                .forEach(host -> checkHostIsValid(conditions, host, component)));
        log.traceExit();
    }

    private static void checkHostIsValid(List<StatusCondition> conditions, String host, String spec) {
        log.traceEntry(() -> host, () -> spec);
        if (!VALID_HOST_PATTERN.matcher(host).matches()) {
            conditions.add(invalidHostNameResponse(host, spec));
        } else if (doesExceedMaxHostLengthLimit(host)) {
            conditions.add(invalidHostNameLengthResponse(host, spec));
        }
        log.traceEntry();
    }

    private static StatusCondition invalidHostNameLengthResponse(String hostname, String spec) {
        log.traceEntry(() -> hostname, () -> spec);
        return log.traceExit(StatusCondition.createErrorCondition(INVALID_HOSTNAME_REASON,
            String.format("%s host '%s' is an invalid hostname. A valid hostname cannot be longer than 64 characters. Edit spec.%s.endpoints to provide a valid hostname.", spec, hostname, spec)));
    }

    private static boolean doesExceedMaxHostLengthLimit(String host) {
        log.traceEntry(() -> host);
        return log.traceExit(host.length() > ROUTE_HOST_NAME_MAX_LENGTH);
    }

    private static StatusCondition invalidHostNameResponse(String hostname, String spec) {
        log.traceEntry(() -> hostname, () -> spec);
        return log.traceExit(StatusCondition.createErrorCondition(INVALID_HOSTNAME_REASON,
            String.format("%s host '%s' is an invalid hostname. A valid hostname contains lowercase alphanumeric characters and full stops (%s). Edit spec.%s.endpoints to provide a valid hostname.", spec, hostname, VALID_NAME_REGEX, spec)));
    }

    private static void checkHasUniqueHosts(List<StatusCondition> conditions, Optional<List<EndpointSpec>> adminApiEndpoints, Optional<List<EndpointSpec>> restProducerEndpoints, Optional<List<EndpointSpec>> schemaRegistryEndpoints, Optional<String> adminUiHost) {
        log.traceEntry(() -> conditions, () -> adminApiEndpoints, () -> restProducerEndpoints, () -> schemaRegistryEndpoints, () -> adminUiHost);
        if (!hasUniqueHosts(adminApiEndpoints, restProducerEndpoints, schemaRegistryEndpoints, adminUiHost)) {
            conditions.add(nonUniqueHostNamesResponse());
        }
    }

    private static boolean hasUniqueHosts(Optional<List<EndpointSpec>> adminApiEndpoints, Optional<List<EndpointSpec>> restProducerEndpoints, Optional<List<EndpointSpec>> schemaRegistryEndpoints, Optional<String> adminUiHost) {
        log.traceEntry();
        Set<String> hosts = new HashSet<>();
        AtomicInteger numOfDefinedHosts = new AtomicInteger(0);

        addHostsToSetAndIncrementCount(adminApiEndpoints, hosts, numOfDefinedHosts);
        addHostsToSetAndIncrementCount(restProducerEndpoints, hosts, numOfDefinedHosts);
        addHostsToSetAndIncrementCount(schemaRegistryEndpoints, hosts, numOfDefinedHosts);
        adminUiHost.ifPresent(host -> addHostToSetAndIncrement(host, hosts, numOfDefinedHosts));

        return log.traceExit(hosts.size() == numOfDefinedHosts.get());
    }

    private static StatusCondition nonUniqueHostNamesResponse() {
        log.traceEntry();
        return log.traceExit(StatusCondition.createErrorCondition(DUPLICATE_HOST_NAMES_REASON,
            String.format("There are two or more hosts that have the same value. Each host must have a unique value. To provide unique hostnames, edit spec.%s.endpoints, spec.%s.endpoints, spec.%s.endpoints, and spec.%s.host.",
                ADMIN_API_SPEC_NAME, REST_PRODUCER_SPEC_NAME, SCHEMA_REGISTRY_SPEC_NAME, ADMIN_UI_SPEC_NAME)));
    }

    private static void addHostsToSetAndIncrementCount(Optional<List<EndpointSpec>> endpointSpecs, Set<String> hosts, AtomicInteger numOfDefinedHosts) {
        log.traceEntry(() -> endpointSpecs, () -> hosts, () -> numOfDefinedHosts);
        endpointSpecs.ifPresent(endpoints -> endpoints.stream()
            .map(EndpointSpec::getHost)
            .filter(Objects::nonNull)
            .forEachOrdered(host -> addHostToSetAndIncrement(host, hosts, numOfDefinedHosts)));
        log.traceExit();
    }

    private static void addHostToSetAndIncrement(String host, Set<String> hosts, AtomicInteger numOfDefinedHosts) {
        log.traceEntry(() -> host, () -> hosts, () -> numOfDefinedHosts);
        hosts.add(host);
        numOfDefinedHosts.incrementAndGet();
        log.traceExit();
    }

    private static StatusCondition invalidExternalListenerType() {
        log.traceEntry();
        return log.traceExit(StatusCondition.createErrorCondition(INVALID_EXTERNAL_KAFKA_LISTENER_TYPE,
            "spec.strimziOverrides.kafka.listener.external.type is an invalid listener type. Edit spec.strimziOverrides.kafka.listener.external.type to set 'route' as the value."));
    }

    private static void checkNoEndpointsOnReservedPorts(List<StatusCondition> conditions, String specName, List<EndpointSpec> endpoints) {
        log.traceEntry(() -> specName, () -> endpoints);
        if (hasEndpointsOnReservedPorts(endpoints)) {
            conditions.add(reservedEndpointResponse(specName));
        }
        log.traceExit();
    }

    private static boolean hasEndpointsOnReservedPorts(List<EndpointSpec> endpoints) {
        log.traceEntry();
        return log.traceExit(endpoints.stream()
            .anyMatch(endpoint -> endpoint.getContainerPort() >= 7000 && endpoint.getContainerPort() <= 7999)); // reserve 7000 - 7999 to give us some space
    }

    private static StatusCondition reservedEndpointResponse(String spec) {
        log.traceEntry(() -> spec);
        return log.traceExit(StatusCondition.createErrorCondition(INVALID_PORT_REASON,
            String.format("%s has an endpoint that requested access on a reserved port between 7000 and 7999, inclusive. Edit spec.%s.endpoints to choose a port number outside of that range.", spec, spec)));
    }

    private static void checkValidNames(List<StatusCondition> conditions, String specName, List<EndpointSpec> endpoints) {
        log.traceEntry(() -> specName, () -> endpoints);
        if (hasInvalidName(endpoints)) {
            conditions.add(invalidEndpointNameResponse(specName));
        } else if (hasNameTooLong(endpoints)) {
            conditions.add(nameTooLongResponse(specName));
        }
        log.traceExit();
    }

    private static boolean hasInvalidName(List<EndpointSpec> endpoints) {
        log.traceEntry(() -> endpoints);
        return log.traceExit(endpoints.stream()
            .anyMatch(endpoint -> !VALID_NAME_PATTERN.matcher(endpoint.getName()).matches()));
    }

    private static StatusCondition invalidEndpointNameResponse(String spec) {
        log.traceEntry();
        return log.traceExit(StatusCondition.createErrorCondition(INVALID_ENDPOINT_NAME_REASON,
            String.format("%s has an endpoint with an invalid name. Acceptable names are lowercase alphanumeric with dashes (%s). Edit spec.%s.endpoints to provide a valid endpoint names.", spec, VALID_NAME_REGEX, spec)));
    }

    private static boolean hasNameTooLong(List<EndpointSpec> endpoints) {
        log.traceEntry();
        return log.traceExit(endpoints.stream()
            .anyMatch(endpoint -> endpoint.getName().length() > ENDPOINT_NAME_MAX_LENGTH));
    }

    private static StatusCondition nameTooLongResponse(String spec) {
        log.traceEntry();
        return log.traceExit(StatusCondition.createErrorCondition(INVALID_ENDPOINT_NAME_REASON,
            String.format("%s has an endpoint with an invalid name. Names cannot be longer than %d characters. Edit spec.%s.endpoints to provide a valid endpoint name.", spec, ENDPOINT_NAME_MAX_LENGTH, spec)));
    }

    private static void checkUniqueNames(List<StatusCondition> conditions, String specName, List<EndpointSpec> endpoints) {
        log.traceEntry(() -> specName, () -> endpoints);
        if (!hasUniqueNames(endpoints)) {
            conditions.add(nonUniqueNameResponse(specName));
        }
        log.traceExit();
    }

    private static boolean hasUniqueNames(List<EndpointSpec> endpoints) {
        log.traceEntry(() -> endpoints);
        Set<String> names = new HashSet<>();
        endpoints.forEach(endpoint -> names.add(endpoint.getName()));
        return log.traceExit(names.size() == endpoints.size());
    }

    private static StatusCondition nonUniqueNameResponse(String spec) {
        log.traceEntry(() -> spec);
        return log.traceExit(StatusCondition.createErrorCondition(DUPLICATE_ENDPOINT_NAME_REASON,
            String.format("%s has multiple endpoints with the same name. Edit spec.%s.endpoints to ensure that each endpoint has a unique name.", spec, spec)));
    }

    private static void checkUniquePorts(List<StatusCondition> conditions, String specName, List<EndpointSpec> endpoints) {
        log.traceEntry(() -> conditions, () -> specName, () -> endpoints);
        if (!hasUniquePorts(endpoints)) {
            conditions.add(nonUniquePortResponse(specName));
        }
        log.traceExit();
    }
    private static boolean hasUniquePorts(List<EndpointSpec> endpoints) {
        log.traceEntry(() -> endpoints);
        Set<Integer> ports = new HashSet<>();
        endpoints.forEach(endpoint -> ports.add(endpoint.getContainerPort()));
        return log.traceExit(ports.size() == endpoints.size());
    }

    private static StatusCondition nonUniquePortResponse(String spec) {
        log.traceEntry(() -> spec);
        return log.traceExit(StatusCondition.createErrorCondition(DUPLICATE_ENDPOINT_PORTS_REASON,
            String.format("%s has multiple endpoints with the same containerPort. Edit spec.%s.endpoints to ensure that each endpoint has a unique containerPort.", spec, spec)));
    }

    private static void checkValidTypes(List<StatusCondition> conditions, String specName, List<EndpointSpec> endpoints) {
        log.traceEntry(() -> conditions, () -> specName, () -> endpoints);
        if (hasInvalidTypes(endpoints)) {
            conditions.add(invalidTypeResponse(specName));
        }
        log.traceExit();
    }

    private static boolean hasInvalidTypes(List<EndpointSpec> endpoints) {
        log.traceEntry(() -> endpoints);
        return log.traceExit(endpoints.stream()
            .filter(endpoint -> endpoint.getType() != null)
            .anyMatch(endpoint -> endpoint.getType().equals(EndpointServiceType.NODE_PORT) ||
                endpoint.getType().equals(EndpointServiceType.INGRESS) ||
                endpoint.getType().equals(EndpointServiceType.LOAD_BALANCER)));
    }

    private static StatusCondition invalidTypeResponse(String spec) {
        log.traceEntry(() -> spec);
        return log.traceExit(StatusCondition.createErrorCondition(INVALID_ENDPOINT_TYPE_REASON,
            String.format("%s has an endpoint with an invalid type. Acceptable types are 'Route' and 'Internal'. Edit spec.%s.endpoints to ensure that each endpoint has an acceptable type.", spec, spec)));
    }

    private static void checkNoIAMBearer(List<StatusCondition> conditions, List<EndpointSpec> endpoints) {
        log.traceEntry(() -> conditions, () -> endpoints);
        if (hasIAMBearerAuth(endpoints)) {
            conditions.add(invalidIAMBearerEndpointResponse());
        }
        log.traceExit();
    }

    private static void checkAdminApiHasIAMBearer(List<StatusCondition> conditions, List<EndpointSpec> endpoints) {
        log.traceEntry(() -> endpoints);
        if (!hasIAMBearerAuth(endpoints)) {
            conditions.add(StatusCondition.createWarningCondition(ADMIN_API_MISSING_IAM_BEARER_REASON, ADMIN_API_MISSING_IAM_BEARER_MESSAGE));
        }
        log.traceExit();
    }

    private static boolean hasIAMBearerAuth(List<EndpointSpec> endpoints) {
        log.traceEntry(() -> endpoints);
        return log.traceExit(endpoints.stream()
            .anyMatch(endpoint -> Optional.ofNullable(endpoint.getAuthenticationMechanisms()).orElse(Collections.emptyList()).contains(Endpoint.IAM_BEARER_KEY)));
    }

    private static StatusCondition invalidIAMBearerEndpointResponse() {
        log.traceEntry();
        return log.traceExit(StatusCondition.createErrorCondition(UNSUPPORTED_ENDPOINT_AUTHENTICATION_MECHANISM_REASON,
            String.format("restProducer has an endpoint using authentication mechanism '%s' which is not supported. Edit the authenticationMechanisms property in spec.restProducer.endpoints to set '%s', '%s', or both.", Endpoint.IAM_BEARER_KEY, Endpoint.SCRAM_SHA_512_KEY, Endpoint.MAC_KEY)));
    }
}
