#!/usr/bin/env bash
FG_RED="\033[91m"
FG_CYAN="\033[96m"
REV_ON="\033[7m"
REV_OFF="\033[27m"
RESET="\033[0m"
username=${1:?"ERROR: usage $0 username password cluster_name"}
password=${2:?"ERROR: usage $0 username password cluster_name"}
kubeadminPassword=$("${0%/*}/get-kubeadmin-password.sh" "${3:?"ERROR: usage $0 username password cluster_name"}")
echo -e "Enabling htpassword access and creating cluster-admin user ${FG_CYAN}${username}:${password}${RESET}"
set -ex
passwordFile=/tmp/pwfile
htpasswd -b -c ${passwordFile} "${username}" "${password}"
oc create secret generic htpass-secret --from-file=htpasswd=${passwordFile} -n openshift-config
rm ${passwordFile}
oc apply -f - << "EOF"
apiVersion: config.openshift.io/v1
kind: OAuth
metadata:
  name: cluster
spec:
  identityProviders:
  - name: my_htpasswd_provider
    mappingMethod: claim
    type: HTPasswd
    htpasswd:
      fileData:
        name: htpass-secret
EOF
for x in $(seq 12); do
    sleep 5
    if oc login -u "${username}" -p "${password}"; then
        [[ $(oc whoami) == "${username}" ]] || exit 1
        oc login -u kubeadmin -p "${kubeadminPassword}"
        oc adm policy add-cluster-role-to-user cluster-admin "${username}"
        exit 0
    fi
done
echo -e "${FG_RED}${REV_ON}[ERROR]${REV_OFF} Could not create user ${username}${RESET}"
exit 1
