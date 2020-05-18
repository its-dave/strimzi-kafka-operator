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
import com.ibm.eventstreams.controller.models.StatusCondition;
import io.fabric8.kubernetes.client.CustomResource;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

public interface Validation {

    static <T extends CustomResource> T getSpecFromRequest(RoutingContext routingContext, Class<T> specClass) {
        JsonObject requestBody = routingContext.getBodyAsJson();
        JsonObject requestPayload = requestBody.getJsonObject("request");
        return requestPayload.getJsonObject("object").mapTo(specClass);
    }

    static EventStreams getSpecFromRequest(RoutingContext routingContext) {
        return getSpecFromRequest(routingContext, EventStreams.class);
    }

    List<StatusCondition> validateCr(EventStreams spec);

    default void rejectCr(RoutingContext routingContext) {
        sendResponse(routingContext, validateCr(getSpecFromRequest(routingContext)));
    }

    default void sendResponse(RoutingContext routingContext, List<StatusCondition> conditions) {
        routingContext
            .response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(conditions.isEmpty() ? ValidationResponsePayload.createSuccessResponsePayload() : conditions.get(0).toPayload()));
    }
}