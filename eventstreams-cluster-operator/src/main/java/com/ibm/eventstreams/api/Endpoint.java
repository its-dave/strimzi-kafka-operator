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

import com.ibm.eventstreams.api.model.AbstractModel;
import com.ibm.eventstreams.api.spec.EndpointSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import io.strimzi.api.kafka.model.CertAndKeySecretSource;
import io.strimzi.operator.common.model.Labels;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Endpoint {
    private static final boolean DEFAULT_TLS_SETTING = true;
    private static final boolean DEFAULT_EXTERNAL_ENDPOINT_TLS_SETTING = true;
    public static final TlsVersion DEFAULT_TLS_VERSION = AbstractModel.DEFAULT_INTERNAL_TLS;
    private static final TlsVersion DEFAULT_P2P_TLS_VERSION = AbstractModel.DEFAULT_INTERNAL_TLS;

    public static final String DEFAULT_EXTERNAL_NAME = "external";
    public static final int DEFAULT_EXTERNAL_TLS_PORT = 9443;
    public static final EndpointServiceType DEFAULT_EXTERNAL_SERVICE_TYPE = EndpointServiceType.ROUTE;
    private static final int DEFAULT_EXTERNAL_PLAIN_PORT = 9080;
    public static final String IAM_BEARER_KEY = "IAM-BEARER";
    public static final String SCRAM_SHA_512_KEY = "SCRAM-SHA-512";
    public static final String MUTUAL_TLS_KEY = "TLS";
    public static final String DEFAULT_HOST_ADDRESS = null;
    public static final String MAC_KEY = "MAC";
    public static final String RUNAS_ANONYMOUS_KEY = "RUNAS-ANONYMOUS";
    private static final List<String> DEFAULT_EXTERNAL_AUTHENTICATION_MECHANISM = Arrays.asList(IAM_BEARER_KEY, MUTUAL_TLS_KEY, SCRAM_SHA_512_KEY);

    public static final String DEFAULT_P2P_TLS_NAME = "p2ptls";
    public static final String DEFAULT_P2P_PLAIN_NAME = "pod2pod";
    public static final int DEFAULT_P2P_TLS_PORT = 7443;
    public static final int DEFAULT_P2P_PLAIN_PORT = 7080;

    private String name;
    private Optional<String> host;
    private int port;
    private TlsVersion tlsVersion;
    private EndpointServiceType type;
    private CertAndKeySecretSource certificateAndKeyOverride;
    private List<String> authenticationMechanisms;
    // This is a list of Label configurations for identify pods that can communicate with this endpoint
    private List<Labels> endpointIngressLabels;

    public Endpoint(String name, int port, TlsVersion tlsVersion, EndpointServiceType type, String host, CertAndKeySecretSource certificateAndKeyOverride, List<String> authenticationMechanisms, List<Labels> endpointIngressLabels) {
        this.name = name;
        this.port = port;
        this.tlsVersion = tlsVersion;
        this.host = Optional.ofNullable(host).filter(string -> !string.isEmpty());
        this.type = type;
        this.certificateAndKeyOverride = certificateAndKeyOverride;
        this.authenticationMechanisms = authenticationMechanisms;
        this.endpointIngressLabels = endpointIngressLabels;
    }

    /**
     * Creates a default endpoint object which contains all the configurations needed for all Event Streams components
     * to talk externally to the endpoint through. Creates a plain/tls externally accessible endpoint based on
     * overall security of CR.
     * @param kafkaAuthenticationEnabled - true if any Kafka listeners have an authentication attribute
     * @return external endpoint
     */
    public static Endpoint createDefaultExternalEndpoint(boolean kafkaAuthenticationEnabled) {
        return new Endpoint(DEFAULT_EXTERNAL_NAME,
                            DEFAULT_EXTERNAL_TLS_PORT,
                            DEFAULT_TLS_VERSION,
                            DEFAULT_EXTERNAL_SERVICE_TYPE,
                            DEFAULT_HOST_ADDRESS,
                            null,
                            kafkaAuthenticationEnabled ? DEFAULT_EXTERNAL_AUTHENTICATION_MECHANISM : Collections.singletonList(RUNAS_ANONYMOUS_KEY),
                            Collections.emptyList());
    }

    /**
     * This endpoint is created regardless of what has been configured by the user. This endpoint is used by other
     * Event Streams components to talk to the specified endpoint.
     * @param instance
     * @return A Plain/TCP Pod To Pod endpoint based on the overall security configuration of the CR.
     */
    public static Endpoint createP2PEndpoint(EventStreams instance, List<String> podToPodAuth, List<Labels> endpointIngressLabels) {
        boolean isTls = isTls(instance);

        return new Endpoint(isTls ? DEFAULT_P2P_TLS_NAME : DEFAULT_P2P_PLAIN_NAME,
                            isTls ? DEFAULT_P2P_TLS_PORT : DEFAULT_P2P_PLAIN_PORT,
                            getP2PTlsVersion(instance),
                            EndpointServiceType.INTERNAL,
                            DEFAULT_HOST_ADDRESS,
                            null,
                            podToPodAuth.isEmpty() ? Collections.singletonList(RUNAS_ANONYMOUS_KEY) : podToPodAuth,
                            endpointIngressLabels);
    }

    /**
     * This endpoint is created based on what has been configured by the user. If the user has not configured the
     * following fields, then defaults have been created for them.
     * @return an endpoint object representing what the user has configured this endpoint to look like.
     */
    public static Endpoint createEndpointFromSpec(EndpointSpec spec) {
        return new Endpoint(spec.getName(),
                            getPortOrDefault(spec),
                            getTlsVersionOrDefault(spec),
                            getTypeOrDefault(spec),
                            spec.getHost(),
                            spec.getCertOverrides(),
                            getAuthenticationMechanismsOrDefault(spec),
                            getEndpointIngressLabels(spec));

    }

    /**
     * Gets the port specified by the user or it will default to a default TLS/Plain port depending on the overall
     * security configuration of the CR
     * @param spec the user configured endpoint CR
     * @return a port number
     */
    private static int getPortOrDefault(EndpointSpec spec) {
        return Optional.ofNullable(spec.getContainerPort())
            .orElse(getTlsOrDefault(spec) ? DEFAULT_EXTERNAL_TLS_PORT : DEFAULT_EXTERNAL_PLAIN_PORT);
    }

    /**
     * Gets the type of service specified by the user or it will default to a default route.
     * @param spec the user configured endpoint CR
     * @return the service type of the endpoint
     */
    private static EndpointServiceType getTypeOrDefault(EndpointSpec spec) {
        return Optional.ofNullable(spec.getType()).orElse(DEFAULT_EXTERNAL_SERVICE_TYPE);
    }

    /**
     * Gets the pod to pod TLS version based on the internal TLS setting of the Event Streams Spec
     * @param instance the event streams CR
     * @return Pod to Pod TLS Version
     */
    private static TlsVersion getP2PTlsVersion(EventStreams instance) {
        return AbstractModel.getInternalTlsVersion(instance);
    }

    /**
     * Gets the TLS specified by the user or it will default to a default TLS version.
     * @param spec the user configured endpoint CR
     * @return the Tls version configured by the user or the default
     */
    private static TlsVersion getTlsVersionOrDefault(EndpointSpec spec) {
        return Optional.ofNullable(spec.getTlsVersion()).orElse(DEFAULT_TLS_VERSION);
    }

    /**
     * Gets whether or not this endpoint is configured with TLS but it defaults to TLS if the overall security CR
     * has been configured to TLS.
     * @param spec the user configured endpoint CR
     * @return the service type of the endpoint
     */
    private static boolean getTlsOrDefault(EndpointSpec spec) {
        return Optional.ofNullable(spec.getTlsVersion())
            .map(tlsVersion -> !TlsVersion.NONE.equals(tlsVersion))
            .orElse(DEFAULT_EXTERNAL_ENDPOINT_TLS_SETTING);
    }

    /**
     * Determines whether the overall security configuration of the cluster is tls.
     * @param instance the CR
     * @return whether the CR has been configured for TLS if not it will default to TLS
     */
    private static boolean isTls(EventStreams instance) {
        return Optional.ofNullable(instance.getSpec())
            .map(EventStreamsSpec::getSecurity)
            .map(SecuritySpec::getInternalTls)
            .map(encryption -> !encryption.equals(TlsVersion.NONE))
            .orElse(DEFAULT_TLS_SETTING);
    }

    /**
     * Gets whether or not this endpoint has specific authentication mechanisms configured by the user. If not, it
     * will default to no authentication.
     * @param spec the user configured endpoint CR
     * @return the service type of the endpoint
     */
    private static List<String> getAuthenticationMechanismsOrDefault(EndpointSpec spec) {
        return Optional.ofNullable(spec.getAuthenticationMechanisms())
                .map(list -> list.isEmpty() ? Collections.singletonList(RUNAS_ANONYMOUS_KEY) : list) // if set empty use RUNAS-ANONYMOUS, otherwise use list
                .orElse(DEFAULT_EXTERNAL_AUTHENTICATION_MECHANISM); // if authenticationMechanisms not set use default
    }

    /**
     * Gets any labels that will restrict ingress to this port via a network policy
     * @param spec the user configured endpoint CR
     * @return the labels of the components allowed to talk to this port
     */
    private static List<Labels> getEndpointIngressLabels(EndpointSpec spec) {
        return Collections.emptyList();
    }

    /**
     * Gets the name of the endpoint configured by the user or that has been defaulted if no endpoints have been configured.
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the port of the endpoint configured by the user or that has been defaulted if no endpoints have been configured.
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the Pod to Pod port depending on what tls version is enabled
     * @param tlsEnabled if tls enabled
     * @return the port
     */
    public static int getPodToPodPort(boolean tlsEnabled) {
        return tlsEnabled ? DEFAULT_P2P_TLS_PORT : DEFAULT_P2P_PLAIN_PORT;
    }

    /**
     * Gets whether or not the user has configured the endpoint to use TLS, if nothing is configured it has been defaulted to true.
     * @return boolean whether endpoint should be configured with TLS
     */
    public boolean isTls() {
        return !TlsVersion.NONE.equals(tlsVersion);
    }

    /**
     * Gets the type of service the Endpoint is configured with, if no type was configured it will default to ROUTE
     * @return the type of service the Endpoint has been configured with
     */
    public EndpointServiceType getType() {
        return type;
    }

    /**
     * Gets the TLS Version the endpoint is configured with
     * @return the tls version
     */
    public TlsVersion getTlsVersion() {
        return tlsVersion;
    }

    /**
     * Gets the custom DNS host that the route should be generated with.
     * @return the custom DNS host
     */
    public Optional<String> getHost() {
        return host;
    }

    /**
     * Determines whether or not the user has specified a specific certificate and key they want to present at this endpoint
     * @return the object that references where the user specified cert and key is.
     */
    public CertAndKeySecretSource getCertificateAndKeyOverride() {
        return certificateAndKeyOverride;
    }

    public void setCertificateAndKeyOverride(CertAndKeySecretSource certificateAndKeyOverride) {
        this.certificateAndKeyOverride = certificateAndKeyOverride;
    }

    /**
     * A list of strings that will configure what authentication mechanisms is used at the configured endpoint.
     * @return a list of strings which represent the authentication mechanisms that the endpoint is configured with
     */
    public List<String> getAuthenticationMechanisms() {
        return authenticationMechanisms;
    }

    public boolean isRoute() {
        return this.type == EndpointServiceType.ROUTE;
    }

    public List<Labels> getEndpointIngressLabels() {
        return endpointIngressLabels;
    }
}
