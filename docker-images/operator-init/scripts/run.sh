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

# Display the version of the init container this script
# is running in, if it's available, but don't fail if not
cat /image.txt || true

#
# Some resources required by the Event Streams Cluster Operator cannot be created
# by OLM, so we create/patch these manually in an init container.
#
# Resources required for Common Services integration:
# 1. OperandRequest
# The init container will wait for the IAM component in Common Services to
# be up and running before continuing.
# The OperandRequest does not have an owner reference and will get left behind after
# the operator is removed incase there are still operands running.
#
# Resources required for the Event Streams operator directly:
# 1. Service
# 2. ConfigMap
# 3. ValidatingWebhookConfiguration
# 4. ConsoleYAMLSample
#
# All of these manually-created resources have the owner reference set to
# point to the cluster role created for the Event Streams Cluster Operator,
# so that they will be automatically deleted if the operator deployment is
# deleted.
#

echo "---------------------------------------------------------------"

echo "Creating Kubernetes resources for"
echo "   Event Streams Operator  : $EVENTSTREAMS_UID"
echo "   running in              : $EVENTSTREAMS_OPERATOR_NAMESPACE"

echo "---------------------------------------------------------------"

#
# 0.   Identifying suitable owner reference
#
#  The ideal owner for resources created for specific instances of
#  Event Streams operators is the clusterrole created for the ES
#  operator deployment.
#
#  The ideal owner for cluster-wide resources shared by all instances
#  of Event Streams operators is the Event Streams CRD.
#

echo "Identifying owner references to use"

# default operator-instance-specific owner if we don't find a cluster role
OWNER_APIVERSION="apps/v1"
OWNER_KIND="Deployment"
OWNER_NAME="eventstreams-cluster-operator"
OWNER_UID=$EVENTSTREAMS_UID

echo "Getting list of cluster role bindings"
clusterrolebindingnames=$(kubectl get clusterrolebinding -o name | grep eventstreams)
echo "Checking subjects for each CRB"
for crbname in $clusterrolebindingnames;
    do
        echo "Checking $crbname"
        crbattrs=$(kubectl get "$crbname" -o jsonpath='{.subjects[0].kind}{" "}{.subjects[0].name}{" "}{.subjects[0].namespace}{" "}{.roleRef.kind}{" "}{.roleRef.name}')
        echo "Attributes: $crbattrs"

        subjectkind=$(cut -d' ' -f1 <<< "$crbattrs")
        subjectname=$(cut -d' ' -f2 <<< "$crbattrs")
        subjectns=$(cut -d' ' -f3 <<< "$crbattrs")
        rolerefkind=$(cut -d' ' -f4 <<< "$crbattrs")
        rolerefname=$(cut -d' ' -f5 <<< "$crbattrs")

        if [ "$subjectkind" == "ServiceAccount" ] && \
            [ "$subjectname" == "eventstreams-cluster-operator-namespaced" ] && \
            [ "$subjectns" == "$EVENTSTREAMS_OPERATOR_NAMESPACE" ] && \
            [ "$rolerefkind" == "ClusterRole" ] ; then

            echo "Found a cluster role to use as ownerref"

            OWNER_APIVERSION="rbac.authorization.k8s.io/v1"
            OWNER_KIND="ClusterRole"
            OWNER_NAME=$rolerefname
            OWNER_UID=$(kubectl get clusterrole "$rolerefname" -o jsonpath='{.metadata.uid}')

            break
        fi
    done

echo "Resource to use as owner for operator supporting resources"
echo "  apiVersion: $OWNER_APIVERSION"
echo "  kind:       $OWNER_KIND"
echo "  name:       $OWNER_NAME"
echo "  uid:        $OWNER_UID"


echo "Getting Event Streams CRD info"
CRD_APIVERSION="apiextensions.k8s.io/v1beta1"
CRD_KIND="CustomResourceDefinition"
CRD_NAME="eventstreams.eventstreams.ibm.com"
CRD_UID=$(kubectl get crd -o jsonpath='{.metadata.uid}' $CRD_NAME)

echo "Resource to use as owner for cluster-wide resources"
echo "  apiVersion: $CRD_APIVERSION"
echo "  kind:       $CRD_KIND"
echo "  name:       $CRD_NAME"
echo "  uid:        $CRD_UID"


