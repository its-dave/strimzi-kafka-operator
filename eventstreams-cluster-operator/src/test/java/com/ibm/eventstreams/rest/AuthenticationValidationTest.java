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
import com.ibm.eventstreams.controller.models.StatusCondition;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class AuthenticationValidationTest {
    private final String instanceName = "test-instance";

    private final EndpointSpec authenticatedEndpoint = new EndpointSpecBuilder()
        .withNewName("authenticated")
        .withContainerPort(9999)
        .withAuthenticationMechanisms(Collections.singletonList("TEST_AUTH"))
        .build();

    private final EndpointSpec unauthenticatedEndpoint = new EndpointSpecBuilder()
        .withNewName("unauthenticated")
        .withContainerPort(9999)
        .withAuthenticationMechanisms(Collections.emptyList())
        .build();

    @Test
    public void TestUnAuthEventStreamsConfigurationWithOneAuthenticatedAdminApiEndpointShouldWarn() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName)
            .editSpec()
            .withNewAdminApi()
            .withEndpoints(authenticatedEndpoint)
            .endAdminApi()
            .withNewRestProducer()
            .withEndpoints(unauthenticatedEndpoint)
            .endRestProducer()
            .withNewSchemaRegistry()
            .withEndpoints(unauthenticatedEndpoint)
            .endSchemaRegistry()
            .endSpec()
            .build();

        List<StatusCondition> responses = new AuthenticationValidation().validateCr(instance);
        assertThat(responses, hasSize(1));
        assertThat(responses.get(0).getReason(), is(AuthenticationValidation.ENDPOINT_AUTHENTICATED_WHEN_KAFKA_UNAUTHENTICATED_REASON));
        assertThat(responses.get(0).getMessage(), is(String.format(AuthenticationValidation.AUTH_ENDPOINT_UNAUTH_ES_WARNING, EndpointValidation.ADMIN_API_SPEC_NAME, EndpointValidation.ADMIN_API_SPEC_NAME)));
    }

    @Test
    public void TestUnAuthEventStreamsConfigurationWithOneAuthenticatedRestProducerEndpointShouldWarn() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName)
            .editSpec()
            .withNewAdminApi()
            .withEndpoints(unauthenticatedEndpoint)
            .endAdminApi()
            .withNewRestProducer()
            .withEndpoints(authenticatedEndpoint)
            .endRestProducer()
            .withNewSchemaRegistry()
            .withEndpoints(unauthenticatedEndpoint)
            .endSchemaRegistry()
            .endSpec()
            .build();

        List<StatusCondition> responses = new AuthenticationValidation().validateCr(instance);
        assertThat(responses, hasSize(1));
        assertThat(responses.get(0).getReason(), is(AuthenticationValidation.ENDPOINT_AUTHENTICATED_WHEN_KAFKA_UNAUTHENTICATED_REASON));
        assertThat(responses.get(0).getMessage(), is(String.format(AuthenticationValidation.AUTH_ENDPOINT_UNAUTH_ES_WARNING, EndpointValidation.REST_PRODUCER_SPEC_NAME, EndpointValidation.REST_PRODUCER_SPEC_NAME)));
    }

    @Test
    public void TestUnAuthEventStreamsConfigurationWithOneAuthenticatedSchemaRegistryEndpointShouldWarn() {
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

        List<StatusCondition> responses = new AuthenticationValidation().validateCr(instance);
        assertThat(responses, hasSize(1));
        assertThat(responses.get(0).getReason(), is(AuthenticationValidation.ENDPOINT_AUTHENTICATED_WHEN_KAFKA_UNAUTHENTICATED_REASON));
        assertThat(responses.get(0).getMessage(), is(String.format(AuthenticationValidation.AUTH_ENDPOINT_UNAUTH_ES_WARNING, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME)));
    }

    @Test
    public void TestAuthEventStreamsConfigurationWithOneUnauthenticatedAdminApiEndpointShouldGetWarningReason() {
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

        List<StatusCondition> responses = new AuthenticationValidation().validateCr(instance);
        assertThat(responses, hasSize(1));
        assertThat(responses.get(0).getReason(), is(AuthenticationValidation.ENDPOINT_UNAUTHENTICATED_WHEN_KAFKA_AUTHENTICATED_REASON));
        assertThat(responses.get(0).getMessage(), is(String.format(AuthenticationValidation.UNAUTH_ENDPOINT_AUTH_ES_WARNING, EndpointValidation.ADMIN_API_SPEC_NAME, EndpointValidation.ADMIN_API_SPEC_NAME)));
    }

    @Test
    public void TestAuthEventStreamsConfigurationWithOneUnauthenticatedRestProducerEndpointShouldGetWarningReason() {
        EventStreams instance = ModelUtils.createEventStreamsWithAuthentication(instanceName)
            .editSpec()
            .withNewAdminApi()
            .withEndpoints(authenticatedEndpoint)
            .endAdminApi()
            .withNewRestProducer()
            .withEndpoints(unauthenticatedEndpoint)
            .endRestProducer()
            .withNewSchemaRegistry()
            .withEndpoints(authenticatedEndpoint)
            .endSchemaRegistry()
            .endSpec()
            .build();

        List<StatusCondition> responses = new AuthenticationValidation().validateCr(instance);
        assertThat(responses, hasSize(1));
        assertThat(responses.get(0).getReason(), is(AuthenticationValidation.ENDPOINT_UNAUTHENTICATED_WHEN_KAFKA_AUTHENTICATED_REASON));
        assertThat(responses.get(0).getMessage(), is(String.format(AuthenticationValidation.UNAUTH_ENDPOINT_AUTH_ES_WARNING, EndpointValidation.REST_PRODUCER_SPEC_NAME, EndpointValidation.REST_PRODUCER_SPEC_NAME)));
    }

    @Test
    public void TestAuthEventStreamsConfigurationWithOneUnauthenticatedSchemaRegistryEndpointShouldGetWarningReason() {
        EventStreams instance = ModelUtils.createEventStreamsWithAuthentication(instanceName)
            .editSpec()
            .withNewAdminApi()
            .withEndpoints(authenticatedEndpoint)
            .endAdminApi()
            .withNewRestProducer()
            .withEndpoints(authenticatedEndpoint)
            .endRestProducer()
            .withNewSchemaRegistry()
            .withEndpoints(unauthenticatedEndpoint)
            .endSchemaRegistry()
            .endSpec()
            .build();
        List<StatusCondition> responses = new AuthenticationValidation().validateCr(instance);
        assertThat(responses, hasSize(1));
        assertThat(responses.get(0).getReason(), is(AuthenticationValidation.ENDPOINT_UNAUTHENTICATED_WHEN_KAFKA_AUTHENTICATED_REASON));
        assertThat(responses.get(0).getMessage(), is(String.format(AuthenticationValidation.UNAUTH_ENDPOINT_AUTH_ES_WARNING, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME)));
    }

    @Test
    public void TestUnAuthEventStreamsConfigurationWithMultipleAuthEndpointsHasMultipleWarningReasons() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName)
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
        List<StatusCondition> responses = new AuthenticationValidation().validateCr(instance);
        assertThat(responses, hasSize(3));
        assertThat(responses.get(0).getReason(), is(AuthenticationValidation.ENDPOINT_AUTHENTICATED_WHEN_KAFKA_UNAUTHENTICATED_REASON));
        assertThat(responses.get(0).getMessage(), is(String.format(AuthenticationValidation.AUTH_ENDPOINT_UNAUTH_ES_WARNING, EndpointValidation.ADMIN_API_SPEC_NAME, EndpointValidation.ADMIN_API_SPEC_NAME)));
        assertThat(responses.get(1).getReason(), is(AuthenticationValidation.ENDPOINT_AUTHENTICATED_WHEN_KAFKA_UNAUTHENTICATED_REASON));
        assertThat(responses.get(1).getMessage(), is(String.format(AuthenticationValidation.AUTH_ENDPOINT_UNAUTH_ES_WARNING, EndpointValidation.REST_PRODUCER_SPEC_NAME, EndpointValidation.REST_PRODUCER_SPEC_NAME)));
        assertThat(responses.get(2).getReason(), is(AuthenticationValidation.ENDPOINT_AUTHENTICATED_WHEN_KAFKA_UNAUTHENTICATED_REASON));
        assertThat(responses.get(2).getMessage(), is(String.format(AuthenticationValidation.AUTH_ENDPOINT_UNAUTH_ES_WARNING, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME)));
    }

    @Test
    public void TestAuthEventStreamsConfigurationWithMultipleUnAuthEndpointsHasMultipleWarningReasons() {
        EventStreams instance = ModelUtils.createEventStreamsWithAuthentication(instanceName)
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

        List<StatusCondition> responses = new AuthenticationValidation().validateCr(instance);
        assertThat(responses, hasSize(3));
        assertThat(responses.get(0).getReason(), is(AuthenticationValidation.ENDPOINT_UNAUTHENTICATED_WHEN_KAFKA_AUTHENTICATED_REASON));
        assertThat(responses.get(0).getMessage(), is(String.format(AuthenticationValidation.UNAUTH_ENDPOINT_AUTH_ES_WARNING, EndpointValidation.ADMIN_API_SPEC_NAME, EndpointValidation.ADMIN_API_SPEC_NAME)));
        assertThat(responses.get(1).getReason(), is(AuthenticationValidation.ENDPOINT_UNAUTHENTICATED_WHEN_KAFKA_AUTHENTICATED_REASON));
        assertThat(responses.get(1).getMessage(), is(String.format(AuthenticationValidation.UNAUTH_ENDPOINT_AUTH_ES_WARNING, EndpointValidation.REST_PRODUCER_SPEC_NAME, EndpointValidation.REST_PRODUCER_SPEC_NAME)));
        assertThat(responses.get(2).getReason(), is(AuthenticationValidation.ENDPOINT_UNAUTHENTICATED_WHEN_KAFKA_AUTHENTICATED_REASON));
        assertThat(responses.get(2).getMessage(), is(String.format(AuthenticationValidation.UNAUTH_ENDPOINT_AUTH_ES_WARNING, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME)));
    }
}
