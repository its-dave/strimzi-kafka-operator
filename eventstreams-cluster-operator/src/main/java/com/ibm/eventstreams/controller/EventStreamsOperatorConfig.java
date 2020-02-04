/*
 * IBM Confidential
 * OCO Source Materials
 *
 * 5737-H33
 *
 * (C) Copyright IBM Corp. 2019  All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 */
package com.ibm.eventstreams.controller;

import io.strimzi.operator.common.InvalidConfigurationException;
import io.strimzi.operator.common.operator.resource.AbstractWatchableResourceOperator;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;


public class EventStreamsOperatorConfig {
    // The key of Env variable to define the list of namespaces that EventStreams operator will be watching
    public static final String EVENTSTREAMS_NAMESPACE = "EVENTSTREAMS_NAMESPACE";

    public static final String EVENTSTREAMS_KAFKA_STATUS_READY_TIMEOUT_MS = "EVENTSTREAMS_KAFKA_STATUS_READY_TIMEOUT_MS";
    public static final String EVENTSTREAMS_FULL_RECONCILIATION_INTERVAL_MS = "EVENTSTREAMS_FULL_RECONCILIATION_INTERVAL_MS";

    public static final String EVENTSTREAMS_DEFAULT_REST_PRODUCER_IMAGE = "EVENTSTREAMS_DEFAULT_REST_PRODUCER_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_ADMIN_PROXY_IMAGE = "EVENTSTREAMS_DEFAULT_ADMIN_PROXY_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_ADMIN_API_IMAGE = "EVENTSTREAMS_DEFAULT_ADMIN_API_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_IMAGE = "EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_AVRO_IMAGE = "EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_AVRO_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_ADMIN_UI_IMAGE = "EVENTSTREAMS_DEFAULT_ADMIN_UI_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_ADMIN_UI_REDIS_IMAGE = "EVENTSTREAMS_DEFAULT_ADMIN_UI_REDIS_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_COLLECTOR_IMAGE = "EVENTSTREAMS_DEFAULT_COLLECTOR_IMAGE";
    public static final String EVENTSTREAMS_IMAGE_PULL_POLICY = "EVENTSTREAMS_IMAGE_PULL_POLICY";

    public static final long DEFAULT_KAFKA_STATUS_READY_TIMEOUT_MS = 600_000;
    public static final long DEFAULT_FULL_RECONCILIATION_INTERVAL_MS = 120_000;
    public static final String DEFAULT_COLLECTOR_IMAGE = "";

    // The list of namespaces that the operator is watching, retrieved from the EVENTSTREAMS_NAMESPACE env variable.
    private final Set<String> namespaces;
    private final long kafkaStatusReadyTimeoutMilliSecs;
    private final long reconciliationIntervalMilliSecs;
    private final ImageLookup images;

    public EventStreamsOperatorConfig(Set<String> namespaces, long kafkaStatusReadyTimeoutMs, long reconciliationIntervalMs, ImageLookup images)  {
        this.namespaces = unmodifiableSet(new HashSet<>(namespaces));
        this.kafkaStatusReadyTimeoutMilliSecs = kafkaStatusReadyTimeoutMs;
        this.reconciliationIntervalMilliSecs = reconciliationIntervalMs;
        this.images = images;
    }


    public static EventStreamsOperatorConfig fromMap(Map<String, String> map) {
        Set<String> namespaces = parseNamespaceList(map.getOrDefault(EventStreamsOperatorConfig.EVENTSTREAMS_NAMESPACE, ""));
        long kafkaStatusTimeout = parseKafkaStatusTimeout(map.get(EventStreamsOperatorConfig.EVENTSTREAMS_KAFKA_STATUS_READY_TIMEOUT_MS));
        long reconciliationInterval = parseReconciliationInterval(map.get(EventStreamsOperatorConfig.EVENTSTREAMS_FULL_RECONCILIATION_INTERVAL_MS));
        ImageLookup images = parseImageList(map);
        return new EventStreamsOperatorConfig(namespaces, kafkaStatusTimeout, reconciliationInterval, images);
    }

