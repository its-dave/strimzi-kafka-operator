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

public enum TlsVersion {
    TLS_V1_2("TLSv1.2"),
    NONE("NONE");

    private final String value;

    TlsVersion(String value) {
        this.value = value;
    }

    @JsonValue
    public String toValue() {
        return value;
    }
}
