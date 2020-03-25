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
# 3. ValidatingWebhookConfiguration
# 4. ConsoleYAMLSample
#
# All of these manually-created resources have the owner reference set to
# point to the Event Streams Cluster Operator, so that they will be automatically
# deleted if the operator deployment is deleted.
#

echo "---------------------------------------------------------------"

echo "Creating Kubernetes resources for"
echo "   Event Streams Operator  : $EVENTSTREAMS_UID"
echo "   running in              : $EVENTSTREAMS_OPERATOR_NAMESPACE"

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
# 3.  Validating Webhook
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
  - name: eventstreams.ibm.com.rejectlicensenotaccepted
    rules:
      - apiGroups: ["eventstreams.ibm.com"]
        apiVersions: ["v1beta1"]
        operations: ["CREATE", "UPDATE"]
        resources: ["eventstreams"]
    failurePolicy: Ignore
    clientConfig:
      service:
        namespace: $EVENTSTREAMS_OPERATOR_NAMESPACE
        name: eventstreams-cluster-operator
        path: /admissionwebhook/rejectlicensenotaccepted
      caBundle: "$cabundle"
  - name: eventstreams.ibm.com.rejectlongnames
    rules:
      - apiGroups: ["eventstreams.ibm.com"]
        apiVersions: ["v1beta1"]
        operations: ["CREATE"]
        resources: ["eventstreams"]
    failurePolicy: Ignore
    clientConfig:
      service:
        namespace: $EVENTSTREAMS_OPERATOR_NAMESPACE
        name: eventstreams-cluster-operator
        path: /admissionwebhook/rejectlongnames
      caBundle: "$cabundle"
  - name: eventstreams.ibm.com.rejectinvalidversions
    rules:
      - apiGroups: ["eventstreams.ibm.com"]
        apiVersions: ["v1beta1"]
        operations: ["CREATE", "UPDATE"]
        resources: ["eventstreams"]
    failurePolicy: Ignore
    clientConfig:
      service:
        namespace: $EVENTSTREAMS_OPERATOR_NAMESPACE
        name: eventstreams-cluster-operator
        path: /admissionwebhook/rejectinvalidversions
      caBundle: "$cabundle"
  - name: eventstreams.ibm.com.rejectmissingtopiclabels
    rules:
      - apiGroups: ["eventstreams.ibm.com"]
        apiVersions: ["v1beta1"]
        operations: ["CREATE", "UPDATE"]
        resources: ["kafkatopics"]
    failurePolicy: Ignore
    clientConfig:
      service:
        namespace: $EVENTSTREAMS_OPERATOR_NAMESPACE
        name: eventstreams-cluster-operator
        path: /admissionwebhook/rejectmissingtopiclabels
      caBundle: "$cabundle"
  - name: eventstreams.ibm.com.rejectmissinguserlabels
    rules:
      - apiGroups: ["eventstreams.ibm.com"]
        apiVersions: ["v1beta1"]
        operations: ["CREATE", "UPDATE"]
        resources: ["kafkausers"]
    failurePolicy: Ignore
    clientConfig:
      service:
        namespace: $EVENTSTREAMS_OPERATOR_NAMESPACE
        name: eventstreams-cluster-operator
        path: /admissionwebhook/rejectmissinguserlabels
      caBundle: "$cabundle"
EOF

echo "Webhook config:"
kubectl get ValidatingWebhookConfiguration validate-eventstreams-$EVENTSTREAMS_OPERATOR_NAMESPACE -o yaml



echo "---------------------------------------------------------------"

#
# 4.  Console YAML samples
#
#  Provide examples in the OpenShift UI for creating Event Streams instances.
#
#  Support for this was introduced in OpenShift 4.3, so to ignore errors if
#   running on OpenShift versions prior to this, all commands in this section
#   are prefixed with !
#

echo "Updating console YAML samples"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: eventstreams-quickstart-$EVENTSTREAMS_OPERATOR_NAMESPACE
  ownerReferences:
  - apiVersion: apps/v1
    kind: Deployment
    name: eventstreams-cluster-operator
    uid: $EVENTSTREAMS_UID
spec:
  description: Small cluster for development use
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
  title: quickstart
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
    metadata:
        name: quickstart
        namespace: placeholder
    spec:
        licenseAccept: false
        version: 2020.1.1
        adminApi: {}
        adminUI: {}
        collector: {}
        restProducer: {}
        replicator: {}
        schemaRegistry:
            storage:
                type: ephemeral
        strimziOverrides:
            kafka:
                replicas: 1
                config:
                    interceptor.class.names: com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor
                    offsets.topic.replication.factor: 1
                    transaction.state.log.min.isr: 1
                    transaction.state.log.replication.factor: 1
                listeners:
                    external:
                        type: route
                    plain: {}
                    tls: {}
                storage:
                    type: ephemeral
                metrics: {}
            zookeeper:
                replicas: 1
                storage:
                    type: ephemeral
                metrics: {}
