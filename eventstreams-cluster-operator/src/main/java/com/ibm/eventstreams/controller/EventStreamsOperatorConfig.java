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
package com.ibm.eventstreams.controller;

import com.ibm.commonservices.CommonServices;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.strimzi.operator.cluster.ClusterOperatorConfig;
import io.strimzi.operator.common.InvalidConfigurationException;
import io.strimzi.operator.common.operator.resource.AbstractWatchableResourceOperator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;


public class EventStreamsOperatorConfig {
    // The key of Env variable to define the list of namespaces that EventStreams operator will be watching
    public static final String EVENTSTREAMS_NAMESPACE = "EVENTSTREAMS_NAMESPACE";

    public static final String EVENTSTREAMS_OPERATOR_NAMESPACE = "EVENTSTREAMS_OPERATOR_NAMESPACE";

    public static final String EVENTSTREAMS_KAFKA_STATUS_READY_TIMEOUT_MS = "EVENTSTREAMS_KAFKA_STATUS_READY_TIMEOUT_MS";
    public static final String EVENTSTREAMS_FULL_RECONCILIATION_INTERVAL_MS = "EVENTSTREAMS_FULL_RECONCILIATION_INTERVAL_MS";
    public static final String EVENTSTREAMS_OPERATION_TIMEOUT_MS = "EVENTSTREAMS_OPERATION_TIMEOUT_MS";
    public static final String EVENTSTREAMS_DEPENDENCY_STATUS_CHECKS = "EVENTSTREAMS_DEPENDENCY_STATUS_CHECKS";

    public static final String EVENTSTREAMS_DEFAULT_REST_PRODUCER_IMAGE = "EVENTSTREAMS_DEFAULT_REST_PRODUCER_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_ADMIN_API_IMAGE = "EVENTSTREAMS_DEFAULT_ADMIN_API_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_IMAGE = "EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_AVRO_IMAGE = "EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_AVRO_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_PROXY_IMAGE = "EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_PROXY_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_ADMIN_UI_IMAGE = "EVENTSTREAMS_DEFAULT_ADMIN_UI_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_ADMIN_UI_REDIS_IMAGE = "EVENTSTREAMS_DEFAULT_ADMIN_UI_REDIS_IMAGE";
    public static final String EVENTSTREAMS_DEFAULT_COLLECTOR_IMAGE = "EVENTSTREAMS_DEFAULT_COLLECTOR_IMAGE";
    public static final String EVENTSTREAMS_IMAGE_PULL_POLICY = "EVENTSTREAMS_IMAGE_PULL_POLICY";

    public static final long DEFAULT_KAFKA_STATUS_READY_TIMEOUT_MS = 600_000;
    public static final long DEFAULT_FULL_RECONCILIATION_INTERVAL_MS = 120_000;
    public static final long DEFAULT_OPERATION_TIMEOUT_MS = 300_000;
    public static final List<String> DEFAULT_DEPENDENCY_STATUS_CHECKS = Collections.singletonList(CommonServices.COMMON_SERVICES_STATUS_IAM);

    // The list of namespaces that the operator is watching, retrieved from the EVENTSTREAMS_NAMESPACE env variable.
    private final Set<String> namespaces;
    private final String operatorNamespace;
    private final long kafkaStatusReadyTimeoutMilliSecs;
    private final long reconciliationIntervalMilliSecs;
    private final long operationTimeoutMilliSecs;
    private final ImageLookup images;
    private final List<String> dependencyStatusChecks;

    public EventStreamsOperatorConfig(Set<String> namespaces, String operatorNamespace, long kafkaStatusReadyTimeoutMs, long reconciliationIntervalMs, long operationTimeoutMs, ImageLookup images, List<String> dependencyStausChecks)  {
        this.namespaces = unmodifiableSet(new HashSet<>(namespaces));
        this.operatorNamespace = operatorNamespace;
        this.kafkaStatusReadyTimeoutMilliSecs = kafkaStatusReadyTimeoutMs;
        this.reconciliationIntervalMilliSecs = reconciliationIntervalMs;
        this.operationTimeoutMilliSecs = operationTimeoutMs;
        this.images = images;
        this.dependencyStatusChecks = dependencyStausChecks;
    }


    public static EventStreamsOperatorConfig fromMap(Map<String, String> map) {
        Set<String> namespaces = parseNamespaceList(map.getOrDefault(EVENTSTREAMS_NAMESPACE, ""));
        String operatorNamespace = map.get(EVENTSTREAMS_OPERATOR_NAMESPACE);
        long kafkaStatusTimeout = parseKafkaStatusTimeout(map.get(EVENTSTREAMS_KAFKA_STATUS_READY_TIMEOUT_MS));
        long reconciliationInterval = parseReconciliationInterval(map.get(EVENTSTREAMS_FULL_RECONCILIATION_INTERVAL_MS));
        long operationTimeout = parseOperationTimeout(map.get(EVENTSTREAMS_OPERATION_TIMEOUT_MS));
        ImageLookup images = parseImageList(map);
        List<String> dependencyStatusChecks = parseDependencyStatusChecks(map.get(EVENTSTREAMS_DEPENDENCY_STATUS_CHECKS));
        return new EventStreamsOperatorConfig(namespaces, operatorNamespace, kafkaStatusTimeout, reconciliationInterval, operationTimeout, images, dependencyStatusChecks);
    }

