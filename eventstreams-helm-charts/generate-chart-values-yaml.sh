#!/bin/bash

# This script is used to take the values.yaml in the eventstreams-helm-charts dir (containing values refering to artifactory image locations)
# copy it to the charts dir and replace the image references with the locations of the released images and with their artifactory digests.

cp "${VALUES_YAML_FILE}" "${CHART_VALUES_YAML_FILE}"

change_image_values(){
    name=$(yq r "${VALUES_YAML_FILE}" "${1}".image.name)
    tag=$(yq r "${VALUES_YAML_FILE}" "${1}".image.tag)


    if [ -z "${tag}" ]; then
        tag=$(yq r "${VALUES_YAML_FILE}" "${1}".image.tagPrefix)
    fi

    if [ "${name}" == "strimzi/kafka" ] && [ "${tag}" == "latest" ]; then
        tag="${tag}-kafka-2.5.0"
    fi

    digest=$(curl -s -u "${ARTIFACTORY_USERNAME}:${ARTIFACTORY_PASSWORD}" \
        "https://hyc-qp-stable-docker-local.artifactory.swg-devops.com:443/artifactory/api/storage/hyc-qp-stable-docker-local/${name}/${tag}/list.manifest.json" \
        | jq -r .checksums.sha256)
    yq w -i "${CHART_VALUES_YAML_FILE}" "${1}".image.repository "${2}"
    yq w -i "${CHART_VALUES_YAML_FILE}" "${1}".image.name "${3}"
    yq w -i "${CHART_VALUES_YAML_FILE}" "${1}".image.digest "${digest}"
}

change_image_values operatorIndex dockerhub.io ibmcom/ibm-eventstreams-catalog
change_image_values operatorBundle dockerhub.io ibmcom/ibm-eventstreams-operator-bundle
change_image_values operator dockerhub.io ibmcom/ibm-eventstreams-operator
change_image_values operatorInit dockerhub.io ibmcom/ibm-eventstreams-operator-init
change_image_values topicOperator dockerhub.io ibmcom/ibm-eventstreams-operator
change_image_values userOperator dockerhub.io ibmcom/ibm-eventstreams-operator
change_image_values kafkaInit dockerhub.io ibmcom/ibm-eventstreams-operator

change_image_values restProducer cp.icr.io cp/ibm-eventstreams-rest-producer
change_image_values adminApi cp.icr.io cp/ibm-eventstreams-admin
change_image_values schemaRegistry cp.icr.io cp/ibm-eventstreams-schema-registry
change_image_values schemaRegistryAvro cp.icr.io cp/ibm-eventstreams-schema-registry-avro
change_image_values schemaRegistryProxy cp.icr.io cp/ibm-eventstreams-schema-proxy
change_image_values adminUi cp.icr.io cp/ibm-eventstreams-admin-ui
change_image_values adminUiDatabase cp.icr.io cp/ibm-eventstreams-session-store
change_image_values collector cp.icr.io cp/ibm-eventstreams-metrics-collector
change_image_values zookeeper cp.icr.io cp/ibm-eventstreams-kafka
change_image_values kafka cp.icr.io cp/ibm-eventstreams-kafka
change_image_values kafkaConnect cp.icr.io cp/ibm-eventstreams-kafka
change_image_values kafkaConnects2i cp.icr.io cp/ibm-eventstreams-kafka
change_image_values cruiseControl cp.icr.io cp/ibm-eventstreams-kafka
change_image_values tlsSidecarZookeeper cp.icr.io cp/ibm-eventstreams-kafka
change_image_values tlsSidecarKafka cp.icr.io cp/ibm-eventstreams-kafka
change_image_values tlsSidecarEntityOperator cp.icr.io cp/ibm-eventstreams-kafka
change_image_values tlsSidecarCruiseControl cp.icr.io cp/ibm-eventstreams-kafka
change_image_values kafkaMirrorMaker cp.icr.io cp/ibm-eventstreams-kafka
change_image_values kafkaExporter cp.icr.io cp/ibm-eventstreams-kafka
change_image_values kafkaMirrorMaker2 cp.icr.io cp/ibm-eventstreams-kafka
change_image_values jmxTrans cp.icr.io cp/ibm-eventstreams-jmxtrans