    private static ImageLookup parseImageList(Map<String, String> map) {
        Map<String, String> images = map.entrySet()
            .stream().filter(entry -> entry.getKey().matches("EVENTSTREAMS.*IMAGE"))
            .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
        return new ImageLookup(images, map.get(EVENTSTREAMS_IMAGE_PULL_POLICY));
    }

    private static Set<String> parseNamespaceList(String namespaces) {
        String checkRegex = "(\\s*[a-z0-9.-]+\\s*,)*\\s*[a-z0-9.-]+\\s*";
        String splitRegex = "\\s*,+\\s*";
        String trimmedNamespaces = namespaces.trim();
        Set<String> namespaceList;

        if (trimmedNamespaces.isEmpty() || (trimmedNamespaces.equals(AbstractWatchableResourceOperator.ANY_NAMESPACE))) {
            namespaceList = Collections.singleton(AbstractWatchableResourceOperator.ANY_NAMESPACE);
        } else if (trimmedNamespaces.matches(checkRegex)) {
            namespaceList = new HashSet<>(asList(trimmedNamespaces.split(splitRegex)));
        } else {
            throw new InvalidConfigurationException(EventStreamsOperatorConfig.EVENTSTREAMS_NAMESPACE + " is invalid namespace");
        }
        return namespaceList;
    }

    private static long parseKafkaStatusTimeout(String kafkaStatusReadyTimeoutEnvVar) {
        long kafkaStatusReadyTimeout = kafkaStatusReadyTimeoutEnvVar != null ? Long.parseLong(kafkaStatusReadyTimeoutEnvVar) : DEFAULT_KAFKA_STATUS_READY_TIMEOUT_MS;
        return kafkaStatusReadyTimeout;
    }

    private static long parseReconciliationInterval(String reconciliationIntervalEnvVar) {
        long reconciliationInterval = reconciliationIntervalEnvVar != null ?  Long.parseLong(reconciliationIntervalEnvVar) : DEFAULT_FULL_RECONCILIATION_INTERVAL_MS;
        return reconciliationInterval;
    }

    public Set<String> getNamespaces() {
        return namespaces;
    }

    public long getKafkaStatusReadyTimeoutMs() {
        return kafkaStatusReadyTimeoutMilliSecs;
    }

    public long getReconciliationIntervalMilliSecs() {
        return reconciliationIntervalMilliSecs;
    }

    public ImageLookup getImages() {
        return images;
    }

    public static class ImageLookup {
        private final String pullPolicy;
        private final Map<String, String> images;

        protected ImageLookup(Map<String, String> images, String pullPolicy) {
            this.images = images;
            this.pullPolicy = pullPolicy;
        }

        public Optional<String> getPullPolicy() {
            return Optional.ofNullable(pullPolicy);
        }

        public Optional<String> getAdminApiImage() {
            return Optional.ofNullable(images.get(EVENTSTREAMS_DEFAULT_ADMIN_API_IMAGE));
        }

        public Optional<String> getAdminProxyImage() {
            return Optional.ofNullable(images.get(EVENTSTREAMS_DEFAULT_ADMIN_PROXY_IMAGE));
        }

        public Optional<String> getAdminUIImage() {
            return Optional.ofNullable(images.get(EVENTSTREAMS_DEFAULT_ADMIN_UI_IMAGE));
        }

        public Optional<String> getAdminUIRedisImage() {
            return Optional.ofNullable(images.get(EVENTSTREAMS_DEFAULT_ADMIN_UI_REDIS_IMAGE));
        }

        public Optional<String> getCollectorImage() {
            return Optional.ofNullable(images.get(EVENTSTREAMS_DEFAULT_COLLECTOR_IMAGE));
        }

        public Optional<String> getSchemaRegistryImage() {
            return Optional.ofNullable(images.get(EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_IMAGE));
        }

        public Optional<String> getSchemaRegistryAvroImage() {
            return Optional.ofNullable(images.get(EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_AVRO_IMAGE));
        }

        public Optional<String> getRestProducerImage() {
            return Optional.ofNullable(images.get(EVENTSTREAMS_DEFAULT_REST_PRODUCER_IMAGE));
        }
    }

}