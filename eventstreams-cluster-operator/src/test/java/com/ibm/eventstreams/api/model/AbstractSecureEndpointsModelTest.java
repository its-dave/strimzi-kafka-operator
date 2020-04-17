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
import com.ibm.eventstreams.api.TlsVersion;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EndpointSpec;
import com.ibm.eventstreams.api.spec.EndpointSpecBuilder;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.EventStreamsSpecBuilder;
import com.ibm.eventstreams.api.spec.SecurityComponentSpec;
import com.ibm.eventstreams.api.spec.SecurityComponentSpecBuilder;
import com.ibm.eventstreams.api.spec.SecuritySpecBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.openshift.api.model.Route;
import io.strimzi.api.kafka.model.CertAndKeySecretSourceBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.ibm.eventstreams.api.model.AbstractModel.AUTHENTICATION_LABEL_NO_AUTH;
import static com.ibm.eventstreams.api.model.AbstractModel.AUTHENTICATION_LABEL_SEPARATOR;
import static com.ibm.eventstreams.api.model.AbstractModel.KAFKA_USER_SECRET_VOLUME_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;

public class AbstractSecureEndpointsModelTest {
    private final String instanceName = "test-instance";

    // Extend AbstractSecureEndpointsModel to test the abstract class
    private class ComponentModel extends AbstractSecureEndpointsModel {
        public static final String COMPONENT_NAME = "test";

        public ComponentModel(EventStreams instance, SecurityComponentSpec spec) {
            super(instance, spec, COMPONENT_NAME);
            setTlsVersion(TlsVersion.TLS_V1_2);
            setOwnerReference(instance);
        }
    }

    private EndpointSpec basicEndpointSpec = new EndpointSpecBuilder()
        .withName("required-field")
        .build();

    private EndpointSpec basicPlainEndpointSpec = new EndpointSpecBuilder()
        .withName("basic-tls")
        .withTlsVersion(TlsVersion.NONE)
        .build();

    private EndpointSpec configuredEndpointsSpec = new EndpointSpecBuilder()
        .withName("fully-configured")
        .withAccessPort(8080)
        .withType(EndpointServiceType.NODE_PORT)
        .withTlsVersion(TlsVersion.TLS_V1_3)
        .withCertOverrides(new CertAndKeySecretSourceBuilder()
            .withCertificate("random-cert")
            .withKey("random-key")
            .withSecretName("random-secret")
            .build())
        .withAuthenticationMechanisms(Collections.singletonList("TLS"))
        .build();

