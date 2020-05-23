/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.api.kafka.model.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.strimzi.api.kafka.model.Constants;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a status of the Kafka MirrorMaker 2.0 resource
 */
@Buildable(
        editableEnabled = false,
        builderPackage = Constants.FABRIC8_KUBERNETES_API
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "conditions", "observedGeneration", "url" })
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class KafkaMirrorMaker2Status extends KafkaConnectStatus {
    private static final long serialVersionUID = 1L;

    private List<KafkaMirrorMaker2ConnectorStatus> connectors = new ArrayList<>(3);

    @Description("List of MirrorMaker 2.0 connector statuses, as reported by the Kafka Connect REST API.")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<KafkaMirrorMaker2ConnectorStatus> getConnectors() {
        return connectors;
    }

    public void setConnectors(List<KafkaMirrorMaker2ConnectorStatus> connectors) {
        this.connectors = connectors;
    }

}