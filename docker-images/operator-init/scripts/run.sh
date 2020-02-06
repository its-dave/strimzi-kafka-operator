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
# Some resources required by the Event Streams Cluster Operator cannot be created
# by OLM, so we create/patch these manually in an init container.
#
# 1. Service
# 2. ConfigMap
# 3. Namespace labels
# 4. ValidatingWebhookConfiguration
#
# All of these manually-created resources have the owner reference set to
# point to the Event Streams Cluster Operator, so that they will be automatically
# deleted if the operator deployment is deleted.
#

echo "---------------------------------------------------------------"

echo "Creating Kubernetes resources for"
echo "   Event Streams Operator  : $EVENTSTREAMS_UID"
echo "   running in              : $EVENTSTREAMS_OPERATOR_NAMESPACE"
echo "   responsible for watching: $EVENTSTREAMS_NAMESPACE"

echo "---------------------------------------------------------------"

#
# 1.  Service
#
#  A Service is needed to provide a predictable, consistent URL for
#  the webhook API that is implemented in the operator container.
#

echo "Creating service"
cat <<EOF | kubectl apply -n $EVENTSTREAMS_OPERATOR_NAMESPACE -f -
apiVersion: v1
kind: Service
metadata:
  name: eventstreams-cluster-operator
  annotations:
    service.beta.openshift.io/serving-cert-secret-name: eventstreams-cluster-operator
  ownerReferences:
  - apiVersion: apps/v1
    kind: Deployment
    name: eventstreams-cluster-operator
    uid: $EVENTSTREAMS_UID
spec:
  selector:
    app: eventstreams
  ports:
    - protocol: TCP
      port: 443
      targetPort: 8081
EOF

echo "Verifying service definition"
kubectl get service -n $EVENTSTREAMS_OPERATOR_NAMESPACE eventstreams-cluster-operator -o yaml

echo "Service certificate"
kubectl get secret -n $EVENTSTREAMS_OPERATOR_NAMESPACE eventstreams-cluster-operator --ignore-not-found -o yaml



echo "---------------------------------------------------------------"

#
# 2.  ConfigMap
#
#  A ConfigMap is needed is store the CA for the certificate that
#  allows the webhook API to be HTTPS. The CA is a required field in
#  the webhook definition to be created.
#

echo "Creating config map"
cat <<EOF | kubectl apply -n $EVENTSTREAMS_OPERATOR_NAMESPACE -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: eventstreams-cluster-operator
  annotations:
    service.beta.openshift.io/inject-cabundle: "true"
  ownerReferences:
  - apiVersion: apps/v1
    kind: Deployment
    name: eventstreams-cluster-operator
    uid: $EVENTSTREAMS_UID
EOF

echo "Verifying config map definition"
kubectl get configmap -n $EVENTSTREAMS_OPERATOR_NAMESPACE eventstreams-cluster-operator -o yaml


echo "Retrieving CA from configmap"
cabundle=""
while [ -z "$cabundle" ]
do
  echo "Waiting for OpenShift to update configmap"
  sleep 1
  echo "Querying config map"
  cabundle=`kubectl get configmap -n $EVENTSTREAMS_OPERATOR_NAMESPACE eventstreams-cluster-operator --ignore-not-found -o jsonpath="{.data['service-ca\.crt']}"`
done

echo "CA for webhook"
kubectl get configmap -n $EVENTSTREAMS_OPERATOR_NAMESPACE eventstreams-cluster-operator -o jsonpath="{.data['service-ca\.crt']}"
echo "Base64-encoding CA"
kubectl get configmap -n $EVENTSTREAMS_OPERATOR_NAMESPACE eventstreams-cluster-operator -o jsonpath="{.data['service-ca\.crt']}" > /tmp/service-ca.crt
cabundle=`base64 -w0 /tmp/service-ca.crt`
echo $cabundle



echo "---------------------------------------------------------------"

#
# 3.  Namespace
#
#  A label is required in each of the namespaces that the operator is
#  responsible for watching.
#
#  This is needed to allow the webhook to specify which namespaces to
#  be used in.
#

if [[ -z $EVENTSTREAMS_NAMESPACE ]]
then
  echo "No list of namespaces provided, so getting list of all namespaces"
  list_of_watched_namespaces=()
  while IFS= read -r line; do
    list_of_watched_namespaces+=( "${line:10}" )
  done < <( kubectl get ns -o name )
else
  echo "Splitting list of comma-separated list of namespaces provided"
  IFS=',' read -ra list_of_watched_namespaces <<< "$EVENTSTREAMS_NAMESPACE"
fi

for ns in "${list_of_watched_namespaces[@]}"
do
  if [[ $ns == kube* ]] || [[ $ns == openshift* ]] || [[ $ns == icp-system ]]
  then
  	echo "Skipping namespace $ns as a system namespace"
  else
    echo "Updating namespace $ns to restrict webhook operations"
    kubectl label namespace $ns --overwrite \
      eventstreams-enable-webhook-$EVENTSTREAMS_OPERATOR_NAMESPACE=$EVENTSTREAMS_OPERATOR_NAMESPACE

    echo "Namespace definition"
    kubectl get namespace $ns -o yaml
  fi
done



echo "---------------------------------------------------------------"

#
# 4.  Validating Webhook
#
#  Defines a webhook rule for each endpoint implemented in the Operator.
#

echo "Updating webhook config"
cat <<EOF | kubectl apply -f -
apiVersion: admissionregistration.k8s.io/v1beta1
kind: ValidatingWebhookConfiguration
metadata:
  name: validate-eventstreams-$EVENTSTREAMS_OPERATOR_NAMESPACE
  ownerReferences:
  - apiVersion: apps/v1
    kind: Deployment
    name: eventstreams-cluster-operator
    uid: $EVENTSTREAMS_UID
webhooks:
  - name: eventstreams.ibm.com.rejectlongnames
    rules:
      - apiGroups: ["eventstreams.ibm.com"]
        apiVersions: ["v1beta1"]
        operations: ["CREATE"]
        resources: ["eventstreams"]
        scope: "Namespaced"
    failurePolicy: Ignore
    clientConfig:
      service:
        namespace: $EVENTSTREAMS_OPERATOR_NAMESPACE
        name: eventstreams-cluster-operator
        path: /admissionwebhook/rejectlongnames
      caBundle: "$cabundle"
    namespaceSelector:
      matchLabels:
        eventstreams-enable-webhook-$EVENTSTREAMS_OPERATOR_NAMESPACE: $EVENTSTREAMS_OPERATOR_NAMESPACE
  - name: eventstreams.ibm.com.rejectinvalidversions
    rules:
      - apiGroups: ["eventstreams.ibm.com"]
        apiVersions: ["v1beta1"]
        operations: ["CREATE", "UPDATE"]
        resources: ["eventstreams"]
        scope: "Namespaced"
    failurePolicy: Ignore
    clientConfig:
      service:
        namespace: $EVENTSTREAMS_OPERATOR_NAMESPACE
        name: eventstreams-cluster-operator
        path: /admissionwebhook/rejectinvalidversions
      caBundle: "$cabundle"
    namespaceSelector:
      matchLabels:
        eventstreams-enable-webhook-$EVENTSTREAMS_OPERATOR_NAMESPACE: $EVENTSTREAMS_OPERATOR_NAMESPACE
EOF

echo "Webhook config:"
kubectl get ValidatingWebhookConfiguration validate-eventstreams-$EVENTSTREAMS_OPERATOR_NAMESPACE -o yaml



echo "---------------------------------------------------------------"

echo "Setup complete."
