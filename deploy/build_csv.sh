#!/usr/bin/env bash

set -e

${CP} "${INSTALL_DIR}/040-Crd-kafka.yaml" "${KAFKA_CRD_FILE}"
${CP} "${INSTALL_DIR}/041-Crd-kafkaconnect.yaml" "${KAFKA_CONNECT_CRD_FILE}"
${CP} "${INSTALL_DIR}/042-Crd-kafkaconnects2i.yaml" "${KAFKA_CONNECT_S2I_CRD_FILE}"
${CP} "${INSTALL_DIR}/043-Crd-kafkatopic.yaml" "${KAFKA_TOPIC_CRD_FILE}"
${CP} "${INSTALL_DIR}/044-Crd-kafkauser.yaml" "${KAFKA_USER_CRD_FILE}"
# ignoring Mirror Maker and Bridge CRDs as we don't use them in Event Streams
#${CP} ${INSTALL_DIR}/045-Crd-kafkamirrormaker.yaml ${KAFKA_MIRROR_MAKER_CRD_FILE}
#${CP} ${INSTALL_DIR}/046-Crd-kafkabridge.yaml ${KAFKA_BRIDGE_CRD_FILE}
${CP} "${INSTALL_DIR}/047-Crd-kafkaconnector.yaml" "${KAFKA_CONNECTOR_CRD_FILE}"
${CP} "${INSTALL_DIR}/048-Crd-kafkamirrormaker2.yaml" "${KAFKA_MIRROR_MAKER_2_CRD_FILE}"
${CP} "${INSTALL_DIR}/049-Crd-kafkarebalance.yaml" "${KAFKA_REBALANCE_CRD_FILE}"
${CP} "${INSTALL_DIR}/140-Crd-eventstreams.yaml" "${EVENTSTREAMS_CRD_FILE}"
${CP} "${INSTALL_DIR}/143-Crd-eventstreamsreplicator.yaml" "${EVENTSTREAMS_GEOREP_CRD_FILE}"
yq w -i "${EVENTSTREAMS_CRD_FILE}" spec.version v1beta1
yq w -i "${EVENTSTREAMS_GEOREP_CRD_FILE}" spec.version v1beta1

