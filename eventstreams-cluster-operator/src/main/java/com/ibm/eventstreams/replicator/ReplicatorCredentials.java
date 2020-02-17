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

package com.ibm.eventstreams.replicator;

import com.ibm.eventstreams.api.model.ReplicatorModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import io.fabric8.kubernetes.api.model.Secret;
import io.strimzi.api.kafka.model.CertAndKeySecretSource;
import io.strimzi.api.kafka.model.CertSecretSource;
import io.strimzi.api.kafka.model.CertSecretSourceBuilder;
import io.strimzi.api.kafka.model.KafkaConnectTls;
import io.strimzi.api.kafka.model.KafkaConnectTlsBuilder;
import io.strimzi.api.kafka.model.PasswordSecretSourceBuilder;
import io.strimzi.api.kafka.model.authentication.KafkaClientAuthentication;
import io.strimzi.api.kafka.model.authentication.KafkaClientAuthenticationScramSha512Builder;
import io.strimzi.api.kafka.model.authentication.KafkaClientAuthenticationTlsBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthentication;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationScramSha512;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationTls;
import io.strimzi.api.kafka.model.listener.KafkaListenerTls;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;


public class ReplicatorCredentials {

    KafkaConnectTls replicatorConnectTrustStore;
    KafkaClientAuthentication replicatorConnectClientAuth;
    EventStreams instance;

    private static final Logger log = LogManager.getLogger(ReplicatorCredentials.class.getName());

    public ReplicatorCredentials(EventStreams instance) {
        this.instance = instance;
    }

    public void setReplicatorTrustStore(Secret clusterCa) {

        CertSecretSource caCrt = new CertSecretSourceBuilder()
            .withCertificate("ca.crt")
            .withSecretName(clusterCa.getMetadata().getName())
            .build();

        Optional<KafkaListenerTls> kafaServerTLS = Optional.ofNullable(instance.getSpec().getStrimziOverrides().getKafka().getListeners())
                .map(KafkaListeners::getTls);


        if (kafaServerTLS.isPresent()) {
            replicatorConnectTrustStore = new KafkaConnectTlsBuilder()
                    .withTrustedCertificates(caCrt)
                    .build();
        }

    }

    public void setReplicatorClientAuth(Secret connectUserSecret) {

        Optional<KafkaListenerAuthentication> kafkaClientAuth = Optional.ofNullable(instance.getSpec().getStrimziOverrides().getKafka().getListeners())
                .map(KafkaListeners::getTls)
                .map(KafkaListenerTls::getAuth);

        if (kafkaClientAuth.isPresent()) {
            if (kafkaClientAuth.get() instanceof KafkaListenerAuthenticationTls) {
                CertAndKeySecretSource certKey = new CertAndKeySecretSource();
                certKey.setSecretName(connectUserSecret.getMetadata().getName());
                certKey.setKey("user.key");
                certKey.setCertificate("user.crt");
                replicatorConnectClientAuth = new KafkaClientAuthenticationTlsBuilder()
                        .withCertificateAndKey(certKey)
                        .build();

            } else if (kafkaClientAuth.get() instanceof KafkaListenerAuthenticationScramSha512) {

                replicatorConnectClientAuth = new KafkaClientAuthenticationScramSha512Builder()
                        .withPasswordSecret(new PasswordSecretSourceBuilder()
                            .withSecretName(connectUserSecret.getMetadata().getName())
                            .withPassword("password")
                            .build())
                        .withNewUsername(instance.getMetadata().getName() + "-ibm-es-" + ReplicatorModel.REPLICATOR_CONNECT_USER_NAME)
                        .build();
            }
        }
    }

    public KafkaConnectTls getReplicatorConnectTrustStore() {

        return this.replicatorConnectTrustStore;
    }

    public KafkaClientAuthentication getReplicatorConnectClientAuth() {
        return this.replicatorConnectClientAuth;
    }
}
