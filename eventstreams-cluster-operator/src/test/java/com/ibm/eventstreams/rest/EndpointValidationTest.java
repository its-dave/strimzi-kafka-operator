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

import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EndpointSpec;
import com.ibm.eventstreams.api.spec.EndpointSpecBuilder;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.controller.EventStreamsVerticle;

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
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


@ExtendWith(VertxExtension.class)
public class EndpointValidationTest extends RestApiTest {

    @Test
    public void testAdminApiEndpointsNotValidOnReservedPort(VertxTestContext context) {

        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withAccessPort(7080)
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
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.FAILURE_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                        is("adminApi endpoint configuration has requested access on a reserved port 7080/7443"));
            async.flag();
        })));
    }

    @Test
    public void testAdminApiEndpointsNotValidWithDuplicateNames(VertxTestContext context) {

        EndpointSpec endpoint1 = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withAccessPort(8888)
            .build();
        EndpointSpec endpoint2 = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withAccessPort(9999)
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
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.FAILURE_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                        is("adminApi endpoint configuration has endpoints with the same name"));
            async.flag();
        })));
    }

    @Test
    public void testAdminApiEndpointsNotValidWithDuplicatePorts(VertxTestContext context) {

        EndpointSpec endpoint1 = new EndpointSpecBuilder()
            .withName("test-endpoint1")
            .withAccessPort(8888)
            .build();
        EndpointSpec endpoint2 = new EndpointSpecBuilder()
            .withName("test-endpoint2")
            .withAccessPort(8888)
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
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.FAILURE_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                        is("adminApi endpoint configuration has endpoints with the same accessPort"));
            async.flag();
        })));
    }


    @Test
    public void testAdminApiEndpointsNotValidWithInvalidTypes(VertxTestContext context) {

        EndpointSpec endpoint = new EndpointSpecBuilder()
            .withName("test-endpoint")
            .withAccessPort(8888)
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
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.FAILURE_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                        is("adminApi endpoint configuration has endpoints with invalid types. Acceptable types are 'Route' and 'Internal'"));
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
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(EndpointValidation.FAILURE_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                        is("Invalid external kafka listener type, kafka listener can only have type 'route'"));
            async.flag();
        })));
    }
}