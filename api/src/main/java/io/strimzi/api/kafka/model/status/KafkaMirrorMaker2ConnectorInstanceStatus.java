/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.api.kafka.model.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.strimzi.api.kafka.model.Constants;
import io.strimzi.api.kafka.model.UnknownPropertyPreserving;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

/**
 * Represents the instance status for a connector
 */
@Buildable(
    editableEnabled = false,
    builderPackage = Constants.FABRIC8_KUBERNETES_API
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "state", "worker_id", "trace"})
@EqualsAndHashCode
public class KafkaMirrorMaker2ConnectorInstanceStatus implements UnknownPropertyPreserving, Serializable {
    private static final long serialVersionUID = 1L;

    private String state;
    private String workerId;
    private String trace;
    private Map<String, Object> additionalProperties;

    @Description("The instance state. " +
        "Can be `RUNNING`, `PAUSED`, `UNASSIGNED` or `FAILED`")
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Description("The ID of the Kafka Connect worker")
    @JsonProperty("worker_id")
    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    @Description("Trace information. " +
        "Populated when the state is `FAILED`.")
    public String getTrace() {
        return trace;
    }

    public void setTrace(String trace) {
        this.trace = trace;
    }

    @Override
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties != null ? this.additionalProperties : emptyMap();
    }

    @Override
    public void setAdditionalProperty(String name, Object value) {
        if (this.additionalProperties == null) {
            this.additionalProperties = new HashMap<>(1);
        }
        this.additionalProperties.put(name, value);
    }
}