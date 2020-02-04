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

import io.vertx.ext.web.RoutingContext;

/**
 * Implements API handler used to serve the liveness and readiness probes.
 * No checks are performed, the fact that we're able to receive and process
 * the probe API call is enough of a check that we just return an HTTP-200.
 */
public class KubernetesProbe {

    public static void handle(RoutingContext routingContext) {
        routingContext
                .response()
                .setStatusCode(200)
                .end();
    }
}
