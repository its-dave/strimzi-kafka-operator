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

import com.ibm.eventstreams.api.model.AbstractModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.iam.api.spec.Cp4iServicesBinding;
import com.ibm.iam.api.spec.Cp4iServicesBindingBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;

public class Cp4iServicesBindingModel extends AbstractModel {
    private static final String COMPONENT_NAME = "cp4i-services-binding";
    private Cp4iServicesBinding cp4iServicesBinding;

    public Cp4iServicesBindingModel(EventStreams instance) {
        super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), COMPONENT_NAME);

        setOwnerReference(instance);

        ObjectMeta meta = new ObjectMetaBuilder().withName(getDefaultResourceName())
                .withOwnerReferences(getEventStreamsOwnerReference())
                .withNamespace(getNamespace())
                .withLabels(getComponentLabels()).build();


        cp4iServicesBinding = new Cp4iServicesBindingBuilder()
                .withApiVersion(Cp4iServicesBinding.RESOURCE_GROUP + "/" + Cp4iServicesBinding.V1)
                .withMetadata(meta)
                .build();
    }

    public Cp4iServicesBinding getCp4iServicesBinding() {
        return this.cp4iServicesBinding;
    }

    /**
     * Returns the name of the Cp4i instance for the given EventStreams instance name
     * Do not use this method when not referencing Cp4i resources
     */
    public static String getCp4iInstanceName(String instanceName) {
        return getResourcePrefix(instanceName);
    }

}