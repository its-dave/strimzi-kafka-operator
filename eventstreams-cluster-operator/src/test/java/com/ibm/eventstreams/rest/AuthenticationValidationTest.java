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

import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EndpointSpec;
import com.ibm.eventstreams.api.spec.EndpointSpecBuilder;
import com.ibm.eventstreams.api.spec.EventStreams;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AuthenticationValidationTest {
    private final String instanceName = "test-instance";

    private final EndpointSpec authenticatedEndpoint = new EndpointSpecBuilder()
        .withNewName("authenticated")
        .withAccessPort(9999)
        .withAuthenticationMechanisms(Collections.singletonList("TEST_AUTH"))
        .build();

    private final EndpointSpec unauthenticatedEndpoint = new EndpointSpecBuilder()
        .withNewName("unauthenticated")
        .withAccessPort(9999)
        .withAuthenticationMechanisms(Collections.emptyList())
        .build();

    @Test
    public void DefaultEventStreamsConfigurationShouldNotCreateWarning() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName)
            .editSpec()
                .withNewAdminApi()
                    .withEndpoints(unauthenticatedEndpoint)
                .endAdminApi()
                .withNewRestProducer()
                    .withEndpoints(unauthenticatedEndpoint)
                .endRestProducer()
                .withNewSchemaRegistry()
                    .withEndpoints(unauthenticatedEndpoint)
                .endSchemaRegistry()
            .endSpec()
            .build();

        assertThat(AuthenticationValidation.shouldWarn(instance), is(false));
    }

    @Test
    public void AuthenticatedEventStreamsConfigurationWithAuthEndpointsShouldNotCreateWarning() {
        EventStreams instance = ModelUtils.createEventStreamsWithAuthentication(instanceName)
            .editSpec()
                .withNewAdminApi()
                    .withEndpoints(authenticatedEndpoint)
                .endAdminApi()
                .withNewRestProducer()
                    .withEndpoints(authenticatedEndpoint)
                .endRestProducer()
                .withNewSchemaRegistry()
                    .withEndpoints(authenticatedEndpoint)
                .endSchemaRegistry()
            .endSpec()
            .build();

        assertThat(AuthenticationValidation.shouldWarn(instance), is(false));
    }

    @Test
    public void EventStreamsConfigurationWithOneUnauthenticatedEndpointShouldWarn() {
        EventStreams instance = ModelUtils.createEventStreamsWithAuthentication(instanceName)
            .editSpec()
                .withNewAdminApi()
                    .withEndpoints(unauthenticatedEndpoint)
                .endAdminApi()
                .withNewRestProducer()
                    .withEndpoints(authenticatedEndpoint)
                .endRestProducer()
                .withNewSchemaRegistry()
                    .withEndpoints(authenticatedEndpoint)
                .endSchemaRegistry()
            .endSpec()
            .build();

        assertThat(AuthenticationValidation.shouldWarn(instance), is(true));
    }

    @Test
    public void EventStreamsConfigurationWithOneUnauthenticatedEndpointShouldGetWarningReason() {
        EventStreams instance = ModelUtils.createEventStreamsWithAuthentication(instanceName)
            .editSpec()
            .withNewAdminApi()
            .withEndpoints(unauthenticatedEndpoint)
            .endAdminApi()
            .withNewRestProducer()
            .withEndpoints(authenticatedEndpoint)
            .endRestProducer()
            .withNewSchemaRegistry()
            .withEndpoints(authenticatedEndpoint)
            .endSchemaRegistry()
            .endSpec()
            .build();

        assertThat(AuthenticationValidation.getWarningReason(instance), is(AuthenticationValidation.UNAUTH_ENDPOINT_AUTH_ES_WARNING));
    }

    @Test
    public void TestUnAuthEventStreamsConfigurationWithOneAuthenticatedEndpointShouldWarn() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName)
            .editSpec()
                .withNewAdminApi()
                    .withEndpoints(unauthenticatedEndpoint)
                .endAdminApi()
                .withNewRestProducer()
                    .withEndpoints(unauthenticatedEndpoint)
                .endRestProducer()
                .withNewSchemaRegistry()
                    .withEndpoints(authenticatedEndpoint)
                .endSchemaRegistry()
            .endSpec()
            .build();

        assertThat(AuthenticationValidation.shouldWarn(instance), is(true));
    }

    @Test
    public void TestUnAuthEventStreamsConfigurationWithOneAuthenticatedEndpointShouldGetWarningReason() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName)
            .editSpec()
            .withNewAdminApi()
            .withEndpoints(unauthenticatedEndpoint)
            .endAdminApi()
            .withNewRestProducer()
            .withEndpoints(unauthenticatedEndpoint)
            .endRestProducer()
            .withNewSchemaRegistry()
            .withEndpoints(authenticatedEndpoint)
            .endSchemaRegistry()
            .endSpec()
            .build();

        assertThat(AuthenticationValidation.getWarningReason(instance), is(AuthenticationValidation.AUTH_ENDPOINT_UNAUTH_ES_WARNING));
    }
}
