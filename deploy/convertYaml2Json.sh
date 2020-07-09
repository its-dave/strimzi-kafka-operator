#!/usr/bin/env bash

if [[ ${B_OS} == "macos" ]]; then 
    B_OS=darwin
fi
org="bronze1man"
repo="yaml2json"
tag="latest"
artifact="yaml2json_${B_OS}_amd64"
binary_name="yaml2json"

list_asset_url="https://api.github.com/repos/${org}/${repo}/releases/${tag}"
echo "list asset url: ${list_asset_url}"
echo "artifact: ${artifact}"
curl "${list_asset_url}" | jq ".assets[] | select(.name==\"${artifact}\") | .url"
asset_url=$(curl "${list_asset_url}" | jq ".assets[] | select(.name==\"${artifact}\") | .url" | sed 's/\"//g')
echo "downloading asset: ${asset_url}"
echo "curl -LJ -H 'Accept: application/octet-stream' \"${asset_url}\" -o ./${binary_name}"
curl -LJ -H 'Accept: application/octet-stream' "${asset_url}" -o "./${binary_name}"
chmod +x "./${binary_name}"

for yamlfile in olm-catalog/"${OPERATOR_NAME}"/"${CSV_VERSION}"/*.yaml
do
    ./${binary_name} < "${yamlfile}" > "${yamlfile//.yaml/.json}"
    rm -f "${yamlfile}"
done

rm -f ./${binary_name}
