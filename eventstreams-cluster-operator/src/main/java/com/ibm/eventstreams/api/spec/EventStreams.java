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
package com.ibm.eventstreams.api.spec;

import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.ibm.eventstreams.api.status.EventStreamsStatus;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.strimzi.crdgenerator.annotations.Crd;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.Inline;
import lombok.EqualsAndHashCode;

@Crd(
    apiVersion = EventStreams.CRD_API_VERSION,
    spec = @Crd.Spec(
        group = EventStreams.RESOURCE_GROUP,
        names = @Crd.Spec.Names(
            kind = EventStreams.RESOURCE_KIND,
            plural = EventStreams.RESOURCE_PLURAL,
            singular = EventStreams.RESOURCE_SINGULAR,
            shortNames = {EventStreams.SHORT_NAME}),
        scope = EventStreams.SCOPE,
        versions = {
            @Crd.Spec.Version(name = EventStreams.V1BETA1, served = true, storage = true)
        }
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
@JsonPropertyOrder({"apiVersion", "kind", "metadata", "spec", "status"})
@EqualsAndHashCode
public class EventStreams extends CustomResource {

    private static final long serialVersionUID = 1L;
    public static final String V1BETA1 = "v1beta1";
    public static final List<String> VERSIONS = unmodifiableList(singletonList(V1BETA1));

    public static final String SCOPE = "Namespaced";
    public static final String RESOURCE_KIND = "EventStreams";
    public static final String RESOURCE_LIST_KIND = RESOURCE_KIND + "List";
    public static final String RESOURCE_GROUP = "eventstreams.ibm.com";
    public static final String RESOURCE_PLURAL = "eventstreams";
    public static final String RESOURCE_SINGULAR = "eventstreams";
    public static final String CRD_API_VERSION = "apiextensions.k8s.io/v1beta1";
    public static final String CRD_NAME = RESOURCE_PLURAL + "." + RESOURCE_GROUP;
    public static final String SHORT_NAME = "es";
    public static final List<String> RESOURCE_SHORTNAMES = unmodifiableList(singletonList(SHORT_NAME));

    private String apiVersion;
    private ObjectMeta metadata;
    private EventStreamsSpec spec;
    private EventStreamsStatus status;

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

    @JsonProperty(required = true)
    public EventStreamsSpec getSpec() {
        return spec;
    }

    public void setSpec(EventStreamsSpec spec) {
        this.spec = spec;
    }

    public EventStreamsStatus getStatus() {
        return status;
    }

    public void setStatus(EventStreamsStatus status) {
        this.status = status;
    }
}