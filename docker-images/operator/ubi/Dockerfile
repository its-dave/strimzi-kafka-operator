FROM strimzi/base:latest

ARG strimzi_version=1.0-SNAPSHOT
ENV STRIMZI_VERSION ${strimzi_version}
ENV STRIMZI_HOME=/opt/strimzi
RUN mkdir -p ${STRIMZI_HOME}/bin
WORKDIR ${STRIMZI_HOME}

COPY scripts/ tmp/bin/ bin/

COPY tmp/lib/ lib/

COPY LICENSE /licenses/LICENSE

LABEL name="strimzi-operator" \
      vendor="CNCF" \
      release="2.4.0" \
      summary="Strimzi Kafka Operator" \
      description="Strimzi Kafka Operator is a high-throughput, fault-tolerant, pub-sub technology for building event-driven applications. It is built on top of Apache Kafka(R)"

USER 1001
