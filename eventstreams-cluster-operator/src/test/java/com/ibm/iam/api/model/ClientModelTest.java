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
package com.ibm.iam.api.model;

import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import com.ibm.iam.api.spec.Client;
import com.ibm.iam.api.spec.ClientSpec;
import com.ibm.iam.api.spec.OidcLibertyClientSpec;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.Test;


import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.notNullValue;

public class ClientModelTest {

    private final String instanceName = "test";
    private final String namespace = "myproject";
    private final int defaultReplicas = 1;

    private EventStreamsBuilder createDefaultEventStreams() {
        return ModelUtils.createDefaultEventStreams(instanceName)
            .withMetadata(new ObjectMetaBuilder()
                .withNewName(instanceName)
                .withNewNamespace(namespace)
                .build())
            .editSpec()
            .withNewSecurity().
                withEncryption(SecuritySpec.Encryption.INTERNAL_TLS)
            .endSecurity()
            .withNewReplicator()
            .withReplicas(defaultReplicas)
            .endReplicator()
            .endSpec();
    }

    private ClientModel createDefaultModelClient() {
        ClientModel model = new ClientModel(createDefaultEventStreams().build(), "my-route");
        return model;
    }

    @Test
    public void testDefaultClientModel() {
        ClientModel cm = createDefaultModelClient();
        Client oidcClient = cm.getClient();
        ObjectMeta objMeta = oidcClient.getMetadata();
        assertThat(objMeta, notNullValue());
        assertThat(objMeta.getName(), is("test-ibm-es-oidc-client"));
        assertThat(objMeta.getNamespace(), is(namespace));
        assertThat(objMeta.getOwnerReferences(), notNullValue());
        ClientSpec spec = oidcClient.getSpec();
        assertThat(oidcClient.getSpec(), notNullValue());
        assertThat(spec.getClientId(), is(""));
        assertThat(spec.getSecret(), is("test-ibm-es-oidc-secret"));
        OidcLibertyClientSpec libClient = spec.getOidcLibertyClient();
        assertThat(libClient, notNullValue());
        assertThat(libClient.getPostLogoutRedirectURIs(), hasItem("my-route/console/logout"));
        assertThat(libClient.getTrustedURIPrefixes(), hasItem("my-route"));
        assertThat(libClient.getRedirectURIs(), hasItem("my-route/oauth/callback"));
    }


}
