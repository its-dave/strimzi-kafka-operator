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


import com.ibm.commonservices.api.model.ClientModel;
import com.ibm.eventstreams.api.model.AbstractModel;
import com.ibm.eventstreams.api.model.ClusterSecretsModel;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CommonServices {

    private static final Logger log = LogManager.getLogger(CommonServices.class.getName());

    public static final String OIDC_VOLUME_MOUNT_NAME = "oidc-secret";
    public static final String IBMCLOUD_CA_VOLUME_MOUNT_NAME = "ibmcloud";
    public static final String OIDC_ENVIRONMENT_PATH = AbstractModel.ENVIRONMENT_PATH + File.separator + "commonServices";
    public static final String IBMCLOUD_CA_CERTIFICATE_PATH = AbstractModel.CERTIFICATE_PATH + File.separator + "ibmcloud";

    public static final String INGRESS_CM_NAME = "management-ingress-ibmcloud-cluster-info";
    public static final String CA_CERT_SECRET_NAME = "management-ingress-ibmcloud-cluster-ca-cert";

    public static final String COMMON_SERVICES_STATUS_CM_NAME = "ibm-common-services-status";
    public static final String COMMON_SERVICES_STATUS_CM_NAMESPACE = "kube-public";
    public static final String COMMON_SERVICES_STATUS_READY = "Ready";
    public static final String COMMON_SERVICES_STATUS_IAM = "iamstatus";

    public static final String CLUSTER_NAME_KEY = "cluster_name";
    public static final String CONSOLE_HOST_KEY = "cluster_address";
    public static final String CONSOLE_PORT_KEY = "cluster_router_https_port";
    public static final String INGRESS_ENDPOINT_KEY = "cluster_endpoint";
    public static final String CLIENT_ID_KEY = "CLIENT_ID";
    public static final String CLIENT_SECRET_KEY = "CLIENT_SECRET";

    private String clusterName;
    private String ingressEndpoint;
    private String ingressServiceName;
    private String consoleHost;
    private String consolePort;
    private String ibmCloudCASecretName;
    private String instance;
    private String encodedCaCert;
    private String publicCaCert;
    private boolean isPresent = true; // Default to always true for now

    public CommonServices(String instance, Map<String, String> clusterData) {
        this.instance = instance;
        clusterName = clusterData.get(CommonServices.CLUSTER_NAME_KEY);
        ingressEndpoint = clusterData.get(CommonServices.INGRESS_ENDPOINT_KEY);
        consoleHost = clusterData.get(CommonServices.CONSOLE_HOST_KEY);
        consolePort = clusterData.get(CommonServices.CONSOLE_PORT_KEY);
        ingressServiceName = getServiceNameWithNamespace(ingressEndpoint);
        ibmCloudCASecretName = ClusterSecretsModel.getIBMCloudSecretName(instance);
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
                .map(CommonServices::getURI)
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

    // check whether instance of IBM Common services is present
    public boolean isPresent() {
        return isPresent;
    }

    public List<Volume> volumes() {
        String oidcSecretName = ClientModel.getSecretName(instance);
        List<Volume> volumes = new ArrayList<>();

        volumes.add(new VolumeBuilder()
            .withNewName(IBMCLOUD_CA_VOLUME_MOUNT_NAME)
            .withNewSecret()
            .withNewSecretName(ibmCloudCASecretName)
            .addNewItem().withNewKey(AbstractModel.CA_CERT).withNewPath(AbstractModel.CA_CERT).endItem()
            .endSecret()
            .build());

        volumes.add(new VolumeBuilder()
            .withNewName(OIDC_VOLUME_MOUNT_NAME)
            .withNewSecret()
            .withNewSecretName(oidcSecretName)
            .addNewItem().withNewKey(CLIENT_ID_KEY).withNewPath(CLIENT_ID_KEY).endItem()
            .addNewItem().withNewKey(CLIENT_SECRET_KEY).withNewPath(CLIENT_SECRET_KEY).endItem()
            .endSecret()
            .build());

        return volumes;
    }

    public List<VolumeMount> volumeMounts() {
        List<VolumeMount> volumeMounts = new ArrayList<>();
        volumeMounts.add(new VolumeMountBuilder().withNewName(IBMCLOUD_CA_VOLUME_MOUNT_NAME)
            .withMountPath(IBMCLOUD_CA_CERTIFICATE_PATH)
            .withReadOnly(true).build());

        volumeMounts.add(new VolumeMountBuilder().withNewName(OIDC_VOLUME_MOUNT_NAME)
            .withMountPath(OIDC_ENVIRONMENT_PATH)
            .withReadOnly(true).build());

        return volumeMounts;
    }

    public List<EnvVar> envVars() {
        return Arrays.asList(
            new EnvVarBuilder().withName("IAM_CLUSTER_NAME").withValue(clusterName).build(),
            new EnvVarBuilder().withName("IAM_SERVER_URL").withValue(ingressEndpoint).build(),
            new EnvVarBuilder().withName("IAM_SERVER_CA_CERT").withValue(IBMCLOUD_CA_CERTIFICATE_PATH + File.separator + AbstractModel.CA_CERT).build(),
            new EnvVarBuilder().withName("CLIENT_ID").withValue("file://" + OIDC_ENVIRONMENT_PATH + File.separator + CLIENT_ID_KEY).build(),
            new EnvVarBuilder().withName("CLIENT_SECRET").withValue("file://" + OIDC_ENVIRONMENT_PATH + File.separator + CLIENT_SECRET_KEY).build()
        );
    }
}
