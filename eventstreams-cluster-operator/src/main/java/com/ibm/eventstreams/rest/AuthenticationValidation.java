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

import com.ibm.eventstreams.api.ListenerAuthentication;
import com.ibm.eventstreams.api.ListenerType;
import com.ibm.eventstreams.api.spec.EndpointSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.SecurityComponentSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AuthenticationValidation extends AbstractValidation {
    private static final Logger log = LogManager.getLogger(AuthenticationValidation.class.getName());

    public static final String UNAUTH_ENDPOINT_AUTH_ES_WARNING = "At least one Kafka listener has required authentication. "
        + "However, a supporting Event Streams component has an endpoint without authentication enabled. "
        + "Clients connecting through this insecure endpoint will not have permission to access %s features. "
        + "If authentication is required, edit spec.%s.endpoints in the CR YAML and add one or more of the following authenticationMechanisms: 'IAM-BEARER', 'SCRAM-SHA-512', or 'TLS'. ";

    public static final String AUTH_ENDPOINT_UNAUTH_ES_WARNING = "No authentication is enabled for Kafka listeners. "
        + "However, there is a secure endpoint configured for %s. "
        + "If Kafka authentication is not required, edit spec.%s.endpoints in the CR YAML to remove authenticationMechanisms from all endpoints. "
        + "If authentication is required, enable it for Kafka listeners by setting the authentication type in one or more of the following locations: spec.strimziOverrides.kafka.listeners.external.authentication, spec.strimziOverrides.kafka.listeners.tls.authentication, and spec.strimziOverrides.kafka.listeners.plain.authentication.";

    public static final String ENDPOINT_UNAUTHENTICATED_WHEN_KAFKA_AUTHENTICATED_REASON = "EndpointMissingAuthenticationWhenKafkaAuthenticated";
    public static final String ENDPOINT_AUTHENTICATED_WHEN_KAFKA_UNAUTHENTICATED_REASON = "EndpointAuthenticatedWhenKafkaUnauthenticated";

    public static List<ValidationResponsePayload.ValidationResponse> validateKafkaListenerAuthentication(EventStreams instance) {
        log.traceEntry(() -> instance);
        Map<ListenerType, ListenerAuthentication> listenerAuth = getListenerAuth(instance);
        Optional<List<EndpointSpec>> adminApiEndpoints =  Optional.ofNullable(instance.getSpec())
                                                            .map(EventStreamsSpec::getAdminApi)
                                                            .map(SecurityComponentSpec::getEndpoints);

        Optional<List<EndpointSpec>> restProducerEndpoints =  Optional.ofNullable(instance.getSpec())
                                                                .map(EventStreamsSpec::getRestProducer)
                                                                .map(SecurityComponentSpec::getEndpoints);

        Optional<List<EndpointSpec>> schemaRegistryEndpoints = Optional.ofNullable(instance.getSpec())
                                                                .map(EventStreamsSpec::getSchemaRegistry)
                                                                .map(SecurityComponentSpec::getEndpoints);
        List<ValidationResponsePayload.ValidationResponse> responses = new ArrayList<>();

        if (isAuthenticated(listenerAuth)) {
            if (isEndpointConfigured(adminApiEndpoints, false)) {
                responses.add(ValidationResponsePayload.createFailureResponse(String.format(UNAUTH_ENDPOINT_AUTH_ES_WARNING, EndpointValidation.ADMIN_API_SPEC_NAME, EndpointValidation.ADMIN_API_SPEC_NAME), ENDPOINT_UNAUTHENTICATED_WHEN_KAFKA_AUTHENTICATED_REASON));
            }
            if (isEndpointConfigured(restProducerEndpoints, false)) {
                responses.add(ValidationResponsePayload.createFailureResponse(String.format(UNAUTH_ENDPOINT_AUTH_ES_WARNING, EndpointValidation.REST_PRODUCER_SPEC_NAME, EndpointValidation.REST_PRODUCER_SPEC_NAME), ENDPOINT_UNAUTHENTICATED_WHEN_KAFKA_AUTHENTICATED_REASON));
            }
            if (isEndpointConfigured(schemaRegistryEndpoints, false)) {
                responses.add(ValidationResponsePayload.createFailureResponse(String.format(UNAUTH_ENDPOINT_AUTH_ES_WARNING, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME), ENDPOINT_UNAUTHENTICATED_WHEN_KAFKA_AUTHENTICATED_REASON));
            }
            return responses;
        }

        if (isEndpointConfigured(adminApiEndpoints, true)) {
            responses.add(ValidationResponsePayload.createFailureResponse(String.format(AUTH_ENDPOINT_UNAUTH_ES_WARNING, EndpointValidation.ADMIN_API_SPEC_NAME, EndpointValidation.ADMIN_API_SPEC_NAME), ENDPOINT_AUTHENTICATED_WHEN_KAFKA_UNAUTHENTICATED_REASON));
        }
        if (isEndpointConfigured(restProducerEndpoints, true)) {
            responses.add(ValidationResponsePayload.createFailureResponse(String.format(AUTH_ENDPOINT_UNAUTH_ES_WARNING, EndpointValidation.REST_PRODUCER_SPEC_NAME, EndpointValidation.REST_PRODUCER_SPEC_NAME), ENDPOINT_AUTHENTICATED_WHEN_KAFKA_UNAUTHENTICATED_REASON));
        }
        if (isEndpointConfigured(schemaRegistryEndpoints, true)) {
            responses.add(ValidationResponsePayload.createFailureResponse(String.format(AUTH_ENDPOINT_UNAUTH_ES_WARNING, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME), ENDPOINT_AUTHENTICATED_WHEN_KAFKA_UNAUTHENTICATED_REASON));
        }
        return responses;
    }

    private static Map<ListenerType, ListenerAuthentication> getListenerAuth(EventStreams instance) {
        log.traceEntry(() -> instance);
        HashMap<ListenerType, ListenerAuthentication> listenerAuth = new HashMap<>();

        listenerAuth.put(ListenerType.PLAIN, ListenerAuthentication.getAuthentication(instance, ListenerType.PLAIN));
        listenerAuth.put(ListenerType.TLS, ListenerAuthentication.getAuthentication(instance, ListenerType.TLS));
        listenerAuth.put(ListenerType.EXTERNAL, ListenerAuthentication.getAuthentication(instance, ListenerType.EXTERNAL));

        return log.traceExit(listenerAuth);
    }

    private static boolean isAuthenticated(Map<ListenerType, ListenerAuthentication> listenerAuth) {
        log.traceEntry(() -> listenerAuth);
        for (ListenerAuthentication auth : listenerAuth.values()) {
            if (auth != null && auth != ListenerAuthentication.NONE) {
                return log.traceExit(true);
            }
        }

        return log.traceExit(false);
    }

    private static boolean isEndpointConfigured(Optional<List<EndpointSpec>> endpointSpecs, boolean authenticatedEndpoint) {
        log.traceEntry(() -> endpointSpecs, () -> authenticatedEndpoint);
        return log.traceExit(endpointSpecs
                                .map(endpoints -> hasAuthenticatedEndpoint(endpoints, authenticatedEndpoint))
                                .orElse(false));
    }

    private static boolean hasAuthenticatedEndpoint(List<EndpointSpec> endpoints, boolean authenticatedEndpoint) {
        log.traceEntry(() -> endpoints);
        for (EndpointSpec endpointSpec : endpoints) {
            if (doesEndpointHaveAuth(endpointSpec, authenticatedEndpoint)) {
                return log.traceExit(true);
            }
        }
        return log.traceExit(false);
    }

    private static boolean doesEndpointHaveAuth(EndpointSpec spec, boolean authenticatedEndpoint) {
        log.traceEntry(() -> spec);
        return log.traceExit(Optional.ofNullable(spec)
            .map(endpoint -> authenticatedEndpoint == endpoint.hasAuth())
            .orElse(false));
    }
}
