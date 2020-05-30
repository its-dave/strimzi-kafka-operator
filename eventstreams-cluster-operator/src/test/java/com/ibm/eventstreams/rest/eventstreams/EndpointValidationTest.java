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

import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EndpointSpec;
import com.ibm.eventstreams.api.spec.EndpointSpecBuilder;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.controller.EventStreamsVerticle;
import com.ibm.eventstreams.controller.models.StatusCondition;
import com.ibm.eventstreams.rest.RestApiTest;
import io.strimzi.api.kafka.model.KafkaClusterSpecBuilder;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

@ExtendWith(VertxExtension.class)
public class EndpointValidationTest extends RestApiTest {

    @Test
    public void testAdminApiEndpointsNotValidOnReservedPort(VertxTestContext context) {

        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(7080)
            .build();
        
        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
                .withNewAdminApi()
                    .withEndpoints(endpoint)
                .endAdminApi()
            .endSpec()
            .build();
        
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {

            JsonObject responseObj = resp.bodyAsJsonObject();
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.INVALID_PORT_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                        is("adminApi has an endpoint that requested access on a reserved port between 7000 and 7999, inclusive. Edit spec.adminApi.endpoints to choose a port number outside of that range."));
            async.flag();
        })));
    }

    @Test
    public void testAdminApiEndpointsNotValidWithDuplicateNames(VertxTestContext context) {

        EndpointSpec endpoint1 = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(8888)
            .build();
        EndpointSpec endpoint2 = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(9999)
            .build();
        
        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
                .withNewAdminApi()
                    .withEndpoints(endpoint1, endpoint2)
                .endAdminApi()
            .endSpec()
            .build();
        
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {

            JsonObject responseObj = resp.bodyAsJsonObject();
            
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.DUPLICATE_ENDPOINT_NAME_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                        is("adminApi has multiple endpoints with the same name. Edit spec.adminApi.endpoints to ensure that each endpoint has a unique name."));
            async.flag();
        })));
    }

    @Test
    public void testAdminApiEndpointsNotValidWithDuplicatePorts(VertxTestContext context) {

        EndpointSpec endpoint1 = new EndpointSpecBuilder()
            .withName("test-endpoint1")
            .withContainerPort(8888)
            .build();
        EndpointSpec endpoint2 = new EndpointSpecBuilder()
            .withName("test-endpoint2")
            .withContainerPort(8888)
            .build();
        
        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
                .withNewAdminApi()
                    .withEndpoints(endpoint1, endpoint2)
                .endAdminApi()
            .endSpec()
            .build();
        
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {

            JsonObject responseObj = resp.bodyAsJsonObject();
            
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.DUPLICATE_ENDPOINT_PORTS_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                        is("adminApi has multiple endpoints with the same containerPort. Edit spec.adminApi.endpoints to ensure that each endpoint has a unique containerPort."));
            async.flag();
        })));
    }


    @Test
    public void testAdminApiEndpointsNotValidWithInvalidTypes(VertxTestContext context) {

        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(8888)
            .withType(EndpointServiceType.NODE_PORT)
            .build();
        
        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
                .withNewAdminApi()
                    .withEndpoints(endpoint)
                .endAdminApi()
            .endSpec()
            .build();
        
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {

            JsonObject responseObj = resp.bodyAsJsonObject();
            
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.INVALID_ENDPOINT_TYPE_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                        is("adminApi has an endpoint with an invalid type. Acceptable types are 'Route' and 'Internal'. Edit spec.adminApi.endpoints to ensure that each endpoint has an acceptable type."));
            async.flag();
        })));
    }

    @Test
    public void testAdminApiEndpointsNotValidWithInvalidName(VertxTestContext context) {

        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("Bad-EndpoInt")
            .withContainerPort(8888)
            .build();

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewAdminApi()
            .withEndpoints(endpoint)
            .endAdminApi()
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {

            JsonObject responseObj = resp.bodyAsJsonObject();

            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.INVALID_ENDPOINT_NAME_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                is("adminApi has an endpoint with an invalid name. Acceptable names are lowercase alphanumeric with dashes (^[a-z][-a-z0-9]*$). Edit spec.adminApi.endpoints to provide a valid endpoint names."));
            async.flag();
        })));
    }

    @Test
    public void testAdminApiEndpointsWithValidWithName(VertxTestContext context) {
        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("good-endpoint-1")
            .withContainerPort(8888)
            .withAuthenticationMechanisms("iam-bearer")
            .build();

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewAdminApi()
            .withEndpoints(endpoint)
            .endAdminApi()
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {

            JsonObject responseObj = resp.bodyAsJsonObject();

            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(true));
            async.flag();
        })));
    }

    @Test
    public void testKafkaExternalListenerNotValidWithNodeport(VertxTestContext context) {
    
        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withKafka(new KafkaClusterSpecBuilder()
                    .withNewListeners()
                        .withNewKafkaListenerExternalNodePort()
                        .endKafkaListenerExternalNodePort()
                    .endListeners()
                    .build())
                .build())
            .endSpec()
            .build();
        
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {

            JsonObject responseObj = resp.bodyAsJsonObject();
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.INVALID_EXTERNAL_KAFKA_LISTENER_TYPE));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                        is("spec.strimziOverrides.kafka.listener.external.type is an invalid listener type. Edit spec.strimziOverrides.kafka.listener.external.type to set 'route' as the value."));
            async.flag();
        })));
    }

    @Test
    public void testAdminApiWithoutIamBearerWarns() {
        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(8888)
            .addToAuthenticationMechanisms("scram-sha-512")
            .build();

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewAdminApi()
            .withEndpoints(endpoint)
            .endAdminApi()
            .endSpec()
            .build();

        List<StatusCondition> conditions = new EndpointValidation().validateCr(test);

        assertThat(conditions, hasSize(1));

        assertThat(conditions.get(0).getReason(), is(EndpointValidation.ADMIN_API_MISSING_IAM_BEARER_REASON));
        assertThat(conditions.get(0).getMessage(), is(EndpointValidation.ADMIN_API_MISSING_IAM_BEARER_MESSAGE));
    }

    @Test
    public void testAdminApiWithoutAuthenticationMechanismsDoesNotWarn() {
        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(8888)
            .build();

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewAdminApi()
            .withEndpoints(endpoint)
            .endAdminApi()
            .endSpec()
            .build();

        List<StatusCondition> conditions = new EndpointValidation().validateCr(test);

        assertThat(conditions, hasSize(0));
    }

    @Test
    public void testAdminApiWithoutIamBearerDoesNotReject(VertxTestContext context) {
        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(8888)
            .addToAuthenticationMechanisms("SCRAM-SHA-512")
            .build();

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewAdminApi()
            .withEndpoints(endpoint)
            .endAdminApi()
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);

        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {

            JsonObject responseObj = resp.bodyAsJsonObject();

            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(true));
            async.flag();
        })));
    }

    @Test
    public void testRestProducerNotValidWithIAMBearer(VertxTestContext context) {

        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(8888)
            .addToAuthenticationMechanisms("scram-sha-512", "iam-bearer")
            .build();

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewRestProducer()
            .withEndpoints(endpoint)
            .endRestProducer()
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {

            JsonObject responseObj = resp.bodyAsJsonObject();

            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.UNSUPPORTED_ENDPOINT_AUTHENTICATION_MECHANISM_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                is("restProducer has an endpoint using authentication mechanism 'iam-bearer' which is not supported. Edit the authenticationMechanisms property in spec.restProducer.endpoints to set 'scram-sha-512', 'mac', or both."));
            async.flag();
        })));
    }

    @Test
    public void testAdminApiValidWithIAMBearer(VertxTestContext context) {
        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(8888)
            .addToAuthenticationMechanisms("scram-sha-512", "iam-bearer")
            .build();

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewAdminApi()
            .withEndpoints(endpoint)
            .endAdminApi()
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {

            JsonObject responseObj = resp.bodyAsJsonObject();

            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(true));
            async.flag();
        })));
    }

    @Test
    public void testValidHost(VertxTestContext context) {
        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(8888)
            .withHost("this-will-work.com")
            .addToAuthenticationMechanisms("scram-sha-512", "iam-bearer")
            .build();

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewAdminApi()
            .withEndpoints(endpoint)
            .endAdminApi()
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {
            JsonObject responseObj = resp.bodyAsJsonObject();

            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(true));
            async.flag();
        })));
    }

    @Test
    public void testAdminApiWithLongHostName(VertxTestContext context) {
        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(8888)
            .withHost("a-very-long-name-ibm-es-ui-this-is-an-absurdly-long-namespace-cos-i-like-to-break-things.apps.frodo.os.fyre.ibm.com")
            .addToAuthenticationMechanisms("scram-sha-512", "iam-bearer")
            .build();

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewAdminApi()
            .withEndpoints(endpoint)
            .endAdminApi()
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {
            JsonObject responseObj = resp.bodyAsJsonObject();
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.INVALID_HOSTNAME_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                is(String.format("adminApi host '%s' is an invalid hostname. A valid hostname cannot be longer than 64 characters. Edit spec.adminApi.endpoints to provide a valid hostname.", endpoint.getHost())));
            async.flag();
        })));
    }

    @Test
    public void testAdminApiWithInvalidHost(VertxTestContext context) {
        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(8888)
            .withHost("THIS-WON'T-Work")
            .addToAuthenticationMechanisms("scram-sha-512", "iam-bearer")
            .build();

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewAdminApi()
                .withEndpoints(endpoint)
            .endAdminApi()
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {
            JsonObject responseObj = resp.bodyAsJsonObject();
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.INVALID_HOSTNAME_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                is(String.format("adminApi host '%s' is an invalid hostname. A valid hostname contains lowercase alphanumeric characters and full stops (^[a-z][-a-z0-9]*$). Edit spec.adminApi.endpoints to provide a valid hostname.", endpoint.getHost())));
            async.flag();
        })));
    }

    @Test
    public void testRestProducerWithInvalidHost(VertxTestContext context) {
        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(8888)
            .withHost("THIS-WON'T-Work")
            .addToAuthenticationMechanisms("SCRAM-SHA-512")
            .build();

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewRestProducer()
            .withEndpoints(endpoint)
            .endRestProducer()
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {
            JsonObject responseObj = resp.bodyAsJsonObject();
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.INVALID_HOSTNAME_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                is(String.format("restProducer host 'THIS-WON'T-Work' is an invalid hostname. A valid hostname contains lowercase alphanumeric characters and full stops (^[a-z][-a-z0-9]*$). Edit spec.restProducer.endpoints to provide a valid hostname.", endpoint.getHost())));
            async.flag();
        })));
    }

    @Test
    public void testSchemaRegistryWithInvalidHost(VertxTestContext context) {
        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withContainerPort(8888)
            .withHost("THIS-WON'T-Work")
            .addToAuthenticationMechanisms("scram-sha-512", "iam-bearer")
            .build();

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewSchemaRegistry()
            .withEndpoints(endpoint)
            .endSchemaRegistry()
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {
            JsonObject responseObj = resp.bodyAsJsonObject();
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.INVALID_HOSTNAME_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                is(String.format("schemaRegistry host '%s' is an invalid hostname. A valid hostname contains lowercase alphanumeric characters and full stops (^[a-z][-a-z0-9]*$). Edit spec.schemaRegistry.endpoints to provide a valid hostname.", endpoint.getHost())));
            async.flag();
        })));
    }

    @Test
    public void testAdminUiWithInvalidHost(VertxTestContext context) {
        String host = "This-Won'tWor.com";

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewAdminUI()
            .withNewHost(host)
            .endAdminUI()
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {
            JsonObject responseObj = resp.bodyAsJsonObject();
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.INVALID_HOSTNAME_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                is(String.format("adminUi host '%s' is an invalid hostname. A valid hostname contains lowercase alphanumeric characters and full stops (^[a-z][-a-z0-9]*$). Edit spec.adminUi.endpoints to provide a valid hostname.", host)));
            async.flag();
        })));
    }

    @Test
    public void testNonUniqueHostsInTheSameEndpointSpec(VertxTestContext context) {
        EndpointSpec first = new EndpointSpecBuilder()
            .withName("first-endpoint")
            .withContainerPort(8888)
            .withHost("this-will-work.com")
            .addToAuthenticationMechanisms("scram-sha-512", "iam-bearer")
            .build();

        EndpointSpec second = new EndpointSpecBuilder()
            .withName("second-endpoint2")
            .withContainerPort(9999)
            .withHost("this-will-work.com")
            .addToAuthenticationMechanisms("scram-sha-512", "iam-bearer")
            .build();

        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewAdminApi()
                .withEndpoints(first, second)
            .endAdminApi()
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {
            JsonObject responseObj = resp.bodyAsJsonObject();
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.DUPLICATE_HOST_NAMES_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                is(String.format("There are two or more hosts that have the same value. Each host must have a unique value. To provide unique hostnames, edit spec.%s.endpoints, spec.%s.endpoints, spec.%s.endpoints, and spec.%s.host.",
                    EndpointValidation.ADMIN_API_SPEC_NAME, EndpointValidation.REST_PRODUCER_SPEC_NAME, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME, EndpointValidation.ADMIN_UI_SPEC_NAME)));
            async.flag();
        })));
    }

    @Test
    public void testNonUniqueHostsInDifferentSpecs(VertxTestContext context) {
        String host = "this-will-work.com";
        EndpointSpec first = new EndpointSpecBuilder()
            .withName("first-endpoint")
            .withContainerPort(8888)
            .withHost("this-will-work.com")
            .addToAuthenticationMechanisms("scram-sha-512", "iam-bearer")
            .build();


        EventStreams test = ModelUtils.createDefaultEventStreams("test-es")
            .editOrNewSpec()
            .withNewAdminApi()
            .withEndpoints(first)
            .endAdminApi()
            .withNewAdminUI()
                .withNewHost(host)
            .endAdminUI()
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);


        Checkpoint async = context.checkpoint();
        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidendpoints").sendJson(payload, context.succeeding(resp -> context.verify(() -> {
            JsonObject responseObj = resp.bodyAsJsonObject();
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.DUPLICATE_HOST_NAMES_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                is(String.format("There are two or more hosts that have the same value. Each host must have a unique value. To provide unique hostnames, edit spec.%s.endpoints, spec.%s.endpoints, spec.%s.endpoints, and spec.%s.host.",
                    EndpointValidation.ADMIN_API_SPEC_NAME, EndpointValidation.REST_PRODUCER_SPEC_NAME, EndpointValidation.SCHEMA_REGISTRY_SPEC_NAME, EndpointValidation.ADMIN_UI_SPEC_NAME)));
            async.flag();
        })));
    }
}