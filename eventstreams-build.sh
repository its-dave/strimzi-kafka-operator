#!/bin/bash

set -e

echo "Attempting to curl yq..."

sudo curl -Lvo /usr/local/bin/yq "https://github.com/mikefarah/yq/releases/download/2.4.0/yq_linux_${B_ARCH}"
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
OPERATOR_IMAGE="${destination_registry}/strimzi/operator-${B_ARCH}:${TAG}"

echo "Retagging strimzi/kafka:latest to ${KAFKA_IMAGE}..."
docker tag "strimzi/kafka:latest" "${KAFKA_IMAGE}"

echo "Retagging strimzi/operator:latest to ${OPERATOR_IMAGE}..."
docker tag "strimzi/operator:latest" "${OPERATOR_IMAGE}"

echo "Pushing images to ${destination_registry}..."
docker push "${KAFKA_IMAGE}"
docker push "${OPERATOR_IMAGE}"