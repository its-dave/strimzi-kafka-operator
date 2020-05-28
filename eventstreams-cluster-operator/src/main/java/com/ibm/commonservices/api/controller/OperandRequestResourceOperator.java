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
package com.ibm.commonservices.api.controller;

import com.ibm.commonservices.api.spec.OperandRequest;
import com.ibm.commonservices.api.spec.OperandRequestDoneable;
import com.ibm.commonservices.api.spec.OperandRequestList;
import io.fabric8.kubernetes.client.KubernetesClient;
import com.ibm.eventstreams.api.Crds;

import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.vertx.core.Vertx;

/**
 * Implementation of CrdOperator, this gives us easy access to several utility methods for interacting with Kubernetes objects
 */
public class OperandRequestResourceOperator extends CrdOperator<KubernetesClient, OperandRequest, OperandRequestList, OperandRequestDoneable> {
    /**
     * Constructor
     *
     * @param vertx       The Vertx instance
     * @param client      The Kubernetes client
     */
    public OperandRequestResourceOperator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, OperandRequest.class, OperandRequestList.class, OperandRequestDoneable.class, Crds.getCrd(OperandRequest.class));
    }
}
