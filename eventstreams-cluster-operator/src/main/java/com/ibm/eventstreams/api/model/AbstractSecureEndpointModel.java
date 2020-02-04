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
package com.ibm.eventstreams.api.model;

import com.ibm.eventstreams.api.Listener;
import com.ibm.eventstreams.api.spec.EventStreams;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.openshift.api.model.Route;
import io.strimzi.certs.CertAndKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractSecureEndpointModel extends AbstractModel {
    public static final String EXTERNAL_SERVICE_POSTFIX = "external";
    public static final String INTERNAL_SERVICE_POSTFIX = "internal";
    public static final String CERT_GENERATION_KEY = "certificateGenerationID";
    private final CertificateSecretModel certSecret;
    private final List<Listener> listeners;
    private Service externalService = null;
    private Service internalService = null;
    private Map<String, Route> routes;

    protected AbstractSecureEndpointModel(EventStreams instance, String namespace, String componentName) {
        super(instance.getMetadata().getName(), namespace, componentName);
        certSecret = new CertificateSecretModel(instance, namespace, componentName);
        this.listeners = Listener.enabledListeners();
    }

    // Exposed for testing
    protected AbstractSecureEndpointModel(EventStreams instance, String namespace, String componentName, List<Listener> listeners) {
        super(instance.getMetadata().getName(), namespace, componentName);
        certSecret = new CertificateSecretModel(instance, namespace, componentName);
        this.listeners = listeners;
    }

    protected void createServices() {
        List<ServicePort> externalPorts = getExternalListeners().stream().map(super::createServicePort).collect(Collectors.toList());
        List<ServicePort> internalPorts = getInternalListeners().stream().map(super::createServicePort).collect(Collectors.toList());
        externalService = createService(fullPrefixedName(EXTERNAL_SERVICE_POSTFIX), externalPorts, Collections.emptyMap());
        internalService = createService("ClusterIP", fullPrefixedName(INTERNAL_SERVICE_POSTFIX), internalPorts, Collections.emptyMap());
    }

    protected void createRoutes() {
        routes = listeners.stream()
                .filter(Listener::isExposed)
                .collect(Collectors.toMap(Listener::getName, listener -> createRoute(externalService.getMetadata().getName(), listener)));
    }

    public List<Listener> getListeners() {
        return new ArrayList<>(listeners);
    }

    public List<Listener> getTlsListeners() {
        return listeners.stream().filter(Listener::isTls).collect(Collectors.toList());
    }

    private List<Listener> getExternalListeners() {
        return listeners.stream().filter(Listener::isExposed).collect(Collectors.toList());
    }

    private List<Listener> getInternalListeners() {
        List<Listener> internalListeners = listeners.stream().filter(listener -> !listener.isExposed()).collect(Collectors.toList());
        internalListeners.add(Listener.podToPodListener(tlsEnabled()));
        return internalListeners;
    }

    public void setCertAndKey(String name, CertAndKey certAndKey) {
        certSecret.setCertAndKey(name, certAndKey);
    }

    public String getCertSecretName() {
        return certSecret.getSecretName();
    }

    public String getCertSecretCertID(String name) {
        return certSecret.getCertID(name);
    }

    public String getCertSecretKeyID(String name) {
        return certSecret.getKeyID(name);
    }

    public Secret getCertSecret() {
        return certSecret.getSecret();
    }

    /**
     * @return Service return the external service
     */
    public Service getExternalService() {
        return externalService;
    }

    /**
     * @return Service return the external service name
     */
    public static String getExternalServiceName(String instanceName, String componentName) {
        return getDefaultResourceName(instanceName, componentName) + "-" + EXTERNAL_SERVICE_POSTFIX;
    }

    /**
     * @return Service return the internal service
     */
    public Service getInternalService() {
        return internalService;
    }

    /**
     * @return Service return the internal service name
     */
    public static String getInternalServiceName(String instanceName, String componentName) {
        return getDefaultResourceName(instanceName, componentName) + "-" + INTERNAL_SERVICE_POSTFIX;
    }

    /**
     * @return Route return the accessible routes
     */
    public Map<String, Route> getRoutes() {
        return routes;
    }
}
