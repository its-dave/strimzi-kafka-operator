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

import com.fasterxml.jackson.annotation.JsonValue;

public enum ConditionType {
    WARNING,
    ERROR,
    PENDING;

    private static final String WARNING_STRING = "Warning";
    private static final String ERROR_STRING = "Error";
    private static final String PENDING_STRING = "Pending";

    @JsonValue
    public String toValue() {
        switch (this) {
            case WARNING:
                return WARNING_STRING;
            case ERROR:
                return ERROR_STRING;
            case PENDING:
                return PENDING_STRING;
            default:
                return null;
        }
    }

    public static ConditionType forValue(String value) {
        switch (value) {
            case WARNING_STRING:
                return WARNING;
            case ERROR_STRING:
                return ERROR;
            case PENDING_STRING:
                return PENDING;
            default:
                return null;
        }
    }
}
