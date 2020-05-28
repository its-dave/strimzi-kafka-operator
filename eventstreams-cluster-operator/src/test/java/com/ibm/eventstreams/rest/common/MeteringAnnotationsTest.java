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
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;

public class MeteringAnnotationsTest {

    @Test
    public void testEmptyCrAnnotationsOnNonProduction() {
        Map<String, String> crAnnotations = Collections.emptyMap();
        Map<String, String> correctAnnotations = AbstractModel.getEventStreamsMeteringAnnotations("blah", ProductUse.CP4I_NON_PRODUCTION);

        Map<String, String> missingRequiredAnnotations = MeteringAnnotations.findMissingRequiredKeys(correctAnnotations, crAnnotations);

        for (String key : correctAnnotations.keySet()) {
            assertThat(missingRequiredAnnotations, hasEntry(key, correctAnnotations.get(key)));
        }
    }

    @Test
    public void testEmptyCrAnnotationsOnProduction() {
        Map<String, String> crAnnotations = Collections.emptyMap();
        Map<String, String> correctAnnotations = AbstractModel.getEventStreamsMeteringAnnotations("blah", ProductUse.CP4I_PRODUCTION);

        Map<String, String> missingRequiredAnnotations = MeteringAnnotations.findMissingRequiredKeys(correctAnnotations, crAnnotations);

        for (String key : correctAnnotations.keySet()) {
            assertThat(missingRequiredAnnotations, hasEntry(key, correctAnnotations.get(key)));
        }
    }

    @Test
    public void testIncorrectValuesOnProduction() {
        Map<String, String> correctAnnotations = AbstractModel.getEventStreamsMeteringAnnotations("blah", ProductUse.CP4I_PRODUCTION);
        Map<String, String> crAnnotations = new HashMap<>();
        correctAnnotations.keySet().forEach(key -> crAnnotations.put(key, ""));

        Map<String, String> missingRequiredAnnotations = MeteringAnnotations.findMissingRequiredKeys(correctAnnotations, crAnnotations);

        for (String key : correctAnnotations.keySet()) {
            assertThat(missingRequiredAnnotations, hasEntry(key, correctAnnotations.get(key)));
        }
    }

    @Test
    public void testIncorrectValuesOnNonProduction() {
        Map<String, String> correctAnnotations = AbstractModel.getEventStreamsMeteringAnnotations("blah", ProductUse.CP4I_NON_PRODUCTION);
        Map<String, String> crAnnotations = new HashMap<>();
        correctAnnotations.keySet().forEach(key -> crAnnotations.put(key, ""));

        Map<String, String> missingRequiredAnnotations = MeteringAnnotations.findMissingRequiredKeys(correctAnnotations, crAnnotations);

        for (String key : correctAnnotations.keySet()) {
            assertThat(missingRequiredAnnotations, hasEntry(key, correctAnnotations.get(key)));
        }
    }

    @Test
    public void testFormattingMessage() {
        Map<String, String> testMap = Collections.singletonMap("will", "Dale");

        String message = MeteringAnnotations.getMissingMeteringAnnotationsMessage(testMap, "check");
        assertThat(message, is("check spec is missing metering annotations. Edit the following paths and provide the appropriate values: spec.template.pod.metadata.annotations.will=Dale."));
    }
}
