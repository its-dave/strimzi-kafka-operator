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
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class ClusterSecretsModel extends AbstractModel {
    public static final String EVENTSTREAMS_IBMCLOUD_CA_CERT_SECRET_SUFFIX = "ibmcloud-ca-cert";
    public static final String COMPONENT_NAME = "cluster-secrets";
    private SecretOperator secretOperator;
    private EventStreams instance;
    private static final Logger log = LogManager.getLogger(ClusterSecretsModel.class.getName());

    /**
     * This class is used to create cluster wide secrets that are used by a number of different EventStreams components.
     * @param instance - This Event Streams instance
     * @param secretOperator - A SecretOperator instance
     */
    public ClusterSecretsModel(EventStreams instance, SecretOperator secretOperator) {
        super(instance.getMetadata().getName(), instance.getMetadata().getNamespace(), COMPONENT_NAME);
        setOwnerReference(instance);
        this.secretOperator = secretOperator;
        this.instance = instance;
    }


    public static String getIBMCloudSecretName(EventStreams instance) {
        return getDefaultResourceName(instance.getMetadata().getName(), EVENTSTREAMS_IBMCLOUD_CA_CERT_SECRET_SUFFIX);
    }

    /*
        This method is used to generate the secret containing the ICP Cluster CA Cert.
        We use this secret to volume mount the ca cert for use as the Admin API truststore during
        IAM calls.
     */
    public Future<Void> createIBMCloudCASecret(String clusterCert) {

        Promise<Void> generateICPClusterCASecretPromise = Promise.promise();

        secretOperator.getAsync(instance.getMetadata().getNamespace(), getIBMCloudSecretName(instance)).setHandler(getRes -> {
            if (getRes.result() == null) {
                Map<String, String> data = new HashMap<>();
                data.put(CA_CERT, clusterCert);

                Secret esICPCASecret = this.createSecret(ClusterSecretsModel.getIBMCloudSecretName(this.instance), data);
                secretOperator.createOrUpdate(esICPCASecret).setHandler(createSecretRes -> {
                    if (createSecretRes.succeeded()) {
                        log.debug("Secret {} successfully generated", createSecretRes.result().resource().getMetadata().getName());
                        generateICPClusterCASecretPromise.complete();
                    } else {
                        log.error("Failed to generate Secret {}: {}", getIBMCloudSecretName(instance), createSecretRes.cause());
                        generateICPClusterCASecretPromise.fail(createSecretRes.cause());
                    }
                });
            } else {
                log.debug("Secret {} already exists", getIBMCloudSecretName(instance));
                generateICPClusterCASecretPromise.complete();
            }
        });

        return generateICPClusterCASecretPromise.future();
    }
}