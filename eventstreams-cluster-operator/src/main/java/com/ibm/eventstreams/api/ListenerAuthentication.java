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

import java.util.Optional;

public enum ListenerAuthentication {
    TLS,
    SCRAM_SHA_512,
    OAUTH,
    NONE;

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
            KafkaListenerAuthentication authentication = null;
            switch (listener) {
                case PLAIN:
                    authentication = listeners
                        .map(KafkaListeners::getPlain)
                        .map(KafkaListenerPlain::getAuth)
                        .orElse(null);
                    break;
                case EXTERNAL:
                    authentication = listeners
                        .map(KafkaListeners::getExternal)
                        .map(KafkaListenerExternal::getAuth)
                        .orElse(null);
                    break;
                case TLS:
                    authentication = listeners
                        .map(KafkaListeners::getTls)
                        .map(KafkaListenerTls::getAuth)
                        .orElse(null);
            }

            if (authentication == null) {
                return null;
            }

            return Optional.of(authentication)
                .map(ListenerAuthentication::toValue)
                .orElse(ListenerAuthentication.NONE);
        }

        return NONE;
    }
}