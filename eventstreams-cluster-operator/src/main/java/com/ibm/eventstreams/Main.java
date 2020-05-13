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
package com.ibm.eventstreams;

import com.ibm.eventstreams.controller.EventStreamsReplicatorVerticle;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.strimzi.api.kafka.Crds;
import io.strimzi.certs.OpenSslCertManager;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.strimzi.operator.cluster.operator.assembly.KafkaAssemblyOperator;
import io.strimzi.operator.cluster.operator.assembly.KafkaConnectAssemblyOperator;
import io.strimzi.operator.cluster.operator.assembly.KafkaConnectS2IAssemblyOperator;
import io.strimzi.operator.cluster.operator.assembly.KafkaMirrorMaker2AssemblyOperator;
import io.strimzi.operator.cluster.operator.resource.ResourceOperatorSupplier;
import io.strimzi.operator.common.MetricsProvider;
import io.strimzi.operator.common.PasswordGenerator;
import io.strimzi.operator.common.operator.resource.ClusterRoleOperator;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.vertx.core.VertxOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;

import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import com.ibm.eventstreams.controller.EventStreamsVerticle;
import java.util.Optional;

import io.strimzi.operator.cluster.ClusterOperatorConfig;
import io.strimzi.operator.cluster.ClusterOperator;

@SuppressFBWarnings(value = "DM_EXIT",
        justification = "System.exit is appropriate here as startup has failed.")
public class Main {

    private static final Logger log = LogManager.getLogger(Main.class.getName());

    public static final String CLUSTER_NAME = Optional.ofNullable(System.getenv("CLUSTER_NAME"))
            .orElse("cluster.local");

    static {
        try {
            Crds.registerCustomKinds();
        } catch (Error | RuntimeException t) {
            t.printStackTrace();
        }
    }

