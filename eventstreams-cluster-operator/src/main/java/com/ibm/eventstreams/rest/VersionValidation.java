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

import java.util.List;
import static java.util.Collections.unmodifiableList;
import static java.util.Arrays.asList;


public class VersionValidation extends AbstractValidation {

    private static final Logger log = LogManager.getLogger(NameValidation.class.getName());

    public static final List<String> VALID_APP_VERSIONS = unmodifiableList(asList("2020.1.1", "2020.1"));

    public static boolean shouldReject(EventStreams customResourceSpec) {
        return !(VALID_APP_VERSIONS.contains(customResourceSpec.getSpec().getAppVersion()));
    }


    public static void rejectInvalidAppVersions(RoutingContext routingContext) {
        log.traceEntry();

        EventStreams customResourceSpec = getSpecFromRequest(routingContext);

        ValidationResponsePayload outcome = null;

        if (shouldReject(customResourceSpec)) {
            outcome = ValidationResponsePayload.createFailureResponse(
                    "Supported appVersion values are: 2020.1, 2020.1.1",
                    "Unsupported appVersion");
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