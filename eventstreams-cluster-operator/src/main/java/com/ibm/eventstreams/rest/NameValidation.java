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

import java.util.regex.Pattern;


public class NameValidation extends AbstractValidation {

    private static final Logger log = LogManager.getLogger(NameValidation.class.getName());

    public static final int MAX_NAME_LENGTH = 16;

    private static final String VALID_NAME_REGEX = "[a-z]([-a-z0-9]*[a-z0-9])?";
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile(VALID_NAME_REGEX);

    public static boolean shouldReject(EventStreams customResourceSpec) {
        String name = customResourceSpec.getMetadata().getName();
        return name.length() > MAX_NAME_LENGTH || !VALID_NAME_PATTERN.matcher(name).matches();
    }


    public static void rejectInvalidNames(RoutingContext routingContext) {
        log.traceEntry();

        String name = getSpecFromRequest(routingContext).getMetadata().getName();

        ValidationResponsePayload outcome = null;

        if (name.length() > MAX_NAME_LENGTH) {
            outcome = ValidationResponsePayload.createFailureResponsePayload(
                    "Names should not be longer than 16 characters",
                    "Name too long");
        } else if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            outcome = ValidationResponsePayload.createFailureResponsePayload(
                    "Invalid metadata.name. " +
                            "Names must consist of lower case alphanumeric characters or '-', " +
                            "start with an alphabetic character, and " +
                            "end with an alphanumeric character " +
                            "(regex used for validation is '" + VALID_NAME_REGEX + "')",
                    "Invalid name");
        } else {
            outcome = ValidationResponsePayload.createSuccessResponsePayload();
        }

        routingContext
                .response()
                .setStatusCode(200)
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(outcome));

        log.traceExit();
    }
}
