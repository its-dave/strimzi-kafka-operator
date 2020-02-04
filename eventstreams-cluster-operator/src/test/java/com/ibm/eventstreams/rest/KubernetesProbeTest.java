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
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(VertxExtension.class)
public class KubernetesProbeTest extends RestApiTest {

    @Test
    public void testLivenessProbe(VertxTestContext context) {
        httpClient.getNow(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/liveness", resp -> {
            assertThat(resp.statusCode(), is(200));
            context.completeNow();
        });
    }

    @Test
    public void testReadinessProbe(VertxTestContext context) {
        httpClient.getNow(EventStreamsVerticle.API_SERVER_PORT, "localhost", "/readiness", resp -> {
            assertThat(resp.statusCode(), is(200));
            context.completeNow();
        });
    }
}