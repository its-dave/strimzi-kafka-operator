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
package com.ibm.eventstreams.api;

import com.fasterxml.jackson.annotation.JsonValue;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationOAuth;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationScramSha512;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationTls;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternal;
import io.strimzi.api.kafka.model.listener.KafkaListenerPlain;
import io.strimzi.api.kafka.model.listener.KafkaListenerTls;
import io.strimzi.api.kafka.model.listener.KafkaListeners;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum ListenerAuthentication {
    TLS("tls"),
    SCRAM_SHA_512("scram-sha-512"),
    OAUTH("oauth"),
    NONE("none");

    private final String value;

    ListenerAuthentication(String value) {
        this.value = value;
    }

    @JsonValue
    public String toValue() {
        return this.value;
    }

    public static ListenerAuthentication toValue(KafkaListenerAuthentication auth) {
        switch (auth.getType()) {
            case KafkaListenerAuthenticationTls.TYPE_TLS:
                return TLS;
            case KafkaListenerAuthenticationScramSha512.SCRAM_SHA_512:
                return SCRAM_SHA_512;
            case KafkaListenerAuthenticationOAuth.TYPE_OAUTH:
                return OAUTH;
            default:
                return NONE;
        }
    }

    public static ListenerAuthentication getAuthentication(EventStreams instance, ListenerType listener) {
        Optional<KafkaListeners> listeners = Optional.ofNullable(instance)
            .map(EventStreams::getSpec)
            .map(EventStreamsSpec::getStrimziOverrides)
            .map(KafkaSpec::getKafka)
            .map(KafkaClusterSpec::getListeners);

        if (listeners.isPresent()) {
            ListenerAuthentication authentication = null;
            switch (listener) {
                case PLAIN:
                    Optional<KafkaListenerPlain> plainListener = listeners
                        .map(KafkaListeners::getPlain);
                    if (plainListener.isPresent()) {
                        authentication = plainListener.map(KafkaListenerPlain::getAuth)
                            .map(ListenerAuthentication::toValue)
                            .orElse(NONE);
                    }
                    break;
                case EXTERNAL:
                    Optional<KafkaListenerExternal> externalListener = listeners
                        .map(KafkaListeners::getExternal);
                    if (externalListener.isPresent()) {
                        authentication = externalListener.map(KafkaListenerExternal::getAuth)
                            .map(ListenerAuthentication::toValue)
                            .orElse(NONE);
                    }
                    break;
                case TLS:
                    Optional<KafkaListenerTls> tlsListener = listeners
                        .map(KafkaListeners::getTls);
                    if (tlsListener.isPresent()) {
                        authentication = tlsListener.map(KafkaListenerTls::getAuth)
                            .map(ListenerAuthentication::toValue)
                            .orElse(NONE);
                    }
            }
            return authentication;
        }
        return NONE;
    }

    public static Map<ListenerType, ListenerAuthentication> getListenerAuth(EventStreams instance) {
        HashMap<ListenerType, ListenerAuthentication> listenerAuth = new HashMap<>();

        listenerAuth.put(ListenerType.PLAIN, ListenerAuthentication.getAuthentication(instance, ListenerType.PLAIN));
        listenerAuth.put(ListenerType.TLS, ListenerAuthentication.getAuthentication(instance, ListenerType.TLS));
        listenerAuth.put(ListenerType.EXTERNAL, ListenerAuthentication.getAuthentication(instance, ListenerType.EXTERNAL));

        return listenerAuth;
    }
}