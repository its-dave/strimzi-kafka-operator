#!/usr/bin/env bash
set -ex
oc create secret docker-registry ibm-entitlement-key --docker-server=hyc-qp-stable-docker-local.artifactory.swg-devops.com --docker-username="${ARTIFACTORY_USERNAME:?"required"}" --docker-password="${ARTIFACTORY_PASSWORD:?"required"}" --docker-email="${ARTIFACTORY_USERNAME}" "$@"
