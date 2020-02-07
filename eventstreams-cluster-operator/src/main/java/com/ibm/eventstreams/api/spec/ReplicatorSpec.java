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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.strimzi.api.kafka.model.KafkaConnectSpec;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import io.vertx.core.cli.annotations.DefaultValue;
import lombok.EqualsAndHashCode;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "replicas", "metadataConnectName", "bootstrapServers", "configStorageReplicationFactor", "offsetStorageReplicationFactor", "statusStorageReplicationFactor", "env" })
@EqualsAndHashCode
public class ReplicatorSpec extends KafkaConnectSpec implements Serializable  {

    private static final long serialVersionUID = 1L;
    
    private Integer replicas;
    private String metadataConnectName;
    private String bootstrapServers;
    private List<EnvVar> env;
    private KafkaConnectSpec connectSpec;
  

    public int hashCode() {
        return super.hashCode();
    }


    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    @Description("The number of connect replicas to start for  Event Streams replication")
    @DefaultValue("0")
    @JsonProperty(required = true)
    public Integer getReplicas() {
        return replicas;
    }

    public void setReplicas(Integer replicas) {
        this.replicas = replicas;
    }

    @Description("The name of the Kafka Connect instance running the replicator")
    public String getMetadataConnectName() {
        return metadataConnectName;
    }

    public void setMetadataConnectName(String metadataConnectName) {
        this.metadataConnectName = metadataConnectName;
    }

    //Override needed so that this is no longer a required field in the Replicator CRD
    @Override
    @JsonProperty(required = false)
    @Description("The bootstrap address of the Kafka cluster to connect to : Event Streams sets this")
    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

}

    