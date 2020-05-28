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

import com.ibm.eventstreams.api.TlsVersion;
import com.ibm.eventstreams.api.model.AbstractModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.controller.models.StatusCondition;
import com.ibm.eventstreams.rest.Validation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class GeneralSecurityValidation implements Validation {
    private static final Logger log = LogManager.getLogger(GeneralSecurityValidation.class.getName());

    public static final String KAFKA_UNAUTHENTICATED_REASON = "KafkaUnauthenticated";
    public static final String KAFKA_UNAUTHENTICATED_MESSAGE = "No authentication is enabled for Kafka listeners. "
        + "Any client can connect to your Kafka brokers. "
        + "However, you will have reduced access to Event Streams and Cloud Pak for Integration features, including the use of the UI, and monitoring and metrics capabilities. "
        + "To access all features, enable authentication for Kafka by setting the authentication type in one or more of the following locations: spec.strimziOverrides.kafka.listeners.external.authentication, spec.strimziOverrides.kafka.listeners.tls.authentication, and spec.strimziOverrides.kafka.listeners.plain.authentication";

    public static final String KAFKA_UNAUTHORIZED_REASON = "KafkaUnauthorized";
    public static final String KAFKA_UNAUTHORIZED_MESSAGE = "No authorization is enabled for Kafka. "
        + "Any verified user will have permissions to access and perform actions against your Kafka brokers. "
        + "If Kafka authorization is required, edit spec.strimziOverrides.kafka.authorization.type to provide value ‘runas’.";

    public static final String EVENTSTREAMS_NO_TLS_REASON = "EventStreamsNoTls";
    public static final String EVENTSTREAMS_NO_TLS_MESSAGE = "Communication between Event Streams components is plain text. "
        + "If encrypted data is required, edit spec.security.internalTls to provide value 'TLSv1.2'.";

    @Override
    public List<StatusCondition> validateCr(EventStreams instance) {
        log.traceEntry(() -> instance);
        List<StatusCondition> conditions = new ArrayList<>();

        if (!isEventStreamsEncrypted(instance)) {
            conditions.add(StatusCondition.createWarningCondition(EVENTSTREAMS_NO_TLS_REASON, EVENTSTREAMS_NO_TLS_MESSAGE));
        }

        if (!AbstractModel.isKafkaAuthorizationEnabled(instance)) {
            conditions.add(StatusCondition.createWarningCondition(KAFKA_UNAUTHORIZED_REASON, KAFKA_UNAUTHORIZED_MESSAGE));
        }

        if (!AbstractModel.isKafkaAuthenticationEnabled(instance)) {
            conditions.add(StatusCondition.createWarningCondition(KAFKA_UNAUTHENTICATED_REASON, KAFKA_UNAUTHENTICATED_MESSAGE));
        }

        return log.traceExit(conditions);
    }

    private boolean isEventStreamsEncrypted(EventStreams instance) {
        log.traceEntry();
        return log.traceExit(AbstractModel.getInternalTlsVersion(instance) != TlsVersion.NONE);
    }
}