    public static void main(String[] args) {

        log.info("ClusterOperator {} is starting", Main.class.getPackage().getImplementationVersion());
        ClusterOperatorConfig config = ClusterOperatorConfig.fromMap(System.getenv());

        //Setup Micrometer metrics options
        VertxOptions options = new VertxOptions().setMetricsOptions(
                new MicrometerMetricsOptions()
                        .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true))
                        .setEnabled(true));
        Vertx vertx = Vertx.vertx(options);
        KubernetesClient client = new DefaultKubernetesClient();

        maybeCreateClusterRoles(vertx, config, client).setHandler(crs -> {
            if (crs.succeeded())    {
                PlatformFeaturesAvailability.create(vertx, client).setHandler(pfa -> {
                    if (pfa.succeeded()) {
                        PlatformFeaturesAvailability pfaObj = pfa.result();

                        log.info("Environment facts gathered: {}", pfaObj);

                        ResourceOperatorSupplier resourceOperatorSupplier = new ResourceOperatorSupplier(vertx, client, pfaObj, config.getOperationTimeoutMs());

                        run(vertx, client, resourceOperatorSupplier, pfaObj, config).setHandler(ar -> {
                            if (ar.failed()) {
                                log.error("Unable to start operator for 1 or more namespace", ar.cause());
                                System.exit(1);
                            }
                        });

                        EventStreamsOperatorConfig eventStreamsConfig = EventStreamsOperatorConfig.fromMap(System.getenv());

                        runEventStreams(vertx, client, resourceOperatorSupplier.metricsProvider, pfaObj, eventStreamsConfig).setHandler(asyncResult -> {
                            if (asyncResult.failed()) {
                                log.error("Failed to start EventStreams operator for 1 or more namespace", asyncResult.cause());
                                System.exit(1);
                            }
                        });
                    } else {
                        log.error("Failed to gather environment facts", pfa.cause());
                        System.exit(1);
                    }
                });
            } else  {
                log.error("Failed to create Cluster Roles", crs.cause());
                System.exit(1);
            }
        });
    }


    static CompositeFuture runEventStreams(Vertx vertx, KubernetesClient client, MetricsProvider metricsProvider, PlatformFeaturesAvailability pfa, EventStreamsOperatorConfig config) {
        List<Future> futures = new ArrayList<>();

        logOperatorEnvVars();
        for (String namespace : config.getNamespaces()) {
            Promise<String> promise = Promise.promise();
            Promise<String> replicatorPromise = Promise.promise();
            futures.add(promise.future());
            futures.add(replicatorPromise.future());

            EventStreamsVerticle eventStreamsVerticle = new EventStreamsVerticle(vertx,
                client,
                namespace,
                metricsProvider,
                pfa,
                config);

            vertx.deployVerticle(eventStreamsVerticle,
                result -> {
                    if (result.succeeded()) {
                        log.info("EventStreams Operator verticle started in namespace {}", namespace);
                    } else {
                        log.error("EventStreams Operator verticle in namespace {} failed to start", namespace, result.cause());
                        System.exit(1);
                    }
                    promise.handle(result);
                });

            EventStreamsReplicatorVerticle eventSteamsReplicatorVerticle = new EventStreamsReplicatorVerticle(vertx,
                    client,
                    namespace,
                    metricsProvider,
                    pfa,
                    config);

            vertx.deployVerticle(eventSteamsReplicatorVerticle,
                result -> {
                    if (result.succeeded()) {
                        log.info("EventStreams geo-replicator operator verticle started in namespace {}", namespace);
                    } else {
                        log.error("EventStreams geo-replicator operator verticle in namespace {} failed to start", namespace, result.cause());
                        System.exit(1);
                    }
                    replicatorPromise.handle(result);
                });
        }
        return CompositeFuture.join(futures);
    }

    static void logOperatorEnvVars() {
        StringBuffer configString = new StringBuffer();
        Map<String, String> envList = new HashMap<>(System.getenv());

        for (Map.Entry<String, String> entry: envList.entrySet()) {
            configString.append("\t").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        log.info("EventStreams operator env vars:\n" + configString.toString());
    }


    static CompositeFuture run(Vertx vertx, KubernetesClient client, ResourceOperatorSupplier resourceOperatorSupplier, PlatformFeaturesAvailability pfa, ClusterOperatorConfig config) {
        printEnvInfo();

        // ResourceOperatorSupplier resourceOperatorSupplier = new ResourceOperatorSupplier(vertx, client, pfa, config.getOperationTimeoutMs());

        OpenSslCertManager certManager = new OpenSslCertManager();
        PasswordGenerator passwordGenerator = new PasswordGenerator(12,
                "abcdefghijklmnopqrstuvwxyz" +
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                "abcdefghijklmnopqrstuvwxyz" +
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                        "0123456789");
        KafkaAssemblyOperator kafkaClusterOperations = new KafkaAssemblyOperator(vertx, pfa,
                certManager, passwordGenerator, resourceOperatorSupplier, config);
        KafkaConnectAssemblyOperator kafkaConnectClusterOperations = new KafkaConnectAssemblyOperator(vertx, pfa,
                resourceOperatorSupplier, config);

        KafkaConnectS2IAssemblyOperator kafkaConnectS2IClusterOperations = null;
        if (pfa.supportsS2I()) {
            kafkaConnectS2IClusterOperations = new KafkaConnectS2IAssemblyOperator(vertx, pfa, resourceOperatorSupplier, config);
        } else {
            log.info("The KafkaConnectS2I custom resource definition can only be used in environment which supports OpenShift build, image and apps APIs. These APIs do not seem to be supported in this environment.");
        }

        KafkaMirrorMaker2AssemblyOperator kafkaMirrorMaker2AssemblyOperator =
                new KafkaMirrorMaker2AssemblyOperator(vertx, pfa, resourceOperatorSupplier, config);

        List<Future> futures = new ArrayList<>();
        for (String namespace : config.getNamespaces()) {
            Promise<String> prom = Promise.promise();
            futures.add(prom.future());
            ClusterOperator operator = new ClusterOperator(namespace,
                    config.getReconciliationIntervalMs(),
                    client,
                    kafkaClusterOperations,
                    kafkaConnectClusterOperations,
                    kafkaConnectS2IClusterOperations,
                    null,
                    kafkaMirrorMaker2AssemblyOperator,
                    null,
                    resourceOperatorSupplier.metricsProvider);
            vertx.deployVerticle(operator,
                res -> {
                    if (res.succeeded()) {
                        log.info("Cluster Operator verticle started in namespace {}", namespace);
                    } else {
                        log.error("Cluster Operator verticle in namespace {} failed to start", namespace, res.cause());
                        System.exit(1);
                    }
                    prom.handle(res);
                });
        }
        return CompositeFuture.join(futures);
    }

    /*test*/ static Future<Void> maybeCreateClusterRoles(Vertx vertx, ClusterOperatorConfig config, KubernetesClient client)  {
        if (config.isCreateClusterRoles()) {
            List<Future> futures = new ArrayList<>();
            ClusterRoleOperator cro = new ClusterRoleOperator(vertx, client, config.getOperationTimeoutMs());

            Map<String, String> clusterRoles = new HashMap<String, String>() {
                {
                    put("strimzi-cluster-operator-namespaced", "020-ClusterRole-strimzi-cluster-operator-role.yaml");
                    put("strimzi-cluster-operator-global", "021-ClusterRole-strimzi-cluster-operator-role.yaml");
                    put("strimzi-kafka-broker", "030-ClusterRole-strimzi-kafka-broker.yaml");
                    put("strimzi-entity-operator", "031-ClusterRole-strimzi-entity-operator.yaml");
                    put("strimzi-topic-operator", "032-ClusterRole-strimzi-topic-operator.yaml");
                }
            };

            for (Map.Entry<String, String> clusterRole : clusterRoles.entrySet()) {
                log.info("Creating cluster role {}", clusterRole.getKey());

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(Main.class.getResourceAsStream("/cluster-roles/" + clusterRole.getValue()),
                                StandardCharsets.UTF_8))) {
                    String yaml = br.lines().collect(Collectors.joining(System.lineSeparator()));
                    ClusterRole role = cro.convertYamlToClusterRole(yaml);
                    Future fut = cro.reconcile(role.getMetadata().getName(), role);
                    futures.add(fut);
                } catch (IOException e) {
                    log.error("Failed to create Cluster Roles.", e);
                    throw new RuntimeException(e);
                }

            }

            Promise<Void> returnPromise = Promise.promise();
            CompositeFuture.all(futures).setHandler(res -> {
                if (res.succeeded())    {
                    returnPromise.complete();
                } else  {
                    returnPromise.fail("Failed to create Cluster Roles.");
                }
            });

            return returnPromise.future();
        } else {
            return Future.succeededFuture();
        }
    }

    static void printEnvInfo() {
        Map<String, String> m = new HashMap<>(System.getenv());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry: m.entrySet()) {
            sb.append("\t").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        log.info("Using config:\n" + sb.toString());
    }
}


