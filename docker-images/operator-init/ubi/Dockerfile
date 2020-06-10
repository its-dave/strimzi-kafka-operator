ARG B_ARCH
ARG B_PLATFORM
ARG B_OS
ARG B_REGISTRY
ARG JAVA_IMAGE_TAG_UBI7_OPENJDK8_JRE

FROM ${B_REGISTRY}/eventstreams-common-utils-${B_PLATFORM}-${B_OS}-${B_ARCH}:2019-06-18-12.52.34-aaa1c21 as builder

FROM ${B_REGISTRY}/openjdk-8-jre-ubi7-${B_PLATFORM}-${B_OS}-${B_ARCH}:${JAVA_IMAGE_TAG_UBI7_OPENJDK8_JRE}

COPY --from=builder /usr/local/bin/kubectl /usr/bin/

COPY scripts/* ./

ARG B_ARCH
ARG ES_IMAGE_TAG
RUN echo "operator-init-${B_ARCH}:${ES_IMAGE_TAG}" > /image.txt

USER ${user}

LABEL name="eventstreams-operator-init"

ENTRYPOINT ["./run.sh"]
