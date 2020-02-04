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
@JsonPropertyOrder({"architecture", "images", "adminApiComponent", "restProducerComponent", "adminProxyComponent", "adminUIComponent", "schemaRegistryComponent", "collectorComponent", "monitoring", "security", "replicator", "strimziOverrides"})
@EqualsAndHashCode
public class EventStreamsSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private String architecture;
    private String appVersion;
    private ComponentSpec adminApi;
    private ComponentSpec restProducer;
    private ComponentSpec adminProxy;
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

    @Description("The architecture of the worker nodes on which to install")
    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    @Description("The specification of the admin API server")
    public ComponentSpec getAdminApi() {
        return adminApi;
    }

    public void setAdminApi(ComponentSpec adminApi) {
        this.adminApi = adminApi;
    }

    @Description("The specification of the rest producer")
    public ComponentSpec getRestProducer() {
        return restProducer;
    }

    public void setRestProducer(ComponentSpec spec) {
        this.restProducer = spec;
    }

    public ComponentSpec getAdminProxy() {
        return adminProxy;
    }

    public void setAdminProxy(ComponentSpec spec) {
        this.adminProxy = spec;
    }

    @Description("The specification of the graphical user interface")
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

    @Description("The specification of the collector")
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

    @Description("The specification of the strimzi kafka cluster")
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