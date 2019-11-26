#!/bin/bash

set -e

echo "Attempting to curl yq..."

sudo curl -L -o /usr/local/bin/yq "https://github.com/mikefarah/yq/releases/download/2.4.0/yq_linux_${B_ARCH}"
sudo chmod +x /usr/local/bin/yq

if ! [ -x "$(command -v yq)" ]; then
  echo 'Error: yq is not installed.' >&2
  exit 1
fi

curl -v -H "X-JFrog-Art-Api:${ARTIFACTORY_PASSWORD}" -o /tmp/kafka_2.12-2.3.1.tgz "https://na.artifactory.swg-devops.com/artifactory/hyc-qp-artifacts-generic-local/kafka-vnext/2019-11-25-14.57.14-4d30d3c/kafka_2.12-2.3.1.tgz"

# Move Event Streams version to kafka-versions.yaml to use EventStreams supported Kafka binaries
mv eventstreams-kafka-versions.yaml kafka-versions.yaml

mvn clean

ALTERNATE_BASE=ubi MVN_ARGS=-DskipTests make docker_build || echo "Build failed but might be expected due to helm missing"

docker login -u="${ARTIFACTORY_USERNAME}" -p="${ARTIFACTORY_PASSWORD}" "${destination_registry}"

KAFKA_IMAGE="${destination_registry}/strimzi/kafka-${B_ARCH}:${TAG}"
OPERATOR_IMAGE="${destination_registry}/strimzi/operator-${B_ARCH}:${TAG}"

echo "Retagging strimzi/kafka:latest to ${KAFKA_IMAGE}..."
docker tag "strimzi/kafka:latest" "${KAFKA_IMAGE}"

echo "Retagging strimzi/operator:latest to ${OPERATOR_IMAGE}..."
docker tag "strimzi/operator:latest" "${OPERATOR_IMAGE}"

echo "Pushing images to ${destination_registry}..."
docker push "${KAFKA_IMAGE}"
docker push "${OPERATOR_IMAGE}"