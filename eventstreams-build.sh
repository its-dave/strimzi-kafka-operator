#!/bin/bash

set -e
. ./eventstreams-build-functions.sh

get_yq

OPERATOR_SDK_VERSION=v0.17.0

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

make eventstreams_build
make -C deploy get_opm
make -C deploy build_bundle

if [[ $TAG == *exp ]]; then
  echo 'Docker tag ends with "exp" so not transferring Strimzi artifact to Artifactory'
else
  echo 'Docker tag does not end with "exp" so transferring Strimzi artifact to Artifactory...'
  mvn deploy -s ./eventstreams-settings.xml -DskipTests
fi

sourceImageNameSuffixes=("strimzi/kafka" "strimzi/operator" "strimzi/operator-init" "strimzi/jmxtrans" "local-operator-registry" "olm-bundle")
targetImageNameSuffixes=("strimzi/kafka" "strimzi/operator" "strimzi/operator-init" "strimzi/jmxtrans" "operator-index" "operator-bundle")
targetImageLatestTagOverride=("latest-kafka-$(get_kafka_versions)" "" "" "" "" "")
for ((i=0; i<${#sourceImageNameSuffixes[*]}; i++)); do
  sourceImage="${sourceImageNameSuffixes[i]}:latest"
  targetImageName="${destination_registry}/${targetImageNameSuffixes[i]}-${B_ARCH}"
  echo "Retagging ${sourceImage} and pushing to ${targetImageName}:${TAG}"
  docker tag "${sourceImage}" "${targetImageName}:${TAG}"
  docker push "${targetImageName}:${TAG}"
  if [[ ${TAG} == *exp ]]; then
    echo 'Docker tag ends with "exp" so not pushing latest images'
  else
    echo 'Docker tag does not end with "exp" so pushing latest images'
    targetLatestImage="${targetImageName}:${targetImageLatestTagOverride[i]:-"latest"}"
    docker tag "${sourceImage}" "${targetLatestImage}"
    docker push "${targetLatestImage}"
  fi
done
