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
package com.ibm.commonservices.api.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "post_logout_redirect_uris", "trusted_uri_prefixes", "redirect_uris" })
@EqualsAndHashCode
public class OidcLibertyClientSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<String> postLogoutRedirectURIs;
    private List<String> trustedURIPrefixes;
    private List<String> redirectURIs;

    @JsonProperty("post_logout_redirect_uris")
    @Description("The uri for redirecting on logout")
    public List<String> getPostLogoutRedirectURIs() {
        return postLogoutRedirectURIs;
    }

    public void setPostLogoutRedirectURIs(List<String> postLogoutRedirectURIs) {
        this.postLogoutRedirectURIs = postLogoutRedirectURIs;
    }

    @JsonProperty("trusted_uri_prefixes")
    @Description("The uri's that can be truested")
    public List<String> getTrustedURIPrefixes() {
        return trustedURIPrefixes;
    }

    public void setTrustedURIPrefixes(List<String> trustedURIPrefixes) {
        this.trustedURIPrefixes = trustedURIPrefixes;
    }

    @JsonProperty("redirect_uris")
    @Description("The uri's to be redirected to")
    public List<String> getRedirectURIs() {
        return redirectURIs;
    }

    public void setRedirectURIs(List<String> redirectURIs) {
        this.redirectURIs = redirectURIs;
    }
}