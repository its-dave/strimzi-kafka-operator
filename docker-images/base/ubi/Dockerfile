FROM registry.access.redhat.com/ubi8/ubi-minimal
ARG JAVA_VERSION

RUN microdnf -y update \
    && microdnf -y install hostname openssl tar vi which java-${JAVA_VERSION}-openjdk-headless \
    && microdnf -y clean all

LABEL JAVA_VERSION=${JAVA_VERSION}
