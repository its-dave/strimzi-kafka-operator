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
package com.ibm.commonservices.api.model;

import com.ibm.eventstreams.api.TlsVersion;
import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.commonservices.api.spec.Cp4iServicesBinding;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class Cp4iServicesBindingTest {

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
            .withNewSecurity()
                .withInternalTls(TlsVersion.TLS_V1_2)
            .endSecurity()
            .endSpec();
    }

    private Cp4iServicesBindingModel createDefaultModelCp4iServicesBinding() {
        return new Cp4iServicesBindingModel(createDefaultEventStreams().build());
    }

    @Test
    public void testDefaultCp4iServiceBindingModel() {
        Cp4iServicesBindingModel cp4iModel = createDefaultModelCp4iServicesBinding();
        Cp4iServicesBinding cp4iServicesBinding = cp4iModel.getCp4iServicesBinding();
        ObjectMeta objMeta = cp4iServicesBinding.getMetadata();
        assertThat(objMeta, notNullValue());
        assertThat(objMeta.getName(), is("test-ibm-es-eventstreams"));
        assertThat(objMeta.getNamespace(), is(namespace));
        assertThat(objMeta.getOwnerReferences(), notNullValue());
        assertThat(objMeta.getLabels(), notNullValue());
    }
}