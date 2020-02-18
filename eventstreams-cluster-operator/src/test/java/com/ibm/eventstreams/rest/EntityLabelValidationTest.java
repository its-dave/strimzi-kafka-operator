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

import com.ibm.eventstreams.controller.EventStreamsVerticle;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.strimzi.api.kafka.model.KafkaTopic;
import io.strimzi.api.kafka.model.KafkaTopicBuilder;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserBuilder;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;


@ExtendWith(VertxExtension.class)
public class EntityLabelValidationTest extends RestApiTest {

    private static final Logger log = LogManager.getLogger(EntityLabelValidationTest.class.getName());



    @Test
    public void testValidUser(VertxTestContext context) {
        KafkaUser test = createKafkaUser();
        test.getMetadata().getLabels().put("eventstreams.ibm.com/cluster", "mycluster");

        testValidEntity(context, test, "/admissionwebhook/rejectmissinguserlabels");
    }
    @Test
    public void testInvalidUser(VertxTestContext context) {
        testInvalidEntity(context, createKafkaUser(), "/admissionwebhook/rejectmissinguserlabels");
    }

    @Test
    public void testValidTopic(VertxTestContext context) {
        KafkaTopic test = createKafkaTopic();
        test.getMetadata().getLabels().put("eventstreams.ibm.com/cluster", "mycluster");

        testValidEntity(context, test, "/admissionwebhook/rejectmissingtopiclabels");
    }
    @Test
    public void testInvalidTopic(VertxTestContext context) {
        testInvalidEntity(context, createKafkaTopic(), "/admissionwebhook/rejectmissingtopiclabels");
    }





    private void testInvalidEntity(VertxTestContext context, CustomResource entity, String url) {
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", entity);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);

        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", url).sendJson(payload, resp -> {
            if (resp.succeeded()) {
                JsonObject responseObj = resp.result().bodyAsJsonObject();
                assertThat(responseObj.getJsonObject("response").getBoolean("allowed"), is(false));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("status"), is("Failure"));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("reason"), is("Missing cluster label"));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getInteger("code"), is(400));
                assertThat(responseObj.getJsonObject("response").getJsonObject("status").getString("message"),
                           is("eventstreams.ibm.com/cluster is a required label to identify the cluster"));
            } else {
                fail("Failed to post webhook request");
            }
            context.completeNow();
        });
    }

    private void testValidEntity(VertxTestContext context, CustomResource entity, String url) {
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("object", entity);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("request", request);

        WebClient.wrap(httpClient).post(EventStreamsVerticle.API_SERVER_PORT, "localhost", url).sendJson(payload, resp -> {
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



    private KafkaUser createKafkaUser() {
        return new KafkaUserBuilder()
                .withMetadata(
                        new ObjectMetaBuilder()
                                .withNamespace("namespace")
                                .withName("user")
                                .withLabels(Collections.emptyMap())
                                .build()
                )
                .withNewSpec().endSpec()
                .build();
    }
    private KafkaTopic createKafkaTopic() {
        return new KafkaTopicBuilder()
                .withMetadata(
                        new ObjectMetaBuilder()
                                .withNamespace("namespace")
                                .withName("user")
                                .withLabels(Collections.emptyMap())
                                .build()
                )
                .withNewSpec().endSpec()
                .build();
    }
}