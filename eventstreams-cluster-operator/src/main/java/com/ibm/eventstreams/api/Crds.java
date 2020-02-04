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
package com.ibm.eventstreams.api;

import com.ibm.eventstreams.api.spec.EventStreams;

import com.ibm.iam.api.spec.Client;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceSubresourceStatus;
import io.fabric8.kubernetes.client.CustomResource;

public class Crds {
    public static final String CRD_KIND = "CustomResourceDefinition";

    public static CustomResourceDefinition getCrd(Class<? extends CustomResource> cls) {
        String version;
        String scope;
        String crdApiVersion;
        String plural;
        String singular;
        String group;
        String kind;
        String shortName;
        String listKind;
        CustomResourceSubresourceStatus status;

        if (cls.equals(EventStreams.class)) {
            version = EventStreams.V1BETA1;
            scope = EventStreams.SCOPE;
            crdApiVersion = EventStreams.CRD_API_VERSION;
            plural = EventStreams.RESOURCE_PLURAL;
            singular = EventStreams.RESOURCE_SINGULAR;
            group = EventStreams.RESOURCE_GROUP;
            kind = EventStreams.RESOURCE_KIND;
            listKind = EventStreams.RESOURCE_LIST_KIND;
            shortName = "es";
            status = new CustomResourceSubresourceStatus();
        } else if (cls.equals(Client.class)) {
            version = Client.V1;
            scope = Client.SCOPE;
            crdApiVersion = Client.CRD_API_VERSION;
            plural = Client.RESOURCE_PLURAL;
            singular = Client.RESOURCE_SINGULAR;
            group = Client.RESOURCE_GROUP;
            kind = Client.RESOURCE_KIND;
            listKind = Client.RESOURCE_LIST_KIND;
            shortName = Client.SHORT_NAME;
            status = new CustomResourceSubresourceStatus();
        } else {
            throw new RuntimeException();
        }

        return new CustomResourceDefinitionBuilder()
            .withApiVersion(crdApiVersion)
            .withKind(CRD_KIND)
            .withNewMetadata()
                .withName(plural + "." + group)
            .endMetadata()
            .withNewSpec()
                .withScope(scope)
                .withGroup(group)
                .withVersion(version)
                .withNewNames()
                    .withSingular(singular)
                    .withPlural(plural)
                    .withKind(kind)
                    .withShortNames(shortName)
                    .withListKind(listKind)
                .endNames()
                .withNewSubresources()
                    .withStatus(status)
                .endSubresources()
            .endSpec()
            .build();
    }
}