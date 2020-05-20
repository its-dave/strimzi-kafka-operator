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

import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.strimzi.operator.common.operator.resource.ReconcileResult;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClusterSecretsModelTest {

    private final String instanceName = "testInstance";
    private static final String NAMESPACE = "test-namespace";
    private static final String CLUSTER_NAME = "my-es";
    private static final String TEST_DUMMY_CERT = Base64.getEncoder().encode("dummycertstring".getBytes()).toString();

    private SecretOperator mockSecretOperator;
    private EventStreams eventStreamsResource;

    @Captor
    private ArgumentCaptor<Handler<AsyncResult<Secret>>> secretCaptor;

    private ClusterSecretsModel createClusterSecretsModel() {
        return new ClusterSecretsModel(eventStreamsResource, mockSecretOperator);
    }

    private Secret mockESSecret;


    @BeforeEach
    public void init() {

        eventStreamsResource = ModelUtils.createDefaultEventStreams(instanceName)
            .withMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(NAMESPACE).build())
            .build();

        Map<String, String> secretData = new HashMap();
        secretData.put("ca.crt", TEST_DUMMY_CERT);

        mockESSecret = ModelUtils.generateSecret(NAMESPACE, ClusterSecretsModel.getIBMCloudSecretName(eventStreamsResource.getMetadata().getName()), secretData);

        mockSecretOperator = mock(SecretOperator.class);
    }

    @Test
    public void testGenerateCertSecret() {
        when(mockSecretOperator.getAsync(anyString(), anyString())).thenReturn(Future.succeededFuture(null));
        when(mockSecretOperator.createOrUpdate(any(Secret.class))).thenReturn(Future.succeededFuture(ReconcileResult.created(mockESSecret)));
        ClusterSecretsModel clusterSecretsModel = createClusterSecretsModel();
        clusterSecretsModel.createIBMCloudCASecret(TEST_DUMMY_CERT)
            .onSuccess(ar -> {
                Mockito.mockingDetails(mockSecretOperator).getInvocations().forEach(invocation -> {
                    if (invocation.getMethod().equals("createOrUpdate")) {
                        assertThat(invocation.getRawArguments().length, is(1));
                        Secret passedSecret = (Secret) invocation.getRawArguments()[0];
                        assertThat(passedSecret.getMetadata().getName(), is(mockESSecret.getMetadata().getName()));
                    }
                });
            })
            .onFailure(err -> {
                fail("Expected call to generateIBMCloudCASecret to be successful, but instead failed with {}", err.getCause());
            });
    }

    @Test
    public void testGenerateCertSecretFailureToGenerateNewCert() {
        when(mockSecretOperator.getAsync(anyString(), anyString())).thenReturn(Future.succeededFuture(null));
        when(mockSecretOperator.createOrUpdate(any(Secret.class))).thenReturn(Future.failedFuture("Failed to generate new Secret"));

        ClusterSecretsModel clusterSecretsModel = createClusterSecretsModel();
        clusterSecretsModel.createIBMCloudCASecret(TEST_DUMMY_CERT)
            .onSuccess(ar -> {
                fail("Expected call to generateIBMCloudCASecret to fail, but instead was successful");
            })
            .onFailure(err -> {
                assertThat(err.getMessage(), is("Failed to generate new Secret"));
            });
    }


    @Test
    public void testGenerateCertSecretAlreadyExists() {
        when(mockSecretOperator.getAsync(anyString(), anyString())).thenReturn(Future.succeededFuture(mockESSecret));
        ClusterSecretsModel clusterSecretsModel = createClusterSecretsModel();
        clusterSecretsModel.createIBMCloudCASecret(TEST_DUMMY_CERT)
            .onComplete(ar -> {
                assertThat(ar.succeeded(), is(true));
                AtomicBoolean createOrUpdateMethodInvoked = new AtomicBoolean(false);
                Mockito.mockingDetails(mockSecretOperator).getInvocations().forEach(invocation -> {
                    if (invocation.getMethod().equals("createOrUpdate")) {
                        createOrUpdateMethodInvoked.set(true);
                    }
                });

                assertThat(createOrUpdateMethodInvoked.get(), is(false));
            });
    }
}