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

import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsReplicator;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import io.fabric8.openshift.client.OpenShiftClient;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.strimzi.operator.common.MetricsProvider;
import io.strimzi.operator.common.operator.resource.RouteOperator;
import io.vertx.core.AsyncResult;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.Handler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EventStreamsReplicatorVerticle extends AbstractVerticle {

    private static final Logger log = LogManager.getLogger(EventStreamsReplicatorVerticle.class);
    private final EventStreamsOperatorConfig.ImageLookup imageConfig;
    private RouteOperator routeOperator;
    private final MetricsProvider metricsProvider;

    private KubernetesClient client;
    private Vertx vertx;

    private final String namespace;
    private PlatformFeaturesAvailability pfa;

    public static final int API_SERVER_PORT = 8081;
    private HttpServer httpServer;
    private static final String API_SSL_CERT_PATH = "/etc/eventstreams/tls.crt";
    private static final String API_SSL_KEY_PATH = "/etc/eventstreams/tls.key";

    private final long replicatorStatusReadyTimeoutMilliSecs;
    private final long reconciliationIntervalMilliSecs;
    private Watch eventStreamsReplicatorCRWatcher;
    private long reconcileTimer;

    public EventStreamsReplicatorVerticle(Vertx vertx, KubernetesClient client, String namespace, MetricsProvider metricsProvider, PlatformFeaturesAvailability pfa, EventStreamsOperatorConfig config) {
        log.info("Creating EventStreamsVerticle for namespace {}", namespace);
        this.vertx = vertx;
        this.client = client;
        this.pfa = pfa;
        this.namespace = namespace;
        this.replicatorStatusReadyTimeoutMilliSecs = config.getKafkaStatusReadyTimeoutMs();
        this.reconciliationIntervalMilliSecs = config.getReconciliationIntervalMilliSecs();
        this.imageConfig = config.getImages();
        this.routeOperator = pfa.hasRoutes() ? new RouteOperator(vertx, client.adapt(OpenShiftClient.class)) : null;
        this.metricsProvider = metricsProvider;
    }

    @Override
    public void start(Promise<Void> start) {
        try {
            log.info("EventStreamsReplicatorVerticle for namespace {} started." + this.namespace);
            KubernetesDeserializer.registerCustomKind(EventStreamsReplicator.RESOURCE_GROUP + "/" + EventStreamsReplicator.V1BETA1, EventStreamsReplicator.RESOURCE_KIND, EventStreamsReplicator.class);

            EventStreamsReplicatorResourceOperator replicatorResourceOperator = new EventStreamsReplicatorResourceOperator(vertx, client, EventStreamsReplicator.RESOURCE_KIND);
            EventStreamsResourceOperator esResourceOperator = new EventStreamsResourceOperator(vertx, client);
            EventStreamsReplicatorOperator eventStreamsReplicatorOperator = new EventStreamsReplicatorOperator(
                    vertx,
                    client,
                    EventStreams.RESOURCE_KIND,
                    pfa,
                    replicatorResourceOperator,
                    esResourceOperator,
                    routeOperator,
                    metricsProvider,
                    replicatorStatusReadyTimeoutMilliSecs);

            eventStreamsReplicatorOperator.createWatch(namespace, eventStreamsReplicatorOperator.recreateWatch(namespace))
                    .compose(w -> {
                        log.info("Started operator for EventStreamsReplicator kind.");
                        eventStreamsReplicatorCRWatcher = w;
                        log.info("Setting up periodic reconciliation for Replicator for namespace {}" + namespace);
                        reconcileTimer = vertx.setPeriodic(reconciliationIntervalMilliSecs, handler -> {
                            Handler<AsyncResult<Void>> asyncHandler = ignoredHandler -> { };
                            log.info("Triggering periodic reconciliation for Replicator for namespace {}..." + namespace);
                            eventStreamsReplicatorOperator.reconcileAll("timer", namespace, asyncHandler);
                        });
                        return Future.<Void>succeededFuture();
                    })
                    .setHandler(start);
        } catch (Exception e) {
            log.error("Failed to start EventStreamsReplicatorVerticle", e);
        }
    }

    @Override
    public void stop(Promise<Void> stop) {
        log.info("Stopping EventStreamsReplicatorVerticle for namespace {}", namespace);
        vertx.cancelTimer(reconcileTimer);
        if (eventStreamsReplicatorCRWatcher != null) {
            eventStreamsReplicatorCRWatcher.close();
        }
        if (client != null) {
            client.close();
        }
        if (httpServer != null) {
            httpServer.close();
        }
        stop.complete();
    }

}
