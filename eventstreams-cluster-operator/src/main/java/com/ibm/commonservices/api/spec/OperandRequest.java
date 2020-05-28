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
package com.ibm.commonservices.api.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
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
        apiVersion = OperandRequest.CRD_API_VERSION,
        spec = @Crd.Spec(
                group = OperandRequest.RESOURCE_GROUP,
                names = @Crd.Spec.Names(
                        kind = OperandRequest.RESOURCE_KIND,
                        plural = OperandRequest.RESOURCE_PLURAL,
                        singular = OperandRequest.RESOURCE_SINGULAR,
                        shortNames = {OperandRequest.SHORT_NAME}),
                scope = OperandRequest.SCOPE,
                versions = {
                        @Crd.Spec.Version(name = OperandRequest.V1ALPHA1, served = true, storage = true)
                }
        ))
@Buildable(
        editableEnabled = false,
        builderPackage = "io.fabric8.kubernetes.api.builder",
        inline = {
                @Inline(type = Doneable.class, suffix = "Doneable", value = "done"),
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"apiVersion", "kind", "metadata", "spec", "status"})
@EqualsAndHashCode
/**
 * OperandRequest is a Custom Resource needed to interact with common services
 * To make upgrades and evolution of this CR easier spec and status are both considered a Java Object
 */
public class OperandRequest extends CustomResource {
    private static final long serialVersionUID = 1L;
    public static final String V1ALPHA1 = "v1alpha1";
    public static final List<String> VERSIONS = unmodifiableList(singletonList(V1ALPHA1));

    public static final String SCOPE = "Namespaced";
    public static final String RESOURCE_KIND = "OperandRequest";
    public static final String RESOURCE_LIST_KIND = RESOURCE_KIND + "List";
    public static final String RESOURCE_GROUP = "operator.ibm.com";
    public static final String RESOURCE_PLURAL = "operandrequests";
    public static final String RESOURCE_SINGULAR = "operandrequest";
    public static final String CRD_API_VERSION = RESOURCE_GROUP + "/" + V1ALPHA1;
    public static final String CRD_NAME = RESOURCE_PLURAL + "." + RESOURCE_GROUP;
    public static final String SHORT_NAME = "opreq";
    public static final List<String> RESOURCE_SHORTNAMES = unmodifiableList(singletonList(SHORT_NAME));

    private String apiVersion = CRD_API_VERSION;
    private ObjectMeta metadata;
    private Object spec;
    private Object status;

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

    public Object getSpec() {
        return spec;
    }

    public void setSpec(Object spec) {
        this.spec = spec;
    }

    public ObjectMeta getMetadata() {
        return metadata;
    }

    public void setMetadata(ObjectMeta metadata) {
        this.metadata = metadata;
    }

    public Object getStatus() {
        return status;
    }

    public void setStatus(Object status) {
        this.status = status;
    }
}