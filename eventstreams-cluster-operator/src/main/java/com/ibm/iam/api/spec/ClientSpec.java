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
package com.ibm.iam.api.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"secret", "oidcLibertyClient", "clientId"})
@EqualsAndHashCode
public class ClientSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private String secret;
    private OidcLibertyClientSpec oidcLibertyClient;
    private String clientId; 

    @JsonProperty(required = true)
    @Description("The secret to be populated the client id")
    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    @JsonProperty(required = true)
    @Description("")
    public OidcLibertyClientSpec getOidcLibertyClient() {
        return oidcLibertyClient;
    }

    public void setOidcLibertyClient(OidcLibertyClientSpec oidcLibertyClient) {
        this.oidcLibertyClient = oidcLibertyClient;
    }

    @JsonProperty(required = true)
    @Description("")
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}