echo "---------------------------------------------------------------"

#
# Create OperandRequest
#

source ./createAndWaitForCommonServices.sh

echo "---------------------------------------------------------------"

#
# 1.  Service
#
#  A Service is needed to provide a predictable, consistent URL for
#  the webhook API that is implemented in the operator container.
#

echo "Creating service"
cat <<EOF | kubectl apply -n "$EVENTSTREAMS_OPERATOR_NAMESPACE" -f -
apiVersion: v1
kind: Service
metadata:
  name: eventstreams-cluster-operator
  annotations:
    service.beta.openshift.io/serving-cert-secret-name: eventstreams-cluster-operator
  ownerReferences:
  - apiVersion: $OWNER_APIVERSION
    kind: $OWNER_KIND
    name: $OWNER_NAME
    uid: $OWNER_UID
spec:
  selector:
    app: eventstreams
  ports:
    - protocol: TCP
      port: 443
      targetPort: 8081
EOF

echo "Verifying service definition"
kubectl get service -n "$EVENTSTREAMS_OPERATOR_NAMESPACE" eventstreams-cluster-operator -o yaml

echo "Service certificate"
kubectl get secret -n "$EVENTSTREAMS_OPERATOR_NAMESPACE" eventstreams-cluster-operator --ignore-not-found -o yaml

echo "---------------------------------------------------------------"

#
# 2.  Network Policy
#
# A network policy is required to allow communication to the operator
#  pod for:
#
# - (8080) API that Prometheus will scrape to fetch operator metrics
# - (8081) API that the Kubernetes API server will use for webhooks
#

echo "Creating network policy"

cat <<EOF | kubectl apply -n "$EVENTSTREAMS_OPERATOR_NAMESPACE" -f -
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: eventstreams-cluster-operator
  ownerReferences:
  - apiVersion: $OWNER_APIVERSION
    kind: $OWNER_KIND
    name: $OWNER_NAME
    uid: $OWNER_UID
spec:
  podSelector:
    matchLabels:
      name: eventstreams-cluster-operator
  ingress:
    - ports:
        - protocol: TCP
          port: 8080
        - protocol: TCP
          port: 8081
      from:
        - namespaceSelector: {}
  policyTypes:
    - Ingress
EOF

echo "---------------------------------------------------------------"

#
# 2.  ConfigMap
#
#  A ConfigMap is needed is store the CA for the certificate that
#  allows the webhook API to be HTTPS. The CA is a required field in
#  the webhook definition to be created.
#

echo "Creating config map"
cat <<EOF | kubectl apply -n "$EVENTSTREAMS_OPERATOR_NAMESPACE" -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: eventstreams-cluster-operator
  annotations:
    service.beta.openshift.io/inject-cabundle: "true"
  ownerReferences:
  - apiVersion: $OWNER_APIVERSION
    kind: $OWNER_KIND
    name: $OWNER_NAME
    uid: $OWNER_UID
EOF

echo "Verifying config map definition"
kubectl get configmap -n "$EVENTSTREAMS_OPERATOR_NAMESPACE" eventstreams-cluster-operator -o yaml


echo "Retrieving CA from configmap"
cabundle=""
while [ -z "$cabundle" ]
do
  echo "Waiting for OpenShift to update configmap"
  sleep 1
  echo "Querying config map"
  cabundle=$(kubectl get configmap -n "$EVENTSTREAMS_OPERATOR_NAMESPACE" eventstreams-cluster-operator --ignore-not-found -o jsonpath="{.data['service-ca\.crt']}")
done

