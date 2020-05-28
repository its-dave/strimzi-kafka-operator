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

import com.ibm.eventstreams.api.ProductUse;
import com.ibm.eventstreams.api.model.AbstractModel;
import com.ibm.eventstreams.controller.models.ConditionType;
import com.ibm.eventstreams.controller.models.StatusCondition;
import io.strimzi.api.kafka.model.KafkaConnect;
import io.strimzi.api.kafka.model.KafkaConnectBuilder;
import io.strimzi.api.kafka.model.KafkaConnectResources;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ibm.eventstreams.rest.common.MeteringAnnotations.EVENTSTREAMS_PRODUCT_USAGE_KEY;
import static com.ibm.eventstreams.rest.common.MeteringAnnotations.MISSING_METERING_ANNOTATIONS_MESSAGE;
import static com.ibm.eventstreams.rest.common.MeteringAnnotations.MISSING_METERING_ANNOTATIONS_REASON;
import static com.ibm.eventstreams.rest.common.MeteringAnnotations.MISSING_PRODUCT_USAGE_MESSAGE;
import static com.ibm.eventstreams.rest.common.MeteringAnnotations.MISSING_PRODUCT_USAGE_REASON;
import static com.ibm.eventstreams.rest.common.MeteringAnnotations.SPEC_PATH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class KafkaConnectMeteringAnnotationsTest {
    @Test
    public void testMissingProductUsageFails() {
        KafkaConnect spec = new KafkaConnectBuilder().build();

        KafkaConnectMeteringAnnotationsValidation validation = new KafkaConnectMeteringAnnotationsValidation();

        List<StatusCondition> conditions = validation.validateCr(spec);

        assertThat(conditions, hasSize(1));

        assertThat(conditions.get(0).getReason(), is(MISSING_PRODUCT_USAGE_REASON));
        assertThat(conditions.get(0).getMessage(), is(String.format(MISSING_PRODUCT_USAGE_MESSAGE, KafkaConnectMeteringAnnotationsValidation.SPEC_NAME)));
        assertThat(conditions.get(0).getType(), is(ConditionType.ERROR));
    }

    @Test
    public void testWrongProductUsageFails() {
        KafkaConnect spec = new KafkaConnectBuilder()
            .withNewSpec()
                .withNewTemplate()
                    .withNewPod()
                        .withNewMetadata()
                            .withAnnotations(Collections.singletonMap(EVENTSTREAMS_PRODUCT_USAGE_KEY, "random-key"))
                        .endMetadata()
                    .endPod()
                .endTemplate()
            .endSpec()
            .build();

        KafkaConnectMeteringAnnotationsValidation validation = new KafkaConnectMeteringAnnotationsValidation();

        List<StatusCondition> conditions = validation.validateCr(spec);

        assertThat(conditions, hasSize(1));

        assertThat(conditions.get(0).getReason(), is(MISSING_PRODUCT_USAGE_REASON));
        assertThat(conditions.get(0).getMessage(), is(String.format(MISSING_PRODUCT_USAGE_MESSAGE, KafkaConnectMeteringAnnotationsValidation.SPEC_NAME)));
        assertThat(conditions.get(0).getType(), is(ConditionType.ERROR));
    }

    @Test
    public void testMissingAnnotationsFails() {
        String name = "test-instance";
        KafkaConnect spec = new KafkaConnectBuilder()
            .withNewMetadata()
                .withNewName(name)
            .endMetadata()
            .withNewSpec()
                .withNewTemplate()
                    .withNewPod()
                        .withNewMetadata()
                            .withAnnotations(Collections.singletonMap(EVENTSTREAMS_PRODUCT_USAGE_KEY, ProductUse.CP4I_NON_PRODUCTION.toValue()))
                        .endMetadata()
                    .endPod()
                .endTemplate()
            .endSpec()
            .build();


        KafkaConnectMeteringAnnotationsValidation validation = new KafkaConnectMeteringAnnotationsValidation();

        List<StatusCondition> conditions = validation.validateCr(spec);

        assertThat(conditions, hasSize(1));

        assertThat(conditions.get(0).getReason(), is(MISSING_METERING_ANNOTATIONS_REASON));
        testMessageContainsValidValues(conditions.get(0).getMessage(), AbstractModel.getEventStreamsMeteringAnnotations(KafkaConnectResources.deploymentName(name), ProductUse.CP4I_NON_PRODUCTION));
        assertThat(conditions.get(0).getType(), is(ConditionType.ERROR));
    }

    @Test
    public void testWrongValuesAnnotationsFails() {
        String name = "test-instance";
        Map<String, String> correctAnnotations = AbstractModel.getEventStreamsMeteringAnnotations(KafkaConnectResources.deploymentName(name), ProductUse.CP4I_NON_PRODUCTION);
        Map<String, String> crAnnotations = new HashMap<>();
        correctAnnotations.keySet().forEach(key -> crAnnotations.put(key, ""));
        crAnnotations.put(EVENTSTREAMS_PRODUCT_USAGE_KEY, ProductUse.CP4I_NON_PRODUCTION.toValue());

        KafkaConnect spec = new KafkaConnectBuilder()
            .withNewMetadata()
                .withNewName(name)
            .endMetadata()
            .withNewSpec()
                .withNewTemplate()
                    .withNewPod()
                        .withNewMetadata()
                            .withAnnotations(crAnnotations)
                        .endMetadata()
                    .endPod()
                .endTemplate()
            .endSpec()
            .build();


        KafkaConnectMeteringAnnotationsValidation validation = new KafkaConnectMeteringAnnotationsValidation();

        List<StatusCondition> conditions = validation.validateCr(spec);

        assertThat(conditions, hasSize(1));

        assertThat(conditions.get(0).getReason(), is(MISSING_METERING_ANNOTATIONS_REASON));
        testMessageContainsValidValues(conditions.get(0).getMessage(), correctAnnotations);
        assertThat(conditions.get(0).getType(), is(ConditionType.ERROR));
    }

    @Test
    public void testCorrectValuesPass() {
        String name = "test-instance";
        Map<String, String> correctAnnotations = AbstractModel.getEventStreamsMeteringAnnotations(KafkaConnectResources.deploymentName(name), ProductUse.CP4I_NON_PRODUCTION);
        Map<String, String> crAnnotations = new HashMap<>();
        correctAnnotations.forEach(crAnnotations::put);
        crAnnotations.put(EVENTSTREAMS_PRODUCT_USAGE_KEY, ProductUse.CP4I_NON_PRODUCTION.toValue());

        KafkaConnect spec = new KafkaConnectBuilder()
            .withNewMetadata()
            .withNewName(name)
            .endMetadata()
            .withNewSpec()
            .withNewTemplate()
            .withNewPod()
            .withNewMetadata()
            .withAnnotations(crAnnotations)
            .endMetadata()
            .endPod()
            .endTemplate()
            .endSpec()
            .build();

        KafkaConnectMeteringAnnotationsValidation validation = new KafkaConnectMeteringAnnotationsValidation();

        List<StatusCondition> conditions = validation.validateCr(spec);

        assertThat(conditions, hasSize(0));
    }

    private static void testMessageContainsValidValues(String message, Map<String, String> missingRequiredAnnotations) {
        assertThat(message, containsString(String.format(MISSING_METERING_ANNOTATIONS_MESSAGE, KafkaConnectMeteringAnnotationsValidation.SPEC_NAME)));
        missingRequiredAnnotations.forEach((key, value) -> assertThat(message, containsString(String.format("%s.%s=%s", SPEC_PATH, key, value))));
    }
}
