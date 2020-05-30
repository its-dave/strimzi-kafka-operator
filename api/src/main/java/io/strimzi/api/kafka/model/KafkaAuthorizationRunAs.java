/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.api.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.strimzi.crdgenerator.annotations.Description;
import io.strimzi.crdgenerator.annotations.Example;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Configures the broker authorization
 */
@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "superUsers"})
@EqualsAndHashCode
public class KafkaAuthorizationRunAs extends KafkaAuthorization {
    private static final long serialVersionUID = 1L;

    public static final String TYPE_RUNAS = "runas";

    public static final String AUTHORIZER_CLASS_NAME = "com.ibm.eventstreams.runas.authorizer.RunAsAuthorizer";
    public static final String PRINCIPAL_BUILDER_CLASS_NAME = "com.ibm.eventstreams.runas.authenticate.RunAsPrincipalBuilder";

    private List<String> superUsers;

    @Description("Must be `" + TYPE_RUNAS + "`")
    @Override
    public String getType() {
        return TYPE_RUNAS;
    }

    @Description("List of super users. Should contain list of user principals which should get unlimited access rights.")
    @Example("- CN=my-user\n" +
             "- CN=my-other-user")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<String> getSuperUsers() {
        return superUsers;
    }

    public void setSuperUsers(List<String> superUsers) {
        this.superUsers = superUsers;
    }
}