echo "CA for webhook"
kubectl get configmap -n "$EVENTSTREAMS_OPERATOR_NAMESPACE" eventstreams-cluster-operator -o jsonpath="{.data['service-ca\.crt']}"
echo "Base64-encoding CA"
kubectl get configmap -n "$EVENTSTREAMS_OPERATOR_NAMESPACE" eventstreams-cluster-operator -o jsonpath="{.data['service-ca\.crt']}" > /tmp/service-ca.crt
cabundle=$(base64 -w0 /tmp/service-ca.crt)
echo "$cabundle"



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
  - apiVersion: $OWNER_APIVERSION
    kind: $OWNER_KIND
    name: $OWNER_NAME
    uid: $OWNER_UID
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
  - name: eventstreams.ibm.com.rejectinvalidnames
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
        path: /admissionwebhook/rejectinvalidnames
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
  - name: eventstreams.ibm.com.rejectinvalidtopics
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
        path: /admissionwebhook/rejectinvalidtopics
      caBundle: "$cabundle"
  - name: eventstreams.ibm.com.rejectinvalidusers
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
        path: /admissionwebhook/rejectinvalidusers
      caBundle: "$cabundle"
  - name: eventstreams.ibm.com.rejectinvalidendpoints
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
        path: /admissionwebhook/rejectinvalidendpoints
      caBundle: "$cabundle"
  - name: eventstreams.ibm.com.rejectinvalidproperties
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
        path: /admissionwebhook/rejectinvalidproperties
      caBundle: "$cabundle"
  - name: eventstreams.ibm.com.rejectinvalidkafkaconnectmetering
    rules:
      - apiGroups: ["eventstreams.ibm.com"]
        apiVersions: ["v1beta1"]
        operations: ["CREATE", "UPDATE"]
        resources: ["kafkaconnects"]
    failurePolicy: Ignore
    clientConfig:
      service:
        namespace: $EVENTSTREAMS_OPERATOR_NAMESPACE
        name: eventstreams-cluster-operator
        path: /admissionwebhook/rejectinvalidkafkaconnectmetering
      caBundle: "$cabundle"
  - name: eventstreams.ibm.com.rejectinvalidkafkaconnects2imetering
    rules:
      - apiGroups: ["eventstreams.ibm.com"]
        apiVersions: ["v1beta1"]
        operations: ["CREATE", "UPDATE"]
        resources: ["kafkaconnects2is"]
    failurePolicy: Ignore
    clientConfig:
      service:
        namespace: $EVENTSTREAMS_OPERATOR_NAMESPACE
        name: eventstreams-cluster-operator
        path: /admissionwebhook/rejectinvalidkafkaconnects2imetering
      caBundle: "$cabundle"
  - name: eventstreams.ibm.com.rejectinvalidmirrormaker2metering
    rules:
      - apiGroups: ["eventstreams.ibm.com"]
        apiVersions: ["v1alpha1"]
        operations: ["CREATE", "UPDATE"]
        resources: ["kafkamirrormaker2s"]
    failurePolicy: Ignore
    clientConfig:
      service:
        namespace: $EVENTSTREAMS_OPERATOR_NAMESPACE
        name: eventstreams-cluster-operator
        path: /admissionwebhook/rejectinvalidmirrormaker2metering
      caBundle: "$cabundle"
  - name: eventstreams.ibm.com.rejectgeneralproperties
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
        path: /admissionwebhook/rejectgeneralproperties
      caBundle: "$cabundle"
EOF

echo "Webhook config:"
kubectl get ValidatingWebhookConfiguration validate-eventstreams-"$EVENTSTREAMS_OPERATOR_NAMESPACE" -o yaml



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

echo "Creating ConsoleYAMLSample samples"

echo "Creating the lightweight insecure sample"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: es-0-light-insecure.eventstreams.ibm.com
  ownerReferences:
  - apiVersion: $CRD_APIVERSION
    kind: $CRD_KIND
    name: $CRD_NAME
    uid: $CRD_UID
spec:
  description: Suitable for basic development and test activities in environments with minimum resource requirements where storage persistence, access control and encryption are not required.
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
  title: Lightweight without security
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
    metadata:
        name: light-insecure
        namespace: placeholder
    spec:
        version: 10.0.0
        license:
            accept: false
            use: CloudPakForIntegrationNonProduction
        adminApi: {}
        adminUI: {}
        collector: {}
        restProducer: {}
        schemaRegistry:
            storage:
                type: ephemeral
        security:
            internalTls: NONE
        strimziOverrides:
            kafka:
                replicas: 1
                config:
                    inter.broker.protocol.version: "2.5"
                    interceptor.class.names: com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor
                    log.message.format.version: "2.5"
                    offsets.topic.replication.factor: 1
                    transaction.state.log.min.isr: 1
                    transaction.state.log.replication.factor: 1
                listeners:
                    plain: {}
                metrics: {}
                storage:
                    type: ephemeral
            zookeeper:
                replicas: 1
                metrics: {}
                storage:
                    type: ephemeral
