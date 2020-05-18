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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PhaseState {
    READY,
    PENDING,
    FAILED;

    private final static String READY_STRING = "Ready";
    private final static String PENDING_STRING = "Pending";
    private final static String FAILED_STRING = "Failed";

    @JsonCreator
    public static PhaseState fromValue(String value) {
        switch (value) {
            case READY_STRING:
                return READY;
            case PENDING_STRING:
                return PENDING;
            case FAILED_STRING:
                return FAILED;
            default:
                return null;
        }
    }

    @JsonValue
    public String toValue() {
        switch (this) {
            case READY:
                return READY_STRING;
            case PENDING:
                return PENDING_STRING;
            case FAILED:
                return FAILED_STRING;
            default:
                return null;
        }
    }
}
