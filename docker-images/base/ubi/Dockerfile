ARG B_ARCH
ARG JAVA_IMAGE_TAG_UBI7_OPENJDK8_JDK
FROM hyc-qp-stable-docker-local.artifactory.swg-devops.com/openjdk-8-sdk-ubi7-icp-linux-${B_ARCH}:${JAVA_IMAGE_TAG_UBI7_OPENJDK8_JDK}

RUN microdnf install --nodocs hostname which && \
    microdnf clean all
