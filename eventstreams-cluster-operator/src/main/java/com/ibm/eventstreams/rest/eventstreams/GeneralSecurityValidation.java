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

import java.util.ArrayList;
import java.util.List;

public class GeneralSecurityValidation implements Validation {
    public static final String KAFKA_UNAUTHENTICATED_REASON = "KafkaUnauthenticated";
    public static final String KAFKA_UNAUTHENTICATED_MESSAGE = "Kafka is unauthenticated. "
        + "Anyone can produce and consume from your Kafka. "
        + "The Event Streams User Interface will also contain a reduced set of features such as not access to Monitoring and Metrics capabilities, reduced functionality in other dialogs and no access to Cloud Pak for Integration capabilities. "
        + "If Kafka authentication is required, enable it by setting the authentication type in one or more of the following locations: spec.strimziOverrides.kafka.listeners.external.authentication, spec.strimziOverrides.kafka.listeners.tls.authentication, and spec.strimziOverrides.kafka.listeners.plain.authentication.";

    public static final String KAFKA_UNAUTHORIZED_REASON = "KafkaUnauthorized";
    public static final String KAFKA_UNAUTHORIZED_MESSAGE = "Kafka is unauthorized. "
        + "Anyone with the appropriate credentials can produce and consume from your Kafka. "
        + "If Kafka authorization is required, edit spec.strimziOverrides.kafka.authorization.type to provide value 'runas'.";

    public static final String EVENTSTREAMS_NO_TLS_REASON = "EventStreamsNoTls";
    public static final String EVENTSTREAMS_NO_TLS_MESSAGE = "Communication between Event Streams components are unencrypted. "
        + "All data sent between Event Streams components are sent in plain text. "
        + "If encrypted data is required, edit spec.security.internalTls and provide value 'TLSv1.2'.";

    @Override
    public List<StatusCondition> validateCr(EventStreams instance) {
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

        return conditions;
    }

    private boolean isEventStreamsEncrypted(EventStreams instance) {
        return AbstractModel.getInternalTlsVersion(instance) != TlsVersion.NONE;
    }
}
