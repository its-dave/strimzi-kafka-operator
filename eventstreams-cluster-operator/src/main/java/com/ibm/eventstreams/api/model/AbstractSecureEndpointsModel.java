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
import com.ibm.eventstreams.api.spec.EndpointSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.TLSConfig;
import io.strimzi.certs.CertAndKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractSecureEndpointsModel extends AbstractModel {
    public static final String NODE_PORT_SERVICE_SUFFIX = "node-port";
    public static final String ROUTE_SERVICE_SUFFIX = "external";
    public static final String INTERNAL_SERVICE_SUFFIX = "internal";

    public static final String CERT_GENERATION_KEY = "certificateGenerationID";

    private static final String KEY_VALUE_SEPARATOR = ":";
    private static final String AUTHENTICATION_SEPARATOR = ";";
    private static final String PORT_SEPARATOR = ",";

    private static final String AUTHENTICATION_ENV_VAR_KEY = "AUTHENTICATION";
    private static final String ENDPOINTS_ENV_VAR_KEY = "ENDPOINTS";

    public static final String CERTS_VOLUME_MOUNT_NAME = "certs";
    public static final String CLIENT_CA_VOLUME_MOUNT_NAME = "client-ca";
    public static final String CLUSTER_CA_VOLUME_MOUNT_NAME = "cluster-ca";
    public static final String CERTIFICATE_PATH = "/certs";
    public static final String KAFKA_USER_CERTIFICATE_PATH = CERTIFICATE_PATH + "/p2p";
    public static final String CLUSTER_CERTIFICATE_PATH = CERTIFICATE_PATH + "/cluster";
    public static final String CLIENT_CA_CERTIFICATE_PATH = CERTIFICATE_PATH + "/client";


    private final CertificateSecretModel certificateSecretModel;

    private List<Endpoint> endpoints;
    protected Map<String, Route> routes;

    private Service internalService;
    private Service routeService;
    private Service nodePortService;


    public AbstractSecureEndpointsModel(EventStreams instance, List<EndpointSpec> endpointSpecs, String namespace, String componentName) {
        super(instance.getMetadata().getName(), namespace, componentName);
        this.certificateSecretModel = new CertificateSecretModel(instance, namespace, componentName);
        this.endpoints = createEndpoints(instance, endpointSpecs);
        this.routes = createRoutesFromEndpoints();
    }

    /**
     * Method creates a model of the endpoints that will be created for the user to use and communicate to the component
     * with. If user has not configured endpoints, then a default endpoint will be created for the user which
     * will enable all Event Streams capabilities. A Pod-To-Pod endpoint is always created for Event Streams components
     * to communicate with one another.
     * @param instance The spec of the Event Streams CR
     * @param spec The list of endpoints passed into the component. This needs to be passed in to determine which
     *             component's endpoint (schema registry, admin rest, or rest producer) to configure.
     * @return list of secure endpoints
     */
    public List<Endpoint> createEndpoints(EventStreams instance, List<EndpointSpec> spec) {
        List<Endpoint> endpoints =  Optional.ofNullable(spec)
            .map(endpointSpec -> endpointSpec.stream().map(Endpoint.createEndpointFromSpec()).collect(Collectors.toList()))
            .orElse(new ArrayList<>(Collections.singletonList(Endpoint.createDefaultExternalEndpoint(instance))));

        endpoints.add(Endpoint.createP2PEndpoint(instance));

        return endpoints;
    }

    /**
     * Creates one service per type of service (NodePort, Route, Internal, Load Balancer, or Ingress) from endpoints.
     */
    public void createSecurityServices() {
        createServiceTypeFromEndpoints(EndpointServiceType.NODE_PORT);
        createServiceTypeFromEndpoints(EndpointServiceType.ROUTE);
        // TODO: Create Services for Loadbalancer and Ingress

        createServiceTypeFromEndpoints(EndpointServiceType.INTERNAL);
    }

    /**
     * Creates a single service per type of Service with all access ports of the same Service type configured
     * @param type the specified EndpointServiceType service.
     */
    private void createServiceTypeFromEndpoints(EndpointServiceType type) {
        List<ServicePort> ports = endpoints.stream()
            .filter(endpoint -> endpoint.getType() == type)
            .map(endpoint -> new ServicePortBuilder()
                .withName(endpoint.getName())
                .withNewProtocol("TCP")
                .withPort(endpoint.getPort())
                .build())
            .collect(Collectors.toList());

        updateServiceValueFromType(type, createService(type.toValue(), getServiceName(type), ports, Collections.emptyMap()));
    }

    /**
     * Returns the name of the service based on the specified EndpointServiceType
     * @param type the specified type of EndpointServiceType wanted
     * @return the name of the service
     */
    private String getServiceName(EndpointServiceType type) {
        return getDefaultResourceNameWithSuffix(getServiceSuffix(type));
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
            default:
                return INTERNAL_SERVICE_SUFFIX;
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
            default:
                return internalService;
        }
    }

    /**
     * Update the service based on the EndpointServiceType. Note that are currently only
     * implementing Routes/NodePort/Regular services so defaults to returning an internal service when
     * LoadBalancer and Ingress are specified.
     * @param type the EndpointServiceType of service wanted
     * @return the services that matches the EndpointService type
     */
    private void updateServiceValueFromType(EndpointServiceType type, Service value) {
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
    }

    /**
     * Create separate routes for each endpoint that is configured with the Route EndpointServiceType. This will
     * create a route with the endpoint's name at the end of the route.
     * @return
     */
    protected Map<String, Route> createRoutesFromEndpoints() {
        return endpoints.stream()
            .filter(endpoint -> endpoint.getType() == EndpointServiceType.ROUTE)
            .collect(Collectors.toMap(endpoint ->
                    getRouteName(endpoint.getName()),
                endpoint -> {
                    TLSConfig tlsConfig = endpoint.isTls() ? getDefaultTlsConfig() : null;
                    return createRoute(getRouteName(endpoint.getName()), getServiceName(endpoint.getType()), endpoint.getPort(), tlsConfig);
                }));
    }

    /**
     * Creates the required volumes needed set up the TrustStore and Mutual TLS pieces for an endpoint. Cluster CA and
     * Client CA are needed for Mutual TLS. User cert is needed for Pod To Pod and the Cert volume is necessary to
     * for an external endpoint to be configured with a client's certificate.
     * @return the necessary volumes to mount
     */
    protected List<Volume> getSecurityVolumes() {
        List<Volume> volumes = new ArrayList<>();

        volumes.add(new VolumeBuilder()
            .withNewName(CERTS_VOLUME_MOUNT_NAME)
            .withNewSecret()
            .withNewSecretName(getCertificateSecretName()) //mount everything in the secret into this volume
            .endSecret()
            .build());

        volumes.add(new VolumeBuilder()
            .withNewName(CLUSTER_CA_VOLUME_MOUNT_NAME)
            .withNewSecret()
            .withNewSecretName(EventStreamsKafkaModel.getKafkaClusterCaCertName(getInstanceName()))
            .addNewItem().withNewKey(CA_CERT).withNewPath("ca.crt").endItem()
            .addNewItem().withNewKey(CA_P12).withNewPath("ca.p12").endItem()
            .endSecret()
            .build());

        volumes.add(new VolumeBuilder()
            .withNewName(CLIENT_CA_VOLUME_MOUNT_NAME)
            .withNewSecret()
            .withNewSecretName(EventStreamsKafkaModel.getKafkaClientCaCertName(getInstanceName()))
            .addNewItem().withNewKey(CA_P12).withNewPath("ca.p12").endItem()
            .endSecret()
            .build());

        volumes.add(createKafkaUserCertVolume());

        return volumes;
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
                return String.format("%d%s%s", endpoint.getPort(), KEY_VALUE_SEPARATOR, endpoint.getPath());
            } else {
                return Integer.toString(endpoint.getPort());
            }
        };
    }

    /**
     * @return routes accessible to the user
     */
    public Map<String, Route> getRoutes() {
        return routes;
    }

    public static String getExternalServiceName(String instanceName, String componentName) {
        return getDefaultResourceNameWithSuffix(ROUTE_SERVICE_SUFFIX, instanceName, componentName);
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
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