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
package com.ibm.eventstreams.rest.eventstreams;

import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.controller.models.StatusCondition;
import com.ibm.eventstreams.rest.Validation;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

public interface EventStreamsValidation extends Validation {
    List<StatusCondition> validateCr(EventStreams spec);

    default void rejectCr(RoutingContext routingContext) {
        sendResponse(routingContext, validateCr(Validation.getSpecFromRequest(routingContext, EventStreams.class)));
    }
}
