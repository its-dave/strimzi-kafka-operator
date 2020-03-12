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
package com.ibm.iam.api.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.ibm.iam.api.status.Cp4iServicesBindingStatus;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.strimzi.crdgenerator.annotations.Crd;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.Inline;
import lombok.EqualsAndHashCode;

import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

@Crd(
    apiVersion = Cp4iServicesBinding.CRD_API_VERSION,
    spec = @Crd.Spec(
        group = Cp4iServicesBinding.RESOURCE_GROUP,
        versions = {
            @Crd.Spec.Version(name = Cp4iServicesBinding.V1, served = true, storage = true)
        },
        scope = Cp4iServicesBinding.SCOPE,
        names = @Crd.Spec.Names(
            kind = Cp4iServicesBinding.RESOURCE_KIND,
            plural = Cp4iServicesBinding.RESOURCE_PLURAL,
            singular = Cp4iServicesBinding.RESOURCE_SINGULAR,
            shortNames = {Cp4iServicesBinding.SHORT_NAME})
    ))
@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder",
        inline = {
                @Inline(type = Doneable.class, suffix = "Doneable", value = "done"),
        }
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"apiVersion", "kind", "metadata", "spec"})
@EqualsAndHashCode
public class Cp4iServicesBinding extends CustomResource {

    private static final long serialVersionUID = 1L;
    public static final String V1 = "v1";
    public static final List<String> VERSIONS = unmodifiableList(singletonList(V1));

    public static final String SCOPE = "Namespaced";
    public static final String RESOURCE_KIND = "Cp4iServicesBinding";
    public static final String RESOURCE_LIST_KIND = RESOURCE_KIND + "List";
    public static final String RESOURCE_GROUP = "cp4i.ibm.com";
    public static final String RESOURCE_PLURAL = "cp4iservicesbindings";
    public static final String RESOURCE_SINGULAR = "cp4iservicesbinding";
    public static final String CRD_API_VERSION = "apiextensions.k8s.io/v1beta1";
    public static final String CRD_NAME = RESOURCE_PLURAL + "." + RESOURCE_GROUP;
    public static final String SHORT_NAME = "cp4inav";
    public static final List<String> RESOURCE_SHORTNAMES = unmodifiableList(singletonList(SHORT_NAME));

    private String apiVersion = CRD_API_VERSION;
    private ObjectMeta metadata;
    private Cp4iServicesBindingSpec spec;
    private Cp4iServicesBindingStatus status; 

    @Override
    public String toString() {
        YAMLMapper mapper = new YAMLMapper().disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID);
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @JsonProperty("kind")
    @Override
    public String getKind() {
        return RESOURCE_KIND;
    }

    @Override
    public String getApiVersion() {
        return apiVersion;
    }

    @Override
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public Cp4iServicesBindingSpec getSpec() {
        return spec;
    }

    public void setSpec(Cp4iServicesBindingSpec spec) {
        this.spec = spec;
    }

    public ObjectMeta getMetadata() {
        return metadata;
    }

    public void setMetadata(ObjectMeta metadata) {
        this.metadata = metadata;
    }

    public Cp4iServicesBindingStatus getStatus() {
        return status;
    }

    public void setStatus(Cp4iServicesBindingStatus status) {
        this.status = status;
    }

}