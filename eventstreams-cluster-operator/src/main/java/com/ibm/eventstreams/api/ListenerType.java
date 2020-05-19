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

public enum ListenerType {
    PLAIN("plain"),
    EXTERNAL("external"),
    TLS("tls");

    private final String value;

    ListenerType(String value) {
        this.value = value;
    }

    public String toValue() {
        return this.value;
    }
}