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

import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.api.kafka.model.status.ConditionBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ConditionUtils {

    private static Condition createCondition(String type, String status) {
        return new ConditionBuilder()
                .withNewLastTransitionTime(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()))
                .withNewType(type)
                .withNewStatus(status)
                .build();
    }
    private static Condition createConditionWithReason(String type, String status, String reason, String message) {
        return new ConditionBuilder()
                .withNewLastTransitionTime(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()))
                .withNewType(type)
                .withNewStatus(status)
                .withNewReason(reason)
                .withNewMessage(message)
                .build();
    }


    /**
     * @return a list of status conditions with a condition that indicates a CR is still
     *  deploying
     */
    public static List<Condition> getInitialConditions() {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(createConditionWithReason("NotReady", "True", "Creating", "Kafka cluster is being deployed"));
        return conditions;
    }

    /**
     * @return a list of status conditions with a condition that indicates a CR is still
     *  deploying, with some warnings
     */
    public static List<Condition> getInitialConditionsWithWarnings() {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(createConditionWithReason(
                "Warning",
                "True",
                "KafkaStorage",
                "A Kafka cluster with a single replica and ephemeral storage will lose topic messages after any restart or rolling update."));
        conditions.add(createConditionWithReason(
                "Warning",
                "True",
                "ZooKeeperStorage",
                "A ZooKeeper cluster with a single replica and ephemeral storage will be in a defective state after any restart or rolling update."));
        conditions.add(createConditionWithReason("NotReady", "True", "Creating", "Kafka cluster is being deployed"));
        return conditions;
    }

    /**
     * @return a list of status conditions with a condition that indicates a CR is ready
     *  but with some warnings
     */
    public static List<Condition> getReadyConditionsWithWarnings() {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(createConditionWithReason(
                "Warning",
                "True",
                "KafkaStorage",
                "A Kafka cluster with a single replica and ephemeral storage will lose topic messages after any restart or rolling update."));
        conditions.add(createConditionWithReason(
                "Warning",
                "True",
                "ZooKeeperStorage",
                "A ZooKeeper cluster with a single replica and ephemeral storage will be in a defective state after any restart or rolling update."));
        conditions.add(createCondition("Ready", "True"));
        return conditions;
    }

    /**
     * @return a list of status conditions with a warning and a condition that indicates irrecoverable failure
     */
    public static List<Condition> getNotReadyConditionsWithWarnings() {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(createConditionWithReason(
                "Warning",
                "True",
                "KafkaStorage",
                "A Kafka cluster with a single replica and ephemeral storage will lose topic messages after any restart or rolling update."));
        conditions.add(createConditionWithReason("NotReady", "True", "MockFailure", "Something went wrong"));
        return conditions;
    }

    /**
     * @return a list of status conditions with a condition that indicates a CR is ready
     */
    public static List<Condition> getReadyCondition() {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(createCondition("Ready", "True"));
        return conditions;
    }

    /**
     * @return a list of status conditions with a condition that indicates irrecoverable failure
     */
    public static List<Condition> getFailureCondition() {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(createConditionWithReason("NotReady", "True", "MockFailure", "Something went wrong"));
        return conditions;
    }

    /**
     * @return an empty list of status conditions
     */
    public static List<Condition> getEmptyConditions() {
        List<Condition> conditions = new ArrayList<>();
        return conditions;
    }
}
