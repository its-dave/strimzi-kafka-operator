ARG B_ARCH
ARG JAVA_IMAGE_TAG_UBI7_OPENJDK8_JRE
FROM hyc-qp-stable-docker-local.artifactory.swg-devops.com/openjdk-8-jre-ubi7-icp-linux-${B_ARCH}:${JAVA_IMAGE_TAG_UBI7_OPENJDK8_JRE}

RUN microdnf install --nodocs hostname openssl which && \
    microdnf clean all
