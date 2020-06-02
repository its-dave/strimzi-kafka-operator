#!/usr/bin/env bash
FG_RED="\033[91m"
FG_GREEN="\033[92m"
FG_CYAN="\033[96m"
REV_ON="\033[7m"
REV_OFF="\033[27m"
RESET="\033[0m"
set -e
internalRegistry=$(oc registry info)
set -x
oc patch configs.imageregistry.operator.openshift.io/cluster --patch '{"spec":{"defaultRoute":true}}' --type=merge
set +x
SECONDS=0
echo -e "${FG_CYAN}Waiting for registry to update...${RESET}"
for x in $(seq 5); do
    sleep 60
    registry=$(oc registry info)
    echo -e "Registry is ${FG_CYAN}${registry}${RESET} after $((SECONDS/60)) minutes..."
    if [[ "${registry}" != "${internalRegistry}" ]]; then
        [[ $(oc registry login --skip-check 2>&1) != *"internal"* ]] || continue
        echo -e "${FG_GREEN}Docker registry exposed${RESET}"
        exit 0
    fi
done
echo -e "${FG_RED}${REV_ON}[ERROR]${REV_OFF} Could not expose Docker registry${RESET}"
exit 1
