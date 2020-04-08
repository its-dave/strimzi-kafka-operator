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

import com.ibm.eventstreams.api.spec.EventStreamsReplicator;
import com.ibm.iam.api.spec.Client;
import com.ibm.iam.api.spec.Cp4iServicesBinding;
import com.ibm.iam.api.spec.Cp4iServicesBindingDoneable;
import com.ibm.iam.api.spec.Cp4iServicesBindingList;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceSubresourceStatus;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

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
        } else if (cls.equals(Cp4iServicesBinding.class)) {
            version = Cp4iServicesBinding.V1;
            scope = Cp4iServicesBinding.SCOPE;
            crdApiVersion = Cp4iServicesBinding.CRD_API_VERSION;
            plural = Cp4iServicesBinding.RESOURCE_PLURAL;
            singular = Cp4iServicesBinding.RESOURCE_SINGULAR;
            group = Cp4iServicesBinding.RESOURCE_GROUP;
            kind = Cp4iServicesBinding.RESOURCE_KIND;
            listKind = Cp4iServicesBinding.RESOURCE_LIST_KIND;
            shortName = Cp4iServicesBinding.SHORT_NAME;
            status = new CustomResourceSubresourceStatus();
        } else if (cls.equals(EventStreamsReplicator.class)) {
            version = EventStreamsReplicator.V1BETA1;
            scope = EventStreamsReplicator.SCOPE;
            crdApiVersion = EventStreamsReplicator.CRD_API_VERSION;
            plural = EventStreamsReplicator.RESOURCE_PLURAL;
            singular = EventStreamsReplicator.RESOURCE_SINGULAR;
            group = EventStreamsReplicator.RESOURCE_GROUP;
            kind = EventStreamsReplicator.RESOURCE_KIND;
            listKind = EventStreamsReplicator.RESOURCE_LIST_KIND;
            shortName = EventStreamsReplicator.SHORT_NAME;
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

    public static MixedOperation<Cp4iServicesBinding, Cp4iServicesBindingList, Cp4iServicesBindingDoneable, Resource<Cp4iServicesBinding, Cp4iServicesBindingDoneable>> cp4iServicesBindingOperation(KubernetesClient client) {
        return client.customResources(com.ibm.eventstreams.api.Crds.getCrd(Cp4iServicesBinding.class), Cp4iServicesBinding.class, Cp4iServicesBindingList.class, Cp4iServicesBindingDoneable.class);
    }
}