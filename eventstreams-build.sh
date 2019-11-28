#!/bin/bash

set -e

echo "Attempting to curl yq..."

sudo curl -L -o /usr/local/bin/yq "https://github.com/mikefarah/yq/releases/download/2.4.0/yq_linux_${B_ARCH}"
sudo chmod +x /usr/local/bin/yq

if ! [ -x "$(command -v yq)" ]; then
  echo 'Error: yq is not installed.' >&2
  exit 1
fi

curl https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get > get_helm.sh
chmod 700 get_helm.sh
./get_helm.sh
helm init -c

./eventstreams-local-build.sh

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