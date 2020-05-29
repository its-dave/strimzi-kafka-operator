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
package com.ibm.eventstreams.controller;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.Crds;
import io.strimzi.api.kafka.KafkaMirrorMaker2List;
import io.strimzi.api.kafka.model.DoneableKafkaMirrorMaker2;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2;
import io.strimzi.operator.common.operator.resource.AbstractResourceOperator;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KafkaMirrorMaker2Operator extends
        AbstractResourceOperator<KubernetesClient, KafkaMirrorMaker2, KafkaMirrorMaker2List, DoneableKafkaMirrorMaker2, Resource<KafkaMirrorMaker2, DoneableKafkaMirrorMaker2>> {

    private static final Logger log = LogManager.getLogger(KafkaMirrorMaker2Operator.class.getName());

    private KubernetesClient client;

    public KafkaMirrorMaker2Operator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, KafkaMirrorMaker2.RESOURCE_KIND);
        log.info("Creating KafkaMirrorMaker2Operator");
        this.client = client;
    }

    @Override
    protected MixedOperation<KafkaMirrorMaker2, KafkaMirrorMaker2List, DoneableKafkaMirrorMaker2, Resource<KafkaMirrorMaker2, DoneableKafkaMirrorMaker2>> operation() {
        return client.customResources(Crds.kafkaMirrorMaker2(), KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class,
                DoneableKafkaMirrorMaker2.class);
    }
}