EOF

echo "Creating the development sample"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: es-1-development.eventstreams.ibm.com
  ownerReferences:
  - apiVersion: $CRD_APIVERSION
    kind: $CRD_KIND
    name: $CRD_NAME
    uid: $CRD_UID
spec:
  description:  Ideal for trying out Event Streams. Suitable for basic development and test activities, with high availability but no storage persistence.
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
  title: Development
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
    metadata:
        name: development
        namespace: placeholder
    spec:
        version: 10.0.0
        license:
            accept: false
            use: CloudPakForIntegrationNonProduction
        adminApi: {}
        adminUI: {}
        collector: {}
        restProducer: {}
        schemaRegistry:
            storage:
                type: ephemeral
        strimziOverrides:
            kafka:
                replicas: 3
                authorization:
                    type: runas
                config:
                    inter.broker.protocol.version: "2.5"
                    interceptor.class.names: com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor
                    log.cleaner.threads: 6
                    log.message.format.version: "2.5"
                    num.io.threads: 24
                    num.network.threads: 9
                    num.replica.fetchers: 3
                    offsets.topic.replication.factor: 3
                listeners:
                    external:
                        authentication:
                            type: scram-sha-512
                        type: route
                    tls:
                        authentication:
                            type: tls
                metrics: {}
                storage:
                    type: ephemeral
            zookeeper:
                replicas: 3
                metrics: {}
                storage:
                    type: ephemeral
EOF

echo "Creating the minimal production sample"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: es-2-minimal-prod.eventstreams.ibm.com
  ownerReferences:
  - apiVersion: $CRD_APIVERSION
    kind: $CRD_KIND
    name: $CRD_NAME
    uid: $CRD_UID
spec:
  description: A secured cluster of 3 Kafka brokers licensed for production use, with the least resource footprint for a gentle workload.
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
  title: Minimal production
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
    metadata:
        name: minimal-prod
        namespace: placeholder
    spec:
        version: 10.0.0
        license:
            accept: false
            use: CloudPakForIntegrationProduction
        adminApi: {}
        adminUI: {}
        collector: {}
        restProducer: {}
        schemaRegistry:
            storage:
                type: persistent-claim
                size: 100Mi
                class: enter-storage-class-name-here
        strimziOverrides:
            kafka:
                replicas: 3
                authorization:
                    type: runas
                config:
                    inter.broker.protocol.version: "2.5"
                    interceptor.class.names: com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor
                    log.cleaner.threads: 6
                    log.message.format.version: "2.5"
                    num.io.threads: 24
                    num.network.threads: 9
                    num.replica.fetchers: 3
                    offsets.topic.replication.factor: 3
                listeners:
                    external:
                        type: route
                        authentication:
                            type: scram-sha-512
                    tls:
                        authentication:
                            type: tls
                metrics: {}
                storage:
                    type: persistent-claim
                    size: 4Gi
                    class: enter-storage-class-name-here
            zookeeper:
                replicas: 3
                metrics: {}
                storage:
                    type: persistent-claim
                    size: 2Gi
                    class: enter-storage-class-name-here
EOF

echo "Creating the production three brokers sample"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: es-3-broker.eventstreams.ibm.com
  ownerReferences:
  - apiVersion: $CRD_APIVERSION
    kind: $CRD_KIND
    name: $CRD_NAME
    uid: $CRD_UID
