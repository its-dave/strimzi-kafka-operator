#!/bin/bash
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

#
# 1.  OperandRequest
#
#  An OperandRequest is needed to declare which Common Services
#  Event Streams requires. The request will be fulfilled by ODLM.
#  The OperandRequest status indicates if the request is fulfilled.
#

operandRequestName="eventstreams-cluster-operator"

echo "Creating OperandRequest"
cat <<EOF | kubectl apply -n $EVENTSTREAMS_OPERATOR_NAMESPACE -f -
apiVersion: operator.ibm.com/v1alpha1
kind: OperandRequest
metadata:
  name: eventstreams-cluster-operator
  namespace: $EVENTSTREAMS_OPERATOR_NAMESPACE  
  ownerReferences:
  - apiVersion: $OWNER_APIVERSION
    kind: $OWNER_KIND
    name: $OWNER_NAME
    uid: $OWNER_UID
spec:
  requests:
    - operands:
        - name: ibm-management-ingress-operator
        - name: ibm-monitoring-exporters-operator
        - name: ibm-monitoring-prometheusext-operator
        - name: ibm-monitoring-grafana-operator
        - name: ibm-iam-operator
        - name: ibm-commonui-operator
        - name: ibm-platform-api-operator
        - name: ibm-metering-operator
      registry: common-service
      registryNamespace: ibm-common-services
EOF

echo "Verifying OperandRequest definition"
kubectl get operandrequest -n $EVENTSTREAMS_OPERATOR_NAMESPACE eventstreams-cluster-operator -o yaml

echo "---------------------------------------------------------------"

#
# 2.  Wait for OperandRequest
#
#  The OperandRequest status indicates if the request is fulfilled.
#  We only verify the IAM component as it is required for Event Streams
#  to start up correctly.
#

echo "Waiting for IAM OperandRequest to be fulfilled"
operatorPhase="$(kubectl get operandrequest -n ${EVENTSTREAMS_OPERATOR_NAMESPACE} ${operandRequestName} --ignore-not-found -o jsonpath='{.status.members[?(@.name=="ibm-iam-operator")].phase.operatorPhase}')"
echo "OperatorPhase:${operatorPhase}"
while [ "${operatorPhase}" != "Running" ]
do
  echo "Waiting for IAM member to have running operatorPhase"
  sleep 30
  echo "Checking status"
  operatorPhase="$(kubectl get operandrequest -n ${EVENTSTREAMS_OPERATOR_NAMESPACE} ${operandRequestName} --ignore-not-found -o jsonpath='{.status.members[?(@.name=="ibm-iam-operator")].phase.operatorPhase}')"
  echo "OperatorPhase:${operatorPhase}"
done
echo "Found OperatorPhase status ${operatorPhase}. IAM operand request has been fulfilled"

operandPhase="$(kubectl get operandrequest -n ${EVENTSTREAMS_OPERATOR_NAMESPACE} ${operandRequestName} --ignore-not-found -o jsonpath='{.status.members[?(@.name=="ibm-iam-operator")].phase.operandPhase}')"
echo "OperandPhase:${operandPhase}"
while [ "${operandPhase}" != "Running" ]
do
  echo "Waiting for IAM member to have running operandPhase"
  sleep 30
  echo "Checking status"
  operandPhase="$(kubectl get operandrequest -n ${EVENTSTREAMS_OPERATOR_NAMESPACE} ${operandRequestName} --ignore-not-found -o jsonpath='{.status.members[?(@.name=="ibm-iam-operator")].phase.operandPhase}')"
  echo "OperandPhase:${operandPhase}"
done
echo "Found OperandPhase status ${operandPhase}. IAM operand request has been fulfilled"

echo "---------------------------------------------------------------"

#
# 3.  Wait for Common Services dependencies
#
#  The Event Streams operator requires the following resources to
#  be present for any instance to be created successfully:
#   - secret containg cluster ca cert
#   - configmap containing Management Ingress configuration
#

echo "Waiting for Common Services dependencies"

csCertSecretName="management-ingress-ibmcloud-cluster-ca-cert"
csCertSecret="$(kubectl get secret -n ${EVENTSTREAMS_OPERATOR_NAMESPACE} ${csCertSecretName} --ignore-not-found -oname)"
while [ -z ${csCertSecret} ]
do
  echo "Waiting for Common Services cert secret ${csCertSecretName} to be present in namespace ${EVENTSTREAMS_OPERATOR_NAMESPACE}"
  sleep 30
  echo "Checking for secret"
  csCertSecret="$(kubectl get secret -n ${EVENTSTREAMS_OPERATOR_NAMESPACE} ${csCertSecretName} --ignore-not-found -oname)"
done
echo "Found ${csCertSecret}. Common Services cert secret is present"

ingressCMName="management-ingress-ibmcloud-cluster-info"
ingressCM="$(kubectl get configmap -n ${EVENTSTREAMS_OPERATOR_NAMESPACE} ${ingressCMName} --ignore-not-found -oname)"
while [ -z ${ingressCM} ]
do
  echo "Waiting for Common Services configmap ${ingressCMName} to be present in namespace ${EVENTSTREAMS_OPERATOR_NAMESPACE}"
  sleep 30
  echo "Checking for configmap"
  ingressCM="$(kubectl get configmap -n ${EVENTSTREAMS_OPERATOR_NAMESPACE} ${ingressCMName} --ignore-not-found -oname)"
done
echo "Found ${ingressCM} Common Services configmap for Managment Ingress is present"
