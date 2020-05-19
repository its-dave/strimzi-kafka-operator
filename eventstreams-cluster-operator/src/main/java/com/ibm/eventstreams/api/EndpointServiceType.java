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

public enum EndpointServiceType {
    ROUTE("Route"),
    NODE_PORT("NodePort"),
    INGRESS("Ingress"),
    LOAD_BALANCER("LoadBalancer"),
    INTERNAL("Internal");

    private final String value;

    EndpointServiceType(String value) {
        this.value = value;
    }

    @JsonValue
    public String toValue() {
        return this.value;
    }

    private static final String NODE_PORT_STRING = "NodePort";
    private static final String INGRESS_STRING = "Ingress";
    private static final String LOAD_BALANCER_STRING = "LoadBalancer";

    private static final String CLUSTER_IP_STRING = "ClusterIP";

    public String toServiceValue() {
        switch (this) {
            case ROUTE:
            case INTERNAL:
                // Routes and Internal services use ClusterIPs
                return CLUSTER_IP_STRING;
            case NODE_PORT:
                return NODE_PORT_STRING;
            case INGRESS:
                return INGRESS_STRING;
            case LOAD_BALANCER:
                return LOAD_BALANCER_STRING;
            default:
                return null;
        }
    }
}
