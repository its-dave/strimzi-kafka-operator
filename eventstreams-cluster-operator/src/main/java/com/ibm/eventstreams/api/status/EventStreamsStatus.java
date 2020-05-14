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
package com.ibm.eventstreams.api.status;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;


@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"phase", "conditions", "versions", "endpoints", "adminUiUrl", "kafkaListeners", "routes", "customImages"})
@EqualsAndHashCode
public class EventStreamsStatus extends EventStreamsAbstractStatus {

    private static final long serialVersionUID = 1L;

    private List<ListenerStatus> kafkaListeners;
    private String adminUiUrl;
    private Map<String, String> routes;
    private List<EventStreamsEndpoint> endpoints;


    @Description("Addresses of the internal and external listeners")
    public List<ListenerStatus> getKafkaListeners() {
        return kafkaListeners;
    }

    public void setKafkaListeners(List<ListenerStatus> kafkaListeners) {
        this.kafkaListeners = kafkaListeners;
    }

    @Description("OpenShift Routes created as part of the Event Streams cluster")
    public Map<String, String> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, String> routes) {
        this.routes = routes;
    }

    @Description("Addresses of the interfaces provided by the Event Streams cluster")
    public List<EventStreamsEndpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<EventStreamsEndpoint> endpoints) {
        this.endpoints = endpoints;
    }


    @Description("Web address for the Event Streams administration UI")
    public String getAdminUiUrl() {
        return adminUiUrl;
    }

    public void setAdminUiUrl(String adminUiUrl) {
        this.adminUiUrl = adminUiUrl;
    }

    @Override
    public String toString() {
        YAMLMapper mapper = new YAMLMapper().disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID);
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
