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
import com.ibm.eventstreams.controller.models.ConditionType;
import com.ibm.eventstreams.controller.models.StatusCondition;
import io.fabric8.kubernetes.client.CustomResource;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.stream.Collectors;

public interface Validation {
    String ADMIN_API_SPEC_NAME = "adminApi";
    String REST_PRODUCER_SPEC_NAME = "restProducer";
    String SCHEMA_REGISTRY_SPEC_NAME = "schemaRegistry";
    String ADMIN_UI_SPEC_NAME = "adminUi";

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
        List<StatusCondition> errorConditions = conditions.stream().filter(condition -> condition.getType().equals(ConditionType.ERROR)).collect(Collectors.toList());
        routingContext
            .response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(errorConditions.isEmpty() ? ValidationResponsePayload.createSuccessResponsePayload() : errorConditions.get(0).toPayload()));
    }
}