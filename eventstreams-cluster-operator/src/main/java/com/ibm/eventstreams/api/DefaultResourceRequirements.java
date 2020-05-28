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
package com.ibm.eventstreams.api;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;

public class DefaultResourceRequirements {

    public static final ResourceRequirements ADMIN_UI = new ResourceRequirementsBuilder()
        .addToRequests("cpu", new Quantity("250m"))
        .addToRequests("memory", new Quantity("512Mi"))
        .addToLimits("cpu", new Quantity("1000m"))
        .addToLimits("memory", new Quantity("512Mi"))
        .build();
        
    public static final ResourceRequirements ADMIN_UI_REDIS = new ResourceRequirementsBuilder()
        .addToRequests("cpu", new Quantity("100m"))
        .addToRequests("memory", new Quantity("128Mi"))
        .addToLimits("cpu", new Quantity("100m"))
        .addToLimits("memory", new Quantity("128Mi"))
        .build();

    public static final ResourceRequirements ADMIN_API = new ResourceRequirementsBuilder()
        .addToRequests("cpu", new Quantity("500m"))
        .addToRequests("memory", new Quantity("1Gi"))
        .addToLimits("cpu", new Quantity("2000m"))
        .addToLimits("memory", new Quantity("1Gi"))
        .build();
    
    public static final ResourceRequirements COLLECTOR = new ResourceRequirementsBuilder()
        .addToRequests("cpu", new Quantity("100m"))
        .addToRequests("memory", new Quantity("128Mi"))
        .addToLimits("cpu", new Quantity("100m"))
        .addToLimits("memory", new Quantity("128Mi"))
        .build();

    public static final ResourceRequirements KAFKA = new ResourceRequirementsBuilder()
        .addToRequests("cpu", new Quantity("100m"))
        .addToRequests("memory", new Quantity("128Mi"))
        .addToLimits("cpu", new Quantity("1000m"))
        .addToLimits("memory", new Quantity("2Gi"))
        .build();

    public static final ResourceRequirements ZOOKEEPER = new ResourceRequirementsBuilder()
        .addToRequests("cpu", new Quantity("100m"))
        .addToRequests("memory", new Quantity("128Mi"))
        .addToLimits("cpu", new Quantity("1000m"))
        .addToLimits("memory", new Quantity("1Gi"))
        .build();

    public static final ResourceRequirements TLS_SIDECAR = new ResourceRequirementsBuilder()
        .addToRequests("cpu", new Quantity("10m"))
        .addToRequests("memory", new Quantity("128Mi"))
        .addToLimits("cpu", new Quantity("100m"))
        .addToLimits("memory", new Quantity("128Mi"))
        .build();

    public static final ResourceRequirements ENTITY_OPERATOR = new ResourceRequirementsBuilder()
        .addToRequests("cpu", new Quantity("10m"))
        .addToRequests("memory", new Quantity("1Gi"))
        .addToLimits("cpu", new Quantity("1000m"))
        .addToLimits("memory", new Quantity("1Gi"))
        .build();

    public static final ResourceRequirements REST_PRODUCER = new ResourceRequirementsBuilder()
        .addToRequests("cpu", new Quantity("250m"))
        .addToRequests("memory", new Quantity("1Gi"))
        .addToLimits("cpu", new Quantity("1000m"))
        .addToLimits("memory", new Quantity("1Gi"))
        .build();

    public static final ResourceRequirements SCHEMA_REGISTRY = new ResourceRequirementsBuilder()
        .addToRequests("cpu", new Quantity("500m"))
        .addToRequests("memory", new Quantity("256Mi"))
        .addToLimits("cpu", new Quantity("500m"))
        .addToLimits("memory", new Quantity("256Mi"))
        .build();

    public static final ResourceRequirements SCHEMA_PROXY = new ResourceRequirementsBuilder()
        .addToRequests("cpu", new Quantity("500m"))
        .addToRequests("memory", new Quantity("1Gi"))
        .addToLimits("cpu", new Quantity("500m"))
        .addToLimits("memory", new Quantity("1Gi"))
        .build();

    public static final ResourceRequirements AVRO_SERVICE = new ResourceRequirementsBuilder()
        .addToRequests("cpu", new Quantity("500m"))
        .addToRequests("memory", new Quantity("1Gi"))
        .addToLimits("cpu", new Quantity("500m"))
        .addToLimits("memory", new Quantity("1Gi"))
        .build();

    public static final ResourceRequirements JMX_TRANS = new ResourceRequirementsBuilder()
        .addToRequests("cpu", new Quantity("250m"))
        .addToRequests("memory", new Quantity("1Gi"))
        .addToLimits("cpu", new Quantity("1000m"))
        .addToLimits("memory", new Quantity("1Gi"))
        .build();
}