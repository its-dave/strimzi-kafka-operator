#!/usr/bin/env bash
set -e
sshTarget="${1:?"ERROR: usage $0 cluster_name"}-inf.fyre.ibm.com"
ssh "root@${sshTarget}" cat /root/auth/kubeadmin-password
