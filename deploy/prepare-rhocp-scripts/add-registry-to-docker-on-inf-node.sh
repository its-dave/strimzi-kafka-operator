#!/usr/bin/env bash
FG_CYAN="\033[96m"
RESET="\033[0m"
set -e
sshTarget="${1:?"ERROR: usage $0 cluster_name"}-inf.fyre.ibm.com"
dockerDaemonConfigFile=/etc/docker/daemon.json
tempFile=/tmp/daemon.json
registry=$(oc registry info)
echo -e "Adding ${FG_CYAN}${registry}${RESET} to ${FG_CYAN}${sshTarget}:${dockerDaemonConfigFile}${RESET}"
ssh "root@${sshTarget}" << EOF
    set -ex
    curl https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64 -Lo /usr/local/bin/jq
    chmod +x /usr/local/bin/jq
    [ -f ${dockerDaemonConfigFile} ] || echo "{}" > ${dockerDaemonConfigFile}
    if ! jq '."insecure-registries" | .[]' ${dockerDaemonConfigFile} | grep "${registry}"; then
        jq -c '."insecure-registries"[."insecure-registries" | length] |= . + "'"${registry}"'"' ${dockerDaemonConfigFile} > ${tempFile}
        mv ${tempFile} ${dockerDaemonConfigFile}
        service docker restart
    fi

    string=\$(systemctl status docker | awk '/Loaded/ {print \$3}')
    sed -i 's|--authorization-plugin=rhel-push-plugin|-H tcp://'"\$(hostname -i)"':2376|g' "\${string:1:\${#string}-2}"
    systemctl daemon-reload
    systemctl restart docker
EOF
