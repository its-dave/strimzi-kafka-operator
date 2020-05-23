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

/**
 * Represents the instance status for a connector task
 */
@Buildable(
    editableEnabled = false,
    builderPackage = Constants.FABRIC8_KUBERNETES_API
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "id", "state", "worker_id", "trace"})
@EqualsAndHashCode
public class KafkaMirrorMaker2ConnectorTaskInstanceStatus extends KafkaMirrorMaker2ConnectorInstanceStatus {

    private int id;

    @Description("The ID of the connector task")
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}