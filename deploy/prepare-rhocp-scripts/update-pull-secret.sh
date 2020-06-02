#!/usr/bin/env bash
set -e
encodeOpts=();
decodeOpts=(-D);
if [[ "$(uname -s)" = *"Linux"* ]]; then
    encodeOpts=(-w 0)
    decodeOpts=(-d)
fi
set -x
auth=$(echo -n "${ARTIFACTORY_USERNAME:?"required"}:${ARTIFACTORY_PASSWORD:?"required"}" | base64 "${encodeOpts[@]}")
json=$(oc get secret -n openshift-config pull-secret -o jsonpath='{.data.\.dockerconfigjson}' | base64 "${decodeOpts[@]}" | jq '.auths |= . + {"hyc-qp-stable-docker-local.artifactory.swg-devops.com":{"auth":"'"${auth}"'","email":"'"${ARTIFACTORY_USERNAME}"'"}}')
oc set data secret/pull-secret -n openshift-config --from-literal=.dockerconfigjson="${json}"
