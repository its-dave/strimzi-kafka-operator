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
package com.ibm.eventstreams.api.model;

import com.ibm.eventstreams.api.spec.EventStreams;
import io.fabric8.kubernetes.api.model.Secret;
import io.strimzi.certs.CertAndKey;

import java.util.HashMap;
import java.util.Map;

public class CertificateSecretModel extends AbstractModel {
    public static final String CERT_SECRET_NAME_POSTFIX = "cert";
    public static final String CERT_ID_POSTFIX = "crt";
    public static final String KEY_ID_POSTFIX = "key";
    private static final String ID_FORMAT = "%s.%s";
    private Map<String, CertAndKey> certificates = new HashMap<>();

    public CertificateSecretModel(EventStreams instance, String namespace, String componentName) {
        super(instance.getMetadata().getName(), namespace, componentName);
        setOwnerReference(instance);
    }

    public String getSecretName() {
        return getDefaultResourceName() + "-" + CERT_SECRET_NAME_POSTFIX;
    }

    public static String formatCertID(String name) {
        return String.format(ID_FORMAT, name, CERT_ID_POSTFIX);
    }

    public static String formatKeyID(String name) {
        return String.format(ID_FORMAT, name, KEY_ID_POSTFIX);
    }

    public String getCertID(String name) {
        return formatCertID(name);
    }

    public String getKeyID(String name) {
        return formatKeyID(name);
    }

    public Secret getSecret() {
        return createSecret(getNamespace(), getSecretName(), formSecretData(), getComponentLabels(), null);
    }

    public void setCertAndKey(String id, CertAndKey certAndKey) {
        certificates.put(id, certAndKey);
    }

    private Map<String, String> formSecretData() {
        Map<String, String> data = new HashMap<>();
        certificates.forEach((key, value) -> {
            data.put(getCertID(key), value.certAsBase64String());
            data.put(getKeyID(key), value.keyAsBase64String());
        });
        return data;
    }

}
