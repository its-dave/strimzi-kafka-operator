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
package com.ibm.eventstreams.rest.eventstreams;

import com.ibm.eventstreams.rest.Validation;
import com.ibm.eventstreams.rest.ValidationResponsePayload;
import io.fabric8.kubernetes.client.CustomResource;
import io.strimzi.api.kafka.model.Constants;
import io.strimzi.api.kafka.model.KafkaTopic;
import io.strimzi.api.kafka.model.KafkaUser;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * Checks an instance of KafkaUser or KafkaTopic to ensure that a label has been
 * included to specify which Kafka cluster it is for.
 *
 * The contents of the label is not checked, as one instance of an operator isn't
 * able to know all current running operands. In addition, a user may intentionally
 * prepare resources for a cluster that is not currently running.
 *
 * The aim is just to catch the accidental omission of a required label.
 */
public class EntityLabelValidation {

    private static final Logger log = LogManager.getLogger(NameValidation.class.getName());
    
    private static final String EXPECTED_LABEL = Constants.RESOURCE_GROUP_NAME + "/cluster";


    public static boolean shouldReject(CustomResource customResourceSpec) {
        return !(customResourceSpec.getMetadata().getLabels().containsKey(EXPECTED_LABEL));
    }

    public static void rejectInvalidKafkaTopics(RoutingContext routingContext) {
        rejectInvalidEntities(routingContext, KafkaTopic.class);
    }
    public static void rejectInvalidKafkaUsers(RoutingContext routingContext) {
        rejectInvalidEntities(routingContext, KafkaUser.class);
    }

    private static void rejectInvalidEntities(RoutingContext routingContext, Class entityClass) {
        log.traceEntry();

        CustomResource customResourceSpec = Validation.getSpecFromRequest(routingContext, entityClass);

        ValidationResponsePayload outcome;

        if (shouldReject(customResourceSpec)) {
            outcome = ValidationResponsePayload.createFailureResponsePayload(
                    EXPECTED_LABEL + " is a required label to identify the cluster",
                    "Missing cluster label");
        } else {
            outcome = ValidationResponsePayload.createSuccessResponsePayload();
        }

        routingContext
                .response()
                .setStatusCode(200)
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(outcome));

        log.traceExit();
    }
}
