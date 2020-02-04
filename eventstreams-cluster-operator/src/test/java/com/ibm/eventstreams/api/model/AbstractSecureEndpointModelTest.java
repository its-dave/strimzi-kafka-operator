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
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.Route;
import io.strimzi.certs.CertAndKey;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class AbstractSecureEndpointModelTest {

    private final String instanceName = "test-instance";

    // Extend AbstractSecureEndpointModel to test the abstract class
    private class ComponentModel extends AbstractSecureEndpointModel {
        public static final String COMPONENT_NAME = "test-component";

        public ComponentModel(EventStreams instance, List<Listener> listeners) {
            super(instance, instance.getMetadata().getNamespace(), COMPONENT_NAME, listeners);
            setEncryption(SecuritySpec.Encryption.TLS);
            setArchitecture(instance.getSpec().getArchitecture());
            setOwnerReference(instance);
            createServices();
            createRoutes();
        }
    }

    @Test
    public void testCertificateSecretNameAndIDs() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        Listener listener = new Listener("test-name", true, true, 8080, spec -> Optional.empty());
        ComponentModel model = new ComponentModel(instance, Collections.singletonList(listener));
        assertThat("Certificate secret name is generated correctly", model.getCertSecretName(), is(model.getDefaultResourceName() + "-" + CertificateSecretModel.CERT_SECRET_NAME_POSTFIX));
        assertThat("Certificate secret cert ID is generated correctly", model.getCertSecretCertID(listener.getName()), is(listener.getName() + "." + CertificateSecretModel.CERT_ID_POSTFIX));
        assertThat("Certificate secret key ID id generated correctly", model.getCertSecretKeyID(listener.getName()), is(listener.getName() + "." + CertificateSecretModel.KEY_ID_POSTFIX));
    }

    @Test
    public void testCertificateSecretCreation() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        Listener listener = new Listener("test-name", true, true, 8080, spec -> Optional.empty());
        ComponentModel model = new ComponentModel(instance, Collections.singletonList(listener));
        String certData = "test-cert";
        String keyData = "test-key";
        CertAndKey certAndKey = new CertAndKey(keyData.getBytes(), certData.getBytes());
        model.setCertAndKey(listener.getName(), certAndKey);
        Secret certSecret = model.getCertSecret();
        assertThat("Secret contains the cert data base64 encoded", new String(Base64.getDecoder().decode(certSecret.getData().get(model.getCertSecretCertID(listener.getName())))), is(certData));
        assertThat("Secret contains the key data base64 encoded", new String(Base64.getDecoder().decode(certSecret.getData().get(model.getCertSecretKeyID(listener.getName())))), is(keyData));
        assertThat("Secret name is correct", certSecret.getMetadata().getName(), is(model.getCertSecretName()));
    }

    @Test
    public void testCreateServicesWithNoListeners() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        ComponentModel model = new ComponentModel(instance, Collections.emptyList());

        Service externalService = model.getExternalService();
        assertThat(externalService.getMetadata().getName(), is(model.getDefaultResourceName() + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_POSTFIX));
        assertThat(externalService.getSpec().getType(), is("ClusterIP"));
        assertThat(externalService.getSpec().getPorts().size(), is(0));

        Service internalService = model.getInternalService();
        assertThat(internalService.getMetadata().getName(), is(model.getDefaultResourceName() + "-" + AbstractSecureEndpointModel.INTERNAL_SERVICE_POSTFIX));
        assertThat(internalService.getSpec().getType(), is("ClusterIP"));
        assertThat(internalService.getSpec().getPorts().size(), is(1));
        assertThat(internalService.getSpec().getPorts().get(0).getName(), is(model.getComponentName() + "-" + Listener.podToPodListener(true).getName()));
        assertThat(internalService.getSpec().getPorts().get(0).getPort(), is(Listener.podToPodListener(true).getPort()));
        assertThat(internalService.getSpec().getPorts().get(0).getProtocol(), is("TCP"));
    }

    @Test
    public void testCreateServicesWithOneInternalListener() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        Listener listener = new Listener("test-internal-name", true, false, 8080, spec -> Optional.empty());
        ComponentModel model = new ComponentModel(instance, Collections.singletonList(listener));

        Service externalService = model.getExternalService();
        assertThat(externalService.getMetadata().getName(), is(model.getDefaultResourceName() + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_POSTFIX));
        assertThat(externalService.getSpec().getType(), is("ClusterIP"));
        assertThat(externalService.getSpec().getPorts().size(), is(0));

        Service internalService = model.getInternalService();
        assertThat(internalService.getMetadata().getName(), is(model.getDefaultResourceName() + "-" + AbstractSecureEndpointModel.INTERNAL_SERVICE_POSTFIX));
        assertThat(internalService.getSpec().getType(), is("ClusterIP"));
        assertThat(internalService.getSpec().getPorts().size(), is(2));
        boolean foundPodToPodPort = internalService.getSpec().getPorts().stream().anyMatch(port -> {
            return port.getName().equals(model.getComponentName() + "-" + Listener.podToPodListener(true).getName()) &&
                    port.getPort() == Listener.podToPodListener(true).getPort() &&
                    port.getProtocol().equals("TCP");
        });
        boolean foundListenerPort = internalService.getSpec().getPorts().stream().anyMatch(port -> {
            return port.getName().equals(model.getComponentName() + "-" + listener.getName()) &&
                    port.getPort() == listener.getPort() &&
                    port.getProtocol().equals("TCP");
        });
        assertThat(foundPodToPodPort, is(true));
        assertThat(foundListenerPort, is(true));
    }

    @Test
    public void testCreateServicesWithOneExternalListener() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        Listener listener = new Listener("test-external-name", true, true, 9080, spec -> Optional.empty());
        ComponentModel model = new ComponentModel(instance, Collections.singletonList(listener));

        Service externalService = model.getExternalService();
        assertThat(externalService.getMetadata().getName(), is(model.getDefaultResourceName() + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_POSTFIX));
        assertThat(externalService.getSpec().getType(), is("ClusterIP"));
        assertThat(externalService.getSpec().getPorts().size(), is(1));
        assertThat(externalService.getSpec().getPorts().get(0).getName(), is(model.getComponentName() + "-" + listener.getName()));
        assertThat(externalService.getSpec().getPorts().get(0).getPort(), is(listener.getPort()));
        assertThat(externalService.getSpec().getPorts().get(0).getProtocol(), is("TCP"));

        Service internalService = model.getInternalService();
        assertThat(internalService.getMetadata().getName(), is(model.getDefaultResourceName() + "-" + AbstractSecureEndpointModel.INTERNAL_SERVICE_POSTFIX));
        assertThat(internalService.getSpec().getType(), is("ClusterIP"));
        assertThat(internalService.getSpec().getPorts().size(), is(1));
    }

    @Test
    public void testCreateServicesWithOneExternalAndInternalListener() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        Listener externalListener = new Listener("test-external-name", true, true, 9080, spec -> Optional.empty());
        Listener internalListener = new Listener("test-internal-name", true, false, 8080, spec -> Optional.empty());
        ComponentModel model = new ComponentModel(instance, Arrays.asList(externalListener, internalListener));

        Service externalService = model.getExternalService();
        assertThat(externalService.getMetadata().getName(), is(model.getDefaultResourceName() + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_POSTFIX));
        assertThat(externalService.getSpec().getType(), is("ClusterIP"));
        assertThat(externalService.getSpec().getPorts().size(), is(1));
        assertThat(externalService.getSpec().getPorts().get(0).getName(), is(model.getComponentName() + "-" + externalListener.getName()));
        assertThat(externalService.getSpec().getPorts().get(0).getPort(), is(externalListener.getPort()));
        assertThat(externalService.getSpec().getPorts().get(0).getProtocol(), is("TCP"));

        Service internalService = model.getInternalService();
        assertThat(internalService.getMetadata().getName(), is(model.getDefaultResourceName() + "-" + AbstractSecureEndpointModel.INTERNAL_SERVICE_POSTFIX));
        assertThat(internalService.getSpec().getType(), is("ClusterIP"));
        assertThat(internalService.getSpec().getPorts().size(), is(2));
        boolean foundInternalListenerPort = internalService.getSpec().getPorts().stream().anyMatch(port -> {
            return port.getName().equals(model.getComponentName() + "-" + internalListener.getName()) &&
                    port.getPort() == internalListener.getPort() &&
                    port.getProtocol().equals("TCP");
        });
        assertThat(foundInternalListenerPort, is(true));
    }

    @Test
    public void testCreateServicesWithMultipleExternalAndInternalListener() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        Listener externalListener1 = new Listener("test-external-name-1", true, true, 9080, spec -> Optional.empty());
        Listener externalListener2 = new Listener("test-external-name-2", true, true, 9081, spec -> Optional.empty());
        Listener internalListener1 = new Listener("test-internal-name-1", true, false, 8080, spec -> Optional.empty());
        Listener internalListener2 = new Listener("test-internal-name-2", true, false, 8081, spec -> Optional.empty());
        Listener internalListener3 = new Listener("test-internal-name-3", true, false, 8082, spec -> Optional.empty());
        ComponentModel model = new ComponentModel(instance,
                Arrays.asList(externalListener1, externalListener2, internalListener1, internalListener2, internalListener3));

        Service externalService = model.getExternalService();
        assertThat(externalService.getMetadata().getName(), is(model.getDefaultResourceName() + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_POSTFIX));
        assertThat(externalService.getSpec().getType(), is("ClusterIP"));
        assertThat(externalService.getSpec().getPorts().size(), is(2));

        Service internalService = model.getInternalService();
        assertThat(internalService.getMetadata().getName(), is(model.getDefaultResourceName() + "-" + AbstractSecureEndpointModel.INTERNAL_SERVICE_POSTFIX));
        assertThat(internalService.getSpec().getType(), is("ClusterIP"));
        assertThat(internalService.getSpec().getPorts().size(), is(4));
    }

    @Test
    public void testCreateRoutesWithOneExternalTlsListener() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        Listener externalListener = new Listener("test-external-name-1", true, true, 9080, spec -> Optional.empty());
        ComponentModel model = new ComponentModel(instance,
                Collections.singletonList(externalListener));

        Map<String, Route> routes = model.getRoutes();
        assertThat(routes.size(), is(1));
        assertThat(routes.containsKey(externalListener.getName()), is(true));
        assertThat(routes.get(externalListener.getName()).getMetadata().getName(), is(model.getDefaultResourceName() + "-" + externalListener.getName()));
        assertThat(routes.get(externalListener.getName()).getSpec().getTo().getKind(), is("Service"));
        assertThat(routes.get(externalListener.getName()).getSpec().getTo().getName(), is(model.getDefaultResourceName() + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_POSTFIX));
        assertThat(routes.get(externalListener.getName()).getSpec().getPort().getTargetPort().getIntVal(), is(externalListener.getPort()));
        assertThat(routes.get(externalListener.getName()).getSpec().getTls(), is(notNullValue()));
        assertThat(routes.get(externalListener.getName()).getSpec().getTls().getTermination(), is("passthrough"));
        assertThat(routes.get(externalListener.getName()).getSpec().getTls().getInsecureEdgeTerminationPolicy(), is("None"));
    }

    @Test
    public void testCreateRoutesWithOneExternalPlainListener() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        Listener externalListener = new Listener("test-external-name-1", false, true, 9080, spec -> Optional.empty());
        ComponentModel model = new ComponentModel(instance,
                Collections.singletonList(externalListener));

        Map<String, Route> routes = model.getRoutes();
        assertThat(routes.size(), is(1));
        assertThat(routes.containsKey(externalListener.getName()), is(true));
        assertThat(routes.get(externalListener.getName()).getMetadata().getName(), is(model.getDefaultResourceName() + "-" + externalListener.getName()));
        assertThat(routes.get(externalListener.getName()).getSpec().getTo().getKind(), is("Service"));
        assertThat(routes.get(externalListener.getName()).getSpec().getTo().getName(), is(model.getDefaultResourceName() + "-" + AbstractSecureEndpointModel.EXTERNAL_SERVICE_POSTFIX));
        assertThat(routes.get(externalListener.getName()).getSpec().getPort().getTargetPort().getIntVal(), is(externalListener.getPort()));
        assertThat(routes.get(externalListener.getName()).getSpec().getTls(), is(nullValue()));
    }

    @Test
    public void testCreateRoutesWithOneInternalListener() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        Listener externalListener = new Listener("test-internal-name-1", false, false, 9080, spec -> Optional.empty());
        ComponentModel model = new ComponentModel(instance,
                Collections.singletonList(externalListener));

        Map<String, Route> routes = model.getRoutes();
        assertThat(routes.size(), is(0));
    }

    @Test
    public void testCreateRoutesWithExternalAndInternalListeners() {
        EventStreams instance = ModelUtils.createDefaultEventStreams(instanceName).build();
        Listener externalListener1 = new Listener("test-external-name-1", true, true, 9080, spec -> Optional.empty());
        Listener externalListener2 = new Listener("test-external-name-2", false, true, 9081, spec -> Optional.empty());
        Listener internalListener1 = new Listener("test-internal-name-1", true, false, 8080, spec -> Optional.empty());
        ComponentModel model = new ComponentModel(instance,
                Arrays.asList(externalListener1, externalListener2, internalListener1));

        Map<String, Route> routes = model.getRoutes();
        assertThat(routes.size(), is(2));
        assertThat(routes.containsKey(externalListener1.getName()), is(true));
        assertThat(routes.containsKey(externalListener2.getName()), is(true));
        assertThat(routes.get(externalListener1.getName()).getSpec().getTls(), is(notNullValue()));
        assertThat(routes.get(externalListener2.getName()).getSpec().getTls(), is(nullValue()));
    }
}
