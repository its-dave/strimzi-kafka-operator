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
package com.ibm.commonservices.api.controller;

import com.ibm.commonservices.api.spec.Cp4iServicesBinding;
import com.ibm.commonservices.api.spec.Cp4iServicesBindingBuilder;
import com.ibm.commonservices.api.spec.Cp4iServicesBindingDoneable;
import com.ibm.commonservices.api.spec.Cp4iServicesBindingList;
import com.ibm.commonservices.api.status.Cp4iServicesBindingStatus;
import com.ibm.commonservices.api.status.Cp4iServicesBindingStatusBuilder;
import com.ibm.eventstreams.api.model.utils.MockEventStreamsKube;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.strimzi.test.mockkube.MockKube;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class Cp4iServicesBindingResourceOperatorTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String NAME = "my-es";

    private static Vertx vertx;
    private static KubernetesClient mockClient;

    @BeforeAll
    public static void setup() {
        vertx = Vertx.vertx();
        mockClient = new MockEventStreamsKube().build();
    }

    @AfterAll
    public static void closeVertxInstance() {
        vertx.close();
        mockClient.close();
    }

    @Test
    public void testGetCp4iHeaderUrl() {
        String url = "1.2.3.4:443";
        Map<String, String> navigatorEndpoint = new HashMap<>();
        navigatorEndpoint.put(Cp4iServicesBindingStatus.NAME_KEY, Cp4iServicesBindingStatus.NAVIGATOR_ENDPOINT_NAME);
        navigatorEndpoint.put(Cp4iServicesBindingStatus.URL_KEY, url);
        Cp4iServicesBindingStatus cp4iServicesBindingStatus = new Cp4iServicesBindingStatusBuilder()
                .withEndpoints(Collections.singletonList(navigatorEndpoint))
                .build();
        KubernetesClient mockedKafkaClient = prepareKubeClient(cp4iServicesBindingStatus);

        Cp4iServicesBindingResourceOperator cp4iServicesBindingResourceOperator = new Cp4iServicesBindingResourceOperator(vertx, mockedKafkaClient, Cp4iServicesBinding.RESOURCE_KIND);
        Optional<String> headerUrl = cp4iServicesBindingResourceOperator.getCp4iHeaderUrl(NAMESPACE, NAME);

        assertTrue(headerUrl.isPresent());
        assertThat(headerUrl.get(), is(url));

        mockedKafkaClient.close();
    }

    @Test
    public void testGetCp4iHeaderUrlStatusMissing() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(null);

        Cp4iServicesBindingResourceOperator cp4iServicesBindingResourceOperator = new Cp4iServicesBindingResourceOperator(vertx, mockedKafkaClient, Cp4iServicesBinding.RESOURCE_KIND);
        Optional<String> headerUrl = cp4iServicesBindingResourceOperator.getCp4iHeaderUrl(NAMESPACE, NAME);

        assertFalse(headerUrl.isPresent());

        mockedKafkaClient.close();
    }

    @Test
    public void testGetCp4iHeaderUrlEndpointsMissing() {
        Cp4iServicesBindingStatus cp4iServicesBindingStatus = new Cp4iServicesBindingStatusBuilder()
                .withEndpoints(new ArrayList<>())
                .build();
        KubernetesClient mockedKafkaClient = prepareKubeClient(cp4iServicesBindingStatus);

        Cp4iServicesBindingResourceOperator cp4iServicesBindingResourceOperator = new Cp4iServicesBindingResourceOperator(vertx, mockedKafkaClient, Cp4iServicesBinding.RESOURCE_KIND);
        Optional<String> headerUrl = cp4iServicesBindingResourceOperator.getCp4iHeaderUrl(NAMESPACE, NAME);

        assertFalse(headerUrl.isPresent());

        mockedKafkaClient.close();
    }

    @Test
    public void testGetCp4iHeaderUrlEndpointsEmpty() {
        KubernetesClient mockedKafkaClient = prepareKubeClient(new Cp4iServicesBindingStatusBuilder().build());

        Cp4iServicesBindingResourceOperator cp4iServicesBindingResourceOperator = new Cp4iServicesBindingResourceOperator(vertx, mockedKafkaClient, Cp4iServicesBinding.RESOURCE_KIND);
        Optional<String> headerUrl = cp4iServicesBindingResourceOperator.getCp4iHeaderUrl(NAMESPACE, NAME);

        assertFalse(headerUrl.isPresent());

        mockedKafkaClient.close();
    }

    @Test
    public void testGetCp4iHeaderUrlNavigatorEndpointMissing() {
        Map<String, String> navigatorEndpoint = new HashMap<>();
        navigatorEndpoint.put(Cp4iServicesBindingStatus.NAME_KEY, "endpoint");
        navigatorEndpoint.put(Cp4iServicesBindingStatus.URL_KEY, "1.2.3.4:443");
        Cp4iServicesBindingStatus cp4iServicesBindingStatus = new Cp4iServicesBindingStatusBuilder()
                .withEndpoints(Collections.singletonList(navigatorEndpoint))
                .build();
        KubernetesClient mockedKafkaClient = prepareKubeClient(cp4iServicesBindingStatus);

        Cp4iServicesBindingResourceOperator cp4iServicesBindingResourceOperator = new Cp4iServicesBindingResourceOperator(vertx, mockedKafkaClient, Cp4iServicesBinding.RESOURCE_KIND);
        Optional<String> headerUrl = cp4iServicesBindingResourceOperator.getCp4iHeaderUrl(NAMESPACE, NAME);

        assertFalse(headerUrl.isPresent());

        mockedKafkaClient.close();
    }

    private KubernetesClient prepareKubeClient(Cp4iServicesBindingStatus status) {
        Cp4iServicesBinding cp4iServicesBinding = new Cp4iServicesBindingBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(NAME).withNamespace(NAMESPACE).build())
                .build();
        if (status != null) {
            cp4iServicesBinding.setStatus(status);
        }
        CustomResourceDefinition crd = mockClient.customResourceDefinitions().withName(Cp4iServicesBinding.CRD_NAME).get();
        return new MockKube()
                .withCustomResourceDefinition(crd, Cp4iServicesBinding.class, Cp4iServicesBindingList.class, Cp4iServicesBindingDoneable.class)
                .withInitialInstances(Collections.singleton(cp4iServicesBinding))
                .end()
                .build();
    }
}
