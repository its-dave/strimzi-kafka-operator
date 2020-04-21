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

package com.ibm.eventstreams.controller;

import com.ibm.eventstreams.api.model.utils.MockEventStreamsKube;
import com.ibm.eventstreams.controller.certificates.EventStreamsCertificateManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasLength;

@ExtendWith(VertxExtension.class)
public class EventStreamsCertificateManagerTest {
    private EventStreamsCertificateManager certificateManager;
    private String namespace = "namespace";

    @BeforeEach
    public void before(Vertx vertx) {
        KubernetesClient mockClient = new MockEventStreamsKube().build();
        String kafkaInstance = "test-instance";
        certificateManager = new EventStreamsCertificateManager(new SecretOperator(vertx, mockClient), namespace, kafkaInstance);
    }

    @Test
    public void testRouteCommonNameGetsSplitAppropriately() {
        String componentName = "admapi";
        String routeName = String.format("quickstart-ibm-es-%s-external-%s.apps.test-domain.os.fyre.ibm.com", componentName, namespace);
        assertThat(certificateManager.getRouteCommonName(routeName, componentName), is(String.format("*.%s-external-%s.apps.test-domain.os.fyre.ibm.com", componentName, namespace)));
    }

    @Test
    public void testRouteCommonNameGetsTruncatedAppropriately() {
        String componentName = "admapi";
        String longDomainName = "really-long-domain-name-Ahg2KjSG6HYTMJQQNm6SQMyXwtUJXY.com";
        String routeName = String.format("quickstart-ibm-es-%s-external-%s.%s", componentName, namespace, longDomainName);

        String truncatedName = certificateManager.getRouteCommonName(routeName, componentName);
        assertThat(truncatedName, is(String.format("*pace.%s", longDomainName)));
        assertThat(truncatedName, hasLength(64));
    }
}
