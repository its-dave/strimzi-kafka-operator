#!/bin/sh
#
# Licensed Materials - Property of IBM
#
# 5737-H33
#
# (C) Copyright IBM Corp. 2020  All Rights Reserved.
#
# US Government Users Restricted Rights - Use, duplication or
# disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
#


set -e

# Before running this lint install the EventStreams operator into your cluster,
# then deploy the EventStreams CustomResource file and wait for the instances resources to be ready
# https://github.ibm.com/certauto/cvscan (go project, clone and run make build)
# https://github.ibm.com/IBMPrivateCloud/content-verification (cv, download binary)

if [ -z "${CV_TOOL}" ]; then
  echo "Set CV_TOOL as path to cv tool"
  echo "Example Usages:"
  echo "CV_TOOL=~/Downloads/cv CVSCAN_TOOL=~/Downloads/cvscan [NAMESPACE=myproject] ./lint.sh"
  echo "CV_TOOL=\"\$(which cv)\" CVSCAN_TOOL=\"\$(which cvscan)\" [NAMESPACE=myproject] ./lint.sh"
  exit 1
fi
if [ -z "${CVSCAN_TOOL}" ]; then
  echo "Set CVSCAN_TOOL as path to cvscan tool"
  echo "Example Usages:"
  echo "CV_TOOL=~/Downloads/cv CVSCAN_TOOL=~/Downloads/cvscan [NAMESPACE=myproject] ./lint.sh"
  echo "CV_TOOL=\"\$(which cv)\" CVSCAN_TOOL=\"\$(which cvscan)\" [NAMESPACE=myproject] ./lint.sh"
  exit 1
fi
NAMESPACE="${NAMESPACE:-myproject}"

SCAN_FOLDER="scan"

# Delete scans folder
rm -rf ${SCAN_FOLDER}
${CVSCAN_TOOL} ${SCAN_FOLDER} --namespace "${NAMESPACE}"
${CV_TOOL} lint resources "${SCAN_FOLDER}"  --overrides lintOverrides.yaml