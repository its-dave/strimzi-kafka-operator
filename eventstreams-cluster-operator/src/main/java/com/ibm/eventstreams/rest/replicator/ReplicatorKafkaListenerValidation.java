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
package com.ibm.eventstreams.rest.replicator;

import com.ibm.eventstreams.api.ListenerAuthentication;
import com.ibm.eventstreams.api.ListenerType;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.controller.models.StatusCondition;
import com.ibm.eventstreams.rest.Validation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ReplicatorKafkaListenerValidation implements Validation {
    public static final String INVALID_INTERNAL_KAFKA_LISTENER_REASON = "ReplicatorInvalidInternalKafkaListener";
    public static final String INVALID_INTERNAL_KAFKA_LISTENER_MESSAGE = "Invalid authentication type '%s' for '%s' Kafka Listener. "
        + "Acceptable authentication types for geo-replication are one of the following: 'tls' or 'scram-sha-512. "
        + "Edit spec.strimziOverrides.kafka.listeners.%s.authentication.type to provide a valid authentication type.";
    public static final String INVALID_EXTERNAL_KAFKA_LISTENER_REASON = "ReplicatorInvalidExternalKafkaListener";
    public static final String INVALID_EXTERNAL_KAFKA_LISTENER_MESSAGE = "Invalid authentication type '%s' for external Kafka Listener. "
        + "Acceptable external authentication types for geo-replication are 'tls' or 'scram-sha-512'. "
        + "Edit spec.strimziOverrides.kafka.listeners.external.authentication.type to provide a valid authentication type.";

    private static final List<ListenerAuthentication> VALID_INTERNAL_LISTENER_AUTHENTICATION = Arrays.asList(ListenerAuthentication.SCRAM_SHA_512, ListenerAuthentication.TLS);
    private static final List<ListenerAuthentication> VALID_EXTERNAL_LISTENER_AUTHENTICATION = Arrays.asList(ListenerAuthentication.SCRAM_SHA_512, ListenerAuthentication.TLS);

    public List<StatusCondition> validateCr(EventStreams instance) {
        Map<ListenerType, ListenerAuthentication> listenerAuthentications = ListenerAuthentication.getListenerAuth(instance);
        List<StatusCondition> conditions = new ArrayList<>();

        hasCorrectInternalKafkaListenerAuthentication(listenerAuthentications, conditions);
        hasCorrectExternalKafkaListenerAuthentication(listenerAuthentications, conditions);
        return conditions;
    }

    private void hasCorrectInternalKafkaListenerAuthentication(Map<ListenerType, ListenerAuthentication> listenerAuthentications, List<StatusCondition> conditions) {
        if (!VALID_INTERNAL_LISTENER_AUTHENTICATION.contains(listenerAuthentications.get(ListenerType.TLS))) {
            Optional<ListenerAuthentication> authentication = Optional.ofNullable(listenerAuthentications.get(ListenerType.TLS));
            if (authentication.isPresent()) {
                conditions.add(invalidInternalKafkaListenerAuthenticationCondition(authentication.get().toValue(), ListenerType.TLS.toValue()));
            } else {
                conditions.add(invalidInternalKafkaListenerAuthenticationCondition(ListenerAuthentication.NONE.toValue(), ListenerType.TLS.toValue()));
            }
        }
    }

    private void hasCorrectExternalKafkaListenerAuthentication(Map<ListenerType, ListenerAuthentication> listenerAuthentications, List<StatusCondition> conditions) {
        if (!VALID_EXTERNAL_LISTENER_AUTHENTICATION.contains(listenerAuthentications.get(ListenerType.EXTERNAL))) {
            Optional<ListenerAuthentication> authentication = Optional.ofNullable(listenerAuthentications.get(ListenerType.EXTERNAL));
            if (authentication.isPresent()) {
                conditions.add(invalidExternalKafkaListenerAuthenticationCondition(authentication.get().toValue(), ListenerType.EXTERNAL.toValue()));
            }  else {
                conditions.add(invalidExternalKafkaListenerAuthenticationCondition(ListenerAuthentication.NONE.toValue(), ListenerType.EXTERNAL.toValue()));
            }


        }
    }

    private StatusCondition invalidInternalKafkaListenerAuthenticationCondition(String authentication, String listener) {
        return StatusCondition.createErrorCondition(INVALID_INTERNAL_KAFKA_LISTENER_REASON,
            String.format(INVALID_INTERNAL_KAFKA_LISTENER_MESSAGE, authentication, listener, listener));
    }

    private StatusCondition invalidExternalKafkaListenerAuthenticationCondition(String authentication, String listener) {
        return StatusCondition.createErrorCondition(INVALID_EXTERNAL_KAFKA_LISTENER_REASON,
            String.format(INVALID_EXTERNAL_KAFKA_LISTENER_MESSAGE, authentication));
    }
}
