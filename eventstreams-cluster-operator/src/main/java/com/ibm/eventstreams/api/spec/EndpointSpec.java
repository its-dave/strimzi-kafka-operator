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
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.TlsVersion;
import io.strimzi.api.kafka.model.CertAndKeySecretSource;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import static com.ibm.eventstreams.api.Endpoint.DEFAULT_EXTERNAL_AUTHENTICATION_MECHANISM;
import static com.ibm.eventstreams.api.Endpoint.RUNAS_ANONYMOUS_KEY;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"name", "type", "containerPort", "tlsVersion", "host", "certOverrides", "authenticationMechanisms"})
@EqualsAndHashCode
public class EndpointSpec implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private EndpointServiceType type;
    private Integer containerPort;
    private TlsVersion tlsVersion;
    private String host;
    private CertAndKeySecretSource certOverrides;
    private List<String> authenticationMechanisms;

    @JsonProperty(required = true)
    @Description("The name that will be used as a suffix for the created route.")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Description("Defines what type of service/route the operator will lay down for this endpoint.")
    public EndpointServiceType getType() {
        return type;
    }

    public void setType(EndpointServiceType type) {
        this.type = type;
    }

    @JsonProperty(required = true)
    @Description("Defines what port will be opened up for the client to communicate with.")
    public Integer getContainerPort() {
        return containerPort;
    }

    public void setContainerPort(Integer containerPort) {
        this.containerPort = containerPort;
    }

    @Description("Defines which TLS version or no TLS version if that will be used for the endpoint.")
    public TlsVersion getTlsVersion() {
        return tlsVersion;
    }

    public void setTlsVersion(TlsVersion tlsVersion) {
        this.tlsVersion = tlsVersion;
    }

    @Description("Defines the DNS name that the route will be created with for a user to connect on. If nothing is specified, then a route will be generated for you.")
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    @Description("Defines the certificate file and key file from a secret to use as the Endpoint's truststore.")
    public CertAndKeySecretSource getCertOverrides() {
        return certOverrides;
    }

    public void setCertOverrides(CertAndKeySecretSource certOverrides) {
        this.certOverrides = certOverrides;
    }

    @Description("Sets the authentication mechanisms that can be used to authenticate a client using this endpoint.")
    public List<String> getAuthenticationMechanisms() {
        return authenticationMechanisms;
    }

    public void setAuthenticationMechanisms(List<String> authenticationMechanisms) {
        this.authenticationMechanisms = authenticationMechanisms;
    }

    public boolean hasAuth() {
        return Optional.ofNullable(authenticationMechanisms)
            .map(authenticationMechanisms -> !authenticationMechanisms.isEmpty() && !authenticationMechanisms.contains(RUNAS_ANONYMOUS_KEY))
            .orElse(!DEFAULT_EXTERNAL_AUTHENTICATION_MECHANISM.isEmpty());
    }
}