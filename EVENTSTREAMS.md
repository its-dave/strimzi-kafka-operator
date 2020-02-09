# eventstreams-cluster-operator

Event Streams variant of the Strimzi Kafka Operator
This project combines the verticles of the Strimzi Kafka Operator and the now deprecated
[eventstreams-operator](github.ibm.com/mhub/qp-eventstreams-operator) into a single Vert.x instance.
This results in a single `Main` that runs two completely separate operators.

## Build

### Pre-reqs

- If building the whole project you will need all of the [pre-reqs listed for Strimzi](DEV_QUICK_START_MACOS.md#Preparing your Mac for work) or for [non-Mac users](DEV_QUICK_START.md#pre-requisites)

### Install

To build the project from scratch run: 
```
make eventstreams_build
```
This will build all of the Strimzi sub-modules in order and produce a set of images. The image `strimzi/operator:latest` will contain the eventstreams operator.

To build just the `eventstreams-cluster-operator` project run:
```
make eventstreams_operator_build
```
(this requires the `eventstreams-cluster-operator` dependencies such as the `cluster-operator` to already be built and available in your local maven repository)
This will generate a jar in `evenstreams-cluster-operator/target`.

To build just the `Docker` images, run:
```
make eventstreams_docker_build
```
(this requires the `eventstreams-cluster-operator` Jar and its dependencies such as the `cluster-operator` Jar to already be built and available in your local maven repository)
This will generate a docker image for EventStreams.

To just regenerate the EventStreams CustomResourceDefinition into `install/cluster-operator`, run:
```
make eventstreams_generate_crd
```

## Deploy

To deploy the EventStreams operator into your Kubernetes Environment with a `common-services` installed
```
oc apply -f install/cluster-operator/
```

Finally you can create an instance of the EventStreams Custom resource.

```
kubectl apply -f examples/evenstreams/eventstreams-ephemeral-single.yaml
```

### Watching multiple namespaces
To watch multiple namespaces, make sure you create the appropriate role bindings for resource watching permissions for 
the Cluster Operator and create a Security Context Constraint for EventStreams in each namespace.
To create the role bindings for Cluster Operator, apply the following to each watched namespace:
```
kubectl apply -f install/cluster-operator/020-RoleBinding-strimzi-cluster-operator.yaml -n <WATCHED_NAMESPACE>
kubectl apply -f install/cluster-operator/031-RoleBinding-strimzi-cluster-operator-entity-operator-delegation.yaml -n <WATCHED_NAMESPACE>
kubectl apply -f install/cluster-operator/032-RoleBinding-strimzi-cluster-operator-topic-operator-delegation.yaml -n <WATCHED_NAMESPACE>
```

For more details, follow the [Strimzi instructions](https://strimzi.io/docs/quickstart/latest/#proc-install-product-str)

Apply EventStreams SCC by replacing the namespace to the custom namespace.
```
oc apply -f install/cluster-operator/100-SecurityContextConstraints-eventstreams.yaml
```

In the operator deployment `install/cluster-operator/150-Deployment-eventstreams-cluster-operator.yaml` set the 
`eventstreams-operator` container env-var:
```
env:
## TODO we should merge these envars
- name: EVENTSTREAMS_NAMESPACE
  value: "<namespace1>,<namespace2>"
- name: STRIMZI_NAMESPACE
  value: "<namespace1>,<namespace2>"
```

Logs for the operator should show that it is watching the custom resources on the supplied namespaces.

## Integration Testing
Integration tests verify installs on a live environment, so to run them you need to be logged into a running OpenShift environment.
To run the integration tests, run: `mvn verify`


## Manual Testing
Firstly get the routes using the following command:
```
kubectl get routes
```
example output:
```
NAME                           HOST/PORT                                                      PATH   SERVICES                       PORT   TERMINATION   WILDCARD
my-es-ibm-es-rest-admin        my-es-ibm-es-rest-admin-myproject.192.168.99.100.nip.io               my-es-ibm-es-rest-admin        9080                 None
my-es-ibm-es-rest-proxy        my-es-ibm-es-rest-proxy-myproject.192.168.99.100.nip.io               my-es-ibm-es-rest-proxy        9443                 None
my-es-ibm-es-schema-registry   my-es-ibm-es-schema-registry-myproject.192.168.99.100.nip.io          my-es-ibm-es-schema-registry   3000                 None
```

### Rest Producer
Produces message `message` to the topic `test`:
```
curl -k "http://<rest-proxy-route>/topics/test/records" -d 'message' -H "Content-Type: text/plain" -H "Authorization: Bearer qGC3T__FYe5kQWoAedyQDdXqdUwSohEOLpS4zzF-eo7u" -v

example:
curl -k "http://my-es-ibm-es-rest-proxy-myproject.192.168.99.100.nip.io/topics/test/records" -d 'message' -H "Content-Type: text/plain" -H "Authorization: Bearer qGC3T__FYe5kQWoAedyQDdXqdUwSohEOLpS4zzF-eo7u" -v
```
You can then start a consumer in the kafka pod to check that the message has been produced.
Note: the Authorization Bearer token is not currently used.

### Rest
Create a topic:
```
curl -X POST http://<rest-admin-route>/admin/topics -H 'Content-Type: application/json' -H 'Accept: application/json' -H 'Authorization: Bearer 1234567890123456789012345678901234567890123456789012345678901234' -d '{"name": "<topicName>", "partition_count": 1, "replication_factor": 1}'

example:
curl -X POST http://my-es-ibm-es-rest-admin-myproject.192.168.99.100.nip.io/admin/topics -H 'Content-Type: application/json' -H 'Accept: application/json' -H 'Authorization: Bearer 1234567890123456789012345678901234567890123456789012345678901234' -d '{"name": "steve", "partition_count": 1, "replication_factor": 1}'
```
Gets a list of topics:
```
curl -XGET http://<rest-admin-route>/admin/topics -H 'Authorization: Bearer 1234567890123456789012345678901234567890123456789012345678901234'

example:
curl -XGET http://my-es-ibm-es-rest-admin-myproject.192.168.99.100.nip.io/admin/topics -H 'Authorization: Bearer 1234567890123456789012345678901234567890123456789012345678901234'
```

## Tools
### Spotbugs
Spotbugs is a java static code analyser. It is run as part of `make all`, or can be run separately using the command `mvn compile spotbugs:spotbugs spotbugs:check`.