    private static List<LocalObjectReference> parseImagePullSecrets(String imagePullSecretList) {
        List<LocalObjectReference> imagePullSecrets = new ArrayList<>();

        if (imagePullSecretList != null && !imagePullSecretList.isEmpty()) {
            if (imagePullSecretList.matches("(\\s*[a-z0-9.-]+\\s*,)*\\s*[a-z0-9.-]+\\s*")) {
                imagePullSecrets = Arrays.stream(imagePullSecretList.trim().split("\\s*,+\\s*")).map(secret -> new LocalObjectReferenceBuilder().withName(secret).build()).collect(Collectors.toList());
            } else {
                throw new InvalidConfigurationException(ClusterOperatorConfig.STRIMZI_IMAGE_PULL_SECRETS
                        + " is not a valid list of secret names");
            }
        }

        return imagePullSecrets;
    }

    private static ImageLookup parseImageList(Map<String, String> map) {
        Map<String, String> images = map.entrySet()
            .stream().filter(entry -> entry.getKey().matches("EVENTSTREAMS.*IMAGE"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<LocalObjectReference> imagePullSecrets = parseImagePullSecrets(map.get(ClusterOperatorConfig.STRIMZI_IMAGE_PULL_SECRETS));
        return new ImageLookup(images, map.get(EVENTSTREAMS_IMAGE_PULL_POLICY), imagePullSecrets);
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
        return kafkaStatusReadyTimeoutEnvVar != null ? Long.parseLong(kafkaStatusReadyTimeoutEnvVar) : DEFAULT_KAFKA_STATUS_READY_TIMEOUT_MS;
    }

    private static long parseReconciliationInterval(String reconciliationIntervalEnvVar) {
        return reconciliationIntervalEnvVar != null ?  Long.parseLong(reconciliationIntervalEnvVar) : DEFAULT_FULL_RECONCILIATION_INTERVAL_MS;
    }

    private static long parseOperationTimeout(String operationTimeoutEnvVar) {
        return operationTimeoutEnvVar != null ?  Long.parseLong(operationTimeoutEnvVar) : DEFAULT_OPERATION_TIMEOUT_MS;
    }

    private static List<String> parseDependencyStatusChecks(String dependencyStatusChecksEnvVar) {
        return Optional.ofNullable(dependencyStatusChecksEnvVar)
            .map(value -> value.isEmpty() ? Collections.<String>emptyList() : asList(value.split(",")))
            .orElse(DEFAULT_DEPENDENCY_STATUS_CHECKS);
    }

    public Set<String> getNamespaces() {
        return namespaces;
    }

    public String getOperatorNamespace() {
        return operatorNamespace;
    }

    public long getKafkaStatusReadyTimeoutMs() {
        return kafkaStatusReadyTimeoutMilliSecs;
    }

    public long getReconciliationIntervalMilliSecs() {
        return reconciliationIntervalMilliSecs;
    }

    public long getOperationTimeoutMilliSecs() {
        return operationTimeoutMilliSecs;
    }

    public ImageLookup getImages() {
        return images;
    }

    public List<String> getDependencyStatusChecks() {
        return dependencyStatusChecks;
    }

    public static class ImageLookup {
        private final String pullPolicy;
        private final List<LocalObjectReference> pullSecrets;
        private final Map<String, String> images;

        protected ImageLookup(Map<String, String> images, String pullPolicy, List<LocalObjectReference> pullSecrets) {
            this.images = images;
            this.pullPolicy = pullPolicy;
            this.pullSecrets = pullSecrets;
        }

        public Optional<String> getPullPolicy() {
            return Optional.ofNullable(pullPolicy);
        }

        public List<LocalObjectReference> getPullSecrets() {
            return pullSecrets;
        }

        public Optional<String> getAdminApiImage() {
            return Optional.ofNullable(images.get(EVENTSTREAMS_DEFAULT_ADMIN_API_IMAGE));
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

        public Optional<String> getSchemaRegistryProxyImage() {
            return Optional.ofNullable(images.get(EVENTSTREAMS_DEFAULT_SCHEMA_REGISTRY_PROXY_IMAGE));
        }

        public Optional<String> getRestProducerImage() {
            return Optional.ofNullable(images.get(EVENTSTREAMS_DEFAULT_REST_PRODUCER_IMAGE));
        }
    }

}