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
package com.ibm.eventstreams.rest.mirrormaker2;

import com.ibm.eventstreams.controller.models.StatusCondition;
import com.ibm.eventstreams.rest.Validation;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

public interface MirrorMaker2Validation extends Validation {
    List<StatusCondition> validateCr(KafkaMirrorMaker2 spec);

    default void rejectCr(RoutingContext routingContext) {
        sendResponse(routingContext, validateCr(Validation.getSpecFromRequest(routingContext, KafkaMirrorMaker2.class)));
    }
}
