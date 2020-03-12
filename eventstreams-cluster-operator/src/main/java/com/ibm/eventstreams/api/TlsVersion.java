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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TlsVersion {
    TLS_V1_2,
    TLS_V1_3;

    private static final String TLS_V1_2_STRING = "TLSv1.2";
    private static final String TLS_V1_3_STRING = "TLSv1.3";

    @JsonCreator
    public static TlsVersion forValue(String value) {
        switch (value) {
            case TLS_V1_2_STRING:
                return TLS_V1_2;
            case TLS_V1_3_STRING:
                return TLS_V1_3;
            default:
                return null;
        }
    }

    @JsonValue
    public String toValue() {
        switch (this) {
            case TLS_V1_2:
                return TLS_V1_2_STRING;
            case TLS_V1_3:
                return TLS_V1_3_STRING;
            default:
                return null;
        }
    }
}
