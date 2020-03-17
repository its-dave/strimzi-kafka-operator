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
package com.ibm.eventstreams.api.status;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;


@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode
public class EventStreamsVersions implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String OPERAND_VERSION = "2020.1.1";
    public static final String AUTO_UPGRADE_VERSION = "2020.1";
    private static final EventStreamsAvailableVersions AVAILABLE_VERSIONS = new EventStreamsAvailableVersions();

    public String getInstalled() {
        return OPERAND_VERSION;
    }

    // This method is needed to enable deserialising by Jackson
    public void setInstalled(String version) {
    }

    public EventStreamsAvailableVersions getAvailable() {
        return AVAILABLE_VERSIONS;
    }

    // This method is needed to enable deserialising by Jackson
    public void setAvailable(EventStreamsAvailableVersions versions) {
    }
}
