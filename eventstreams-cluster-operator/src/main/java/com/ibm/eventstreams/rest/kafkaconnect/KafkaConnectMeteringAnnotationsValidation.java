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
package com.ibm.eventstreams.rest.kafkaconnect;

import com.ibm.eventstreams.controller.models.StatusCondition;
import com.ibm.eventstreams.rest.common.MeteringAnnotations;
import io.strimzi.api.kafka.model.KafkaConnect;
import io.strimzi.api.kafka.model.KafkaConnectResources;
import io.strimzi.api.kafka.model.KafkaConnectSpec;
import io.strimzi.api.kafka.model.template.KafkaConnectTemplate;
import io.strimzi.api.kafka.model.template.MetadataTemplate;
import io.strimzi.api.kafka.model.template.PodTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class KafkaConnectMeteringAnnotationsValidation implements KafkaConnectValidation {
    public static final String SPEC_NAME = "KafkaConnect";
    @Override
    public List<StatusCondition> validateCr(KafkaConnect spec) {
        List<StatusCondition> conditions = new ArrayList<>();
        Map<String, String> annotations = Optional.ofNullable(spec)
            .map(KafkaConnect::getSpec)
            .map(KafkaConnectSpec::getTemplate)
            .map(KafkaConnectTemplate::getPod)
            .map(PodTemplate::getMetadata)
            .map(MetadataTemplate::getAnnotations)
            .orElse(Collections.emptyMap());

        MeteringAnnotations.checkMeteringAnnotations(SPEC_NAME, KafkaConnectResources.deploymentName(spec.getMetadata().getName()), annotations, conditions);

        return conditions;
    }
}
