#!/usr/bin/env bash

DEFAULT_OS=darwin
if [ -z ${B_OS} || ${B_OS} == "macos" ]; then 
    read -p "os in use (default: darwin): " B_OS
    B_OS=${B_OS:-${DEFAULT_OS}}
fi
os=${B_OS}

download_artifact() {
    org=$1
    repo=$2
    tag=$3
    artifact=$4
    binary_name=$5
    list_asset_url="https://api.github.com/repos/${org}/${repo}/releases/${tag}"
    echo "list asset url: ${list_asset_url}"
    echo "artifact: ${artifact}"
    curl "${list_asset_url}" | jq ".assets[] | select(.name==\"${artifact}\") | .url"
    asset_url=$(curl "${list_asset_url}" | jq ".assets[] | select(.name==\"${artifact}\") | .url" | sed 's/\"//g')
    echo "downloading asset: ${asset_url}"
    echo "curl -LJ -H 'Accept: application/octet-stream' "${asset_url}" -o ${binary_name}"
    curl -LJ -H 'Accept: application/octet-stream' "${asset_url}" -o ${binary_name}
    sudo mkdir -p /tmp/bin
    sudo mv ${binary_name} /tmp/bin/${binary_name}
    sudo chmod +x /tmp/bin/${binary_name}
}

download_artifact "bronze1man" "yaml2json" "latest" "yaml2json_${os}_amd64" "yaml2json"


for yamlfile in olm-catalog/ibm-eventstreams/*/*.yaml
do
    jsonfile=$(sed -e "s|.yaml|.json|g" <<< ${yamlfile})
    echo $jsonfile
    /tmp/bin/yaml2json < ${yamlfile} > ${jsonfile}
    rm -f ${yamlfile}
done