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
package com.ibm.eventstreams.controller;

import com.ibm.eventstreams.api.Crds;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsDoneable;
import com.ibm.eventstreams.api.spec.EventStreamsList;
import io.fabric8.kubernetes.client.KubernetesClient;

import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.operator.common.operator.resource.AbstractWatchableResourceOperator;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class EventStreamsResourceOperator extends
        AbstractWatchableResourceOperator<KubernetesClient, EventStreams, EventStreamsList, EventStreamsDoneable, Resource<EventStreams, EventStreamsDoneable>> {

    private static final Logger log = LogManager.getLogger(EventStreamsResourceOperator.class.getName());

    private KubernetesClient client;

    public EventStreamsResourceOperator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, EventStreams.RESOURCE_KIND);
        log.info("Creating EventStreamsResourceOperator");
        this.client = client;
    }

    @Override
    protected MixedOperation<EventStreams, EventStreamsList, EventStreamsDoneable, Resource<EventStreams, EventStreamsDoneable>> operation() {
        return client.customResources(Crds.getCrd(EventStreams.class), EventStreams.class, EventStreamsList.class,
                EventStreamsDoneable.class);
    }
}