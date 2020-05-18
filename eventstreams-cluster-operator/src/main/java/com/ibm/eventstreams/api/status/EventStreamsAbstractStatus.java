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

package com.ibm.eventstreams.api.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ibm.eventstreams.controller.models.PhaseState;
import io.strimzi.api.kafka.model.status.Condition;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"phase", "customImages", "endpoints", "conditions"})
@EqualsAndHashCode
public abstract class EventStreamsAbstractStatus implements Serializable {

    private PhaseState phase;
    private boolean customImages;
    private EventStreamsVersions versions;
    private List<Condition> conditions;

    @Description("Identifies the current state of the Event Streams instance.")
    public PhaseState getPhase() {
        return phase;
    }

    public void setPhase(PhaseState phase) {
        this.phase = phase;
    }

    @Description("Identifies whether any of the Docker images have been modified from the defaults for this version of Event Streams")
    public boolean isCustomImages() {
        return customImages;
    }

    public void setCustomImages(boolean customImages) {
        this.customImages = customImages;
    }

    @Description("Information about the version of this instance and it's upgradable versions")
    public EventStreamsVersions getVersions() {
        return versions;
    }

    public void setVersions(EventStreamsVersions versions) {
        this.versions = versions;
    }

    @Description("Current state of the Event Streams cluster")
    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

}
