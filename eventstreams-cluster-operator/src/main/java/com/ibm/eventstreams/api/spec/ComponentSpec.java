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

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"replicas", "template", "externalAccess"})
@EqualsAndHashCode(callSuper = true)
public class ComponentSpec extends ContainerSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private int replicas;
    private ExternalAccess externalAccess;
    private ComponentTemplate template;

    public ComponentTemplate getTemplate() {
        return template;
    }

    public void setTemplate(ComponentTemplate template) {
        this.template = template;
    }

    @Description("The number of instances to deploy.")
    public int getReplicas() {
        return replicas;
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    public ExternalAccess getExternalAccess() {
        return externalAccess;
    }

    public void setExternalAccess(ExternalAccess externalAccess) {
        this.externalAccess = externalAccess;
    }
}
