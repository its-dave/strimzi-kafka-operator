ARG B_ARCH
ARG JRE_IMAGE_TAG
FROM hyc-qp-stable-docker-local.artifactory.swg-devops.com/openjdk-8-jre-ubi7-icp-linux-${B_ARCH}:${JRE_IMAGE_TAG}

RUN microdnf install --nodocs hostname openssl which && \
    microdnf clean all
