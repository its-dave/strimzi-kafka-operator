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
    public static final String MISMATCHED_INTERNAL_AND_EXTERNAL_AUTHENTICATION_REASON = "InvalidInternalAndExternalAuthenticationReason";
    public static final String UNAUTHENTICATED_TLS_BUT_AUTHENTICATED_EXTERNAL_MESSAGE = String.format("'%s' Kafka Listener is authenticated but '%s' Kafka Listener is not authenticated. ", ListenerType.EXTERNAL.toValue(), ListenerType.TLS.toValue())
        + String.format("If Kafka authentication is not required for geo-replication, then remove both spec.strimziOverrides.kafka.listeners.%s.authentication and spec.strimziOverrides.kafka.listeners.%s.authentication. ", ListenerType.EXTERNAL.toValue(), ListenerType.TLS.toValue())
        + String.format("If Kafka authentication is required for geo-replication, then set the authentication type on both spec.strimziOverrides.kafka.listeners.%s.authentication.type and spec.strimziOverrides.kafka.listeners.%s.authentication.type.", ListenerType.EXTERNAL.toValue(), ListenerType.TLS.toValue());

    public static final String AUTHENTICATED_TLS_BUT_UNAUTHENTICATED_EXTERNAL_MESSAGE = String.format("'%s' Kafka Listener is authenticated but '%s' Kafka Listener is not authenticated. ", ListenerType.TLS.toValue(), ListenerType.EXTERNAL.toValue())
        + String.format("If Kafka authentication is not required for geo-replication, then remove both spec.strimziOverrides.kafka.listeners.%s.authentication and spec.strimziOverrides.kafka.listeners.%s.authentication. ", ListenerType.EXTERNAL.toValue(), ListenerType.TLS.toValue())
        + String.format("If Kafka authentication is required for geo-replication, then set the authentication type on both spec.strimziOverrides.kafka.listeners.%s.authentication.type and spec.strimziOverrides.kafka.listeners.%s.authentication.type.", ListenerType.EXTERNAL.toValue(), ListenerType.TLS.toValue());
    public static final String INVALID_INTERNAL_KAFKA_LISTENER_REASON = "ReplicatorInvalidInternalKafkaListener";
    public static final String INVALID_INTERNAL_KAFKA_LISTENER_MESSAGE = "Invalid authentication type '%s' for '%s' Kafka Listener. "
        + "Acceptable authentication types for geo-replication are one of the following: 'tls', 'scram-sha-512', or no authentication. "
        + "Edit spec.strimziOverrides.kafka.listeners.%s.authentication.type to provide a valid authentication type.";
    public static final String INVALID_EXTERNAL_KAFKA_LISTENER_REASON = "ReplicatorInvalidExternalKafkaListener";
    public static final String INVALID_EXTERNAL_KAFKA_LISTENER_MESSAGE = "Invalid authentication type '%s' for external Kafka Listener. "
        + "Acceptable external authentication types for geo-replication are 'tls' or 'scram-sha-512'. "
        + "Edit spec.strimziOverrides.kafka.listeners.external.authentication.type to provide a valid authentication type.";

    private static final List<ListenerAuthentication> VALID_INTERNAL_LISTENER_AUTHENTICATION = Arrays.asList(ListenerAuthentication.SCRAM_SHA_512, ListenerAuthentication.TLS, ListenerAuthentication.NONE);
    private static final List<ListenerAuthentication> VALID_EXTERNAL_LISTENER_AUTHENTICATION = Arrays.asList(ListenerAuthentication.SCRAM_SHA_512, ListenerAuthentication.TLS);

    private final List<StatusCondition> conditions;

    public ReplicatorKafkaListenerValidation(EventStreams instance) {
        this.conditions = validateCr(instance);
    }

    @Override
    public List<StatusCondition> validateCr(EventStreams instance) {
        Map<ListenerType, ListenerAuthentication> listenerAuthentications = ListenerAuthentication.getListenerAuth(instance);
        List<StatusCondition> conditions = new ArrayList<>();

        hasCorrectInternalKafkaListenerAuthentication(listenerAuthentications, conditions);
        hasCorrectExternalKafkaListenerAuthentication(listenerAuthentications, conditions);
        hasInternalAndExternalKafkaListenersWithValidAuthentication(listenerAuthentications, conditions);
        return conditions;
    }

    private void hasInternalAndExternalKafkaListenersWithValidAuthentication(Map<ListenerType, ListenerAuthentication> listenerAuthentications, List<StatusCondition> conditions) {
        if (listenerAuthentications.get(ListenerType.TLS) != ListenerAuthentication.NONE && listenerAuthentications.get(ListenerType.EXTERNAL) == ListenerAuthentication.NONE) {
            conditions.add(StatusCondition.createErrorCondition(MISMATCHED_INTERNAL_AND_EXTERNAL_AUTHENTICATION_REASON, AUTHENTICATED_TLS_BUT_UNAUTHENTICATED_EXTERNAL_MESSAGE));
        } else if (listenerAuthentications.get(ListenerType.TLS) == ListenerAuthentication.NONE && listenerAuthentications.get(ListenerType.EXTERNAL) != ListenerAuthentication.NONE) {
            conditions.add(StatusCondition.createErrorCondition(MISMATCHED_INTERNAL_AND_EXTERNAL_AUTHENTICATION_REASON, UNAUTHENTICATED_TLS_BUT_AUTHENTICATED_EXTERNAL_MESSAGE));
        }
    }

    private void hasCorrectInternalKafkaListenerAuthentication(Map<ListenerType, ListenerAuthentication> listenerAuthentications, List<StatusCondition> conditions) {
        if (!VALID_INTERNAL_LISTENER_AUTHENTICATION.contains(listenerAuthentications.get(ListenerType.TLS))) {
            Optional<ListenerAuthentication> authentication = Optional.ofNullable(listenerAuthentications.get(ListenerType.TLS));
            authentication.ifPresent(listenerAuthentication -> conditions.add(invalidInternalKafkaListenerAuthenticationCondition(listenerAuthentication.toValue(), ListenerType.TLS.toValue())));
        }
    }

    private void hasCorrectExternalKafkaListenerAuthentication(Map<ListenerType, ListenerAuthentication> listenerAuthentications, List<StatusCondition> conditions) {
        if (!VALID_EXTERNAL_LISTENER_AUTHENTICATION.contains(listenerAuthentications.get(ListenerType.EXTERNAL))) {
            Optional<ListenerAuthentication> authentication = Optional.ofNullable(listenerAuthentications.get(ListenerType.EXTERNAL));
            authentication.ifPresent(listenerAuthentication -> conditions.add(invalidExternalKafkaListenerAuthenticationCondition(listenerAuthentication.toValue(), ListenerType.EXTERNAL.toValue())));
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

    public List<StatusCondition> getConditions() {
        return conditions;
    }

    public boolean hasInvalidAuthenticationCondition() {
        return conditions.stream().anyMatch(condition -> condition.getReason().contains(INVALID_INTERNAL_KAFKA_LISTENER_REASON) || condition.getReason().contains(INVALID_EXTERNAL_KAFKA_LISTENER_REASON));
    }

    public boolean hasMismatchedExternalAndInternalListenerAuthentication() {
        return conditions.stream().anyMatch(condition -> condition.getReason().contains(MISMATCHED_INTERNAL_AND_EXTERNAL_AUTHENTICATION_REASON));
    }
}
