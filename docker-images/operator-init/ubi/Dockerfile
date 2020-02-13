ARG B_ARCH
ARG B_PLATFORM
ARG B_OS
ARG B_REGISTRY

FROM ${B_REGISTRY}/eventstreams-common-utils-${B_PLATFORM}-${B_OS}-${B_ARCH}:2019-06-18-12.52.34-aaa1c21 as builder

FROM ${B_REGISTRY}/openjdk-8-jre-ubi7-${B_PLATFORM}-${B_OS}-${B_ARCH}:2020-01-13-16.19.12-b05e627

RUN microdnf install --nodocs openssl && microdnf clean all

COPY --from=builder /usr/local/bin/kubectl /usr/bin/

COPY scripts/* ./

USER 65534

LABEL name="eventstreams-operator-init"

ENTRYPOINT ["./run.sh"]