spec:
  description: A secured cluster of 3 Kafka brokers licensed for production use, tuned to handle a more demanding workload.
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
  title: Production 3 brokers
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: EventStreams
    metadata:
        name: prod-3-brokers
        namespace: placeholder
    spec:
        version: 10.0.0
        license:
            accept: false
            use: CloudPakForIntegrationProduction
        adminApi: {}
        adminUI: {}
        collector: {}
        restProducer: {}
        schemaRegistry:
            storage:
                type: persistent-claim
                size: 1Gi
                class: enter-storage-class-name-here
        strimziOverrides:
            kafka:
                replicas: 3
                authorization:
                    type: runas
                config:
                    inter.broker.protocol.version: "2.5"
                    interceptor.class.names: com.ibm.eventstreams.interceptors.metrics.ProducerMetricsInterceptor
                    log.cleaner.threads: 6
                    log.message.format.version: "2.5"
                    num.io.threads: 24
                    num.network.threads: 9
                    num.replica.fetchers: 3
                    offsets.topic.replication.factor: 3
                listeners:
                    external:
                        type: route
                        authentication:
                            type: scram-sha-512
                    tls:
                        authentication:
                            type: tls
                metrics: {}
                storage:
                    type: persistent-claim
                    size: 10Gi
                    class: enter-storage-class-name-here
                resources:
                    requests:
                        memory: 8096Mi
                        cpu: 4000m
                    limits:
                        memory: 8096Mi
                        cpu: 4000m
            zookeeper:
                replicas: 3
                metrics: {}
                storage:
                    type: persistent-claim
                    size: 4Gi
                    class: enter-storage-class-name-here
EOF

echo "Creating the consumer user sample"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: user-0-consumer.eventstreams.ibm.com
  ownerReferences:
  - apiVersion: $CRD_APIVERSION
    kind: $CRD_KIND
    name: $CRD_NAME
    uid: $CRD_UID
spec:
  description: Client credentials for a Kafka consumer
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaUser
  title: Consumer
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaUser
    metadata:
      name: consumer
      namespace: placeholder
      labels:
        eventstreams.ibm.com/cluster: placeholdercluster
    spec:
      authentication:
        type: scram-sha-512
      authorization:
        type: simple
        acls:
          - resource:
              type: topic
              name: your.topic.name
              patternType: literal
            operation: Read
          - resource:
              type: group
              name: '*'
              patternType: literal
            operation: Read
          - resource:
              type: topic
              name: __schema_
              patternType: prefix
            operation: Read
EOF

echo "Creating the producer user sample"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: user-1-producer.eventstreams.ibm.com
  ownerReferences:
  - apiVersion: $CRD_APIVERSION
    kind: $CRD_KIND
    name: $CRD_NAME
    uid: $CRD_UID
spec:
  description: Client credentials for a Kafka producer
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaUser
  title: Producer
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaUser
    metadata:
      name: producer
      namespace: placeholder
      labels:
        eventstreams.ibm.com/cluster: placeholdercluster
    spec:
      authentication:
        type: scram-sha-512
      authorization:
        type: simple
        acls:
          - resource:
              type: topic
              name: your.topic.name
              patternType: literal
            operation: Write
          - resource:
              type: topic
              name: __schema_
              patternType: prefix
            operation: Read
EOF

echo "Creating the all-permissions user sample"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: user-2-everything.eventstreams.ibm.com
  ownerReferences:
  - apiVersion: $CRD_APIVERSION
    kind: $CRD_KIND
    name: $CRD_NAME
    uid: $CRD_UID
spec:
  description: Client credentials granting all permissions
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaUser
  title: Everything
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaUser
    metadata:
      name: everything
      namespace: placeholder
      labels:
        eventstreams.ibm.com/cluster: placeholdercluster
    spec:
      authentication:
        type: scram-sha-512
      authorization:
        type: simple
        acls:
          - resource:
              type: topic
              name: '*'
              patternType: literal
            operation: Write
          - resource:
              type: topic
              name: '*'
              patternType: literal
            operation: Read
          - resource:
              type: topic
              name: '*'
              patternType: literal
            operation: Create
          - resource:
              type: group
              name: '*'
              patternType: literal
            operation: Read
          - resource:
              type: topic
              name: __schema_
              patternType: prefix
            operation: Read
          - resource:
              type: topic
              name: __schema_
              patternType: prefix
            operation: Alter
          - resource:
              type: transactionalId
              name: '*'
              patternType: literal
            operation: Write
EOF

echo "Creating the kafka connect production sample"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: kafka-connect-production.eventstreams.ibm.com
  ownerReferences:
  - apiVersion: $CRD_APIVERSION
    kind: $CRD_KIND
    name: $CRD_NAME
    uid: $CRD_UID
