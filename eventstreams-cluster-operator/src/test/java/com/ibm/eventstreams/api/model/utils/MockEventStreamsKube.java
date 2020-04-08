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

package com.ibm.eventstreams.api.model.utils;

import com.ibm.eventstreams.api.Crds;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsDoneable;
import com.ibm.eventstreams.api.spec.EventStreamsList;
import com.ibm.eventstreams.api.spec.EventStreamsReplicator;
import com.ibm.eventstreams.api.spec.EventStreamsReplicatorDoneable;
import com.ibm.eventstreams.api.spec.EventStreamsReplicatorList;
import com.ibm.iam.api.spec.Client;
import com.ibm.iam.api.spec.ClientDoneable;
import com.ibm.iam.api.spec.ClientList;
import io.strimzi.api.kafka.KafkaMirrorMaker2List;
import io.strimzi.api.kafka.KafkaList;
import io.strimzi.api.kafka.KafkaUserList;
import io.strimzi.api.kafka.model.DoneableKafka;
import io.strimzi.api.kafka.model.DoneableKafkaMirrorMaker2;
import io.strimzi.api.kafka.model.DoneableKafkaUser;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.test.mockkube.MockKube;

public class MockEventStreamsKube extends MockKube {

    public MockEventStreamsKube() {
        super();

        super.withCustomResourceDefinition(Crds.getCrd(EventStreams.class), EventStreams.class, EventStreamsList.class, EventStreamsDoneable.class)
            .end()
            .withCustomResourceDefinition(io.strimzi.api.kafka.Crds.kafka(), Kafka.class, KafkaList.class, DoneableKafka.class)
            .end()
            .withCustomResourceDefinition(io.strimzi.api.kafka.Crds.kafkaUser(), KafkaUser.class, KafkaUserList.class, DoneableKafkaUser.class)
            .end()
            .withCustomResourceDefinition(Crds.getCrd(Client.class), Client.class, ClientList.class, ClientDoneable.class)
            .end()
            .withCustomResourceDefinition(io.strimzi.api.kafka.Crds.kafkaMirrorMaker2(), KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class, DoneableKafkaMirrorMaker2.class)
            .end()
            .withCustomResourceDefinition(Crds.getCrd(EventStreamsReplicator.class), EventStreamsReplicator.class, EventStreamsReplicatorList.class, EventStreamsReplicatorDoneable.class)
                .end();
    }
}