    @Test
    public void testCreationOfServicesFromDefaultConfigurationOfEndpoints() {
        EventStreamsSpec spec = new EventStreamsSpecBuilder()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.TLS_V1_2).build())
            .build();

        EventStreams instance = ModelUtils.createEventStreams(instanceName, spec).build();

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(basicEndpointSpec)
            .build();

        ComponentModel model = new ComponentModel(instance, securityComponentSpec);

        model.createService(EndpointServiceType.INTERNAL);
        model.createService(EndpointServiceType.ROUTE);
        model.createService(EndpointServiceType.NODE_PORT);
        List<Service> services = model.getSecurityServices();

        assertThat(services, hasSize(3));
        assertThat(services.get(0), is(nullValue()));

        assertThat(services.get(1).getSpec().getPorts(), hasSize(1));
        assertThat(services.get(1).getSpec().getPorts().get(0).getPort(), is(9443));
        assertThat(services.get(1).getSpec().getPorts().get(0).getName(), is(basicEndpointSpec.getName()));
        assertThat(services.get(1).getSpec().getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(services.get(1).getSpec().getPorts().get(0).getNodePort(), is(nullValue()));

        assertThat(services.get(2).getSpec().getPorts().get(0).getPort(), is(7443));
        assertThat(services.get(2).getSpec().getPorts().get(0).getName(), is("p2ptls"));
        assertThat(services.get(2).getSpec().getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(services.get(2).getSpec().getPorts().get(0).getNodePort(), is(nullValue()));


        assertThat(model.getSecurityService(EndpointServiceType.NODE_PORT), is(nullValue()));
        assertThat(model.getSecurityService(EndpointServiceType.ROUTE), is(notNullValue()));
        assertThat(model.getSecurityService(EndpointServiceType.INTERNAL), is(notNullValue()));
    }

    @Test
    public void testCreationOfEndpointFromMinimalConfigurationOfEndpoint() {
        EventStreamsSpec spec = new EventStreamsSpecBuilder()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.TLS_V1_2).build())
            .build();

        EventStreams instance = ModelUtils.createEventStreams(instanceName, spec).build();

        EndpointSpec minimalSpec = new EndpointSpecBuilder()
            .withName("minimal")
            .withAccessPort(9999)
            .build();

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(minimalSpec)
            .build();

        ComponentModel model = new ComponentModel(instance, securityComponentSpec);

        List<Endpoint> endpoint = model.getEndpoints();

        assertThat(endpoint, hasSize(2));

        assertThat(endpoint.get(0).getName(), is(minimalSpec.getName()));
        assertThat(endpoint.get(0).getPort(), is(minimalSpec.getAccessPort()));
        assertThat(endpoint.get(0).isTls(), is(true));
        assertThat(endpoint.get(0).getType(), is(EndpointServiceType.ROUTE));

        assertThat(endpoint.get(1).getName(), is("p2ptls"));
        assertThat(endpoint.get(1).getPort(), is(7443));
        assertThat(endpoint.get(1).isTls(), is(true));
        assertThat(endpoint.get(1).getType(), is(EndpointServiceType.INTERNAL));

    }

    @Test
    public void testCreationOfServicesFromDefaultNonTlsEventStreams() {
        EventStreamsSpec spec = new EventStreamsSpecBuilder()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.NONE).build())
            .build();

        EventStreams instance = ModelUtils.createEventStreams(instanceName, spec).build();

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(basicEndpointSpec)
            .build();

        ComponentModel model = new ComponentModel(instance, securityComponentSpec);

        model.createService(EndpointServiceType.INTERNAL);
        model.createService(EndpointServiceType.ROUTE);
        model.createService(EndpointServiceType.NODE_PORT);

        List<Service> services = model.getSecurityServices();

        assertThat(services, hasSize(3));
        assertThat(services.get(0), is(nullValue()));

        assertThat(services.get(1).getSpec().getPorts(), hasSize(1));
        assertThat(services.get(1).getSpec().getPorts().get(0).getPort(), is(9443));
        assertThat(services.get(1).getSpec().getPorts().get(0).getName(), is(basicEndpointSpec.getName()));
        assertThat(services.get(1).getSpec().getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(services.get(1).getSpec().getPorts().get(0).getNodePort(), is(nullValue()));

        assertThat(services.get(2).getSpec().getPorts().get(0).getPort(), is(7080));
        assertThat(services.get(2).getSpec().getPorts().get(0).getName(), is("pod2pod"));
        assertThat(services.get(2).getSpec().getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(services.get(2).getSpec().getPorts().get(0).getNodePort(), is(nullValue()));

        assertThat(model.getSecurityService(EndpointServiceType.NODE_PORT), is(nullValue()));
        assertThat(model.getSecurityService(EndpointServiceType.ROUTE), is(notNullValue()));
        assertThat(model.getSecurityService(EndpointServiceType.INTERNAL), is(notNullValue()));
    }

    @Test
    public void testCreationOfServicesFromFullyConfiguredOfEndpoint() {
        EventStreamsSpec spec = new EventStreamsSpecBuilder()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.TLS_V1_2).build())
            .build();

        EventStreams instance = ModelUtils.createEventStreams(instanceName, spec).build();

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(configuredEndpointsSpec)
            .build();

        ComponentModel model = new ComponentModel(instance, securityComponentSpec);

        model.createService(EndpointServiceType.INTERNAL);
        model.createService(EndpointServiceType.ROUTE);
        model.createService(EndpointServiceType.NODE_PORT);
        List<Service> services = model.getSecurityServices();

        assertThat(services, hasSize(3));
        assertThat(services.get(0).getSpec().getPorts().get(0).getPort(), is(configuredEndpointsSpec.getAccessPort()));
        assertThat(services.get(0).getSpec().getPorts().get(0).getName(), is(configuredEndpointsSpec.getName()));
        assertThat(services.get(0).getSpec().getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(services.get(0).getSpec().getPorts().get(0).getNodePort(), is(nullValue()));

        assertThat(services.get(1), is(nullValue()));

        assertThat(services.get(2).getSpec().getPorts().get(0).getPort(), is(7443));
        assertThat(services.get(2).getSpec().getPorts().get(0).getName(), is("p2ptls"));
        assertThat(services.get(2).getSpec().getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(services.get(2).getSpec().getPorts().get(0).getNodePort(), is(nullValue()));

        assertThat(model.getSecurityService(EndpointServiceType.NODE_PORT), is(notNullValue()));
        assertThat(model.getSecurityService(EndpointServiceType.ROUTE), is(nullValue()));
        assertThat(model.getSecurityService(EndpointServiceType.INTERNAL), is(notNullValue()));
    }

    @Test
    public void testCreationOfServicesFromMultipleEndpointConfigurations() {
        EventStreamsSpec spec = new EventStreamsSpecBuilder()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.TLS_V1_2).build())
            .build();

        EventStreams instance = ModelUtils.createEventStreams(instanceName, spec).build();

        List<EndpointSpec> endpointSpecs = new ArrayList<>();
        endpointSpecs.add(basicPlainEndpointSpec);
        endpointSpecs.add(configuredEndpointsSpec);

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(basicPlainEndpointSpec, configuredEndpointsSpec)
            .build();

        ComponentModel model = new ComponentModel(instance, securityComponentSpec);

        model.createService(EndpointServiceType.INTERNAL);
        model.createService(EndpointServiceType.ROUTE);
        model.createService(EndpointServiceType.NODE_PORT);
        List<Service> services = model.getSecurityServices();

        assertThat(services, hasSize(3));

        assertThat(services.get(0).getSpec().getPorts(), hasSize(1));
        assertThat(services.get(0).getSpec().getPorts().get(0).getPort(), is(configuredEndpointsSpec.getAccessPort()));
        assertThat(services.get(0).getSpec().getPorts().get(0).getName(), is(configuredEndpointsSpec.getName()));
        assertThat(services.get(0).getSpec().getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(services.get(0).getSpec().getPorts().get(0).getNodePort(), is(nullValue()));

        assertThat(services.get(1).getSpec().getPorts(), hasSize(1));
        assertThat(services.get(1).getSpec().getPorts().get(0).getPort(), is(9080));
        assertThat(services.get(1).getSpec().getPorts().get(0).getName(), is(basicPlainEndpointSpec.getName()));
        assertThat(services.get(1).getSpec().getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(services.get(1).getSpec().getPorts().get(0).getNodePort(), is(nullValue()));

        assertThat(services.get(2).getSpec().getPorts().get(0).getPort(), is(7443));
        assertThat(services.get(2).getSpec().getPorts().get(0).getName(), is("p2ptls"));
        assertThat(services.get(2).getSpec().getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(services.get(2).getSpec().getPorts().get(0).getNodePort(), is(nullValue()));


        assertThat(model.getSecurityService(EndpointServiceType.NODE_PORT), is(notNullValue()));
        assertThat(model.getSecurityService(EndpointServiceType.ROUTE), is(notNullValue()));
        assertThat(model.getSecurityService(EndpointServiceType.INTERNAL), is(notNullValue()));
    }

    @Test
    public void testCreationOfVolumesWithNoCertOverrides() {
        EventStreamsSpec spec = new EventStreamsSpecBuilder()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.TLS_V1_2).build())
            .build();

        EventStreams instance = ModelUtils.createEventStreams(instanceName, spec).build();

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(basicEndpointSpec)
            .build();

        ComponentModel model = new ComponentModel(instance, securityComponentSpec);

        List<Volume> volumes = model.getSecurityVolumes();

        assertThat(volumes, hasSize(4));

        assertThat(volumes.get(0).getName(), is("certs"));
        assertThat(volumes.get(0).getSecret().getSecretName(), is("test-instance-ibm-es-test-cert"));

        assertThat(volumes.get(1).getName(), is("cluster-ca"));
        assertThat(volumes.get(1).getSecret().getSecretName(), is(EventStreamsKafkaModel.getKafkaClusterCaCertName(instanceName)));
        assertThat(volumes.get(1).getSecret().getItems(), hasSize(2));
        assertThat(volumes.get(1).getSecret().getItems().get(0).getKey(), is("ca.crt"));
        assertThat(volumes.get(1).getSecret().getItems().get(0).getPath(), is("ca.crt"));
        assertThat(volumes.get(1).getSecret().getItems().get(1).getKey(), is("ca.p12"));
        assertThat(volumes.get(1).getSecret().getItems().get(1).getPath(), is("ca.p12"));

        assertThat(volumes.get(2).getName(), is("client-ca"));
        assertThat(volumes.get(2).getSecret().getSecretName(), is(EventStreamsKafkaModel.getKafkaClientCaCertName(instanceName)));
        assertThat(volumes.get(2).getSecret().getItems(), hasSize(2));
        assertThat(volumes.get(2).getSecret().getItems().get(0).getKey(), is("ca.p12"));
        assertThat(volumes.get(2).getSecret().getItems().get(0).getPath(), is("ca.p12"));
        assertThat(volumes.get(2).getSecret().getItems().get(1).getKey(), is("ca.crt"));
        assertThat(volumes.get(2).getSecret().getItems().get(1).getPath(), is("ca.crt"));

        assertThat(volumes.get(3).getName(), is(KAFKA_USER_SECRET_VOLUME_NAME));
        assertThat(volumes.get(3).getSecret().getSecretName(), is(String.format("%s-ibm-es-kafka-user", instanceName)));
        assertThat(volumes.get(3).getSecret().getItems(), hasSize(3));
        assertThat(volumes.get(3).getSecret().getItems().get(0).getKey(), is("user.crt"));
        assertThat(volumes.get(3).getSecret().getItems().get(0).getPath(), is("podtls.crt"));
        assertThat(volumes.get(3).getSecret().getItems().get(1).getKey(), is("user.key"));
        assertThat(volumes.get(3).getSecret().getItems().get(1).getPath(), is("podtls.key"));
        assertThat(volumes.get(3).getSecret().getItems().get(2).getKey(), is("user.p12"));
        assertThat(volumes.get(3).getSecret().getItems().get(2).getPath(), is("podtls.p12"));
    }

    @Test
    public void testCreationOfVolumesWithCertOverrides() {
        EventStreamsSpec spec = new EventStreamsSpecBuilder()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.TLS_V1_2).build())
            .build();

        EventStreams instance = ModelUtils.createEventStreams(instanceName, spec).build();

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(basicPlainEndpointSpec, configuredEndpointsSpec)
            .build();

        ComponentModel model = new ComponentModel(instance, securityComponentSpec);
        List<Volume> volumes = model.getSecurityVolumes();

        assertThat(volumes, hasSize(4));

        assertThat(volumes.get(0).getName(), is("certs"));
        assertThat(volumes.get(0).getSecret().getSecretName(), is("test-instance-ibm-es-test-cert"));

        assertThat(volumes.get(1).getName(), is("cluster-ca"));
        assertThat(volumes.get(1).getSecret().getSecretName(), is(EventStreamsKafkaModel.getKafkaClusterCaCertName(instanceName)));
        assertThat(volumes.get(1).getSecret().getItems(), hasSize(2));
        assertThat(volumes.get(1).getSecret().getItems().get(0).getKey(), is("ca.crt"));
        assertThat(volumes.get(1).getSecret().getItems().get(0).getPath(), is("ca.crt"));
        assertThat(volumes.get(1).getSecret().getItems().get(1).getKey(), is("ca.p12"));
        assertThat(volumes.get(1).getSecret().getItems().get(1).getPath(), is("ca.p12"));

        assertThat(volumes.get(2).getName(), is("client-ca"));
        assertThat(volumes.get(2).getSecret().getSecretName(), is(EventStreamsKafkaModel.getKafkaClientCaCertName(instanceName)));
        assertThat(volumes.get(2).getSecret().getItems(), hasSize(2));
        assertThat(volumes.get(2).getSecret().getItems().get(0).getKey(), is("ca.p12"));
        assertThat(volumes.get(2).getSecret().getItems().get(0).getPath(), is("ca.p12"));

        assertThat(volumes.get(3).getName(), is(KAFKA_USER_SECRET_VOLUME_NAME));
        assertThat(volumes.get(3).getSecret().getSecretName(), is(String.format("%s-ibm-es-kafka-user", instanceName)));
        assertThat(volumes.get(3).getSecret().getItems(), hasSize(3));
        assertThat(volumes.get(3).getSecret().getItems().get(0).getKey(), is("user.crt"));
        assertThat(volumes.get(3).getSecret().getItems().get(0).getPath(), is("podtls.crt"));
        assertThat(volumes.get(3).getSecret().getItems().get(1).getKey(), is("user.key"));
        assertThat(volumes.get(3).getSecret().getItems().get(1).getPath(), is("podtls.key"));
        assertThat(volumes.get(3).getSecret().getItems().get(2).getKey(), is("user.p12"));
        assertThat(volumes.get(3).getSecret().getItems().get(2).getPath(), is("podtls.p12"));
    }

    @Test
    public void testCreateVolumeMount() {
        EventStreamsSpec spec = new EventStreamsSpecBuilder()
            .withSecurity(new SecuritySpecBuilder()
                .withInternalTls(TlsVersion.NONE).build())
            .build();

        EventStreams instance = ModelUtils.createEventStreams(instanceName, spec).build();

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(basicPlainEndpointSpec)
            .build();
        ComponentModel model = new ComponentModel(instance, securityComponentSpec);

        ContainerBuilder containerBuilder = new ContainerBuilder();
        model.configureSecurityVolumeMounts(containerBuilder);

        Container container = containerBuilder.build();

        assertThat(container.getVolumeMounts(), hasSize(4));

        assertThat(container.getVolumeMounts().get(0).getName(), is("certs"));
        assertThat(container.getVolumeMounts().get(0).getMountPath(), is("/certs"));
        assertThat(container.getVolumeMounts().get(0).getReadOnly(), is(true));

        assertThat(container.getVolumeMounts().get(1).getName(), is("cluster-ca"));
        assertThat(container.getVolumeMounts().get(1).getMountPath(), is("/certs/cluster"));
        assertThat(container.getVolumeMounts().get(1).getReadOnly(), is(true));

        assertThat(container.getVolumeMounts().get(2).getName(), is("client-ca"));
        assertThat(container.getVolumeMounts().get(2).getMountPath(), is("/certs/client"));
        assertThat(container.getVolumeMounts().get(2).getReadOnly(), is(true));

        assertThat(container.getVolumeMounts().get(3).getName(), is("kafka-user"));
        assertThat(container.getVolumeMounts().get(3).getMountPath(), is("/certs/p2p"));
        assertThat(container.getVolumeMounts().get(3).getReadOnly(), is(true));
    }

    @Test
    public void testCreationOfEnvVarsWithNoOverides() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();

        ComponentModel model = new ComponentModel(instance, new SecurityComponentSpec());

        List<EnvVar> envVars = new ArrayList<>();
        model.configureSecurityEnvVars(envVars);

        assertThat(envVars, hasSize(3));

        assertThat(envVars.get(0).getName(), is("AUTHENTICATION"));
        assertThat(envVars.get(0).getValue(), is("9443,7080"));
        assertThat(envVars.get(1).getName(), is("ENDPOINTS"));
        assertThat(envVars.get(1).getValue(), is("9443:external,7080"));
        assertThat(envVars.get(2).getName(), is("TLS_VERSION"));
        assertThat(envVars.get(2).getValue(), is("9443:TLSv1.2,7080"));
    }

    @Test
    public void testCreationOfEnvVarsWithAuth() {
        EventStreams instance = ModelUtils.createEventStreamsWithAuthentication(instanceName).build();

        ComponentModel model = new ComponentModel(instance, new SecurityComponentSpec());

        List<EnvVar> envVars = new ArrayList<>();
        model.configureSecurityEnvVars(envVars);

        assertThat(envVars, hasSize(3));

        assertThat(envVars.get(0).getName(), is("AUTHENTICATION"));
        assertThat(envVars.get(0).getValue(), is("9443:IAM-BEARER;SCRAM-SHA-512,7080"));
        assertThat(envVars.get(1).getName(), is("ENDPOINTS"));
        assertThat(envVars.get(1).getValue(), is("9443:external,7080"));
        assertThat(envVars.get(2).getName(), is("TLS_VERSION"));
        assertThat(envVars.get(2).getValue(), is("9443:TLSv1.2,7080"));
    }

    @Test
    public void testCreationOfEnvVarsWithBasicOverrides() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(basicEndpointSpec)
            .build();

        ComponentModel model = new ComponentModel(instance, securityComponentSpec);

        List<EnvVar> envVars = new ArrayList<>();
        model.configureSecurityEnvVars(envVars);

        assertThat(envVars, hasSize(3));

        assertThat(envVars.get(0).getName(), is("AUTHENTICATION"));
        assertThat(envVars.get(0).getValue(), is("9443,7080"));
        assertThat(envVars.get(1).getName(), is("ENDPOINTS"));
        assertThat(envVars.get(1).getValue(), is("9443:required-field,7080"));
        assertThat(envVars.get(2).getName(), is("TLS_VERSION"));
        assertThat(envVars.get(2).getValue(), is("9443:TLSv1.2,7080"));
    }

    @Test
    public void testCreationOfEnvVarsWithOverrides() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(basicPlainEndpointSpec, configuredEndpointsSpec)
            .build();

        ComponentModel model = new ComponentModel(instance, securityComponentSpec);

        List<EnvVar> envVars = new ArrayList<>();
        model.configureSecurityEnvVars(envVars);

        assertThat(envVars, hasSize(3));

        assertThat(envVars.get(0).getName(), is("AUTHENTICATION"));
        assertThat(envVars.get(0).getValue(), is("9080,8080:TLS,7080"));
        assertThat(envVars.get(1).getName(), is("ENDPOINTS"));
        assertThat(envVars.get(1).getValue(), is("9080,8080:fully-configured,7080"));
        assertThat(envVars.get(2).getName(), is("TLS_VERSION"));
        assertThat(envVars.get(2).getValue(), is("9080,8080:TLSv1.3,7080"));
    }

    @Test
    public void testCreationOfRoutesWithNoOverrides() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance, new SecurityComponentSpec());

        Map<String, Route> routes = model.createRoutesFromEndpoints();

        assertThat(routes.size(), is(1));

        assertThat(routes.get(String.format("%s-ibm-es-%s-%s", instanceName, ComponentModel.COMPONENT_NAME, Endpoint.DEFAULT_EXTERNAL_NAME)), is(notNullValue()));
    }

    @Test
    public void testCreationOfRoutesWithOverrides() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();

        EndpointSpec longNameRouteSpec = new EndpointSpecBuilder()
            .withName("long-name")
            .withAccessPort(343)
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .build();

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(basicPlainEndpointSpec, basicEndpointSpec, configuredEndpointsSpec, longNameRouteSpec)
            .build();

        ComponentModel model = new ComponentModel(instance, securityComponentSpec);

        Map<String, Route> routes = model.createRoutesFromEndpoints();

        assertThat(routes.size(), is(3));

        assertThat(routes.get(String.format("%s-ibm-es-%s-%s", instanceName, ComponentModel.COMPONENT_NAME, basicEndpointSpec.getName())), is(notNullValue()));
        assertThat(routes.get(String.format("%s-ibm-es-%s-%s", instanceName, ComponentModel.COMPONENT_NAME, longNameRouteSpec.getName())), is(notNullValue()));
        assertThat(routes.get(String.format("%s-ibm-es-%s-%s", instanceName, ComponentModel.COMPONENT_NAME, basicPlainEndpointSpec.getName())), is(notNullValue()));
        assertThat(routes.get(String.format("%s-ibm-es-%s-%s", instanceName, ComponentModel.COMPONENT_NAME, configuredEndpointsSpec.getName())), is(nullValue()));
    }

    @Test
    public void testRoutesHaveValidLabels() {
        String openshiftLabelRegex = "^[a-zA-Z0-9][a-zA-Z0-9.-]+[a-zA-Z0-9]/(([A-Za-z0-9][-A-Za-z0-9_.]*)?[A-Za-z0-9])?";
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance, new SecurityComponentSpec());

        Map<String, Route> routes = model.createRoutesFromEndpoints();

        assertThat(routes, aMapWithSize(1));
        routes.forEach((key, route) -> {
            Set<String> labelKeys = route.getMetadata().getLabels().keySet();
            labelKeys.stream().filter(k -> k.contains(AbstractModel.EVENTSTREAMS_AUTHENTICATION_LABEL)).forEach(label -> {
                assertThat(label, Pattern.matches(openshiftLabelRegex, label), is(true));
            });
        });
    }

    @Test
    public void testCreateRoutesHasDefaultLabels() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance, new SecurityComponentSpec());

        Map<String, Route> routes = model.createRoutesFromEndpoints();

        assertThat(routes, aMapWithSize(1));

        routes.forEach((key, route) -> {
            assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + AUTHENTICATION_LABEL_NO_AUTH, "true"));
            assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_PROTOCOL_LABEL, "https"));
        });
    }

    @Test
    public void testCreateRoutesWithAuthenticationHasAuthLabels() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance, new SecurityComponentSpec());

        Map<String, Route> routes = model.createRoutesFromEndpoints();

        assertThat(routes, aMapWithSize(1));

        routes.forEach((key, route) -> {
            assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + AUTHENTICATION_LABEL_NO_AUTH, "true"));
            assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_PROTOCOL_LABEL, "https"));
        });
    }

    @Test
    public void testCreateRoutesWithTlsEndpointHasCorrectCustomLabels() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();

        EndpointSpec configuredEndpointsSpec = new EndpointSpecBuilder()
            .withName("fully-configured")
            .withAccessPort(8080)
            .withType(EndpointServiceType.ROUTE)
            .withTlsVersion(TlsVersion.TLS_V1_2)
            .withCertOverrides(new CertAndKeySecretSourceBuilder()
                .withCertificate("random-cert")
                .withKey("random-key")
                .withSecretName("random-secret")
                .build())
            .withAuthenticationMechanisms(Collections.singletonList("TLS"))
            .build();

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(configuredEndpointsSpec)
            .build();

        ComponentModel model = new ComponentModel(instance, securityComponentSpec);

        Map<String, Route> routes = model.createRoutesFromEndpoints();

        assertThat(routes, aMapWithSize(1));
        routes.forEach((key, route) -> {
            assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + "TLS", "true"));
            assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_PROTOCOL_LABEL, "https"));
        });
    }

    @Test
    public void testCreateRoutesWithoutTlsEndpointHasCorrectCustomLabels() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();

        EndpointSpec configuredEndpointsSpec = new EndpointSpecBuilder()
            .withName("fully-configured-without-tls")
            .withAccessPort(8080)
            .withType(EndpointServiceType.ROUTE)
            .withTlsVersion(TlsVersion.NONE)
            .withCertOverrides(new CertAndKeySecretSourceBuilder()
                .withCertificate("random-cert")
                .withKey("random-key")
                .withSecretName("random-secret")
                .build())
            .withAuthenticationMechanisms(Collections.singletonList("TLS"))
            .build();

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(configuredEndpointsSpec)
            .build();

        ComponentModel model = new ComponentModel(instance, securityComponentSpec);

        Map<String, Route> routes = model.createRoutesFromEndpoints();

        assertThat(routes, aMapWithSize(1));
        routes.forEach((key, route) -> {
            assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + "TLS", "true"));
            assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_PROTOCOL_LABEL, "http"));
        });
    }

    @Test
    public void testCreateRoutesWithoutAuthenticationEndpointHasCorrectLabels() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();

        EndpointSpec configuredEndpointsSpec = new EndpointSpecBuilder()
            .withName("configured-without-authentication")
            .withAccessPort(8080)
            .withType(EndpointServiceType.ROUTE)
            .withTlsVersion(TlsVersion.NONE)
            .withCertOverrides(new CertAndKeySecretSourceBuilder()
                .withCertificate("random-cert")
                .withKey("random-key")
                .withSecretName("random-secret")
                .build())
            .build();

        SecurityComponentSpec securityComponentSpec = new SecurityComponentSpecBuilder()
            .withEndpoints(configuredEndpointsSpec)
            .build();

        ComponentModel model = new ComponentModel(instance, securityComponentSpec);

        Map<String, Route> routes = model.createRoutesFromEndpoints();

        assertThat(routes, aMapWithSize(1));
        routes.forEach((key, route) -> {
            assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_AUTHENTICATION_LABEL + AUTHENTICATION_LABEL_SEPARATOR + AUTHENTICATION_LABEL_NO_AUTH, "true"));
            assertThat(route.getMetadata().getLabels(), hasEntry(AbstractModel.EVENTSTREAMS_PROTOCOL_LABEL, "http"));
        });
    }

}