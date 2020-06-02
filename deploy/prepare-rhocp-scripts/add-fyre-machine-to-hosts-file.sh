#!/usr/bin/env bash
FG_CYAN="\033[96m"
RESET="\033[0m"
set -e
clusterName=${1:?"ERROR: usage $0 cluster_name"}
ip=$(curl -s -X GET -k -u "${FYRE_USERNAME:?"required"}:${FYRE_API_KEY:?"required"}" 'https://api.fyre.ibm.com/rest/v1/?operation=query&request=showclusterdetails&cluster_name='"${clusterName}" | jq '."'"${clusterName}"'"[] | select(.node=="'"${clusterName}"'-inf") | .publicip' -r)
domain="${clusterName}.os.fyre.ibm.com"
echo -e "Adding ${FG_CYAN}${ip} ${domain}${RESET} to /etc/hosts, root access required..."
echo "${ip}  ${domain} cp-console.apps.${domain} api.${domain} oauth-openshift.apps.${domain}" | sudo tee -a /etc/hosts
