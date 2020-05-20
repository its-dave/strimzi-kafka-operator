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
package com.ibm.commonservices;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public class CommonServicesConfig {

    private static final Logger log = LogManager.getLogger(CommonServicesConfig.class.getName());

    public static final String INGRESS_CM_NAME = "management-ingress-ibmcloud-cluster-info";
    public static final String CA_CERT_SECRET_NAME = "management-ingress-ibmcloud-cluster-ca-cert";

    public static final String CLUSTER_NAME_KEY = "cluster_name";
    public static final String CONSOLE_HOST_KEY = "cluster_address";
    public static final String CONSOLE_PORT_KEY = "cluster_router_https_port";
    public static final String INGRESS_ENDPOINT_KEY = "cluster_endpoint";

    private String clusterName;
    private String ingressEndpoint;
    private String ingressServiceName;
    private String consoleHost;
    private String consolePort;

    private String encodedCaCert;
    private String publicCaCert;

    public CommonServicesConfig(String clusterName, String ingressEndpoint, String consoleHost, String consolePort) {
        this.clusterName = clusterName;
        this.ingressServiceName = getServiceNameWithNamespace(ingressEndpoint);
        this.consoleHost = consoleHost;
        this.consolePort = consolePort;
        this.ingressEndpoint = ingressEndpoint;
    }

    public void setCaCerts(String encodedCaCert, String publicCaCert) {
        this.encodedCaCert = encodedCaCert;
        this.publicCaCert = publicCaCert;
    }

    public String getIngressServiceNameAndNamespace() {
        return ingressServiceName;
    }

    public String getIngressEndpoint() {
        return ingressEndpoint;
    }

    public String getConsoleHost() {
        return consoleHost;
    }

    public String getConsolePort() {
        return consolePort;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getEncodedCaCert() {
        return encodedCaCert;
    }

    public String getPublicCaCert() {
        return publicCaCert;
    }

    private String getServiceNameWithNamespace(String endpoint) {
        log.traceEntry(() -> endpoint);
        String serviceNameWithNamespace;
        String host = Optional.ofNullable(endpoint)
                .map(CommonServicesConfig::getURI)
                .map(URI::getHost)
                .orElse("");
        if (host.isEmpty()) {
            log.error("Host of Common Services Management Ingress is empty, using default");
            serviceNameWithNamespace =  "icp-management-ingress.ibm-common-services";
        } else {
            serviceNameWithNamespace = getHostWithoutSvc(host);
        }
        log.traceExit(serviceNameWithNamespace);
        return serviceNameWithNamespace;
    }

    private static String getHostWithoutSvc(String host) {
        log.traceEntry(() -> host);
        String hostWithoutSvc;
        String[] splitHost = host.split("[.]");
        if (splitHost.length == 3) {
            hostWithoutSvc = String.format("%s.%s", splitHost[0], splitHost[1]);
        } else {
            log.debug("Host string not in usual format, using whole string.");
            hostWithoutSvc = host;
        }
        log.traceExit(hostWithoutSvc);
        return hostWithoutSvc;
    }

    private static URI getURI(String endpoint) {
        log.traceEntry(() -> endpoint);
        URI uri;
        try {
            uri =  new URI(endpoint);
        } catch (URISyntaxException e) {
            log.error("Failed parsing Common Services Management Ingress endpoint", e);
            uri = null;
        }
        log.traceExit(uri);
        return uri;
    }
}
