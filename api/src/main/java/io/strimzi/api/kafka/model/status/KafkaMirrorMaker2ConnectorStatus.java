/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.api.kafka.model.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.strimzi.api.kafka.model.Constants;
import io.strimzi.api.kafka.model.UnknownPropertyPreserving;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;

/**
 * Represents status for a single connector
 */
@Buildable(
    editableEnabled = false,
    builderPackage = Constants.FABRIC8_KUBERNETES_API
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "name", "type", "connector", "tasks"})
@EqualsAndHashCode
public class KafkaMirrorMaker2ConnectorStatus implements UnknownPropertyPreserving, Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String type;
    private KafkaMirrorMaker2ConnectorInstanceStatus connector;
    private List<KafkaMirrorMaker2ConnectorTaskInstanceStatus> tasks;
    private Map<String, Object> additionalProperties;

    @Description("The name of the connector.")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    @Description("The type of the connector. " +
        "Can be either `source` or `sink`")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Description("The connector status for this connector.")
    public KafkaMirrorMaker2ConnectorInstanceStatus getConnector() {
        return connector;
    }

    public void setConnector(KafkaMirrorMaker2ConnectorInstanceStatus connector) {
        this.connector = connector;
    }

    @Description("A list of the task statuses for this connector.")
    public List<KafkaMirrorMaker2ConnectorTaskInstanceStatus> getTasks() {
        return tasks;
    }

    public void setTasks(List<KafkaMirrorMaker2ConnectorTaskInstanceStatus> tasks) {
        this.tasks = tasks;
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

    public static KafkaMirrorMaker2ConnectorStatus fromMap(final Map<String, Object> statusMap) {
        KafkaMirrorMaker2ConnectorStatusBuilder connectorStatusBuilder = new KafkaMirrorMaker2ConnectorStatusBuilder();

        Optional.ofNullable((String) statusMap.get("name")).ifPresent(connectorStatusBuilder::withName);
        Optional.ofNullable((String) statusMap.get("type")).ifPresent(connectorStatusBuilder::withType);
        Optional.ofNullable((Map<String, Object>) statusMap.get("connector")).ifPresent(connectorInstanceMap -> {
            KafkaMirrorMaker2ConnectorInstanceStatusBuilder connectorBuilder = new KafkaMirrorMaker2ConnectorInstanceStatusBuilder();
            Optional.ofNullable((String) connectorInstanceMap.get("state")).ifPresent(connectorBuilder::withState);
            Optional.ofNullable((String) connectorInstanceMap.get("worker_id")).ifPresent(connectorBuilder::withWorkerId);
            Optional.ofNullable((String) connectorInstanceMap.get("trace")).ifPresent(connectorBuilder::withTrace);
            connectorStatusBuilder.withConnector(connectorBuilder.build());
        });
        Optional.ofNullable((List<Map<String, Object>>) statusMap.get("tasks")).ifPresent(tasks -> connectorStatusBuilder.withTasks(tasks.stream()
            .map(taskInstanceMap -> {
                KafkaMirrorMaker2ConnectorTaskInstanceStatusBuilder connectorTaskBuilder = new KafkaMirrorMaker2ConnectorTaskInstanceStatusBuilder();
                Optional.ofNullable((Integer) taskInstanceMap.get("id")).ifPresent(connectorTaskBuilder::withId);
                Optional.ofNullable((String) taskInstanceMap.get("state")).ifPresent(connectorTaskBuilder::withState);
                Optional.ofNullable((String) taskInstanceMap.get("worker_id")).ifPresent(connectorTaskBuilder::withWorkerId);
                Optional.ofNullable((String) taskInstanceMap.get("trace")).ifPresent(connectorTaskBuilder::withTrace);
                return connectorTaskBuilder.build();
            })
            .collect(Collectors.toList())));

        return connectorStatusBuilder.build();
    }
}
