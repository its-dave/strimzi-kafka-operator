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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.strimzi.api.kafka.model.KafkaSpec;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"licenseAccept", "version", "images", "adminApi", "adminUI", "collector", "restProducer", "replicator", "schemaRegistry", "monitoring", "security", "strimziOverrides"})
@EqualsAndHashCode
public class EventStreamsSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean licenseAccept;
    private String version;
    private AdminApiSpec adminApi;
    private SecurityComponentSpec restProducer;
    private AdminUISpec adminUI;
    private SchemaRegistrySpec schemaRegistry;
    private ComponentSpec collector;
    private KafkaSpec strimziOverrides;
    private ReplicatorSpec replicator;
    private SecuritySpec security;
    private ImagesSpec images;

    @JsonProperty(required = true)
    @Description("Accept the product license after reading it at <TBC>")
    public boolean isLicenseAccept() {
        return licenseAccept;
    }
    
    public void setLicenseAccept(boolean licenseAccept) {
        this.licenseAccept = licenseAccept;
    }

    @Description("Configuration for accessing Event Streams Docker images")
    public ImagesSpec getImages() {
        return images;
    }

    public void setImages(ImagesSpec images) {
        this.images = images;
    }

    @JsonProperty(required = true)
    @Description("Version of the Event Streams instance")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @JsonProperty(required = true)
    @Description("Configuration of the Event Streams administration API server")
    public AdminApiSpec getAdminApi() {
        return adminApi;
    }

    public void setAdminApi(AdminApiSpec adminApi) {
        this.adminApi = adminApi;
    }

    @Description("Configuration of the REST Producer server that allows messages to be produced to Kafka topics from REST clients")
    public SecurityComponentSpec getRestProducer() {
        return restProducer;
    }

    public void setRestProducer(SecurityComponentSpec spec) {
        this.restProducer = spec;
    }

    @Description("Configuration of the web server that hosts the administration user interface")
    public AdminUISpec getAdminUI() {
        return adminUI;
    }

    public void setAdminUI(AdminUISpec adminUI) {
        this.adminUI = adminUI;
    }

    @Description("Configuration of the Schema Registry server")
    public SchemaRegistrySpec getSchemaRegistry() {
        return schemaRegistry;
    }

    public void setSchemaRegistry(SchemaRegistrySpec schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    @Description("Configuration of the collector server responsible for aggregating metrics from Kafka brokers")
    public ComponentSpec getCollector() {
        return collector;
    }

    public void setCollector(ComponentSpec collector) {
        this.collector = collector;
    }

    @Description("Configuration of the geo-replicator service")
    public ReplicatorSpec getReplicator() {
        return replicator;
    }

    public void setReplicator(ReplicatorSpec replicator) {
        this.replicator = replicator;
    }

    @JsonProperty(required = true)
    @Description("Configuration of the Kafka and ZooKeeper clusters")
    public KafkaSpec getStrimziOverrides() {
        return strimziOverrides;
    }

    public void setStrimziOverrides(KafkaSpec spec) {
        this.strimziOverrides = spec;
    }

    @Description("Security configuration for the Event Streams components")
    public SecuritySpec getSecurity() {
        return security;
    }

    public void setSecurity(SecuritySpec security) {
        this.security = security;
    }

}
