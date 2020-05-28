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
package com.ibm.eventstreams.rest.kafkaconnectS2I;

import com.ibm.eventstreams.controller.models.StatusCondition;
import com.ibm.eventstreams.rest.common.MeteringAnnotations;
import io.strimzi.api.kafka.model.KafkaConnectS2I;
import io.strimzi.api.kafka.model.KafkaConnectS2IResources;
import io.strimzi.api.kafka.model.KafkaConnectSpec;
import io.strimzi.api.kafka.model.template.KafkaConnectTemplate;
import io.strimzi.api.kafka.model.template.MetadataTemplate;
import io.strimzi.api.kafka.model.template.PodTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class KafkaConnectS2IMeteringAnnotationsValidation implements KafkaConnectS2IValidation {
    public static final String SPEC_NAME = "KafkaConnectS2I";

    @Override
    public List<StatusCondition> validateCr(KafkaConnectS2I spec) {
        List<StatusCondition> conditions = new ArrayList<>();
        Map<String, String> annotations = Optional.ofNullable(spec)
            .map(KafkaConnectS2I::getSpec)
            .map(KafkaConnectSpec::getTemplate)
            .map(KafkaConnectTemplate::getPod)
            .map(PodTemplate::getMetadata)
            .map(MetadataTemplate::getAnnotations)
            .orElse(Collections.emptyMap());

        MeteringAnnotations.checkMeteringAnnotations(SPEC_NAME, KafkaConnectS2IResources.deploymentName(spec.getMetadata().getName()), annotations, conditions);

        return conditions;
    }
}
