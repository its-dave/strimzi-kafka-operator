#!/usr/bin/env bash

REPO_ADDR=$1
REPO_USERNAME=$2
REPO_PASSWORD=$3

set -e

docker logout
if [ "${REPO_ADDR}" == "docker.io/ibmcom/" ]; then
    echo docker login -u "${REPO_USERNAME}" -p "${REPO_PASSWORD}"
    docker login -u "${REPO_USERNAME}" -p "${REPO_PASSWORD}"
else
    echo docker login "${REPO_ADDR}" -u "${REPO_USERNAME}" -p "${REPO_PASSWORD}"
    docker login "${REPO_ADDR}" -u "${REPO_USERNAME}" -p "${REPO_PASSWORD}"
fi

cd ..
opm alpha bundle build --tag "${REPO_ADDR}""${BUNDLE_IMAGE_NAME}"-"${B_ARCH}" --package "${OPERATOR_NAME}" --directory deploy/olm-catalog/"${OPERATOR_NAME}"/"${CSV_VERSION}" --channels "${CHANNELS}" --default "${DEFAULT_CHANNEL}"
cd -

docker push "${REPO_ADDR}""${BUNDLE_IMAGE_NAME}"-"${B_ARCH}"
docker manifest create --amend "${REPO_ADDR}""${BUNDLE_IMAGE_NAME}" "${REPO_ADDR}""${BUNDLE_IMAGE_NAME}"-"${B_ARCH}"
docker manifest push "${REPO_ADDR}""${BUNDLE_IMAGE_NAME}"

opm alpha bundle validate --tag "${REPO_ADDR}""${BUNDLE_IMAGE_NAME}" --image-builder docker

cd ..
opm index add --bundles "${REPO_ADDR}${BUNDLE_IMAGE_NAME}${RELEASED_BUNDLES}" --container-tool docker --tag "${REPO_ADDR}""${CATALOG_IMAGE_NAME}"-"${B_ARCH}"
cd -
docker push "${REPO_ADDR}""${CATALOG_IMAGE_NAME}"-"${B_ARCH}"
docker manifest create --amend "${REPO_ADDR}""${CATALOG_IMAGE_NAME}" "${REPO_ADDR}""${CATALOG_IMAGE_NAME}"-"${B_ARCH}"
docker manifest push "${REPO_ADDR}""${CATALOG_IMAGE_NAME}"