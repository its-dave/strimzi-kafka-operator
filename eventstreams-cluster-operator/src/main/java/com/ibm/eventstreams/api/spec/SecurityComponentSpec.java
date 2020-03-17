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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.util.List;
@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonPropertyOrder({"endpoints"})
@EqualsAndHashCode(callSuper = true)
public class SecurityComponentSpec extends ComponentSpec {
    List<EndpointSpec> endpoints;

    @Description("Defines endpoints that will be created to communicate with the component. If nothing is specified, a " +
        "default endpoint is created that is externally accessible via a Route with Bearer Authentication. ")
    public List<EndpointSpec> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<EndpointSpec> endpoints) {
        this.endpoints = endpoints;
    }
}
