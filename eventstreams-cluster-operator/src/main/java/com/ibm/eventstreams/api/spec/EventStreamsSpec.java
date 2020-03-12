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
@JsonPropertyOrder({"images", "appVersion", "adminApi", "adminUI", "collector", "restProducer", "replicator", "schemaRegistry", "monitoring", "security", "strimziOverrides"})
@EqualsAndHashCode
public class EventStreamsSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private String appVersion;
    private AdminApiSpec adminApi;
    private SecurityComponentSpec restProducer;
    private AdminUISpec adminUI;
    private SchemaRegistrySpec schemaRegistry;
    private ComponentSpec collector;
    private KafkaSpec strimziOverrides;
    private MonitoringSpec monitoring;
    private ReplicatorSpec replicator;
    private SecuritySpec security;
    private ImagesSpec images;

    @Description("The specification of global image properties")
    public ImagesSpec getImages() {
        return images;
    }

    public void setImages(ImagesSpec images) {
        this.images = images;
    }

    @Description("The specification of monitoring configuration")
    public MonitoringSpec getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(MonitoringSpec monitoring) {
        this.monitoring = monitoring;
    }

    @JsonProperty(required = true)
    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    @JsonProperty(required = true)
    @Description("The specification of the admin API server")
    public AdminApiSpec getAdminApi() {
        return adminApi;
    }

    public void setAdminApi(AdminApiSpec adminApi) {
        this.adminApi = adminApi;
    }

    @Description("The specification of the REST producer")
    public SecurityComponentSpec getRestProducer() {
        return restProducer;
    }

    public void setRestProducer(SecurityComponentSpec spec) {
        this.restProducer = spec;
    }

    @Description("The specification of the admin user interface")
    public AdminUISpec getAdminUI() {
        return adminUI;
    }

    public void setAdminUI(AdminUISpec adminUI) {
        this.adminUI = adminUI;
    }

    @Description("The specification of the Schema Registry")
    public SchemaRegistrySpec getSchemaRegistry() {
        return schemaRegistry;
    }

    public void setSchemaRegistry(SchemaRegistrySpec schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    @Description("The specification of the collector pod responsible for aggregating metrics.")
    public ComponentSpec getCollector() {
        return collector;
    }

    public void setCollector(ComponentSpec collector) {
        this.collector = collector;
    }

    public ReplicatorSpec getReplicator() {
        return replicator;
    }

    public void setReplicator(ReplicatorSpec replicator) {
        this.replicator = replicator;
    }

    @JsonProperty(required = true)
    @Description("The specification of the Kafka and ZooKeeper clusters.")
    public KafkaSpec getStrimziOverrides() {
        return strimziOverrides;
    }

    public void setStrimziOverrides(KafkaSpec spec) {
        this.strimziOverrides = spec;
    }

    @Description("The specification of the security properties")
    public SecuritySpec getSecurity() {
        return security;
    }

    public void setSecurity(SecuritySpec security) {
        this.security = security;
    }

}
