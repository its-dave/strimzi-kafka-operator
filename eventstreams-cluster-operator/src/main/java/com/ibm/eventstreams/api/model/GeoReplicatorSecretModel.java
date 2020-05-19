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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

public class GeoReplicatorSecretModel extends AbstractModel {

    public static final String REPLICATOR_TARGET_CLUSTERS_SECRET_KEY_NAME = "georeplicationdestinationclusters";
    public static final String REPLICATOR_SECRET_NAME = "georep-secret";

    private Secret secret;

    private static final Logger log = LogManager.getLogger(GeoReplicatorSecretModel.class.getName());

    public GeoReplicatorSecretModel(EventStreams instance) {
        super(instance, GeoReplicatorModel.COMPONENT_NAME, GeoReplicatorModel.APPLICATION_NAME);
        setOwnerReference(instance);

        Base64.Encoder encoder = Base64.getEncoder();
        Map<String, String> data = Collections.singletonMap(REPLICATOR_TARGET_CLUSTERS_SECRET_KEY_NAME, encoder.encodeToString("[]".getBytes(StandardCharsets.UTF_8)));
        // Secret is always created as it is used by AdminApi to know details about replication even if not enabled
        this.secret = createSecret(getDefaultResourceName(getInstanceName(),  REPLICATOR_SECRET_NAME), data);

    }

    /**
     * @return Secret return the geo-replicator secret
     */
    public Secret getSecret() {
        return this.secret;
    }
    /**
     * @return String return the geo-replicator secret name
     */
    public String getSecretName() {
        return getDefaultResourceName(getInstanceName(), REPLICATOR_SECRET_NAME);
    }

}
