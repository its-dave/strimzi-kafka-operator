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
package com.ibm.eventstreams.rest;

import com.ibm.eventstreams.api.TlsVersion;
import com.ibm.eventstreams.api.model.AbstractModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerPlain;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public class PlainListenerValidation extends AbstractValidation {
    private static final Logger log = LogManager.getLogger(NameValidation.class.getName());

    public static final String FAILURE_MESSAGE = "KafkaPlainListener needs to be configured without authentication when EventStreams Security is disabled";
    public static final String FAILURE_MISSING_PLAIN_LISTENER_REASON = "KafkaPlainListener is not configured";
    public static final String FAILURE_CONFIGURED_PLAIN_LISTENER_AUTHENTICATION_REASON = "KafkaPlainListener is configured with authentication";

    /**
     * Validates if there is no encryption for EventStreams then the Kafka Plain Listener is configured with Authentication
     * @param customResourceSpec Eventstreams CR
     * @return A string representing the reason
     */
    public static boolean shouldReject(EventStreams customResourceSpec) {
        boolean esSecurityDisabled = Optional.ofNullable(customResourceSpec)
            .map(EventStreams::getSpec)
            .map(EventStreamsSpec::getSecurity)
            .map(SecuritySpec::getInternalTls)
            .orElse(AbstractModel.DEFAULT_INTERNAL_TLS)
            .equals(TlsVersion.NONE);

        if (esSecurityDisabled) {
            return !getRejectionReason(customResourceSpec).isEmpty();
        }

        return false;
    }

    public static String getRejectionReason(EventStreams customResourceSpec) {
        Optional<KafkaListenerPlain> plainListener = Optional.ofNullable(customResourceSpec.getSpec())
            .map(EventStreamsSpec::getStrimziOverrides)
            .map(KafkaSpec::getKafka)
            .map(KafkaClusterSpec::getListeners)
            .map(KafkaListeners::getPlain);

        if (!plainListener.isPresent()) {
            return FAILURE_MISSING_PLAIN_LISTENER_REASON;
        }
        Optional<KafkaListenerAuthentication> authentication = Optional.ofNullable(plainListener.get().getAuth());
        return authentication.isPresent() ? FAILURE_CONFIGURED_PLAIN_LISTENER_AUTHENTICATION_REASON : "";
    }

    public static void rejectInvalidPlainListenerConfiguration(RoutingContext routingContext) {
        log.traceEntry();

        EventStreams customResourceSpec = getSpecFromRequest(routingContext);

        ValidationResponsePayload outcome = null;
        if (shouldReject(customResourceSpec)) {
            outcome = ValidationResponsePayload.createFailureResponsePayload(FAILURE_MESSAGE, getRejectionReason(customResourceSpec));
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