spec:
  description: Create a Kafka Connect framework for production use. Each Kafka Connect replica is a separate chargeable unit.
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaConnect
  title: Production Kafka Connect
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaConnect
    metadata:
      name: prod
    spec:
      template:
        pod:
          metadata:
            annotations:
              eventstreams.production.type: CloudPakForIntegrationProduction
              productID: 2cba508800504d0abfa48a0e2c4ecbe2
              productName: IBM Event Streams
              productVersion: 10.0.0
              productMetric: VIRTUAL_PROCESSOR_CORE
              productChargedContainers: <ADD-NAME-OF-KAFKA-CONNECT-CR>-connect
              cloudpakId: c8b82d189e7545f0892db9ef2731b90d
              cloudpakName: IBM Cloud Pak for Integration
              cloudpakVersion: 2020.2.1
              productCloudpakRatio: "1:1"
      bootstrapServers: my-cluster-kafka-bootstrap:9093
      tls:
        trustedCertificates:
          - secretName: my-cluster-cluster-ca-cert
            certificate: ca.crt
      config:
        group.id: connect-cluster
        offset.storage.topic: connect-cluster-offsets
        config.storage.topic: connect-cluster-configs
        status.storage.topic: connect-cluster-status
      replicas: 1
      resources:
        requests:
          cpu: 1000m
          memory: 2Gi
        limits:
          cpu: 2000m
          memory: 2Gi
EOF

echo "Creating the kafka connect non-production sample"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: kafka-connect-non-production.eventstreams.ibm.com
  ownerReferences:
  - apiVersion: $CRD_APIVERSION
    kind: $CRD_KIND
    name: $CRD_NAME
    uid: $CRD_UID
spec:
  description: Create a Kafka Connect framework for development and testing use. Each Kafka Connect replica is a separate chargeable unit.
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaConnect
  title: Non-Production Kafka Connect
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaConnect
    metadata:
      name: prod
    spec:
      template:
        pod:
          metadata:
            annotations:
              eventstreams.production.type: CloudPakForIntegrationNonProduction
              productID: 2a79e49111f44ec3acd89608e56138f5
              productName: IBM Event Streams for Non Production
              productVersion: 10.0.0
              productMetric: VIRTUAL_PROCESSOR_CORE
              productChargedContainers: <ADD-NAME-OF-KAFKA-CONNECT-CR>-connect
              cloudpakId: c8b82d189e7545f0892db9ef2731b90d
              cloudpakName: IBM Cloud Pak for Integration
              cloudpakVersion: 2020.2.1
              productCloudpakRatio: "2:1"
      bootstrapServers: my-cluster-kafka-bootstrap:9093
      tls:
        trustedCertificates:
          - secretName: my-cluster-cluster-ca-cert
            certificate: ca.crt
      config:
        group.id: connect-cluster
        offset.storage.topic: connect-cluster-offsets
        config.storage.topic: connect-cluster-configs
        status.storage.topic: connect-cluster-status
      replicas: 1
      resources:
        requests:
          cpu: 1000m
          memory: 2Gi
        limits:
          cpu: 2000m
          memory: 2Gi
EOF

echo "Creating the kafka connect Source To Image production sample"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: kafka-connect-s2i-production.eventstreams.ibm.com
  ownerReferences:
  - apiVersion: $CRD_APIVERSION
    kind: $CRD_KIND
    name: $CRD_NAME
    uid: $CRD_UID
spec:
  description: Create a Kafka Connect Source to Image framework for production use. Each Kafka Connect replica is a separate chargeable unit.
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaConnectS2I
  title: Production Kafka Connect Source to Image
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaConnectS2I
    metadata:
      name: prod
    spec:
      template:
        pod:
          metadata:
            annotations:
              eventstreams.production.type: CloudPakForIntegrationProduction
              productID: 2cba508800504d0abfa48a0e2c4ecbe2
              productName: IBM Event Streams
              productVersion: 10.0.0
              productMetric: VIRTUAL_PROCESSOR_CORE
              productChargedContainers: <ADD-NAME-OF-KAFKA-CONNECT-CR>-connect
              cloudpakId: c8b82d189e7545f0892db9ef2731b90d
              cloudpakName: IBM Cloud Pak for Integration
              cloudpakVersion: 2020.2.1
              productCloudpakRatio: "1:1"
      bootstrapServers: my-cluster-kafka-bootstrap:9093
      tls:
        trustedCertificates:
          - secretName: my-cluster-cluster-ca-cert
            certificate: ca.crt
      config:
        group.id: connect-cluster
        offset.storage.topic: connect-cluster-offsets
        config.storage.topic: connect-cluster-configs
        status.storage.topic: connect-cluster-status
  replicas: 1
  resources:
    requests:
      cpu: 1000m
      memory: 2Gi
    limits:
      cpu: 2000m
      memory: 2Gi
