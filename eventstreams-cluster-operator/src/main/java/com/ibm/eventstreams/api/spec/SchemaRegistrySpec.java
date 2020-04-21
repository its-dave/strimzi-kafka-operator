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
import io.strimzi.api.kafka.model.storage.Storage;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"storage", "avro", "proxy"})
@EqualsAndHashCode
public class SchemaRegistrySpec extends SecurityComponentSpec {

    private static final long serialVersionUID = 1L;

    private Storage storage;
    private ContainerSpec avro;
    private ContainerSpec proxy;

    @Description("Storage configuration (disk).")
    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    @Description("Specify overrides for the container used to generate Avro classes")
    public ContainerSpec getAvro() {
        return avro;
    }

    public void setAvro(ContainerSpec avro) {
        this.avro = avro;
    }

    @Description("Specify overrides for the container used for proxying HTTP requests to the Schema Registry")
    public ContainerSpec getProxy() {
        return proxy;
    }

    public void setProxy(ContainerSpec proxy) {
        this.proxy = proxy;
    }
}