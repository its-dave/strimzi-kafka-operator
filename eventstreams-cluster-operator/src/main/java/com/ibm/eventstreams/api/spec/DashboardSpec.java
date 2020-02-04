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
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode
public class DashboardSpec implements Serializable {
    private static final long serialVersionUID = 1L;
    /*
     This class is intentionally left blank until we have specific dashboards to create.
     But it allows us to show intent in the CRD for expanding the object, rather than
     using a boolean to signify "generating all dashboards"
     */
}
