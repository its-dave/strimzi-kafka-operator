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

public enum EndpointServiceType {
    ROUTE,
    NODE_PORT,
    INGRESS,
    LOAD_BALANCER,
    INTERNAL;

    private static final String ROUTE_STRING = "Route";
    private static final String NODE_PORT_STRING = "NodePort";
    private static final String INGRESS_STRING = "Ingress";
    private static final String LOAD_BALANCER_STRING = "LoadBalancer";
    private static final String INTERNAL_STRING = "Internal";

    @JsonCreator
    public static EndpointServiceType forValue(String value) {
        switch (value) {
            case ROUTE_STRING:
                return ROUTE;
            case NODE_PORT_STRING:
                return NODE_PORT;
            case INGRESS_STRING:
                return INGRESS;
            case LOAD_BALANCER_STRING:
                return LOAD_BALANCER;
            case INTERNAL_STRING:
                return INTERNAL;
            default:
                return null;
        }
    }

    @JsonValue
    public String toValue() {
        switch (this) {
            case ROUTE:
                return ROUTE_STRING;
            case NODE_PORT:
                return NODE_PORT_STRING;
            case INGRESS:
                return INGRESS_STRING;
            case LOAD_BALANCER:
                return LOAD_BALANCER_STRING;
            case INTERNAL:
                return INTERNAL_STRING;
            default:
                return null;
        }
    }
}
