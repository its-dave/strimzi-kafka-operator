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

import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import io.strimzi.api.kafka.model.CertAndKeySecretSource;
import io.strimzi.api.kafka.model.KafkaClusterSpec;
import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternalConfiguration;
import io.strimzi.api.kafka.model.listener.KafkaListenerExternalRoute;
import io.strimzi.api.kafka.model.listener.KafkaListenerTls;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.strimzi.api.kafka.model.listener.TlsListenerConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Listener {
    private String name;
    private String path;
    private boolean tls;
    private boolean exposed;
    private int port;
    private Function<EventStreamsSpec, Optional<CertAndKeySecretSource>> certOverrideSpec;
    public static final String EXTERNAL_TLS_NAME = "external-tls";
    private static final int EXTERNAL_TLS_PORT = 9444;
    public static final String EXTERNAL_PLAIN_NAME = "external-plain";
    private static final int EXTERNAL_PLAIN_PORT = 9081;
    public static final String INTERNAL_TLS_NAME = "internal-tls";
    private static final int INTERNAL_TLS_PORT = 8443;
    public static final String INTERNAL_PLAIN_NAME = "internal-plain";
    private static final int INTERNAL_PLAIN_PORT = 8080;
    private static final String POD_TO_POD_TLS_NAME = "podtls";
    private static final String POD_TO_POD_TLS_PATH = "p2p/podtls";
    private static final int POD_TO_POD_TLS_PORT = 7443;
    private static final String POD_TO_POD_PLAIN_NAME = "pod";
    private static final int POD_TO_POD_PLAIN_PORT = 7080;
    private static List<Listener> enabledListeners = getDefaultListeners();


    public Listener(String name, boolean tls, boolean exposed, int port, Function<EventStreamsSpec, Optional<CertAndKeySecretSource>> certOverrideSpec) {
        this.name = name;
        this.tls = tls;
        this.exposed = exposed;
        this.port = port;
        this.path = name;
        this.certOverrideSpec = certOverrideSpec;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isTls() {
        return tls;
    }

    public boolean isExposed() {
        return exposed;
    }

    public int getPort() {
        return port;
    }

    public Optional<CertAndKeySecretSource> getCertOverride(EventStreamsSpec spec) {
        return certOverrideSpec.apply(spec);
    }

    public static Listener externalTls() {
        Function<EventStreamsSpec, Optional<CertAndKeySecretSource>> certOverride = spec -> Optional.of(spec)
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getListeners)
                .map(KafkaListeners::getExternal)
                .map(KafkaListenerExternalRoute.class::cast)
                .map(KafkaListenerExternalRoute::getConfiguration)
                .map(KafkaListenerExternalConfiguration::getBrokerCertChainAndKey);
        return new Listener(EXTERNAL_TLS_NAME, true, true, EXTERNAL_TLS_PORT, certOverride);
    }

    public static Listener externalPlain() {
        Function<EventStreamsSpec, Optional<CertAndKeySecretSource>> certOverride = spec -> Optional.empty();
        return new Listener(EXTERNAL_PLAIN_NAME, false, true, EXTERNAL_PLAIN_PORT, certOverride);
    }

    public static Listener internalTls() {
        Function<EventStreamsSpec, Optional<CertAndKeySecretSource>> certOverride = spec -> Optional.of(spec)
                .map(EventStreamsSpec::getStrimziOverrides)
                .map(KafkaSpec::getKafka)
                .map(KafkaClusterSpec::getListeners)
                .map(KafkaListeners::getTls)
                .map(KafkaListenerTls::getConfiguration)
                .map(TlsListenerConfiguration::getBrokerCertChainAndKey);
        return new Listener(INTERNAL_TLS_NAME, true, false, INTERNAL_TLS_PORT, certOverride);
    }

    public static Listener internalPlain() {
        Function<EventStreamsSpec, Optional<CertAndKeySecretSource>> certOverride = spec -> Optional.empty();
        return new Listener(INTERNAL_PLAIN_NAME, false, false, INTERNAL_PLAIN_PORT, certOverride);
    }

    // Define pod to pod listener, should not be added to the listener list
    private static Listener podToPodTls() {
        Function<EventStreamsSpec, Optional<CertAndKeySecretSource>> certOverride = spec -> Optional.empty();
        Listener p2p = new Listener(POD_TO_POD_TLS_NAME, true, false, POD_TO_POD_TLS_PORT, certOverride);
        p2p.setPath(POD_TO_POD_TLS_PATH);
        return p2p;
    }

    // Define pod to pod listener, should not be added to the listener list
    private static Listener podToPodPlain() {
        Function<EventStreamsSpec, Optional<CertAndKeySecretSource>> certOverride = spec -> Optional.empty();
        Listener p2p = new Listener(POD_TO_POD_PLAIN_NAME, false, false, POD_TO_POD_PLAIN_PORT, certOverride);
        return p2p;
    }

    public static Listener podToPodListener(boolean tlsEnabled) {
        return tlsEnabled ? Listener.podToPodTls() : Listener.podToPodPlain();
    }

    public static void setEnabledListeners(List<Listener> listeners) {
        enabledListeners = new ArrayList<>(listeners);
    }

    // Make this configurable in the CR
    public static List<Listener> enabledListeners() {
        return new ArrayList<>(enabledListeners);
    }

    public static String createEndpointsString(List<Listener> listeners) {
        return listeners.stream()
            .map(listener -> {
                if (listener.isTls()) {
                    return String.format("%d:%s", listener.getPort(), listener.getPath());
                }
                return String.format("%d", listener.getPort());
            })
            .collect(Collectors.joining(","));
    }

    public static String createAuthorizationString(List<Listener> listeners) {
        return listeners.stream()
            .map(listener -> {
                return String.format("%d", listener.getPort());
            })
            .collect(Collectors.joining(","));
    }

    private static List<Listener> getDefaultListeners() {
        List<Listener> enabledListeners = new ArrayList<>();
        enabledListeners.add(Listener.externalTls());
        enabledListeners.add(Listener.internalTls());
        enabledListeners.add(Listener.externalPlain());
        return enabledListeners;
    }
}
