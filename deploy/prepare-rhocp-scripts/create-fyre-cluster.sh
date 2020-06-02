#!/usr/bin/env bash
FG_RED="\033[91m"
FG_GREEN="\033[92m"
FG_CYAN="\033[96m"
REV_ON="\033[7m"
REV_OFF="\033[27m"
RESET="\033[0m"
set -e
clusterName=${1:?"ERROR: usage $0 desired_cluster_name [--add-to-etc-hosts] [--oc-login] [--ssh-copy-id]"}
shift
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --add-to-etc-hosts) addToHosts=true;;
    --oc-login) ocLogin=true;;
    --ssh-copy-id) sshCopy=true;;
    *) echo -e "${FG_RED}${REV_ON}[ERROR]${REV_OFF} invalid option [$1]${RESET}"; exit 1;;
  esac
  shift
done
SECONDS=0
curl -X POST -k -u "${FYRE_USERNAME:?"required"}:${FYRE_API_KEY:?"required"}" 'https://api.fyre.ibm.com/rest/v1/?operation=deployopenshiftcluster' --data '{ "cluster_name" : "'"${clusterName}"'", "site" : "svl", "ocp_version" : "4.3", "master_quantity" : 3, "master_cpu" : 4, "master_memory" : 8, "worker_quantity" : 5, "worker_cpu" : 8, "worker_memory" : 32 }'
echo -e "${FG_CYAN}Waiting for Fyre to build cluster and install RHOCP...${RESET}"
for x in $(seq 90); do
    sleep 60
    status=$(curl -X GET -k -u "${FYRE_USERNAME}:${FYRE_API_KEY}" 'https://api.fyre.ibm.com/rest/v1/?operation=query&request=showclusters' -s | jq '.clusters[] | select(.name=="'"${clusterName}"'") | .status' -r)
    echo -e "Status after $((SECONDS/60)) minutes: ${FG_CYAN}${status}${RESET}"
    if [ -n "${sshCopy}" ] && [[ "${status}" == "configuring" ]]; then
        echo -e "${FG_CYAN}Copying ssh key to ${clusterName}-inf.fyre.ibm.com...${RESET}"
        ssh-copy-id "root@${clusterName}-inf.fyre.ibm.com"
        sshCopy=""
    fi
    if [ -n "${addToHosts}" ] && [[ "${status}" == "configuring" ]]; then
        "${0%/*}/add-fyre-machine-to-hosts-file.sh" "${clusterName}"
        addToHosts=""
    fi
    if [[ "${status}" == "deployed" ]]; then
        echo -e "${FG_GREEN}Fyre cluster built and RHOCP installed at ${FG_CYAN}api.${clusterName}.os.fyre.ibm.com:6443${RESET}"
        if [ -n "${ocLogin}" ]; then
            kubeadminPassword=$("${0%/*}/get-kubeadmin-password.sh" "${clusterName}")
            echo -e "kubeadmin password is ${FG_CYAN}${kubeadminPassword}${RESET}, logging in with oc..."
            oc login "api.${clusterName}.os.fyre.ibm.com:6443" -u kubeadmin -p "${kubeadminPassword}"
        fi
        exit 0
    fi
    [[ "${status}" == "error" ]] && break
done
echo -e "${FG_RED}${REV_ON}[ERROR]${REV_OFF} Failed to build cluster ${clusterName}, consider checking the Fyre UI and trying again${RESET}"
exit 1
