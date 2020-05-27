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
package com.ibm.commonservices.api.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "conditions", "customImages", "endpoints", "versions" })
@EqualsAndHashCode
public class Cp4iServicesBindingStatus implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final String URL_KEY = "uri";
    public static final String NAME_KEY = "name";
    public static final String SERVICES_ENDPOINT_NAME = "services";

    private Object conditions;
    private boolean customImages;
    private List<Map<String, String>> endpoints;
    private Object versions;

    public Object getConditions() {
        return conditions;
    }

    public void setConditions(Object conditions) {
        this.conditions = conditions;
    }

    public List<Map<String, String>> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<Map<String, String>> endpoints) {
        this.endpoints = endpoints;
    }

    public boolean isCustomImages() {
        return customImages;
    }

    public void setCustomImages(boolean customImages) {
        this.customImages = customImages;
    }

    public Object getVersions() {
        return versions;
    }

    public void setVersions(Object versions) {
        this.versions = versions;
    }
}