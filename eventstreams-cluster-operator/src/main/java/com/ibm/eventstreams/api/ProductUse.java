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
package com.ibm.eventstreams.api;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ProductUse {
    CP4I_PRODUCTION("CloudPakForIntegrationProduction"),
    CP4I_NON_PRODUCTION("CloudPakForIntegrationNonProduction"),
    IBM_SUPPORTING_PROGRAM("IBMSupportingProgram");

    private final String value;

    ProductUse(String value) {
        this.value = value;
    }

    @JsonValue
    public String toValue() {
        return this.value;
    }

    public static ProductUse fromValue(String value) {
        switch (value) {
            case "CloudPakForIntegrationProduction":
                return CP4I_PRODUCTION;
            case "CloudPakForIntegrationNonProduction":
                return CP4I_NON_PRODUCTION;
            case "IBMSupportingProgram":
                return IBM_SUPPORTING_PROGRAM;
            default:
                return null;
        }
    }
}
