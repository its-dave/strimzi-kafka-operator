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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"pullPolicy", "pullSecrets"})
@EqualsAndHashCode
public class ImagesSpec implements Serializable {
    private static final long serialVersionUID = 1L;

    private String pullPolicy;
    private List<LocalObjectReference> pullSecrets;

    public String getPullPolicy() {
        return pullPolicy;
    }

    public void setPullPolicy(String pullPolicy) {
        this.pullPolicy = pullPolicy;
    }

    public List<LocalObjectReference> getPullSecrets() {
        return pullSecrets;
    }

    public void setPullSecrets(List<LocalObjectReference> pullSecrets) {
        this.pullSecrets = pullSecrets;
    }
}
