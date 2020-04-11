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
package com.ibm.eventstreams.controller.utils;

import io.strimzi.operator.common.MetricsProvider;
import io.strimzi.operator.common.MicrometerMetricsProvider;

import static org.mockito.Mockito.mock;

public class MetricsUtils {

    /**
     * Create mock MetricsProvider for use in tests.
     */
    public static MetricsProvider createMockMetricsProvider() {
        return mock(MicrometerMetricsProvider.class);
    }
}
