#!/bin/bash

set -e
KAFKA_VERSION="2.12-2.4.0"
KAFKA_TAG="2020-02-05-17.15.32-78395e6"
curl -v -H "X-JFrog-Art-Api:${ARTIFACTORY_PASSWORD}" -o /tmp/kafka_${KAFKA_VERSION}.tgz "https://hyc-qp-stable-docker-local.artifactory.swg-devops.com:443/artifactory/hyc-qp-artifacts-generic-local/kafka-vnext/${KAFKA_TAG}/kafka_${KAFKA_VERSION}.tgz"

# Create a backup of kafka-versions.yaml
mv kafka-versions.yaml kafka-versions.yaml.bk
# Move Event Streams version to kafka-versions.yaml to use EventStreams supported Kafka binaries
cp eventstreams-kafka-versions.yaml kafka-versions.yaml

function cleanup {
    mv kafka-versions.yaml.bk kafka-versions.yaml
}

trap cleanup EXIT

function cleanup {
    mv kafka-versions.yaml eventstreams-kafka-versions.yaml
    mv kafka-versions.yaml.bk kafka-versions.yaml
}

trap cleanup EXIT

(cd docker-images/kafka ; 
echo "Pull stunnel from Artifactory..." ; 
curl -u "${ARTIFACTORY_USERNAME}:${ARTIFACTORY_PASSWORD}" -o "stunnel-5.56.sh" "https://eu.artifactory.swg-devops.com/artifactory/hyc-qp-artifacts-generic-local/scripts/stunnel/stunnel-5.56.sh" ;
chmod +x stunnel-5.56.sh ;
./stunnel-5.56.sh ; )

mvn clean

if [[ "${DOCKER_BUILD_ARGS}" != *"B_ARCH"* ]]; then
    DOCKER_BUILD_ARGS="${DOCKER_BUILD_ARGS} --build-arg B_ARCH=amd64"
fi
if [[ "${DOCKER_BUILD_ARGS}" != *"JAVA_IMAGE_TAG_UBI7_OPENJDK8_JRE"* ]]; then
    DOCKER_BUILD_ARGS="${DOCKER_BUILD_ARGS} --build-arg JAVA_IMAGE_TAG_UBI7_OPENJDK8_JRE=latest"
fi
export DOCKER_BUILD_ARGS
MVN_ARGS=-DskipTests make docker_build

# clean up unwanted changes
git checkout -- cluster-operator/src/main/resources
git checkout -- helm-charts/strimzi-kafka-operator/templates/_kafka_image_map.tpl
