/*
 * IBM Confidential
 * OCO Source Materials
 *
 * 5737-H33
 *
 * (C) Copyright IBM Corp. 2019  All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 */
package com.ibm.eventstreams.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProductUse {
    CP4I_PRODUCTION,
    CP4I_NON_PRODUCTION,
    IBM_SUPPORTING_PROGRAM;

    private static final String CP4I_PRODUCTION_STRING = "CloudPakForIntegrationProduction";
    private static final String CP4I_NON_PRODUCTION_STRING = "CloudPakForIntegrationNonProduction";
    private static final String IBM_SUPPORTING_PROGRAM_STRING = "IBMSupportingProgram";

    @JsonCreator
    public static ProductUse forValue(String value) {
        switch (value) {
            case CP4I_PRODUCTION_STRING:
                return CP4I_PRODUCTION;
            case CP4I_NON_PRODUCTION_STRING:
                return CP4I_NON_PRODUCTION;
            case IBM_SUPPORTING_PROGRAM_STRING:
                return IBM_SUPPORTING_PROGRAM;
            default:
                return null;
        }
    }

    @JsonValue
    public String toValue() {
        switch (this) {
            case CP4I_PRODUCTION:
                return CP4I_PRODUCTION_STRING;
            case CP4I_NON_PRODUCTION:
                return CP4I_NON_PRODUCTION_STRING;
            case IBM_SUPPORTING_PROGRAM:
                return IBM_SUPPORTING_PROGRAM_STRING;
            default:
                return null;
        }
    }
}
