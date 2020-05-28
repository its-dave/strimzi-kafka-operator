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

import com.ibm.commonservices.api.controller.Cp4iServicesBindingResourceOperator;
import com.ibm.commonservices.api.controller.OperandRequestResourceOperator;
import com.ibm.commonservices.api.spec.Cp4iServicesBinding;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicator;
import com.ibm.eventstreams.rest.KubernetesProbe;
import com.ibm.eventstreams.rest.eventstreams.EndpointValidation;
import com.ibm.eventstreams.rest.eventstreams.EntityLabelValidation;
import com.ibm.eventstreams.rest.eventstreams.GeneralValidation;
import com.ibm.eventstreams.rest.eventstreams.LicenseValidation;
import com.ibm.eventstreams.rest.eventstreams.NameValidation;
import com.ibm.eventstreams.rest.eventstreams.UnknownPropertyValidation;
import com.ibm.eventstreams.rest.eventstreams.VersionValidation;
import com.ibm.eventstreams.rest.kafkaconnect.KafkaConnectMeteringAnnotationsValidation;
import com.ibm.eventstreams.rest.kafkaconnectS2I.KafkaConnectS2IMeteringAnnotationsValidation;
import com.ibm.eventstreams.rest.mirrormaker2.MirrorMaker2MeteringAnnotationValidation;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import io.fabric8.openshift.client.OpenShiftClient;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.strimzi.operator.common.MetricsProvider;
import io.strimzi.operator.common.operator.resource.RouteOperator;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class EventStreamsVerticle extends AbstractVerticle {

    private static final Logger log = LogManager.getLogger(EventStreamsVerticle.class);
    private final EventStreamsOperatorConfig.ImageLookup imageConfig;
    private RouteOperator routeOperator;
    private final MetricsProvider metricsProvider;

    private KubernetesClient client;
    private Vertx vertx;

    private final String namespace;
    private final String operatorNamespace;
    private PlatformFeaturesAvailability pfa;

    public static final int API_SERVER_PORT = 8081;
    private HttpServer httpServer;
    private static final String API_SSL_CERT_PATH = "/etc/eventstreams/tls.crt";
    private static final String API_SSL_KEY_PATH = "/etc/eventstreams/tls.key";

    private final long kafkaStatusReadyTimeoutMilliSecs;
    private final long reconciliationIntervalMilliSecs;
    private Watch eventStreamsCRWatcher;
    private long reconcileTimer;

    public EventStreamsVerticle(Vertx vertx, KubernetesClient client, String namespace, MetricsProvider metricsProvider, PlatformFeaturesAvailability pfa, EventStreamsOperatorConfig config) {
        log.info("Creating EventStreamsVerticle for namespace {}", namespace);
        this.vertx = vertx;
        this.client = client;
        this.pfa = pfa;
        this.namespace = namespace;
        this.operatorNamespace = config.getOperatorNamespace();
        this.kafkaStatusReadyTimeoutMilliSecs = config.getKafkaStatusReadyTimeoutMs();
        this.reconciliationIntervalMilliSecs = config.getReconciliationIntervalMilliSecs();
        this.imageConfig = config.getImages();
        this.routeOperator = pfa.hasRoutes() ? new RouteOperator(vertx, client.adapt(OpenShiftClient.class)) : null;
        this.metricsProvider = metricsProvider;
    }

    @Override
    public void start(Promise<Void> start) {
        try {
            log.info("EventStreamsVerticle for namespace {} started.", this.namespace);
            KubernetesDeserializer.registerCustomKind(EventStreams.RESOURCE_GROUP + "/" + EventStreams.V1BETA1, EventStreams.RESOURCE_KIND, EventStreams.class);

            EventStreamsResourceOperator esResourceOperator = new EventStreamsResourceOperator(vertx, client);
            OperandRequestResourceOperator operandRequestResourceOperator = new OperandRequestResourceOperator(vertx, client);
            Cp4iServicesBindingResourceOperator cp4iResourceOperator = new Cp4iServicesBindingResourceOperator(vertx, client, Cp4iServicesBinding.RESOURCE_KIND);
            EventStreamsGeoReplicatorResourceOperator replicatorResourceOperator = new EventStreamsGeoReplicatorResourceOperator(vertx, client, EventStreamsGeoReplicator.RESOURCE_KIND);
            KafkaUserOperator kafkaUserOperator = new KafkaUserOperator(vertx, client);
            EventStreamsOperator eventStreamsOperator = new EventStreamsOperator(vertx,
                    client,
                    EventStreams.RESOURCE_KIND,
                    pfa,
                    esResourceOperator,
                    operandRequestResourceOperator,
                    cp4iResourceOperator,
                    replicatorResourceOperator,
                    kafkaUserOperator,
                    imageConfig,
                    routeOperator,
                    metricsProvider,
                    operatorNamespace,
                    kafkaStatusReadyTimeoutMilliSecs);
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
                    })
                    .setHandler(start);
        } catch (Exception e) {
            log.error("Failed to start EventStreamsVerticle", e);
        }
    }

    @Override
    public void stop(Promise<Void> stop) {
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

        Promise<Void> result = Promise.promise();

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

        router.route(HttpMethod.POST, "/admissionwebhook/rejectlicensenotaccepted").handler(new LicenseValidation()::rejectCr);
        router.route(HttpMethod.POST, "/admissionwebhook/rejectinvalidnames").handler(new NameValidation()::rejectCr);
        router.route(HttpMethod.POST, "/admissionwebhook/rejectinvalidversions").handler(new VersionValidation()::rejectCr);
        router.route(HttpMethod.POST, "/admissionwebhook/rejectinvalidtopics").handler(EntityLabelValidation::rejectInvalidKafkaTopics);
        router.route(HttpMethod.POST, "/admissionwebhook/rejectinvalidusers").handler(EntityLabelValidation::rejectInvalidKafkaUsers);
        router.route(HttpMethod.POST, "/admissionwebhook/rejectinvalidendpoints").handler(new EndpointValidation()::rejectCr);
        router.route(HttpMethod.POST, "/admissionwebhook/rejectinvalidproperties").handler(new UnknownPropertyValidation()::rejectCr);
        router.route(HttpMethod.POST, "/admissionwebhook/rejectinvalidkafkaconnectmetering").handler(new KafkaConnectMeteringAnnotationsValidation()::rejectCr);
        router.route(HttpMethod.POST, "/admissionwebhook/rejectinvalidkafkaconnects2imetering").handler(new KafkaConnectS2IMeteringAnnotationsValidation()::rejectCr);
        router.route(HttpMethod.POST, "/admissionwebhook/rejectinvalidmirrormaker2metering").handler(new MirrorMaker2MeteringAnnotationValidation()::rejectCr);
        router.route(HttpMethod.POST, "/admissionwebhook/rejectgeneralproperties").handler(new GeneralValidation()::rejectCr);

        router.errorHandler(500, rc -> {
            Throwable failure = rc.failure();
            if (failure != null) {
                log.error("Unhandled exception", failure);
            }
        });

        httpServer.requestHandler(router).listen(API_SERVER_PORT, asyncResult -> {
            if (asyncResult.succeeded()) {
                log.info("EventStreamsOperator API server is now listening on {})", API_SERVER_PORT);
                result.complete();
            } else {
                log.error("Failed to start API server on {}", API_SERVER_PORT, asyncResult.cause());
                result.fail(asyncResult.cause());
            }
        });

        return result.future();
    }

}
