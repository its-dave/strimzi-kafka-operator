#!/usr/bin/env bash
FG_RED="\033[91m"
FG_GREEN="\033[92m"
FG_CYAN="\033[96m"
REV_ON="\033[7m"
REV_OFF="\033[27m"
RESET="\033[0m"
set -ex
oc apply -f "${0%/*}/../artifactory-mirror.yaml"
set +x
SECONDS=0
echo -e "${FG_CYAN}Waiting for nodes to roll...${RESET}"
for x in $(seq 60); do
    sleep 60
    status=""
    oc get nodes | grep "NotReady\|SchedulingDisabled" || status="ready"
    if [ -n "${status}" ] && [ -n "${lastStatus}" ]; then
        echo -e "${FG_GREEN}All nodes in Ready state${RESET}"
        exit 0
    fi
    lastStatus=${status}
    echo -e "Continuing to wait after $((SECONDS/60)) minutes..."
done
echo -e "${FG_RED}${REV_ON}[ERROR]${REV_OFF} Nodes do not seem to have rolled successfully${RESET}"
exit 1
