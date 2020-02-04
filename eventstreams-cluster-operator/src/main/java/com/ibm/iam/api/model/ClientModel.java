/*
 * IBM Confidential
 * OCO Source Materials
 *
 * 5737-H33
 *
 * (C) Copyright IBM Corp. 2019  All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 */
package com.ibm.iam.api.model;

import com.ibm.eventstreams.api.model.AbstractModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.iam.api.spec.Client;
import com.ibm.iam.api.spec.ClientBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.HashMap;
import java.util.Map;

public class ClientModel extends AbstractModel {
    private static final String COMPONENT_NAME = "oidc-client";
    private static final String SECRET_POSTFIX = "-oidc-secret";
    private Client client;

    public ClientModel(EventStreams instance, String routeHost) {
        super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), COMPONENT_NAME);

        setOwnerReference(instance);
        setArchitecture(instance.getSpec().getArchitecture());
        
        Map<String, String> labels = new HashMap<>();
        labels.put("strimzi.io/cluster", getResourcePrefix());

        String trustgedURIPrefixes = routeHost;
        String redirectURIs = routeHost + "/oauth/callback";
        String postLogoutRedirectURIs = routeHost + "/console/logout";

        ObjectMeta meta = new ObjectMetaBuilder().withName(getDefaultResourceName())
                .withOwnerReferences(getEventStreamsOwnerReference())
                .withNamespace(getNamespace())
                .withLabels(labels).build();

        client = new ClientBuilder()
        .withApiVersion(Client.RESOURCE_GROUP + "/" + Client.V1)

        .withMetadata(meta)
        .withNewSpec()
           .withClientId("")
           .withNewOidcLibertyClient()
              .withPostLogoutRedirectURIs(postLogoutRedirectURIs)
              .withRedirectURIs(redirectURIs)
              .withTrustedURIPrefixes(trustgedURIPrefixes)
           .endOidcLibertyClient()
           .withSecret(getSecretName(instance))
        .endSpec()
        .build();
    }

    public Client getClient() {
        return this.client;
    }

    public static String getSecretName(EventStreams instance) {
        return instance.getMetadata().getName() + SECRET_POSTFIX;
    }
}