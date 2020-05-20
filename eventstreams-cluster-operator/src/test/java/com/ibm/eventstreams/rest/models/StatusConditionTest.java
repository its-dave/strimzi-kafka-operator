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
package com.ibm.eventstreams.rest.models;

import com.ibm.eventstreams.controller.models.StatusCondition;
import io.strimzi.api.kafka.model.status.Condition;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class StatusConditionTest {
    private final String reason = "test-reason";
    private final String message = "This is a test message";

    @Test
    public void testWarningCondition() {
        StatusCondition statusCondition = StatusCondition.createWarningCondition(reason, message);
        Condition condition = statusCondition.toCondition();

        assertThat(condition.getType(), is("Warning"));
        assertThat(condition.getReason(), is(reason));
        assertThat(condition.getMessage(), is(message));
        assertThat(condition.getStatus(), is("True"));
    }

    @Test
    public void testErrorCondition() {
        StatusCondition statusCondition = StatusCondition.createErrorCondition(reason, message);
        Condition condition = statusCondition.toCondition();

        assertThat(condition.getType(), is("Error"));
        assertThat(condition.getReason(), is(reason));
        assertThat(condition.getMessage(), is(message));
        assertThat(condition.getStatus(), is("True"));
    }

    @Test
    public void testPendingCondition() {
        StatusCondition statusCondition = StatusCondition.createPendingCondition(reason, message);
        Condition condition = statusCondition.toCondition();

        assertThat(condition.getType(), is("Pending"));
        assertThat(condition.getReason(), is(reason));
        assertThat(condition.getMessage(), is(message));
        assertThat(condition.getStatus(), is("True"));
    }
}
