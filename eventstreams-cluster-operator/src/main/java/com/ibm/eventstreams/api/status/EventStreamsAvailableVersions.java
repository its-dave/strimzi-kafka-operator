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


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;


@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode
public class EventStreamsAvailableVersions implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final List<String> STRICT_VERSIONS = unmodifiableList(singletonList(EventStreamsVersions.OPERAND_VERSION));
    public static final List<String> LOOSE_VERSIONS = unmodifiableList(singletonList(EventStreamsVersions.AUTO_UPGRADE_VERSION));

    @Description("A list of versions that the Operator is able to upgrade this instance of Event Streams to.")
    public List<String> getStrictVersions() {
        return STRICT_VERSIONS;
    }

    // This method is needed to enable deserialising by Jackson
    public void setStrictVersions(List<String> versions) {
    }

    @Description("A list of versions that the Operator is able to automatically upgrade from.")
    public List<String> getLooseVersions() {
        return LOOSE_VERSIONS;
    }

    // This method is needed to enable deserialising by Jackson
    public void setLooseVersions(List<String> versions) {
    }
}
