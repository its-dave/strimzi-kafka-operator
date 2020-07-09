#!/usr/bin/env bash

set -e

prepare_manifests(){
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
  ${CP} "${INSTALL_DIR}/143-Crd-eventstreamsgeoreplicator.yaml" "${EVENTSTREAMS_GEOREP_CRD_FILE}"

  # update the ES api versions
  yq w -i "${EVENTSTREAMS_CRD_FILE}" spec.version v1beta1
  yq w -i "${EVENTSTREAMS_GEOREP_CRD_FILE}" spec.version v1beta1
  # inject an additional attribute that allows additional properties in
  # the Schema Registry storage definition
  yq w -i "${EVENTSTREAMS_CRD_FILE}" spec.validation.openAPIV3Schema.properties.spec.properties.schemaRegistry.properties.storage['x-kubernetes-preserve-unknown-fields'] "true"

  # copy the examples so they automatically get packed into alm-examples in the CSV to be built
  ${CP} ../examples/eventstreams/*.yaml "${CRD_DIR}"

  LAST_CREATED_DATE="$(yq r "${GENERATED_CSV}" metadata.annotations.createdAt)"
  ${CP} "${INSTALL_DIR}/020-ClusterRole-strimzi-cluster-operator-role.yaml" "${ROLE_FILE}"
  ${CP} "${INSTALL_DIR}/020-RoleBinding-strimzi-cluster-operator.yaml" "${ROLE_BINDING_FILE}"
  ${CP} "${INSTALL_DIR}/010-ServiceAccount-strimzi-cluster-operator.yaml" "${SERVICE_ACCOUNT_FILE}"
  ${CP} "${INSTALL_DIR}/150-Deployment-eventstreams-cluster-operator.yaml" "${OPERATOR_FILE}"
  # Inject the expected service account name (the name of the operator role) into the operator deployment
  yq r "${ROLE_FILE}" metadata.name | xargs yq w -i "${OPERATOR_FILE}" spec.template.spec.serviceAccountName
  # Inject the entity operator delegation ClusterRole rules into the Operator Role
  yq m -i -a "${ROLE_FILE}" "${INSTALL_DIR}/031-ClusterRole-strimzi-entity-operator.yaml"

  ${SED} -i "/WATCHED_NAMESPACE/,/EVENTSTREAMS_OPERATOR_NAMESPACE/ s/metadata.namespace/metadata.annotations['olm.targetNamespaces']/" "${OPERATOR_FILE}"

  ${CP} "${INSTALL_DIR}/031-ClusterRole-strimzi-entity-operator.yaml" "${BUNDLE_DIR}/entityoperator.clusterrole.yaml"
  ${CP} "${INSTALL_DIR}/122-ClusterRole-eventstreams-ui.yaml" "${BUNDLE_DIR}/adminui.clusterrole.yaml"
  ${CP} "${INSTALL_DIR}/123-ClusterRole-eventstreams-admin.yaml" "${BUNDLE_DIR}/adminapi.clusterrole.yaml"
}

generate_csv(){
  echo "Generating csv"
  cd ..
  operator-sdk generate csv --csv-version "${CSV_VERSION}" --operator-name "${OPERATOR_NAME}" --update-crds --make-manifests=false
  cd -
  # cleanup build directory used by operator-sdk
  rm -rf "${BUILD_DIR}"

  # merge generated csv with manual fields to input es specific metadata
  yq d -i "${GENERATED_CSV}" spec.customresourcedefinitions.owned.*
  ${SED} -i "s/owned: \[\]/owned:/" "${GENERATED_CSV}"
  yq m -ix "${GENERATED_CSV}" csv_manual_fields.yaml

  #add version metadata
  yq w -i "${GENERATED_CSV}" metadata.version "${CSV_VERSION}"

  #add skip range annotation
  yq w -i "${GENERATED_CSV}" metadata.annotations["olm.skipRange"] "${OLM_SKIPRANGE}"

  # update the created timestamp for this build
  yq w -i "${GENERATED_CSV}" metadata.annotations.createdAt "${DATE}"

  # sort the attributes in alm-examples to match the samples we provide
  yq r "${GENERATED_CSV}" metadata.annotations['alm-examples'] | jq 'map(.)' | jq -c -f reordering.jq > .ordered-examples
  yq w -i "${GENERATED_CSV}" metadata.annotations['alm-examples'] --tag '!!str' "$(cat .ordered-examples)"
  rm .ordered-examples

  if [ -n "${TRAVIS}" ]; then
	  yq w -i "${GENERATED_CSV}" "metadata.annotations.createdAt" "${LAST_CREATED_DATE}"
  fi
}

add_related_images(){
  # add in all unique images from values.yaml to the related images
  images_config="$(yq r ../eventstreams-helm-charts/ibm-eventstreams-operator/values.yaml --collect *.image)"
  printf "Found images:\n${images_config}\n"

  number_of_images="$(yq r ../eventstreams-helm-charts/ibm-eventstreams-operator/values.yaml --collect *.image --length)"


  for ((i=0;i<number_of_images;i++)); do
    image_config=$(echo "${images_config}" | yq r - [$i])
    repository=$(echo "${image_config}" | yq r - repository)
    image_name=$(echo "${image_config}" | yq r - name)
    digest=$(echo "${image_config}" | yq r - digest)

    image="${repository}/${image_name}@sha256:${digest}"

    if [[ ! "$(yq r "${GENERATED_CSV}" spec.relatedImages[*].name )" =~ ${image_name} ]] && [[ ! ${image_name} =~ "ibm-eventstreams-catalog" ]] && [[ ! ${image_name} =~ "ibm-eventstreams-operator-bundle" ]]; then
      update="
      - command: update
        path: spec.relatedImages[+]
        value:
          name: ${image_name}
          image: ${image}
      "
      yq w -i -s <(cat <<< "$update") "${GENERATED_CSV}"
    fi
  done

}

prepare_manifests

generate_csv

add_related_images
