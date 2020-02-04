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
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public abstract class AbstractValidation {

    static EventStreams getSpecFromRequest(RoutingContext routingContext) {
        JsonObject requestBody = routingContext.getBodyAsJson();
        JsonObject requestPayload = requestBody.getJsonObject("request");
        return requestPayload.getJsonObject("object").mapTo(EventStreams.class);
    }
}
