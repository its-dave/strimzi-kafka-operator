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

import com.ibm.eventstreams.api.model.utils.ModelUtils;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.junit.jupiter.api.Test;
import java.util.Base64;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageAuthenticationModelTest {
    private final String instanceName = "test-instance";
    private final int defaultReplicas = 1;
    private static final int UUID_LENGTH = 36;

    private EventStreamsBuilder createDefaultEventStreams() {
        return ModelUtils.createDefaultEventStreams(instanceName)
            .editSpec()
                .withNewAdminApi()
                    .withReplicas(defaultReplicas)
                .endAdminApi()
            .endSpec();
    }

    private MessageAuthenticationModel createDefaultMessageAuthenticationModel() {
        EventStreams eventStreamsResource = createDefaultEventStreams().build();
        return new MessageAuthenticationModel(eventStreamsResource);
    }

    @Test
    public void testDefaultMessageAuthenticationModel() {
        MessageAuthenticationModel model = createDefaultMessageAuthenticationModel();
        Secret secret = model.getSecret();
        String secretData = new String(Base64.getDecoder().decode(secret.getData().get(MessageAuthenticationModel.HMAC_SECRET)));
        String regex = "([0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12}){" + MessageAuthenticationModel.NUM_OF_UUID_GEN + "}";
        assertTrue(secretData.matches(regex));
        assertThat(secretData, hasLength(MessageAuthenticationModel.NUM_OF_UUID_GEN * UUID_LENGTH));
    }

    @Test
    public void testIsValidHmacSecretTrueWithCorrectKey() {
        MessageAuthenticationModel model = createDefaultMessageAuthenticationModel();
        Secret secret = new SecretBuilder()
                .editOrNewMetadata()
                .withNewName("my-secret")
                .endMetadata()
                .addToData(MessageAuthenticationModel.HMAC_SECRET, "some-secret")
                .build();
        assertThat(model.isValidHmacSecret(secret), is(true));
    }

    @Test
    public void testIsValidHmacSecretFalseWithNullSecret() {
        MessageAuthenticationModel model = createDefaultMessageAuthenticationModel();
        assertThat(model.isValidHmacSecret(null), is(false));
    }

    @Test
    public void testIsValidHmacSecretFalseWithSecretWithoutHmacKey() {
        MessageAuthenticationModel model = createDefaultMessageAuthenticationModel();
        Secret secret = new SecretBuilder()
                .editOrNewMetadata()
                    .withNewName("my-secret")
                .endMetadata()
                .addToData(MessageAuthenticationModel.HMAC_SECRET + "foo", "some-secret")
                .build();
        assertThat(model.isValidHmacSecret(secret), is(false));

    }
}
