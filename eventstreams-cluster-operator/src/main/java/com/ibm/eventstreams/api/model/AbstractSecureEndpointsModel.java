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

import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.SecurityComponentSpec;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.TLSConfig;
import io.strimzi.certs.CertAndKey;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractSecureEndpointsModel extends AbstractModel {
    public static final String NODE_PORT_SERVICE_SUFFIX = "node-port";
    public static final String ROUTE_SERVICE_SUFFIX = "external";
    public static final String LOAD_BALANCER_SERVICE_SUFFIX = "load-balancer";
    public static final String INGRESS_SERVICE_SUFFIX = "ingress";

    public static final String INTERNAL_SERVICE_SUFFIX = "internal";

    private static final String KEY_VALUE_SEPARATOR = ":";
    private static final String AUTHENTICATION_SEPARATOR = ";";
    private static final String PORT_SEPARATOR = ",";

    private static final String AUTHENTICATION_ENV_VAR_KEY = "AUTHENTICATION";
    private static final String ENDPOINTS_ENV_VAR_KEY = "ENDPOINTS";
    private static final String TLS_VERSION_ENV_VAR_KEY = "TLS_VERSION";

    public static final String CERTS_VOLUME_MOUNT_NAME = "certs";
    public static final String CLIENT_CA_VOLUME_MOUNT_NAME = "client-ca";
    public static final String CLUSTER_CA_VOLUME_MOUNT_NAME = "cluster-ca";
    public static final String HMAC_VOLUME_MOUNT_NAME = "hmac-secret";
    public static final String HMAC_ENVIRONMENT_PATH = ENVIRONMENT_PATH + "/hmac";
    public static final String KAFKA_USER_CERTIFICATE_PATH = CERTIFICATE_PATH + "/user";
    public static final String CLUSTER_CERTIFICATE_PATH = CERTIFICATE_PATH + "/cluster";
    public static final String CLIENT_CA_CERTIFICATE_PATH = CERTIFICATE_PATH + "/client";

    public static final String SSL_TRUSTSTORE_P12_PATH_ENV_KEY = "SSL_TRUSTSTORE_P12_PATH";
    public static final String SSL_TRUSTSTORE_P12_PASSWORD_ENV_KEY = "SSL_TRUSTSTORE_P12_PASSWORD";
    public static final String SSL_TRUSTSTORE_CRT_PATH_ENV_KEY = "SSL_TRUSTSTORE_CRT_PATH";
    public static final String CLIENT_CA_PATH_ENV_KEY = "CLIENT_CA_PATH";
    public static final String SSL_KEYSTORE_PATH_ENV_KEY = "SSL_KEYSTORE_PATH";
    public static final String SSL_KEYSTORE_PASSWORD_PATH_ENV_KEY = "SSL_KEYSTORE_PASSWORD";
    public static final String SSL_ENABLED_ENV_KEY = "SSL_ENABLED";
    public static final String AUTHORIZATION_ENABLED_ENV_KEY = "AUTHORIZATION_ENABLED";

    private final CertificateSecretModel certificateSecretModel;

    protected List<Endpoint> endpoints = Collections.emptyList();
    protected Map<String, Route> routes;

    private Service internalService;
    private Service routeService;
    private Service nodePortService;
    // private Service loadBalancerService = null;
    // private Service ingressService = null;
    private final boolean kafkaAuthorizationEnabled;


    public AbstractSecureEndpointsModel(EventStreams instance, String componentName, String applicationName) {
        super(instance, componentName, applicationName);
        this.certificateSecretModel = new CertificateSecretModel(instance, componentName, applicationName);
        this.kafkaAuthorizationEnabled = isKafkaAuthorizationEnabled(instance);

        this.routes = new HashMap<>();
    }

    /**
     * Method creates a model of the endpoints that will be created for the user to use and communicate to the component
     * with. If user has not configured endpoints, then a default endpoint will be created for the user which
     * will enable all Event Streams capabilities. A Pod-To-Pod endpoint is always created for Event Streams components
     * to communicate with one another.
     * @param instance the current EventStreams CR
     * @param spec The list of endpoints passed into the component. This needs to be passed in to determine which
     *             component's endpoint (schema registry, admin rest, or rest producer) to configure.
     * @return A list of endpoints
     */
    public List<Endpoint> createEndpoints(EventStreams instance, SecurityComponentSpec spec) {
        List<Endpoint> publicEndpoints = Optional.ofNullable(spec)
            .map(SecurityComponentSpec::getEndpoints)
            .map(endpointSpecs -> endpointSpecs.stream().map(Endpoint::createEndpointFromSpec).collect(Collectors.toList()))
            .orElse(createDefaultEndpoints(isKafkaAuthenticationEnabled(instance)));

        List<Endpoint> endpoints = new ArrayList<>(publicEndpoints);
        endpoints.addAll(createP2PEndpoints(instance));

        return endpoints;
    }

    protected abstract List<Endpoint> createP2PEndpoints(EventStreams instance);

    protected abstract List<Endpoint> createDefaultEndpoints(boolean authEnabled);

    /**
     * Creates a single service per type of Service with all access ports of the same Service type configured
     * @param type the specified EndpointServiceType service.
     */
    protected Service createService(EndpointServiceType type, Map<String, String> annotations) {
        List<ServicePort> ports = endpoints.stream()
            .filter(endpoint -> endpoint.getType() == type)
            .map(endpoint -> new ServicePortBuilder()
                .withName(endpoint.getName())
                .withNewProtocol("TCP")
                .withPort(endpoint.getPort())
                .build())
            .collect(Collectors.toList());

        return updateServiceValueFromType(type, createService(type.toServiceValue(), getServiceName(type), ports, annotations));
    }

    /**
     * Returns the name of the service based on the specified EndpointServiceType
     * @param type the specified type of EndpointServiceType wanted
     * @return the name of the service
     */
    public String getServiceName(EndpointServiceType type) {
        return getDefaultResourceNameWithSuffix(getServiceSuffix(type));
    }

    /**
     * Returns the name of the internal service.
     * @return name of the service
     */
    @Override
    protected String getServiceName() {
        return getServiceName(EndpointServiceType.INTERNAL);
    }

    /**
     * Get the specific Endpoint suffix based on the EndpointServiceType. Note that are currently only
     * implementing Routes/NodePort/Regular services so we will only default to creating an internal service when
     * LoadBalancer and Ingress are specified.
     * @param type the EndpointServiceType of service wanted
     * @return EndpointServiceType service suffix.
     */
    private String getServiceSuffix(EndpointServiceType type) {
        switch (type) {
            case NODE_PORT:
                return NODE_PORT_SERVICE_SUFFIX;
            case ROUTE:
                return ROUTE_SERVICE_SUFFIX;
            case INTERNAL:
                return INTERNAL_SERVICE_SUFFIX;
            case LOAD_BALANCER:
                return LOAD_BALANCER_SERVICE_SUFFIX;
            case INGRESS:
                return INGRESS_SERVICE_SUFFIX;
            default:
                return "";
        }
    }

    /**
     * Get the service created based on the EndpointServiceType. Note that are currently only
     * implementing Routes/NodePort/Regular services so we will only default to returning an internal service when
     * LoadBalancer and Ingress are specified.
     * @param type the EndpointServiceType of service wanted
     * @return the service of type EndpointServiceType requested
     */
    public Service getSecurityService(EndpointServiceType type) {
        switch (type) {
            case NODE_PORT:
                return nodePortService;
            case ROUTE:
                return routeService;
            case INGRESS:
                return null; // ingressService;
            case LOAD_BALANCER:
                return null; // loadBalancerService;
            case INTERNAL:
                return internalService;
            default:
                return null;
        }
    }

    /**
     * Update the service based on the EndpointServiceType. Note that are currently only
     * implementing Routes/NodePort/Regular services so defaults to returning an internal service when
     * LoadBalancer and Ingress are specified.
     * @param type the EndpointServiceType of service wanted
     */
    private Service updateServiceValueFromType(EndpointServiceType type, Service value) {
        value = value.getSpec().getPorts().size() > 0 ? value : null;
        switch (type) {
            case NODE_PORT:
                nodePortService = value;
                break;
            case ROUTE:
                routeService = value;
                break;
            default:
                internalService = value;
        }
        return value;
    }

    /**
     * Create separate routes for each endpoint that is configured with the Route EndpointServiceType. This will
     * create a route with the endpoint's name at the end of the route.
     * @return
     */
    protected Map<String, Route> createRoutesFromEndpoints() {
        return endpoints.stream()
            .filter(endpoint -> EndpointServiceType.ROUTE.equals(endpoint.getType()))
            .collect(Collectors.toMap(endpoint ->
                    getRouteName(endpoint.getName()),
                endpoint -> {
                    TLSConfig tlsConfig = endpoint.isTls() ? getDefaultTlsConfig() : null;
                    return createRoute(getRouteName(endpoint.getName()), endpoint.getHost(), getServiceName(endpoint.getType()), endpoint.getPort(), tlsConfig, securityLabels(endpoint.isTls(), endpoint.getAuthenticationMechanisms()));
                }));
    }

    /**
     * Creates the required volumes needed set up the TrustStore and Mutual TLS pieces for an endpoint. Cluster CA and
     * Client CA are needed for Mutual TLS. User cert is needed for Pod To Pod and the Cert volume is necessary to
     * for an external endpoint to be configured with a client's certificate.
     * @return the necessary volumes to mount
     */
    protected List<Volume> securityVolumes() {
        List<Volume> volumes = new ArrayList<>();

        volumes.add(certsVolume());

        volumes.add(new VolumeBuilder()
            .withNewName(CLUSTER_CA_VOLUME_MOUNT_NAME)
            .withNewSecret()
                .withNewSecretName(EventStreamsKafkaModel.getKafkaClusterCaCertName(getInstanceName()))
                .addNewItem().withNewKey(CA_CERT).withNewPath(CA_CERT).endItem()
                .addNewItem().withNewKey(CA_P12).withNewPath(CA_P12).endItem()
                .addNewItem().withNewKey(CA_P12_PASS).withNewPath(CA_P12_PASS).endItem()
            .endSecret()
            .build());

        volumes.add(new VolumeBuilder()
            .withNewName(CLIENT_CA_VOLUME_MOUNT_NAME)
            .withNewSecret()
                .withNewSecretName(EventStreamsKafkaModel.getKafkaClientCaCertName(getInstanceName()))
                .addNewItem().withNewKey(CA_P12).withNewPath(CA_P12).endItem()
                .addNewItem().withNewKey(CA_CERT).withNewPath(CA_CERT).endItem()
            .endSecret()
            .build());

        volumes.add(createKafkaUserCertVolume());

        return volumes;
    }

    protected Volume certsVolume() {
        return new VolumeBuilder()
                .withNewName(CERTS_VOLUME_MOUNT_NAME)
                .withNewSecret()
                    .withNewSecretName(getCertificateSecretName()) //mount everything in the secret into this volume
                .endSecret()
                .build();
    }

    protected Volume hmacVolume() {
        return new VolumeBuilder()
                .withNewName(HMAC_VOLUME_MOUNT_NAME)
                .withNewSecret()
                    .withNewSecretName(MessageAuthenticationModel.getSecretName(getInstanceName()))
                    .addNewItem().withNewKey(MessageAuthenticationModel.HMAC_SECRET).withNewPath(MessageAuthenticationModel.HMAC_SECRET).endItem()
                .endSecret()
                .build();
    }

    protected VolumeMount hmacVolumeMount() {
        return new VolumeMountBuilder()
                .withNewName(HMAC_VOLUME_MOUNT_NAME)
                .withMountPath(HMAC_ENVIRONMENT_PATH)
                .withNewReadOnly(true)
                .build();
    }

    /**
     * Creates the volume mounts to mount the Certificates in the necessary paths to configure component.
     * @param containerBuilder
     */
    public void configureSecurityVolumeMounts(ContainerBuilder containerBuilder) {
        containerBuilder
            .addNewVolumeMount()
                .withNewName(CERTS_VOLUME_MOUNT_NAME)
                .withMountPath(CERTIFICATE_PATH)
                .withNewReadOnly(true)
            .endVolumeMount()
            .addNewVolumeMount()
                .withNewName(CLUSTER_CA_VOLUME_MOUNT_NAME)
                .withMountPath(CLUSTER_CERTIFICATE_PATH)
                .withNewReadOnly(true)
            .endVolumeMount()
            .addNewVolumeMount()
                .withNewName(CLIENT_CA_VOLUME_MOUNT_NAME)
                .withMountPath(CLIENT_CA_CERTIFICATE_PATH)
                .withNewReadOnly(true)
            .endVolumeMount()
            .addNewVolumeMount()
                .withNewName(KAFKA_USER_SECRET_VOLUME_NAME)
                .withMountPath(KAFKA_USER_CERTIFICATE_PATH)
                .withNewReadOnly(true)
            .endVolumeMount();
    }

    protected EnvVar hmacSecretEnvVar() {
        return new EnvVarBuilder()
                .withName("HMAC_SECRET")
                .withValue("file://" + HMAC_ENVIRONMENT_PATH + File.separator + MessageAuthenticationModel.HMAC_SECRET)
                .build();
    }

    /**
     * Method to create appropriate AUTHENTICATION and ENDPOINTS env vars based on the configuration of the endpoints.
     */
    public void configureSecurityEnvVars(List<EnvVar> envVars) {
        envVars.add(new EnvVarBuilder()
            .withName(AUTHENTICATION_ENV_VAR_KEY)
            .withValue(endpoints.stream()
                .map(getAuthorizationEnvValue())
                .collect(Collectors.joining(PORT_SEPARATOR)))
            .build());

        envVars.add(new EnvVarBuilder()
            .withName(ENDPOINTS_ENV_VAR_KEY)
            .withValue(endpoints.stream()
                .map(getEndpointsEnvValue())
                .collect(Collectors.joining(PORT_SEPARATOR)))
            .build());

        envVars.add(new EnvVarBuilder()
            .withName(TLS_VERSION_ENV_VAR_KEY)
            .withValue(endpoints.stream()
                .map(getTlsVersionEnvValue())
                .collect(Collectors.joining(PORT_SEPARATOR)))
            .build());

        envVars.addAll(Arrays.asList(
            new EnvVarBuilder().withName(SSL_TRUSTSTORE_P12_PATH_ENV_KEY).withValue(CLUSTER_CERTIFICATE_PATH + File.separator + CA_P12).build(),
            new EnvVarBuilder().withName(SSL_TRUSTSTORE_CRT_PATH_ENV_KEY).withValue(CLUSTER_CERTIFICATE_PATH + File.separator + CA_CERT).build(),
            new EnvVarBuilder().withName(SSL_TRUSTSTORE_P12_PASSWORD_ENV_KEY).withValue("file://" + CLUSTER_CERTIFICATE_PATH + File.separator + CA_P12_PASS).build(),
            new EnvVarBuilder().withName(CLIENT_CA_PATH_ENV_KEY).withValue(CLIENT_CA_CERTIFICATE_PATH + File.separator + CA_CERT).build(),
            new EnvVarBuilder().withName(SSL_KEYSTORE_PATH_ENV_KEY).withValue(KAFKA_USER_CERTIFICATE_PATH + File.separator + USER_P12).build(),
            new EnvVarBuilder().withName(SSL_KEYSTORE_PASSWORD_PATH_ENV_KEY).withValue("file://" + KAFKA_USER_CERTIFICATE_PATH + File.separator + USER_P12_PASS).build(),
            new EnvVarBuilder().withName(SSL_ENABLED_ENV_KEY).withValue(tlsEnabled().toString()).build(),
            new EnvVarBuilder().withName(AUTHORIZATION_ENABLED_ENV_KEY).withValue(Boolean.toString(kafkaAuthorizationEnabled)).build()
        ));
    }

    /**
     * Creates the AUTHENTICATION Env Var value which determines which an endpoints authentication mechanisms
     */
    private Function<Endpoint, String> getAuthorizationEnvValue() {
        return endpoint -> {
            if (endpoint.getAuthenticationMechanisms().size() > 0) {
                return String.format("%d%s%s", endpoint.getPort(), KEY_VALUE_SEPARATOR, String.join(AUTHENTICATION_SEPARATOR, endpoint.getAuthenticationMechanisms()));
            } else {
                return Integer.toString(endpoint.getPort());
            }
        };
    }

    /**
     * Creates the ENDPOINTS Env Var value which determines which endpoint will present a specific certificate to
     * the user when performing TLS communication.
     */
    private Function<Endpoint, String> getEndpointsEnvValue() {
        return endpoint -> {
            if (endpoint.isTls()) {
                return String.format("%d%s%s", endpoint.getPort(), KEY_VALUE_SEPARATOR, endpoint.getName());
            } else {
                return Integer.toString(endpoint.getPort());
            }
        };
    }

    private Function<Endpoint, String> getTlsVersionEnvValue() {
        return endpoint -> {
            if (endpoint.isTls()) {
                return String.format("%d:%s", endpoint.getPort(), endpoint.getTlsVersion().toValue());
            } else {
                return Integer.toString(endpoint.getPort());
            }
        };
    }

    /**
     * Returns a list of TLS endpoints that are not named the Pod to Pod Tls name.
     * @return a filtered list of endpoints
     */
    public List<Endpoint> getTlsEndpoints() {
        return endpoints
            .stream()
            .filter(Endpoint::isTls)
            .collect(Collectors.toList());
    }

    /**
     * @return routes accessible to the user
     */
    public Map<String, Route> getRoutes() {
        return routes;
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


    public static String getExternalServiceName(String instanceName, String componentName) {
        return getDefaultResourceNameWithSuffix(ROUTE_SERVICE_SUFFIX, instanceName, componentName);
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public List<Service> getSecurityServices() {
        return Arrays.asList(nodePortService, routeService, internalService);
    }

    public void setCertAndKey(String name, CertAndKey certAndKey) {
        certificateSecretModel.setCertAndKey(name, certAndKey);
    }

    public String getCertificateSecretName() {
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
}