EOF

echo "Creating the kafka connect Source To Image non-production sample"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: kafka-connect-s2i-non-production.eventstreams.ibm.com
  ownerReferences:
  - apiVersion: $CRD_APIVERSION
    kind: $CRD_KIND
    name: $CRD_NAME
    uid: $CRD_UID
spec:
  description: Create a Kafka Connect Source to Image framework for development and testing use. Each Kafka Connect replica is a separate chargeable unit.
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaConnectS2I
  title: Non-Production Kafka Connect Source to Image
  yaml: |
    apiVersion: eventstreams.ibm.com/v1beta1
    kind: KafkaConnectS2I
    metadata:
      name: non-prod
    spec:
      template:
        pod:
          metadata:
            annotations:
              eventstreams.production.type: CloudPakForIntegrationNonProduction
              productID: 2a79e49111f44ec3acd89608e56138f5
              productName: IBM Event Streams for Non Production
              productVersion: 10.0.0
              productMetric: VIRTUAL_PROCESSOR_CORE
              productChargedContainers: <ADD-NAME-OF-KAFKA-CONNECT-CR>-connect
              cloudpakId: c8b82d189e7545f0892db9ef2731b90d
              cloudpakName: IBM Cloud Pak for Integration
              cloudpakVersion: 2020.2.1
              productCloudpakRatio: "2:1"
      bootstrapServers: my-cluster-kafka-bootstrap:9093
      tls:
        trustedCertificates:
          - secretName: my-cluster-cluster-ca-cert
            certificate: ca.crt
      config:
        group.id: connect-cluster
        offset.storage.topic: connect-cluster-offsets
        config.storage.topic: connect-cluster-configs
        status.storage.topic: connect-cluster-status
      replicas: 1
      resources:
        requests:
          cpu: 1000m
          memory: 2Gi
        limits:
          cpu: 2000m
          memory: 2Gi
EOF

echo "Creating the Mirror Maker 2 production sample"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: mirror-maker-2-production.eventstreams.ibm.com
  ownerReferences:
  - apiVersion: $CRD_APIVERSION
    kind: $CRD_KIND
    name: $CRD_NAME
    uid: $CRD_UID
spec:
  description: Create a Mirror Maker 2 framework for production use. Each Mirror Maker 2 replica is a separate chargeable unit.
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1alpha1
    kind: KafkaMirrorMaker2
  title: Production Mirror Maker 2
  yaml: |
    apiVersion: eventstreams.ibm.com/v1alpha1
    kind: KafkaMirrorMaker2
    metadata:
      name: prod
    spec:
      template:
        pod:
          metadata:
            annotations:
              eventstreams.production.type: CloudPakForIntegrationProduction
              productID: 2cba508800504d0abfa48a0e2c4ecbe2
              productName: IBM Event Streams
              productVersion: 10.0.0
              productMetric: VIRTUAL_PROCESSOR_CORE
              productChargedContainers: <ADD-NAME-OF-MM2-CR>-mirror-maker
              cloudpakId: c8b82d189e7545f0892db9ef2731b90d
              cloudpakName: IBM Cloud Pak for Integration
              cloudpakVersion: 2020.2.1
              productCloudpakRatio: "1:1"
      connectCluster: "my-cluster-target"
      clusters:
        - alias: "my-cluster-source"
          bootstrapServers: my-cluster-source-kafka-bootstrap:9092
        - alias: "my-cluster-target"
          bootstrapServers: my-cluster-target-kafka-bootstrap:9092
          config:
            config.storage.replication.factor: 1
            offset.storage.replication.factor: 1
            status.storage.replication.factor: 1
      mirrors:
        - sourceCluster: "my-cluster-source"
          targetCluster: "my-cluster-target"
          sourceConnector:
            config:
              replication.factor: 1
              offset-syncs.topic.replication.factor: 1
              sync.topic.acls.enabled: "false"
          heartbeatConnector:
            config:
              heartbeats.topic.replication.factor: 1
          checkpointConnector:
            config:
              checkpoints.topic.replication.factor: 1
          topicsPattern: ".*"
          groupsPattern: ".*"
      replicas: 1
      resources:
        requests:
          cpu: 1000m
          memory: 2Gi
        limits:
          cpu: 2000m
          memory: 2Gi
