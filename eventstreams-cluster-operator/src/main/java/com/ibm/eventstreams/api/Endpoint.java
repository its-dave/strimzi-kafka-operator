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

import com.ibm.eventstreams.api.spec.EndpointSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import io.strimzi.api.kafka.model.CertAndKeySecretSource;
import lombok.Builder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Builder
public class Endpoint {
    private static final boolean DEFAULT_TLS_SETTING = true;
    private static final TlsVersion DEFAULT_TLS_VERSION = TlsVersion.TLS_V1_2;

    public static final String DEFAULT_EXTERNAL_NAME = "external";
    private static final int DEFAULT_EXTERNAL_TLS_PORT = 9443;
    private static final EndpointServiceType DEFAULT_EXTERNAL_SERVICE_TYPE = EndpointServiceType.ROUTE;
    private static final int DEFAULT_EXTERNAL_PLAIN_PORT = 9080;
    private static final List<String> DEFAULT_EXTERNAL_AUTHENTICATION_MECHANISM = Collections.singletonList("BEARER");

    private static final String DEFAULT_P2P_TLS_NAME = "p2ptls";
    private static final String DEFAULT_P2P_PLAIN_NAME = "pod2pod";
    private static final int DEFAULT_P2P_TLS_PORT = 7443;
    private static final String DEFAULT_P2P_TLS_MOUNT = "p2p";
    private static final int DEFAULT_P2P_PLAIN_PORT = 7080;
    private static final String DEFAULT_P2P_PATH = DEFAULT_P2P_TLS_MOUNT + "/podtls";

    private String name;
    private int port;
    private boolean tls;
    private TlsVersion tlsVersion;
    private EndpointServiceType type;
    private String path;
    private CertAndKeySecretSource certificateAndKeyOverride;
    private List<String> authenticationMechanisms;

    public Endpoint(String name, int port, boolean tls, TlsVersion tlsVersion, EndpointServiceType type, String path, CertAndKeySecretSource certificateAndKeyOverride, List<String> authenticationMechanisms) {
        this.name = name;
        this.port = port;
        this.tls = tls;
        this.tlsVersion = tlsVersion;
        this.type = type;
        this.path = path;
        this.certificateAndKeyOverride = certificateAndKeyOverride;
        this.authenticationMechanisms = authenticationMechanisms;
    }

    /**
     * Creates a default endpoint object which contains all the configurations needed for all Event Streams components
     * to talk externally to the endpoint through. Creates a plain/tls externally accessible endpoint based on
     * overall security of CR.
     * @param instance
     * @return external endpoint
     */
    public static Endpoint createDefaultExternalEndpoint(EventStreams instance) {
        boolean isTls = isTls(instance);

        return new Endpoint(DEFAULT_EXTERNAL_NAME,
                            isTls ? DEFAULT_EXTERNAL_TLS_PORT : DEFAULT_EXTERNAL_PLAIN_PORT,
                            isTls,
                            isTls ? DEFAULT_TLS_VERSION : null,
                            DEFAULT_EXTERNAL_SERVICE_TYPE,
                            isTls ? DEFAULT_EXTERNAL_NAME : null,
                            null,
                            isTls ? DEFAULT_EXTERNAL_AUTHENTICATION_MECHANISM : Collections.emptyList());
    }

    /**
     * This endpoint is created regardless of what has been configured by the user. This endpoint is used by other
     * Event Streams components to talk to the specified endpoint.
     * @param instance
     * @return A Plain/TCP Pod To Pod endpoint based on the overall security configuration of the CR.
     */
    public static Endpoint createP2PEndpoint(EventStreams instance) {
        boolean isTls = isTls(instance);

        return new Endpoint(isTls ? DEFAULT_P2P_TLS_NAME : DEFAULT_P2P_PLAIN_NAME,
                            isTls ? DEFAULT_P2P_TLS_PORT : DEFAULT_P2P_PLAIN_PORT,
                            isTls,
                            DEFAULT_TLS_VERSION,
                            EndpointServiceType.INTERNAL,
                            isTls ? DEFAULT_P2P_PATH : null,
                            null,
                            Collections.emptyList());
    }

    /**
     * This endpoint is created based on what has been configured by the user. If the user has not configured the
     * following fields, then defaults have been created for them.
     * @return an endpoint object representing what the user has configured this endpoint to look like.
     */
    public static Function<EndpointSpec, Endpoint> createEndpointFromSpec() {
        return spec -> new Endpoint(spec.getName(),
                                    getPortOrDefault(spec),
                                    getTlsOrDefault(spec),
                                    getTlsVersionOrDefault(spec),
                                    getTypeOrDefault(spec),
                                    spec.getName(),
                                    spec.getCertOverrides(),
                                    getAuthenticationMechanismsOrDefault(spec));

    }

    /**
     * Gets the port specified by the user or it will default to a default TLS/Plain port depending on the overall
     * security configuration of the CR
     * @param spec the user configured endpoint CR
     * @return a port number
     */
    private static int getPortOrDefault(EndpointSpec spec) {
        return Optional.ofNullable(spec.getAccessPort())
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
        return Optional.ofNullable(spec.getTls()).orElse(DEFAULT_TLS_SETTING);
    }

    /**
     * Determines whether the overall security configuration of the cluster is tls.
     * @param instance the CR
     * @return whether the CR has been configured for TLS if not it will default to TLS
     */
    private static boolean isTls(EventStreams instance) {
        return Optional.ofNullable(instance.getSpec())
            .map(EventStreamsSpec::getSecurity)
            .map(SecuritySpec::getEncryption)
            .map(encryption ->  encryption == SecuritySpec.Encryption.TLS)
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
            .orElse(Collections.emptyList());
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
     * Gets whether or not the user has configured the endpoint to use TLS, if nothing is configured it has been defaulted to true.
     * @return boolean whether endpoint should be configured with TLS
     */
    public boolean isTls() {
        return tls;
    }

    /**
     * Gets the type of service the Endpoint is configured with, if no type was configured it will default to ROUTE
     * @return the type of service the Endpoint has been configured with
     */
    public EndpointServiceType getType() {
        return type;
    }

    /**
     * Gets the path of where the certificate will be stored in the component to configure which certificate is presented to the client.
     * @return the path to where the certificate has been stored to configure what certificate is presented to the client
     */
    public String getPath() {
        return path;
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
}
