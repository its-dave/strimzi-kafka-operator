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
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.strimzi.operator.common.model.Labels;

public class ClientModel extends AbstractModel {
    private static final String SECRET_POSTFIX = "oidc-secret";
    private Client client;

    public ClientModel(EventStreams instance, String routeHost) {
        super(instance, DEFAULT_COMPONENT_NAME, Labels.APPLICATION_NAME);

        setOwnerReference(instance);

        Labels labels = labels();

        String trustedURIPrefixes = routeHost;
        String redirectURIs = routeHost + "/oauth/callback";
        String postLogoutRedirectURIs = routeHost + "/console/logout";

        client = new ClientBuilder()
            .withApiVersion(Client.RESOURCE_GROUP + "/" + Client.V1)
            .withMetadata(new ObjectMetaBuilder()
                    .withName(getDefaultResourceName())
                    .withOwnerReferences(getEventStreamsOwnerReference())
                    .withNamespace(getNamespace())
                    .withLabels(labels.toMap())
                    .build())
            .withNewSpec()
               .withClientId("")
               .withNewOidcLibertyClient()
                  .withPostLogoutRedirectURIs(postLogoutRedirectURIs)
                  .withRedirectURIs(redirectURIs)
                  .withTrustedURIPrefixes(trustedURIPrefixes)
               .endOidcLibertyClient()
               .withSecret(getSecretName(getInstanceName()))
            .endSpec()
            .build();
    }

    public Client getClient() {
        return this.client;
    }

    public static String getSecretName(String instanceName) {
        return getDefaultResourceName(instanceName, SECRET_POSTFIX);
    }
}
