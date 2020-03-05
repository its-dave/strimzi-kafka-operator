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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.ibm.eventstreams.api.Listener;
import com.ibm.eventstreams.api.spec.EventStreams;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.TLSConfig;
import io.strimzi.certs.CertAndKey;

public abstract class AbstractSecureEndpointModel extends AbstractModel {
    public static final String EXTERNAL_SERVICE_SUFFIX = "external";
    public static final String INTERNAL_SERVICE_SUFFIX = "internal";
    public static final String CERT_GENERATION_KEY = "certificateGenerationID";
    protected static final Set<String> DEFAULT_LISTENERS_WITH_ROUTES = new HashSet<>(Arrays.asList(Listener.EXTERNAL_PLAIN_NAME, Listener.EXTERNAL_TLS_NAME));

    private final CertificateSecretModel certificateSecretModel;
    private final List<Listener> listeners;

    private Service internalService;
    private Service externalService;

    protected Map<String, Route> routes;

    protected AbstractSecureEndpointModel(EventStreams instance, String namespace, String componentName) {
        super(instance.getMetadata().getName(), namespace, componentName);
        this.certificateSecretModel = new CertificateSecretModel(instance, namespace, componentName);
        this.listeners = Listener.enabledListeners();

        // Initialize routes with predefined names for the purposes of EventStreams operator deletion strategy
        this.routes = new HashMap<>();
        DEFAULT_LISTENERS_WITH_ROUTES.forEach(r -> routes.put(getRouteName(r), null));
    }

    // Exposed for testing
    protected AbstractSecureEndpointModel(EventStreams instance, String namespace, String componentName, List<Listener> listeners) {
        super(instance.getMetadata().getName(), namespace, componentName);
        this.certificateSecretModel = new CertificateSecretModel(instance, namespace, componentName);
        this.listeners = listeners;
    }

    protected void createInternalService() {
        List<ServicePort> internalPorts = getInternalListeners()
                .stream()
                .map(super::createServicePort)
                .collect(Collectors.toList());
        internalService = createService("ClusterIP", getDefaultResourceNameWithSuffix(INTERNAL_SERVICE_SUFFIX), internalPorts, Collections.emptyMap());
    }

    protected void createExternalService() {
        List<ServicePort> externalPorts = getExternalListeners().stream()
                .map(super::createServicePort)
                .collect(Collectors.toList());
        externalService = createService(getExternalServiceName(), externalPorts, Collections.emptyMap());
    }

    /**
     * @return Routes for the listeners based on the name of the external service if it exists
     */
    protected void createRoutesFromListeners() {
        if (externalService != null) {
            this.routes = listeners.stream()
                    .filter(Listener::isExposed)
                    .collect(Collectors.toMap(listener -> getRouteName(listener.getName()), listener -> {
                        TLSConfig tlsConfig = null;
                        if (listener.isTls()) {
                            tlsConfig = getDefaultTlsConfig();
                        }
                        return createRoute(getRouteName(listener.getName()), getExternalServiceName(), listener.getPort(), tlsConfig);
                    }));
        }
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
        certificateSecretModel.setCertAndKey(name, certAndKey);
    }

    public String getCertSecretName() {
        return certificateSecretModel.getSecretName();
    }

    public String getCertSecretCertID(String name) {
        return certificateSecretModel.getCertID(name);
    }

    public String getCertSecretKeyID(String name) {
        return certificateSecretModel.getKeyID(name);
    }

    public Secret getCertificateSecretModelSecret() {
        return certificateSecretModel.getSecret();
    }

    public void createCertificateSecretModelSecret() {
        certificateSecretModel.createSecret();
    }

    /**
     * @return Service return the internal service
     */
    public Service getInternalService() {
        return internalService;
    }

    /**
     * @return Service return the external service
     */
    public Service getExternalService() {
        return externalService;
    }

    /**
     * @return Service return the internal service name
     */
    public static String getInternalServiceName(String instanceName, String componentName) {
        return getDefaultResourceNameWithSuffix(INTERNAL_SERVICE_SUFFIX, instanceName, componentName);
    }

    /**
     * @return Service return the internal service name
     */
    public String getInternalServiceName() {
        return getInternalServiceName(getInstanceName(), getComponentName());
    }

    /**
     * @return Service return the external service name
     */
    public String getExternalServiceName() {
        return getExternalServiceName(getInstanceName(), getComponentName());
    }

    public static String getExternalServiceName(String instanceName, String componentName) {
        return getDefaultResourceNameWithSuffix(EXTERNAL_SERVICE_SUFFIX, instanceName, componentName);
    }

    /**
     * @return Route return the accessible routes
     */
    public Map<String, Route> getRoutes() {
        return routes;
    }
}
