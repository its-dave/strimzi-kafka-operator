FROM strimzi/base:latest

ARG strimzi_version=1.0-SNAPSHOT
ENV STRIMZI_VERSION ${strimzi_version}
ENV STRIMZI_HOME=/opt/strimzi
RUN mkdir -p ${STRIMZI_HOME}/bin
WORKDIR ${STRIMZI_HOME}

COPY scripts/ tmp/bin/ bin/

COPY tmp/lib/ lib/

COPY LICENSE /licenses/LICENSE

LABEL name="eventstreams-cluster-operator" \
      vendor="IBM" \
      release="2020.2.1" \
      summary="Event Streams Kafka Operator" \
      description="Event Streams Kafka Operator is a high-throughput, fault-tolerant, pub-sub technology for building event-driven applications. It is built on top of Apache Kafka(R) and Strimzi"

USER ${user}
