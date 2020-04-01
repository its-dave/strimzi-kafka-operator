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

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/*
 * Class for creating Hash-based message authentication code (HMAC) secret which is a shared key that is
 * used by Rest Producer and Schema Registry components to generate/validate hash. It is a mechanism
 * to verify the integrity and authenticity of messages that are exchanged between two components.
 */
public class MessageAuthenticationModel extends AbstractModel {
    public static final String SECRET_SUFFIX = "hmac-secret";
    public static final String HMAC_SECRET = "hmac.secret";
    public static final int NUM_OF_UUID_GEN = 3;
    Secret secret;

    public MessageAuthenticationModel(EventStreams instance) {
        super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), DEFAULT_COMPONENT_NAME);
        setOwnerReference(instance);
        secret = createSecret();
    }

    public static String getSecretName(String instanceName) {
        return getResourcePrefix(instanceName) + "-" + SECRET_SUFFIX;
    }

    public Secret getSecret() {
        return secret;
    }

    public Secret createSecret() {
        return createSecret(getNamespace(), getSecretName(getInstanceName()), createSecretData(), getComponentLabels(), null);
    }

    private Map<String, String> createSecretData() {
        Map<String, String> data = new HashMap<>();
        data.put(HMAC_SECRET, Base64.getEncoder().encodeToString(generateSecret().getBytes(Charset.forName("UTF-8"))));
        return data;
    }

    private String generateSecret() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NUM_OF_UUID_GEN; i++) {
            sb.append(UUID.randomUUID().toString());
        }
        return sb.toString();
    }
}