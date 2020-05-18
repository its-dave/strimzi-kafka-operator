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
package com.ibm.eventstreams.controller.models;

import com.ibm.eventstreams.rest.ValidationResponsePayload;
import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.api.kafka.model.status.ConditionBuilder;
import io.strimzi.operator.cluster.model.ModelUtils;

import java.util.Date;

public class StatusCondition {
    private ConditionType type;
    private String reason;
    private String message;

    public StatusCondition(ConditionType type, String reason, String message) {
        this.type = type;
        this.reason = reason;
        this.message = message;
    }

    public Condition toCondition() {
        return new ConditionBuilder()
            .withLastTransitionTime(ModelUtils.formatTimestamp(new Date()))
            .withType(type.toValue())
            .withStatus("true")
            .withReason(reason)
            .withMessage(message)
            .build();
    }

    public static StatusCondition createWarningCondition(String reason, String message) {
        return new StatusCondition(ConditionType.WARNING, reason, message);
    }

    public static StatusCondition createErrorCondition(String reason, String message) {
        return new StatusCondition(ConditionType.ERROR, reason, message);
    }

    public static StatusCondition createPendingCondition(String reason, String message) {
        return new StatusCondition(ConditionType.PENDING, reason, message);
    }

    public ValidationResponsePayload toPayload() {
        return new ValidationResponsePayload(ValidationResponsePayload.createFailureResponse(message, reason));
    }

    public ConditionType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public String getMessage() {
        return message;
    }
}
