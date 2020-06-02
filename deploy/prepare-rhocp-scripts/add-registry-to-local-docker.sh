#!/usr/bin/env bash
FG_YLW="\033[33m"
FG_CYAN="\033[96m"
REV_ON="\033[7m"
REV_OFF="\033[27m"
RESET="\033[0m"
set -e
if [[ "$(uname -s)" = *"Linux"* ]]; then
    echo -e "${FG_YLW}${REV_ON}[WARNING]${REV_OFF} this step is currently MacOS-specific due to the differences in how Docker runs${RESET}"
    echo -e "Consider looking at the content of ${FG_CYAN}${0%/*}/add-registry-to-docker-on-inf-node.sh${RESET}"
    echo -e "${FG_CYAN}Skipping adding registry to local docker for now${RESET}"
    exit 0
fi
dockerDaemonConfigFile=~/.docker/daemon.json
tempFile=/tmp/daemon.json
set -x
registry=$(oc registry info)
if ! jq '."insecure-registries" | .[]' ${dockerDaemonConfigFile} | grep "${registry}"; then
    jq -c '."insecure-registries"[."insecure-registries" | length] |= . + "'"${registry}"'"' ${dockerDaemonConfigFile} > ${tempFile}
    mv ${tempFile} ${dockerDaemonConfigFile}
    osascript -e 'quit app "Docker"' && open -a Docker
fi
