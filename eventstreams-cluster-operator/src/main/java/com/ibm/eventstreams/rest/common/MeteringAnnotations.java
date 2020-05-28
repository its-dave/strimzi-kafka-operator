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
package com.ibm.eventstreams.rest.common;

import com.ibm.eventstreams.api.ProductUse;
import com.ibm.eventstreams.api.model.AbstractModel;
import com.ibm.eventstreams.controller.models.StatusCondition;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MeteringAnnotations {
    public static final String EVENTSTREAMS_PRODUCT_USAGE_KEY = "eventstreams.production.type";
    public static final String SPEC_PATH = "spec.template.pod.metadata.annotations";
    private static final Set<String> ACCEPTABLE_PRODUCT_USAGES = new HashSet<>(Arrays.asList("CloudPakForIntegrationProduction", "CloudPakForIntegrationNonProduction"));

    public static final String MISSING_METERING_ANNOTATIONS_REASON = "MissingMeteringAnnotations";
    public static final String MISSING_METERING_ANNOTATIONS_MESSAGE = "%s spec is missing metering annotations. "
        + "Edit the following paths and provide the appropriate values";

    public static final String MISSING_PRODUCT_USAGE_REASON = "MissingProductUsage";
    public static final String MISSING_PRODUCT_USAGE_MESSAGE = "%s has an invalid product usage annotation. "
        + String.format("A valid product usage annotation is either '%s' for production purposes or '%s' for non-production purposes. ", ProductUse.CP4I_PRODUCTION.toValue(), ProductUse.CP4I_NON_PRODUCTION.toValue())
        + String.format("Edit spec.template.pod.metadata.annotations.'%s' to provide a valid value.", EVENTSTREAMS_PRODUCT_USAGE_KEY);


    public static void checkMeteringAnnotations(String specName, String containerName, Map<String, String> crAnnotations, List<StatusCondition> conditions) {
        // If it does not have a product usage then subsequent steps won't work so return early
        ProductUse productUse = ProductUse.fromValue(Optional.ofNullable(crAnnotations.get(EVENTSTREAMS_PRODUCT_USAGE_KEY))
            .orElse(""));
        if (productUse == null || !ACCEPTABLE_PRODUCT_USAGES.contains(productUse.toValue())) {
            conditions.add(StatusCondition.createErrorCondition(MISSING_PRODUCT_USAGE_REASON, String.format(MISSING_PRODUCT_USAGE_MESSAGE, specName)));
            return;
        }

        Map<String, String> expectedMeteringAnnotations = AbstractModel.getEventStreamsMeteringAnnotations(containerName, productUse);
        checkMissingMeteringAnnotations(expectedMeteringAnnotations, crAnnotations, conditions, specName);

    }

    // exposed for testing
    static Map<String, String> findMissingRequiredKeys(Map<String, String> expectedMeteringAnnotations, Map<String, String> crAnnotations) {
        return expectedMeteringAnnotations.keySet().stream()
            .filter(key -> !crAnnotations.containsKey(key) || !expectedMeteringAnnotations.get(key).equals(crAnnotations.get(key)))
            .collect(Collectors.toMap(key -> key, expectedMeteringAnnotations::get));
    }

    private static void checkMissingMeteringAnnotations(Map<String, String> expectedMeteringAnnotations, Map<String, String> crAnnotations, List<StatusCondition> conditions, String specName) {
        Map<String, String> missingKeys = findMissingRequiredKeys(expectedMeteringAnnotations, crAnnotations);

        if (missingKeys.size() > 0) {
            conditions.add(StatusCondition.createErrorCondition(MISSING_METERING_ANNOTATIONS_REASON, getMissingMeteringAnnotationsMessage(missingKeys, specName)));
        }
    }

    // exposed for testing
    public static String getMissingMeteringAnnotationsMessage(Map<String, String> correctValues, String specName) {
        String baseMessage = String.format(MISSING_METERING_ANNOTATIONS_MESSAGE, specName);
        return String.format("%s: %s.", baseMessage, correctValues.keySet().stream()
            .map(key -> String.format("%s.%s=%s", SPEC_PATH, key, correctValues.get(key)))
            .collect(Collectors.joining(", ")));
    }
}
