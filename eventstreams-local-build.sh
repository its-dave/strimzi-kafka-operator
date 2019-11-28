#!/bin/bash

set -e

curl -v -H "X-JFrog-Art-Api:${ARTIFACTORY_PASSWORD}" -o /tmp/kafka_2.12-2.3.1.tgz "https://na.artifactory.swg-devops.com/artifactory/hyc-qp-artifacts-generic-local/kafka-vnext/2019-11-25-14.57.14-4d30d3c/kafka_2.12-2.3.1.tgz"

# Move Event Streams version to kafka-versions.yaml to use EventStreams supported Kafka binaries
mv eventstreams-kafka-versions.yaml kafka-versions.yaml

(cd docker-images/kafka ; 
echo "Pull stunnel from Artifactory..." ; 
curl -u "${ARTIFACTORY_USERNAME}:${ARTIFACTORY_PASSWORD}" -o "stunnel-5.56.sh" "https://eu.artifactory.swg-devops.com/artifactory/hyc-qp-artifacts-generic-local/scripts/stunnel/stunnel-5.56.sh" ;
chmod +x stunnel-5.56.sh ;
./stunnel-5.56.sh ; )

mvn clean

ALTERNATE_BASE=ubi MVN_ARGS=-DskipTests make docker_build 