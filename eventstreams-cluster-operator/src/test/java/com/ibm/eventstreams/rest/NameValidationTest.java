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
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.controller.EventStreamsVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;


@ExtendWith(VertxExtension.class)
public class NameValidationTest extends RestApiTest {

    private static final Logger log = LogManager.getLogger(NameValidationTest.class.getName());

    @Test
    public void testInvalidName(VertxTestContext context) {
        EventStreams test = ModelUtils.createDefaultEventStreams("this-is-a-very-long-name-that-exceeds-limits").build();
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);

        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectlongnames").sendJson(payload, resp -> {
            if (resp.succeeded()) {
                JsonObject responseObj = resp.result().bodyAsJsonObject();
                assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is("Name too long"));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                           is("Names should not be longer than 16 characters"));
            } else {
                fail("Failed to post webhook request");
            }
            context.completeNow();
        });
    }


    @Test
    public void testValidName(VertxTestContext context) {
        EventStreams test = ModelUtils.createDefaultEventStreams("short-name").build();
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);

        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectlongnames").sendJson(payload, resp -> {
            if (resp.succeeded()) {
                JsonObject responseObj = resp.result().bodyAsJsonObject();
                assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(true));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Success"));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is("ok"));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(200));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"), is("ok"));
            } else {
                fail("Failed to post webhook request");
            }
            context.completeNow();
        });
    }
}