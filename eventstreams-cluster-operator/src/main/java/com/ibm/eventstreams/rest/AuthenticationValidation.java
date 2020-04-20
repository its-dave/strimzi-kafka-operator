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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AuthenticationValidation extends AbstractValidation {
    private static final Logger log = LogManager.getLogger(AuthenticationValidation.class.getName());

    public static final String UNAUTH_ENDPOINT_AUTH_ES_WARNING = "Event Streams components are authenticated but there is an Endpoint that is unauthenticated. "
                                                                + "Clients connecting to unauthenticated endpoint will not be authorized to do anything!";
    public static final String AUTH_ENDPOINT_UNAUTH_ES_WARNING = "Event Streams components are unauthenticated but there is an Endpoint that is authenticated. "
                                                                + "Clients connecting in on the authenticated endpoint will need to be unnecessarily authenticated";

    public static boolean shouldWarn(EventStreams instance) {
        log.traceEntry(() -> instance);
        return log.traceExit(!getWarningReason(instance).isEmpty());
    }

    public static String getWarningReason(EventStreams instance) {
        log.traceEntry(() -> instance);
        Map<ListenerType, ListenerAuthentication> listenerAuth = getListenerAuth(instance);

        if (isAuthenticated(listenerAuth)) {
            return (isEndpointConfigured(instance, false)) ? UNAUTH_ENDPOINT_AUTH_ES_WARNING : "";
        }

        return log.traceExit(isEndpointConfigured(instance, true) ? AUTH_ENDPOINT_UNAUTH_ES_WARNING : "");
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

    private static boolean isEndpointConfigured(EventStreams instance, boolean authenticatedEndpoint) {
        log.traceEntry(() -> instance);
        boolean adminApiAuthenticationEndpointExist = Optional.ofNullable(instance.getSpec())
            .map(EventStreamsSpec::getAdminApi)
            .map(SecurityComponentSpec::getEndpoints)
            .map(endpoints -> hasAuthenticatedEndpoint(endpoints, authenticatedEndpoint))
            .orElse(false);

        boolean restProducerAuthenticationEndpointExist = Optional.ofNullable(instance.getSpec())
            .map(EventStreamsSpec::getRestProducer)
            .map(SecurityComponentSpec::getEndpoints)
            .map(endpoints -> hasAuthenticatedEndpoint(endpoints, authenticatedEndpoint))
            .orElse(false);

        boolean schemaRegistryEndpointEndpointExist = Optional.ofNullable(instance.getSpec())
            .map(EventStreamsSpec::getSchemaRegistry)
            .map(SecurityComponentSpec::getEndpoints)
            .map(endpoints -> hasAuthenticatedEndpoint(endpoints, authenticatedEndpoint))
            .orElse(false);

        return log.traceExit(adminApiAuthenticationEndpointExist || restProducerAuthenticationEndpointExist || schemaRegistryEndpointEndpointExist);
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
