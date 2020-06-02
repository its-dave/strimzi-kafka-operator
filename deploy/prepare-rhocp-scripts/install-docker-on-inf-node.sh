#!/usr/bin/env bash
FG_CYAN="\033[96m"
RESET="\033[0m"
set -e
sshTarget="${1:?"ERROR: usage $0 cluster_name"}-inf.fyre.ibm.com"
echo -e "Installing Docker on ${FG_CYAN}${sshTarget}${RESET}"
ssh "root@${sshTarget}" << "EOF"
    set -e
    yum -y check-update || true
    yum -y install docker-1.13.1-109.gitcccb291.el7_7.x86_64
    installedPackages=$(rpm -qa)
    echo ${installedPackages} | grep docker-common >/dev/null
    echo ${installedPackages} | grep docker-rhel-push-plugin >/dev/null
    echo ${installedPackages} | grep docker-client >/dev/null
    service docker start
EOF
