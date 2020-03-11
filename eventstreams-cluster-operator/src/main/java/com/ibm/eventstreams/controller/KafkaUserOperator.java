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

import io.strimzi.api.kafka.Crds;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.KafkaUserList;
import io.strimzi.api.kafka.model.DoneableKafkaUser;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.operator.common.operator.resource.AbstractResourceOperator;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KafkaUserOperator extends
        AbstractResourceOperator<KubernetesClient, KafkaUser, KafkaUserList, DoneableKafkaUser, Resource<KafkaUser, DoneableKafkaUser>> {

    private static final Logger log = LogManager.getLogger(KafkaUserOperator.class.getName());

    private KubernetesClient client;

    public KafkaUserOperator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, KafkaUser.RESOURCE_KIND);
        log.info("Creating KafkaUserResourceOperator");
        this.client = client;
    }

    @Override
    protected MixedOperation<KafkaUser, KafkaUserList, DoneableKafkaUser, Resource<KafkaUser, DoneableKafkaUser>> operation() {
        return client.customResources(Crds.kafkaUser(), KafkaUser.class, KafkaUserList.class,
                DoneableKafkaUser.class);
    }
}