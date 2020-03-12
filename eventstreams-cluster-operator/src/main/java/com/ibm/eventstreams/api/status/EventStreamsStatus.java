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

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;


@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"kafkaListeners", "adminUiUrl", "routes", "customImages", "endpoints", "versions", "conditions", "cp4iPresent"})
@EqualsAndHashCode
public class EventStreamsStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<ListenerStatus> kafkaListeners;
    private String adminUiUrl;
    private Map<String, String> routes;
    private List<EventStreamsEndpoint> endpoints;
    private boolean customImages;
    private EventStreamsVersions versions;
    private List<Condition> conditions;
    private boolean cp4iPresent;

    /**
     * @return List<ListenerStatus> return the kafkaListeners
     */
    public List<ListenerStatus> getKafkaListeners() {
        return kafkaListeners;
    }

    /**
     * @param kafkaListeners the kafkaListeners to set
     */
    public void setKafkaListeners(List<ListenerStatus> kafkaListeners) {
        this.kafkaListeners = kafkaListeners;
    }

    /**
     * @return Map<String, String> return the routes
     */
    public Map<String, String> getRoutes() {
        return routes;
    }

    /**
     * @param routes the routes to set
     */
    public void setRoutes(Map<String, String> routes) {
        this.routes = routes;
    }

    /**
     * @return return the URIs
     */
    public List<EventStreamsEndpoint> getEndpoints() {
        return endpoints;
    }

    /**
     * @param endpoints the URIs to set
     */
    public void setEndpoints(List<EventStreamsEndpoint> endpoints) {
        this.endpoints = endpoints;
    }

    @Description("Identifies whether any of the Docker images have been modified from the defaults for this version of Event Streams")
    public boolean isCustomImages() {
        return customImages;
    }

    /**
     * @param customImages the customImages to set
     */
    public void setCustomImages(boolean customImages) {
        this.customImages = customImages;
    }

    /**
     * @return EventStreamsVersions return the versions
     */
    public EventStreamsVersions getVersions() {
        return versions;
    }

    /**
     * @param versions the versions to set
     */
    public void setVersions(EventStreamsVersions versions) {
        this.versions = versions;
    }

    /**
     * @return List<Condition> return the conditions
     */
    public List<Condition> getConditions() {
        return conditions;
    }

    /**
     * @param conditions the conditions to set
     */
    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    /**
     * @return String return the adminUiUrl
     */
    public String getAdminUiUrl() {
        return adminUiUrl;
    }

    /**
     * @param adminUiUrl the adminUiUrl to set
     */
    public void setAdminUiUrl(String adminUiUrl) {
        this.adminUiUrl = adminUiUrl;
    }

    @Description("Identifies whether IBM Cloud Pak for Integration Services Binding is present")
    public boolean isCp4iPresent() {
        return cp4iPresent;
    }

    /**
     * @param cp4iPresent the cp4iPresent status to set
     */
    public void setCp4iPresent(boolean cp4iPresent) {
        this.cp4iPresent = cp4iPresent;
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
