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

import com.ibm.eventstreams.api.TlsVersion;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.controller.EventStreamsVerticle;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.Checkpoint;
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

@ExtendWith(VertxExtension.class)
public class PlainListenerValidationTest extends RestApiTest {
    private static final Logger log = LogManager.getLogger(NameValidationTest.class.getName());

    private String instanceName = "test-instance";

    @Test
    public void testValidConfiguration(VertxTestContext context) {
        EventStreams test = new EventStreamsBuilder()
            .withMetadata(new ObjectMetaBuilder().withName(instanceName).build())
            .withNewSpec()
                .withNewSecurity()
                    .withInternalTls(TlsVersion.NONE)
                .endSecurity()
                .withStrimziOverrides(new KafkaSpecBuilder()
                    .withNewKafka()
                        .withNewListeners()
                            .withNewPlain()
                            .endPlain()
                        .endListeners()
                    .endKafka()
                .build())
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);
        Checkpoint async = context.checkpoint();

        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidlisteners").sendJson(payload, context.succeeding(resp -> context.verify(() -> {
            JsonObject responseObj = resp.bodyAsJsonObject();
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(true));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Success"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is("ok"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(200));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"), is("ok"));

            async.flag();
        })));
    }

    @Test
    public void testWhenTlsEnabledWillPass(VertxTestContext context) {
        EventStreams test = new EventStreamsBuilder()
            .withMetadata(new ObjectMetaBuilder().withName(instanceName).build())
            .withNewSpec()
                .withNewSecurity()
                    .withInternalTls(TlsVersion.TLS_V1_2)
                .endSecurity()
                .withStrimziOverrides(new KafkaSpecBuilder()
                    .withNewKafka()
                    .endKafka()
                    .build())
                .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);
        Checkpoint async = context.checkpoint();

        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidlisteners").sendJson(payload, context.succeeding(resp -> context.verify(() -> {
            JsonObject responseObj = resp.bodyAsJsonObject();
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(true));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Success"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is("ok"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(200));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"), is("ok"));

            async.flag();
        })));
    }

    @Test
    public void testMissingKafkaPlainListener(VertxTestContext context) {
        EventStreams test = new EventStreamsBuilder()
            .withMetadata(new ObjectMetaBuilder().withName(instanceName).build())
            .withNewSpec()
                .withNewSecurity()
                    .withInternalTls(TlsVersion.NONE)
                .endSecurity()
                .withStrimziOverrides(new KafkaSpecBuilder()
                    .withNewKafka()
                        .withNewListeners()
                        .endListeners()
                    .endKafka()
                .build())
            .endSpec()
            .build();

        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", test);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);
        Checkpoint async = context.checkpoint();

        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/admissionwebhook/rejectinvalidlisteners").sendJson(payload, context.succeeding(resp -> context.verify(() -> {
            JsonObject responseObj = resp.bodyAsJsonObject();
            assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is(PlainListenerValidation.FAILURE_MISSING_PLAIN_LISTENER_REASON));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
            assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"), is(PlainListenerValidation.FAILURE_MESSAGE));

            async.flag();
        })));
    }
}
