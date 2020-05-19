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
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicator;
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

public class EventStreamsGeoReplicatorVerticle extends AbstractVerticle {

    private static final Logger log = LogManager.getLogger(EventStreamsGeoReplicatorVerticle.class);
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
    private Watch eventStreamsGeoReplicatorCRWatcher;
    private long reconcileTimer;

    public EventStreamsGeoReplicatorVerticle(Vertx vertx, KubernetesClient client, String namespace, MetricsProvider metricsProvider, PlatformFeaturesAvailability pfa, EventStreamsOperatorConfig config) {
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
            log.info("EventStreamsGeoReplicatorVerticle for namespace {} started." + this.namespace);
            KubernetesDeserializer.registerCustomKind(EventStreamsGeoReplicator.RESOURCE_GROUP + "/" + EventStreamsGeoReplicator.V1BETA1, EventStreamsGeoReplicator.RESOURCE_KIND, EventStreamsGeoReplicator.class);

            EventStreamsGeoReplicatorResourceOperator geoReplicatorResourceOperator = new EventStreamsGeoReplicatorResourceOperator(vertx, client, EventStreamsGeoReplicator.RESOURCE_KIND);
            EventStreamsResourceOperator esResourceOperator = new EventStreamsResourceOperator(vertx, client);
            EventStreamsGeoReplicatorOperator eventStreamsGeoReplicatorOperator = new EventStreamsGeoReplicatorOperator(
                    vertx,
                    client,
                    EventStreams.RESOURCE_KIND,
                    pfa,
                    geoReplicatorResourceOperator,
                    esResourceOperator,
                    routeOperator,
                    metricsProvider,
                    replicatorStatusReadyTimeoutMilliSecs);

            eventStreamsGeoReplicatorOperator.createWatch(namespace, eventStreamsGeoReplicatorOperator.recreateWatch(namespace))
                    .compose(w -> {
                        log.info("Started operator for EventStreamsGeoReplicator kind.");
                        eventStreamsGeoReplicatorCRWatcher = w;
                        log.info("Setting up periodic reconciliation for geo-replicator for namespace {}" + namespace);
                        reconcileTimer = vertx.setPeriodic(reconciliationIntervalMilliSecs, handler -> {
                            Handler<AsyncResult<Void>> asyncHandler = ignoredHandler -> { };
                            log.info("Triggering periodic reconciliation for geo-replicator for namespace {}..." + namespace);
                            eventStreamsGeoReplicatorOperator.reconcileAll("timer", namespace, asyncHandler);
                        });
                        return Future.<Void>succeededFuture();
                    })
                    .setHandler(start);
        } catch (Exception e) {
            log.error("Failed to start EventStreamsGeoReplicatorVerticle", e);
        }
    }

    @Override
    public void stop(Promise<Void> stop) {
        log.info("Stopping EventStreamsGeoReplicatorVerticle for namespace {}", namespace);
        vertx.cancelTimer(reconcileTimer);
        if (eventStreamsGeoReplicatorCRWatcher != null) {
            eventStreamsGeoReplicatorCRWatcher.close();
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