---
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: eventstreams-sample-3-$EVENTSTREAMS_OPERATOR_NAMESPACE
  ownerReferences:
  - apiVersion: apps/v1
    kind: Deployment
    name: eventstreams-cluster-operator
    uid: $EVENTSTREAMS_UID
spec:
  description: Secure production cluster with three brokers
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
  title: 3 brokers
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
    metadata:
        name: sample-three
        namespace: placeholder
    spec:
        licenseAccept: false
        version: 2020.1.1
        adminApi: {}
        adminUI: {}
        collector: {}
        restProducer: {}
        replicator: {}
        schemaRegistry:
            storage:
                type: ephemeral
        security:
            encryption: TLS
        strimziOverrides:
            kafka:
                replicas: 3
                config:
                    interceptor.class.names: com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor
                    num.replica.fetchers: 3
                    num.io.threads: 24
                    num.network.threads: 9
                    log.cleaner.threads: 6
                listeners:
                    external:
                        type: route
                        authentication:
                            type: scram-sha-512
                    tls:
                        authentication:
                            type: tls
                authorization:
                    type: runas
                storage:
                    type: ephemeral
                metrics: {}
                resources:
                    requests:
                        memory: 8096Mi
                        cpu: 4000m
                    limits:
                        memory: 8096Mi
                        cpu: 4000m
            zookeeper:
                replicas: 3
                storage:
                    type: ephemeral
                metrics: {}
---
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: eventstreams-sample-6-$EVENTSTREAMS_OPERATOR_NAMESPACE
  ownerReferences:
  - apiVersion: apps/v1
    kind: Deployment
    name: eventstreams-cluster-operator
    uid: $EVENTSTREAMS_UID
spec:
  description: Secure production cluster with six brokers
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
  title: 6 brokers
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
    metadata:
        name: sample-six
        namespace: placeholder
    spec:
        licenseAccept: false
        version: 2020.1.1
        adminApi: {}
        adminUI: {}
        collector: {}
        restProducer: {}
        replicator: {}
        schemaRegistry:
            storage:
                type: ephemeral
        security:
            encryption: TLS
        strimziOverrides:
            kafka:
                replicas: 6
                config:
                    interceptor.class.names: com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor
                    num.replica.fetchers: 6
                    num.io.threads: 24
                    num.network.threads: 9
                    log.cleaner.threads: 6
                listeners:
                    external:
                        type: route
                        authentication:
                            type: scram-sha-512
                    tls:
                        authentication:
                            type: tls
                authorization:
                    type: runas
                storage:
                    type: ephemeral
                metrics: {}
                resources:
                    requests:
                        memory: 8096Mi
                        cpu: 4000m
                    limits:
                        memory: 8096Mi
                        cpu: 4000m
            zookeeper:
                replicas: 3
                storage:
                    type: ephemeral
                metrics: {}
---
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: eventstreams-sample-9-$EVENTSTREAMS_OPERATOR_NAMESPACE
  ownerReferences:
  - apiVersion: apps/v1
    kind: Deployment
    name: eventstreams-cluster-operator
    uid: $EVENTSTREAMS_UID
spec:
  description: Secure production cluster with nine brokers
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
  title: 9 brokers
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
    metadata:
        name: sample-nine
        namespace: placeholder
    spec:
        licenseAccept: false
        version: 2020.1.1
        adminApi: {}
        adminUI: {}
        collector: {}
        restProducer: {}
        replicator: {}
        schemaRegistry:
            storage:
                type: ephemeral
        security:
            encryption: TLS
        strimziOverrides:
            kafka:
                replicas: 9
                config:
                    interceptor.class.names: com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor
                    num.replica.fetchers: 9
                    num.io.threads: 24
                    num.network.threads: 9
                    log.cleaner.threads: 6
                listeners:
                    external:
                        type: route
                        authentication:
                            type: scram-sha-512
                    tls:
                        authentication:
                            type: tls
                authorization:
                    type: runas
                storage:
                    type: ephemeral
                metrics: {}
                resources:
                    requests:
                        memory: 8096Mi
                        cpu: 4000m
                    limits:
                        memory: 8096Mi
                        cpu: 4000m
            zookeeper:
                replicas: 3
                storage:
                    type: ephemeral
                metrics: {}
EOF

echo "Console YAML samples:"
! kubectl get ConsoleYAMLSample eventstreams-quickstart-$EVENTSTREAMS_OPERATOR_NAMESPACE -o yaml
! kubectl get ConsoleYAMLSample eventstreams-sample-3-$EVENTSTREAMS_OPERATOR_NAMESPACE -o yaml
! kubectl get ConsoleYAMLSample eventstreams-sample-6-$EVENTSTREAMS_OPERATOR_NAMESPACE -o yaml
! kubectl get ConsoleYAMLSample eventstreams-sample-9-$EVENTSTREAMS_OPERATOR_NAMESPACE -o yaml


echo "---------------------------------------------------------------"

echo "Setup complete."
