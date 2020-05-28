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
package com.ibm.eventstreams.rest.mirrormaker2;

import com.ibm.eventstreams.controller.models.StatusCondition;
import com.ibm.eventstreams.rest.common.MeteringAnnotations;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2Resources;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2Spec;
import io.strimzi.api.kafka.model.template.KafkaConnectTemplate;
import io.strimzi.api.kafka.model.template.MetadataTemplate;
import io.strimzi.api.kafka.model.template.PodTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MirrorMaker2MeteringAnnotationValidation implements MirrorMaker2Validation {
    public static final String SPEC_NAME = "MirrorMaker2";

    @Override
    public List<StatusCondition> validateCr(KafkaMirrorMaker2 spec) {
        List<StatusCondition> conditions = new ArrayList<>();
        Map<String, String> annotations = Optional.ofNullable(spec)
            .map(KafkaMirrorMaker2::getSpec)
            .map(KafkaMirrorMaker2Spec::getTemplate)
            .map(KafkaConnectTemplate::getPod)
            .map(PodTemplate::getMetadata)
            .map(MetadataTemplate::getAnnotations)
            .orElse(Collections.emptyMap());

        MeteringAnnotations.checkMeteringAnnotations(SPEC_NAME, KafkaMirrorMaker2Resources.deploymentName(spec.getMetadata().getName()), annotations, conditions);

        return conditions;
    }
}
