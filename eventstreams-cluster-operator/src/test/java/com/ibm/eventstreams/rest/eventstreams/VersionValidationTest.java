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

import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.controller.EventStreamsVerticle;
import com.ibm.eventstreams.rest.RestApiTest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(VertxExtension.class)
public class VersionValidationTest extends RestApiTest {

    private void tryValidVersion(String version, VertxTestContext context) {
        EventStreams test = ModelUtils.createDefaultEventStreams("my-es").build();
        test.getSpec().setVersion(version);

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);

        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidversions").sendJson(payload, resp -> {
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

    @Test
    public void testExplicitVersion(VertxTestContext context) {
        tryValidVersion("2020.2.1", context);
    }

    @Test
    public void testUpgradableVersion(VertxTestContext context) {
        tryValidVersion("2020.2", context);
    }

    @Test
    public void testInvalidVersion(VertxTestContext context) {
        EventStreams test = ModelUtils.createDefaultEventStreams("my-es").build();
        test.getSpec().setVersion("2019.4.1");

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);

        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidversions").sendJson(payload, resp -> {
            if (resp.succeeded()) {
                JsonObject responseObj = resp.result().bodyAsJsonObject();
                assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(VersionValidation.INVALID_VERSION_REASON));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                    is(String.format(VersionValidation.INVALID_VERSION_MESSAGE, test.getSpec().getVersion())));
            } else {
                fail("Failed to post webhook request");
            }
            context.completeNow();
        });
    }
}
