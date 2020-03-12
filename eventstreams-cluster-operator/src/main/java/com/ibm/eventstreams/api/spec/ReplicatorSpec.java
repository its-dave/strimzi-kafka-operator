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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.strimzi.api.kafka.model.KafkaMirrorMaker2Spec;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import io.vertx.core.cli.annotations.DefaultValue;
import lombok.EqualsAndHashCode;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "replicas", "mirrorMaker2Spec" })
@EqualsAndHashCode
public class ReplicatorSpec  implements Serializable  {

    private static final long serialVersionUID = 1L;

    private Integer replicas;
    private KafkaMirrorMaker2Spec mirrorMaker2Spec;


    public int hashCode() {
        return super.hashCode();
    }

    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Description("The number of connect replicas to start for Event Streams replication")
    @DefaultValue("0")
    public Integer getReplicas() {
        return replicas;
    }

    public void setReplicas(Integer replicas) {
        this.replicas = replicas;
    }

    @JsonProperty(required = false)
    public KafkaMirrorMaker2Spec getMirrorMaker2Spec() {
        return mirrorMaker2Spec;
    }


    public void setMirrorMaker2Spec(KafkaMirrorMaker2Spec mirrorMaker2Spec) {
        this.mirrorMaker2Spec = mirrorMaker2Spec;
    }

}

    