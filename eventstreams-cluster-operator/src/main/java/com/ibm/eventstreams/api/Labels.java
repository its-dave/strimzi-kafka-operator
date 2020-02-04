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
package com.ibm.eventstreams.api;

public class Labels {

    public static final String APP_LABEL = "app";
    public static final String COMPONENT_LABEL = "component";
    public static final String INSTANCE_LABEL = "instance";
    public static final String RELEASE_LABEL = "release";
    public static final String SERVICE_SELECTOR_LABEL = "serviceSelector";

    public static final String KUBERNETES_DOMAIN = "app.kubernetes.io/";
    public static final String KUBERNETES_NAME_LABEL = KUBERNETES_DOMAIN + "name";
    public static final String KUBERNETES_NAME = "eventstreams";
    public static final String KUBERNETES_INSTANCE_LABEL = KUBERNETES_DOMAIN + "instance";
    public static final String KUBERNETES_MANAGED_BY_LABEL = KUBERNETES_DOMAIN + "managed-by";
    public static final String KUBERNETES_MANAGED_BY = "eventstreams-operator";

}
