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

package com.ibm.eventstreams.api.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type"})
@EqualsAndHashCode
public class ExternalAccess implements Serializable {

    public static final String TYPE_ROUTE = "route";
    public static final String TYPE_NODEPORT = "nodeport";
    public static final String TYPE_DEFAULT = "clusterIp";

    private static final long serialVersionUID = 1L;

    private String type;

    @JsonProperty(required = true)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

}
