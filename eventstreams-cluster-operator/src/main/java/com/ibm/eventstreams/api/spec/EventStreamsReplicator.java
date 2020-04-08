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
package com.ibm.eventstreams.api.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.ibm.eventstreams.api.status.EventStreamsReplicatorStatus;
import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.strimzi.api.kafka.model.Constants;
import io.strimzi.crdgenerator.annotations.Crd;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.Inline;
import lombok.EqualsAndHashCode;

import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

@Crd(
        apiVersion = EventStreams.CRD_API_VERSION,
        spec = @Crd.Spec(
                group = EventStreamsReplicator.RESOURCE_GROUP,
                names = @Crd.Spec.Names(
                        kind = EventStreamsReplicator.RESOURCE_KIND,
                        plural = EventStreamsReplicator.RESOURCE_PLURAL,
                        singular = EventStreamsReplicator.RESOURCE_SINGULAR,
                        shortNames = {EventStreamsReplicator.SHORT_NAME}),
                scope = EventStreamsReplicator.SCOPE,
                versions = {
                        @Crd.Spec.Version(name = EventStreamsReplicator.V1BETA1, served = true, storage = true)
                },
                subresources = @Crd.Spec.Subresources(
                        status = @Crd.Spec.Subresources.Status()
                )
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
public class EventStreamsReplicator extends CustomResource {

    private static final long serialVersionUID = 1L;
    public static final String V1BETA1 = "v1beta1";
    public static final List<String> VERSIONS = unmodifiableList(singletonList(V1BETA1));

    public static final String SCOPE = "Namespaced";
    public static final String RESOURCE_KIND = "EventStreamsGeoReplicator";
    public static final String RESOURCE_LIST_KIND = RESOURCE_KIND + "List";
    public static final String RESOURCE_GROUP = Constants.RESOURCE_GROUP_NAME;
    public static final String RESOURCE_PLURAL = "eventstreamsgeoreplicators";
    public static final String RESOURCE_SINGULAR = "eventstreamsgeoreplicator";
    public static final String CRD_API_VERSION = "apiextensions.k8s.io/v1beta1";
    public static final String CRD_NAME = RESOURCE_PLURAL + "." + RESOURCE_GROUP;
    public static final String SHORT_NAME = "esgr";
    public static final List<String> RESOURCE_SHORTNAMES = unmodifiableList(singletonList(SHORT_NAME));

    private String apiVersion;
    private ObjectMeta metadata;
    private ReplicatorSpec spec;
    private EventStreamsReplicatorStatus status;

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
    public ReplicatorSpec getSpec() {
        return spec;
    }

    public void setSpec(ReplicatorSpec spec) {
        this.spec = spec;
    }

    public EventStreamsReplicatorStatus getStatus() {
        return status;
    }

    public void setStatus(EventStreamsReplicatorStatus status) {
        this.status = status;
    }


}