EOF

echo "Creating the Mirror Maker 2 non-production sample"
! cat <<EOF | kubectl apply -f -
apiVersion: console.openshift.io/v1
kind: ConsoleYAMLSample
metadata:
  name: mirror-maker-2-non-production.eventstreams.ibm.com
  ownerReferences:
  - apiVersion: $CRD_APIVERSION
    kind: $CRD_KIND
    name: $CRD_NAME
    uid: $CRD_UID
spec:
  description: Create a Mirror Maker 2 framework for development and testing use. Each Mirror Maker 2 replica is a separate chargeable unit.
  snippet: false
  targetResource:
    apiVersion: eventstreams.ibm.com/v1alpha1
    kind: KafkaMirrorMaker2
  title: Non-Production Mirror Maker 2
  yaml: |
    apiVersion: eventstreams.ibm.com/v1alpha1
    kind: KafkaMirrorMaker2
    metadata:
      name: non-prod
    spec:
      template:
        pod:
          metadata:
            annotations:
              eventstreams.production.type: CloudPakForIntegrationNonProduction
              productID: 2a79e49111f44ec3acd89608e56138f5
              productName: IBM Event Streams for Non Production
              productVersion: 10.0.0
              productMetric: VIRTUAL_PROCESSOR_CORE
              productChargedContainers: <ADD-NAME-OF-MM2-CR>-mirror-maker
              cloudpakId: c8b82d189e7545f0892db9ef2731b90d
              cloudpakName: IBM Cloud Pak for Integration
              cloudpakVersion: 2020.2.1
              productCloudpakRatio: "2:1"
      connectCluster: "my-cluster-target"
      clusters:
        - alias: "my-cluster-source"
          bootstrapServers: my-cluster-source-kafka-bootstrap:9092
        - alias: "my-cluster-target"
          bootstrapServers: my-cluster-target-kafka-bootstrap:9092
          config:
            config.storage.replication.factor: 1
            offset.storage.replication.factor: 1
            status.storage.replication.factor: 1
      mirrors:
        - sourceCluster: "my-cluster-source"
          targetCluster: "my-cluster-target"
          sourceConnector:
            config:
              replication.factor: 1
              offset-syncs.topic.replication.factor: 1
              sync.topic.acls.enabled: "false"
          heartbeatConnector:
            config:
              heartbeats.topic.replication.factor: 1
          checkpointConnector:
            config:
              checkpoints.topic.replication.factor: 1
          topicsPattern: ".*"
          groupsPattern: ".*"
      replicas: 1
      resources:
        requests:
          cpu: 1000m
          memory: 2Gi
        limits:
          cpu: 2000m
          memory: 2Gi
EOF


echo "Verifying Console YAML samples:"
all_samples=("es-0-light-insecure.eventstreams.ibm.com" "es-1-development.eventstreams.ibm.com" "es-2-minimal-prod.eventstreams.ibm.com" "es-3-broker.eventstreams.ibm.com" "user-0-consumer.eventstreams.ibm.com" "user-1-producer.eventstreams.ibm.com" "user-2-everything.eventstreams.ibm.com" "kafka-connect-production.eventstreams.ibm.com" "kafka-connect-non-production.eventstreams.ibm.com" "kafka-connect-s2i-production.eventstreams.ibm.com" "kafka-connect-s2i-non-production.eventstreams.ibm.com" "mirror-maker-2-production.eventstreams.ibm.com" "mirror-maker-2-non-production.eventstreams.ibm.com")
for sample_to_check in "${all_samples[@]}"
do
  ! kubectl get ConsoleYAMLSample "$sample_to_check" -o yaml
done

echo "---------------------------------------------------------------"

echo "Setup complete."
