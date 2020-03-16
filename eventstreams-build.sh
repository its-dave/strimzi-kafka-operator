#!/bin/bash

set -e

echo "Attempting to curl yq..."

sudo curl -Lvo /usr/local/bin/yq "https://github.com/mikefarah/yq/releases/download/3.2.1/yq_linux_${B_ARCH}"
sudo chmod +x /usr/local/bin/yq

OPERATOR_SDK_VERSION=v0.15.1

sudo pip3 install operator-courier
sudo curl -vLo /usr/local/bin/operator-sdk https://github.com/operator-framework/operator-sdk/releases/download/$OPERATOR_SDK_VERSION/operator-sdk-$OPERATOR_SDK_VERSION-x86_64-linux-gnu
sudo chmod +x /usr/local/bin/operator-sdk

if ! [ -x "$(command -v yq)" ]; then
  echo 'Error: yq is not installed.' >&2
  exit 1
fi

curl https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get > get_helm.sh
chmod 700 get_helm.sh
./get_helm.sh
helm init -c

docker login -u="${ARTIFACTORY_USERNAME}" -p="${ARTIFACTORY_PASSWORD}" "${destination_registry}"

./eventstreams-local-build.sh

echo "Transfer Strimzi artifact to Artifactory..."
mvn deploy -s ./eventstreams-settings.xml -DskipTests

KAFKA_IMAGE="${destination_registry}/strimzi/kafka-${B_ARCH}:${TAG}"
KAFKA_IMAGE_LATEST="${destination_registry}/strimzi/kafka-${B_ARCH}:latest-kafka-2.4.0"

OPERATOR_IMAGE="${destination_registry}/strimzi/operator-${B_ARCH}:${TAG}"
OPERATOR_IMAGE_LATEST="${destination_registry}/strimzi/operator-${B_ARCH}:latest"

OPERATOR_INIT_IMAGE="${destination_registry}/strimzi/operator-init-${B_ARCH}:${TAG}"
OPERATOR_INIT_IMAGE_LATEST="${destination_registry}/strimzi/operator-init-${B_ARCH}:latest"

echo "Retagging strimzi/kafka:latest to ${KAFKA_IMAGE}..."
docker tag "strimzi/kafka:latest" "${KAFKA_IMAGE}"
echo "Retagging strimzi/kafka:latest to ${KAFKA_IMAGE_LATEST}..."
docker tag "strimzi/kafka:latest" "${KAFKA_IMAGE_LATEST}"

echo "Retagging strimzi/operator:latest to ${OPERATOR_IMAGE}..."
docker tag "strimzi/operator:latest" "${OPERATOR_IMAGE}"
echo "Retagging strimzi/operator:latest to ${OPERATOR_IMAGE_LATEST}..."
docker tag "strimzi/operator:latest" "${OPERATOR_IMAGE_LATEST}"

echo "Retagging strimzi/operator-init:latest to ${OPERATOR_INIT_IMAGE}..."
docker tag "strimzi/operator-init:latest" "${OPERATOR_INIT_IMAGE}"
echo "Retagging strimzi/operator-init:latest to ${OPERATOR_INIT_IMAGE_LATEST}..."
docker tag "strimzi/operator-init:latest" "${OPERATOR_INIT_IMAGE_LATEST}"

echo "Pushing images to ${destination_registry}..."
docker push "${KAFKA_IMAGE}"
docker push "${OPERATOR_IMAGE}"
docker push "${OPERATOR_INIT_IMAGE}"
if [[ $TAG == *exp ]]; then
  echo 'Docker tag ends with "exp" so not pushing latest images'
else
  echo 'Docker tag does not end with "exp" so pushing latest images'
  docker push "${KAFKA_IMAGE_LATEST}"
  docker push "${OPERATOR_IMAGE_LATEST}"
  docker push "${OPERATOR_INIT_IMAGE_LATEST}"
fi
