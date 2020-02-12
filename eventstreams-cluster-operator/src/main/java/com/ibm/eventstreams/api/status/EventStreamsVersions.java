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


import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

import java.io.Serializable;
import java.util.List;

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
    public static final List<String> AVAILABLE_VERSIONS = unmodifiableList(singletonList(OPERAND_VERSION));
    public static final List<String> AUTO_UPGRADE_VERSIONS = unmodifiableList(singletonList(AUTO_UPGRADE_VERSION));

    public String getReconciledVersion() {
        return OPERAND_VERSION;
    }
    
    public void setReconciledVersion(String version) {
    }

    public List<String> getAvailableAppVersions() {
        return AVAILABLE_VERSIONS;
    }

    // This method is for deserialising for jackson
    public void setAvailableAppVersions(List<String> versions) {
    }

    public List<String> getAutoUpgradeVersions() {
        return AUTO_UPGRADE_VERSIONS;
    }

    // This method is for deserialising for jackson
    public void setAutoUpgradeVersions(List<String> versions) {
    }
}