${CP} ../examples/eventstreams/*.yaml "${CRD_DIR}"
LAST_CREATED_DATE="$(yq r "${GENERATED_CSV}" metadata.annotations.createdAt)"
${CP} "${INSTALL_DIR}/020-ClusterRole-strimzi-cluster-operator-role.yaml" "${ROLE_FILE}"
${CP} "${INSTALL_DIR}/020-RoleBinding-strimzi-cluster-operator.yaml" "${ROLE_BINDING_FILE}"
${CP} "${INSTALL_DIR}/010-ServiceAccount-strimzi-cluster-operator.yaml" "${SERVICE_ACCOUNT_FILE}"
${CP} "${INSTALL_DIR}/150-Deployment-eventstreams-cluster-operator.yaml" "${OPERATOR_FILE}"
yq r ${ROLE_FILE} metadata.name | xargs yq w -i ${OPERATOR_FILE} spec.template.spec.serviceAccountName
${SED} -i "s/metadata.namespace/metadata.annotations['olm.targetNamespaces']/" "${OPERATOR_FILE}"
${SED} -i "0,/metadata.annotations\['olm.targetNamespaces'\]/{s/metadata.annotations\['olm.targetNamespaces'\]/metadata.namespace/}" "${OPERATOR_FILE}"

${CP} "${INSTALL_DIR}/031-ClusterRole-strimzi-entity-operator.yaml" "${BUNDLE_DIR}/entityoperator.clusterrole.yaml"
${CP} "${INSTALL_DIR}/122-ClusterRole-eventstreams-ui.yaml" "${BUNDLE_DIR}/adminui.clusterrole.yaml"
${CP} "${INSTALL_DIR}/123-ClusterRole-eventstreams-admin.yaml" "${BUNDLE_DIR}/adminapi.clusterrole.yaml"

echo "Generating csv"
cd ..
operator-sdk generate csv --csv-version "${CSV_VERSION}" --operator-name ${OPERATOR_NAME} --update-crds --make-manifests=false
cd -

echo "Deleting build directory"
rm -rf "${BUILD_DIR}"

yq d -i "${GENERATED_CSV}" spec.customresourcedefinitions.owned.*
${SED} -i "s/owned: \[\]/owned:/" "${GENERATED_CSV}"

yq m -ix "${GENERATED_CSV}" csv_manual_fields.yaml
yq w -i "${GENERATED_CSV}" metadata.annotations.createdAt ${DATE}

images_config="$(yq r ../eventstreams-helm-charts/ibm-eventstreams-operator/values.yaml --collect *.image)"
printf "Found images:\n${images_config}\n"
number_of_images="$(echo '${images_config}' | yq r - --length)"
echo "${number_of_images}"

declare -i relatedImagesIndex=0

function add_to_relatedImages() {
  local name="${1}"
  local sha="${2}"

  yq w -i "${GENERATED_CSV}" spec.relatedImages[${relatedImagesIndex}].name "${name}"
  yq w -i "${GENERATED_CSV}" spec.relatedImages[${relatedImagesIndex}].image "cp.icr.io/cp/icp4i/es/${name}@sha256:${sha}"
  relatedImagesIndex+=1
}

for ((i=0;i<number_of_images;i++)); do
  echo "${i}"
  image_config=$(echo "${images_config}" | yq r - [$i])
  echo "${image_config}"
  repository=$(echo "${image_config}" | yq r - repository)
  image_name=$(echo "${image_config}" | yq r - name)
  image_tag=$(echo "${image_config}" | yq r - tag)

  # Some images use the key tagPrefix instead of tag
  if [ -z "${image_tag}" ]; then
    image_tag=$(echo "${image_config}" | yq r - tagPrefix)
  fi

  # TODO parse kafka versions file to determine all versions
  if [ "${image_name}" == "strimzi/kafka" ]; then
    image_tag="${image_tag}-kafka-2.4.0"
  fi

  image="${repository}/${image_name}:${image_tag}"
  echo "image : ${image}"

  # Query Artifactory for the SHA of the manifest list
  curl -u "${ARTIFACTORY_USERNAME}:${ARTIFACTORY_PASSWORD}" \
    "https://hyc-qp-stable-docker-local.artifactory.swg-devops.com:443/artifactory/api/storage/hyc-qp-stable-docker-local/${image_name}/${image_tag}/list.manifest.json"
  # Assume images come from Artifactory, query the rest api for the shas to avoid having to pull them
  sha=$(curl -u "${ARTIFACTORY_USERNAME}:${ARTIFACTORY_PASSWORD}" \
    "https://hyc-qp-stable-docker-local.artifactory.swg-devops.com:443/artifactory/api/storage/hyc-qp-stable-docker-local/${image_name}/${image_tag}/list.manifest.json" \
    | jq -r .checksums.sha256)

  if [[ "${image_tag}" =~ "latest" ]]; then
      # Placeholder sha
      sha="4097889236A2AF26C293033FEB964C4CF118C0224E0D063FEC0A89E9D0569EF2"
  fi


  image_id="${image_name//\//-}-${image_tag}"
  if [[ ! "$(yq r "${GENERATED_CSV}" spec.relatedImages[*].name )" =~ ${image_id} ]]; then
      add_to_relatedImages "${image_id}" "${sha}"
  fi

done


# operator-courier verify "olm-catalog/${OPERATOR_NAME}"

# In travis reset the created date so we can perform a git status --porcelain
if [ -n "${TRAVIS}" ]; then
	yq w -i "${GENERATED_CSV}" "metadata.annotations.createdAt" "${LAST_CREATED_DATE}"
fi
