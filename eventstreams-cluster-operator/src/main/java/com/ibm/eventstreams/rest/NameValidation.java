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

import com.ibm.eventstreams.api.spec.EventStreams;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class NameValidation extends AbstractValidation {

    private static final Logger log = LogManager.getLogger(NameValidation.class.getName());

    public static final int MAX_NAME_LENGTH = 16;

    public static boolean shouldReject(EventStreams customResourceSpec) {
        return customResourceSpec.getMetadata().getName().length() > MAX_NAME_LENGTH;
    }


    public static void rejectLongNames(RoutingContext routingContext) {
        log.traceEntry();

        EventStreams customResourceSpec = getSpecFromRequest(routingContext);

        ValidationResponsePayload outcome = null;

        if (shouldReject(customResourceSpec)) {
            outcome = ValidationResponsePayload.createFailureResponse(
                    "Names should not be longer than 16 characters",
                    "Name too long");
        } else {
            outcome = ValidationResponsePayload.createSuccessResponse();
        }

        routingContext
                .response()
                .setStatusCode(200)
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(outcome));

        log.traceExit();
    }
}
