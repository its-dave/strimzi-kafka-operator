#!/bin/bash

set -e

curl -v -H "X-JFrog-Art-Api:${ARTIFACTORY_PASSWORD}" -o /tmp/kafka_2.12-2.3.1.tgz "https://na.artifactory.swg-devops.com/artifactory/hyc-qp-artifacts-generic-local/kafka-vnext/2019-12-11-11.29.29-2d2c435-exp/kafka_2.12-2.3.1.tgz"

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

(cd docker-images/base ; 
echo "Pull tini from Artifactory..." ; 
curl -u "${ARTIFACTORY_USERNAME}:${ARTIFACTORY_PASSWORD}" -o "tini-0.18.sh" "https://eu.artifactory.swg-devops.com/artifactory/hyc-qp-artifacts-generic-local/scripts/tini/tini-0.18.sh" ;
chmod +x tini-0.18.sh ;
./tini-0.18.sh ; )

(cd docker-images/kafka ; 
echo "Pull stunnel from Artifactory..." ; 
curl -u "${ARTIFACTORY_USERNAME}:${ARTIFACTORY_PASSWORD}" -o "stunnel-5.56.sh" "https://eu.artifactory.swg-devops.com/artifactory/hyc-qp-artifacts-generic-local/scripts/stunnel/stunnel-5.56.sh" ;
chmod +x stunnel-5.56.sh ;
./stunnel-5.56.sh ; )

mvn clean

ALTERNATE_BASE=ubi MVN_ARGS=-DskipTests make docker_build 