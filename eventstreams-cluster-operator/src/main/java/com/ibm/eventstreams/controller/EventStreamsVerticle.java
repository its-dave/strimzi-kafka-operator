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
import com.ibm.eventstreams.rest.KubernetesProbe;
import com.ibm.eventstreams.rest.NameValidation;
import com.ibm.eventstreams.rest.VersionValidation;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import io.fabric8.openshift.client.OpenShiftClient;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.strimzi.operator.common.operator.resource.RouteOperator;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class EventStreamsVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(EventStreamsVerticle.class);
    private final EventStreamsOperatorConfig.ImageLookup imageConfig;
    private RouteOperator routeOperator;

    private KubernetesClient client;
    private Vertx vertx;

    private final String namespace;
    private PlatformFeaturesAvailability pfa;

    public static final int API_SERVER_PORT = 8080;
    private HttpServer httpServer;
    private static final String API_SSL_CERT_PATH = "/etc/eventstreams/tls.crt";
    private static final String API_SSL_KEY_PATH = "/etc/eventstreams/tls.key";

    private final long kafkaStatusReadyTimeoutMilliSecs;
    private final long reconciliationIntervalMilliSecs;
    private Watch eventStreamsCRWatcher;
    private long reconcileTimer;

    public EventStreamsVerticle(Vertx vertx, KubernetesClient client, String namespace, PlatformFeaturesAvailability pfa, EventStreamsOperatorConfig config) {
        log.info("Creating EventStreamsVerticle for namespace {}", namespace);
        this.vertx = vertx;
        this.client = client;
        this.pfa = pfa;
        this.namespace = namespace;
        this.kafkaStatusReadyTimeoutMilliSecs = config.getKafkaStatusReadyTimeoutMs();
        this.reconciliationIntervalMilliSecs = config.getReconciliationIntervalMilliSecs();
        this.imageConfig = config.getImages();
        this.routeOperator = pfa.hasRoutes() ? new RouteOperator(vertx, client.adapt(OpenShiftClient.class)) : null;
    }

    @Override
    public void start(Future<Void> start) {
        try {
            log.info("EventStreamsVerticle for namespace {} started.", this.namespace);
            KubernetesDeserializer.registerCustomKind(EventStreams.RESOURCE_GROUP + "/" + EventStreams.V1BETA1, EventStreams.RESOURCE_KIND, EventStreams.class);

            EventStreamsResourceOperator resourceOperator = new EventStreamsResourceOperator(vertx, client, EventStreams.RESOURCE_KIND);
            EventStreamsOperator eventStreamsOperator = new EventStreamsOperator(vertx, client, EventStreams.RESOURCE_KIND, pfa, resourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMilliSecs);
            eventStreamsOperator.createWatch(namespace, eventStreamsOperator.recreateWatch(namespace))
                    .compose(w -> {
                        log.info("Started operator for EventStreams kind.");
                        eventStreamsCRWatcher = w;
                        log.info("Setting up periodic reconciliation for namespace {}", namespace);
                        reconcileTimer = vertx.setPeriodic(reconciliationIntervalMilliSecs, handler -> {
                            Handler<AsyncResult<Void>> asyncHandler = ignoredHandler -> { };
                            log.info("Triggering periodic reconciliation for namespace {}...", namespace);
                            eventStreamsOperator.reconcileAll("timer", namespace, asyncHandler);
                        });
                        return startOperatorApiServer();
                    }).compose(start::complete, start);
        } catch (Exception e) {
            log.error("Failed to start EventStreamsVerticle", e);
        }
    }

    @Override
    public void stop(Future<Void> stop) {
        log.info("Stopping EventStreamsVerticle for namespace {}", namespace);
        vertx.cancelTimer(reconcileTimer);
        if (eventStreamsCRWatcher != null) {
            eventStreamsCRWatcher.close();
        }
        if (client != null) {
            client.close();
        }
        if (httpServer != null) {
            httpServer.close();
        }
        stop.complete();
    }

    private Future<Void> startOperatorApiServer() {
        log.debug("Starting API server");

        Future<Void> result = Future.future();

        HttpServerOptions serverOptions = new HttpServerOptions();
        if (new File(API_SSL_CERT_PATH).exists() && new File(API_SSL_KEY_PATH).exists()) {
            log.debug("Enabling SSL for operator HTTP API");
            serverOptions.setSsl(true);
            serverOptions.setPemKeyCertOptions(new PemKeyCertOptions()
                                                   .setCertPath(API_SSL_CERT_PATH)
                                                   .setKeyPath(API_SSL_KEY_PATH));
        }

        httpServer = vertx.createHttpServer(serverOptions);

        Router router = Router.router(vertx);
        router.route(HttpMethod.GET, "/liveness").handler(KubernetesProbe::handle);
        router.route(HttpMethod.GET, "/readiness").handler(KubernetesProbe::handle);

        BodyHandler bodyHandler = BodyHandler.create();
        bodyHandler.setHandleFileUploads(false);
        router.route().handler(bodyHandler);

        router.route(HttpMethod.POST, "/admissionwebhook/rejectlongnames").handler(NameValidation::rejectLongNames);
        router.route(HttpMethod.POST, "/admissionwebhook/rejectinvalidversions").handler(VersionValidation::rejectInvalidAppVersions);

        httpServer.requestHandler(router).listen(API_SERVER_PORT, asyncResult -> {
            if (asyncResult.succeeded()) {
                log.info("EventStreamsOperator API server is now listening on {})", API_SERVER_PORT);
                result.complete();
            } else {
                log.error("Failed to start API server on {}", API_SERVER_PORT, asyncResult.cause());
                result.fail(asyncResult.cause());
            }
        });

        return result;
    }

}
