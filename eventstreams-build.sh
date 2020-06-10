#!/bin/bash

set -e
. ./eventstreams-build-functions.sh

get_yq

OPERATOR_SDK_VERSION=v0.17.0
sudo apk add skopeo
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


make eventstreams_build ES_IMAGE_TAG="${TAG}"


if [[ $TAG == *exp ]]; then
  echo 'Docker tag ends with "exp" so not transferring Strimzi artifact to Artifactory'
else
  echo 'Docker tag does not end with "exp" so transferring Strimzi artifact to Artifactory...'
  mvn deploy -s ./eventstreams-settings.xml -DskipTests
fi

sourceImageNameSuffixes=("strimzi/kafka" "strimzi/operator" "strimzi/operator-init" "strimzi/jmxtrans")
targetImageLatestTagOverride=("latest-kafka-$(get_kafka_versions)" "" "" "")
for ((i=0; i<${#sourceImageNameSuffixes[*]}; i++)); do
  sourceImage="${sourceImageNameSuffixes[i]}:latest"
  targetImageName="${destination_registry}/${sourceImageNameSuffixes[i]}-${B_ARCH}"
  if [ "${sourceImageNameSuffixes[i]}" == "strimzi/kafka" ]; then
    targetImageName="${destination_registry}/${sourceImageNameSuffixes[i]}"
  fi
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


make -C deploy get_opm
make -C deploy build_bundle_staging
make -C deploy build_bundle_artifactory


skopeo_image_to_staging(){
  VALUES_YAML_FILE=./eventstreams-helm-charts/values.yaml
  DIGEST_VALUES_YAML_FILE=./eventstreams-helm-charts/ibm-eventstreams-operator/values.yaml  artifactory_repository=$(yq r "${VALUES_YAML_FILE}" "${1}.image.repository")
  artifactory_image=$(yq r "${VALUES_YAML_FILE}" "${1}.image.name")

  repository=$(yq r "${DIGEST_VALUES_YAML_FILE}" "${1}.image.repository" )
  image=$(yq r "${DIGEST_VALUES_YAML_FILE}" "${1}.image.name")
  digest=$(yq r "${DIGEST_VALUES_YAML_FILE}" "${1}.image.digest" )

  
  src_image=docker://"${artifactory_repository}"/"${artifactory_image}"@sha256:"${digest}"
  dest_image=docker://"${repository}"/"${image}"
  
  # put all images into staging for now
  dest_image=${dest_image//docker.io\/ibmcom/cp.stg.icr.io\/cp}
  dest_image=${dest_image//cp.icr.io\/cp/cp.stg.icr.io\/cp}

  echo "skopeo copy "${src_image}" "${dest_image}" --src-creds "${ARTIFACTORY_USERNAME}":"${ARTIFACTORY_PASSWORD}" --dest-creds "${STAGING_USERNAME}":"${STAGING_PASSWORD}" --all"

  skopeo copy "${src_image}" "${dest_image}" --src-creds "${ARTIFACTORY_USERNAME}":"${ARTIFACTORY_PASSWORD}" --dest-creds "${STAGING_USERNAME}":"${STAGING_PASSWORD}" --all
}

skopeo_image_to_staging restProducer
skopeo_image_to_staging adminApi
skopeo_image_to_staging schemaRegistry
skopeo_image_to_staging schemaRegistryAvro
skopeo_image_to_staging schemaRegistryProxy
skopeo_image_to_staging adminUi
skopeo_image_to_staging adminUiDatabase
skopeo_image_to_staging collector
# images build during this build mirrored last to give manifest job time to run
skopeo_image_to_staging operator
skopeo_image_to_staging operatorInit
skopeo_image_to_staging kafka
skopeo_image_to_staging jmxTrans