# eventstreams-cluster-operator

Event Streams variant of the Strimzi Kafka Operator
This project combines the verticles of the Strimzi Kafka Operator and the now deprecated
[eventstreams-operator](github.ibm.com/mhub/qp-eventstreams-operator) into a single Vert.x instance.
This results in a single `Main` that runs two completely separate operators.

## Build

### Pre-reqs
If building the whole project you will need:
- all of the [pre-reqs listed for Strimzi](DEV_QUICK_START_MACOS.md#Preparing your Mac for work) or for [non-Mac users](DEV_QUICK_START.md#pre-requisites)
- yq v2.4.1 [source repo](https://github.com/mikefarah/yq)
- jq [source repo](https://stedolan.github.io/jq/)
- operator-sdk [source repo](https://github.com/operator-framework/operator-sdk/blob/master/doc/user/install-operator-sdk.md)
- python v3
```
brew install yq jq operator-sdk python3
```
- operator-courier [source repo](https://github.com/operator-framework/operator-courier)
```
pip3 install operator-courier
```

### Install

To build the project from scratch run:
```
ARTIFACTORY_PASSWORD=<API_KEY> make eventstreams_build
```
This will build all of the Strimzi sub-modules in order and produce a set of images. The image `strimzi/operator:latest` will contain the eventstreams operator.
Note: currently the Strimzi tests are broken, so we need to skip them.

To build just the `eventstreams-cluster-operator` project run:
```
make eventstreams_java_build
```
(this requires the `eventstreams-cluster-operator` dependencies such as the `cluster-operator` to already be built and available in your local maven repository)
This will generate a jar in `evenstreams-cluster-operator/target`.

To build just the `Docker` images, run:
```
make eventstreams_operator_build
```
(this requires the `eventstreams-cluster-operator` Jar and its dependencies such as the `cluster-operator` Jar to already be built and available in your local maven repository)
This will generate a docker image for EventStreams.

This will also regenerate the EventStreams CustomResourceDefinition into `install/ibm-eventstreams-operator`

## Deploy

To deploy the EventStreams operator into your Kubernetes Environment with a `common-services` installed
```
oc apply -f install/ibm-eventstreams-operator/
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


## Deploying OLM bundle to your openshift environment

### Log into your OpenShift cluster
1. Click your username in the top right corner, and click **Copy Login Command** to access the API token.
2. Click **Display Token** and copy the command provided to your terminal.
3. Run the command in your terminal to log in.

### Create a new namespace
Use a new namespace, rather than a default one.
To create a new namespace: `oc create ns <name_of_your_namespace>`

### Apply a custom Security Context Constraint (optional)
You **don't need a custom scc**, as the default `restricted` scc should work fine.

However, if you want to apply a custom SCC, a sample is available to get you started. You need to have the `strimzi-kafka-operator` directory locally.  The repository can be found [here](https://github.ibm.com/mhub/strimzi-kafka-operator)

Make the following updates to `install/cluster-operator/100-SecurityContextConstraints-eventstreams.yaml`:
1. Replace the namespace you have created (by default, the `myproject` namespace is used)
```
groups:
    - system:serviceaccounts:<your-namespace>
```
2. Modify `ibm-es-scc` in the metadata section of the file. Update it with a different unique name.
```
metadata:
  name: <your-scc>
```

### Create a secret for your cluster to enable access to Docker registry:
```
oc create secret docker-registry ibm-entitlement-key --docker-server=hyc-qp-stable-docker-local.artifactory.swg-devops.com --docker-username=<your_email> --docker-password=<Artifactory_API> --docker-email=<your_email> -n <name_of_your_namespace>
```


### Update namespace fields
In the `install/cluster-operator` directory

Update the namespace fields to the namespace you have previously specified.
Update the following files:

* `020-RoleBinding-strimzi-cluster-operator.yaml`
* `021-ClusterRoleBinding-strimzi-cluster-operator.yaml`
* `030-ClusterRoleBinding-strimzi-cluster-operator-kafka-broker-delegation.yaml`
* `031-RoleBinding-strimzi-cluster-operator-entity-operator-delegation.yaml`
* `032-RoleBinding-strimzi-cluster-operator-topic-operator-delegation.yaml`

By default, the namespace used in those files is `myproject`.
An easy way to replace the namespace fields is to do a search and replace of `myproject` in the `install/cluster-operator` directory with the name of your namespace.

You can now install your operator.
Apply all the files within the `install/cluster-operator` directory:
```
oc apply -f install/cluster-operator -n <name_of_your_namespace>
```

### Install Common Services
Follow the [instructions](https://github.ibm.com/ICP-DevOps/tf_openshift_4_tools/tree/master/fyre/ceph_and_inception_install) to install Common Services in your OpenShift Cluster.

PreReqs:
* Access to https://fyre.ibm.com
* A running OpenShift cluster (with 3 or more workers)

Summary of the instructions:
1. Clone this repository:
```
git clone git@github.ibm.com:ICP-DevOps/tf_openshift_4_tools.git
```
2. Go into `ceph_and_inception_install` directory in the Cloned repository
```
cd tf_openshift_4_tools/fyre/ceph_and_inception_install
```
3. Copy and rename `terraform.tfvars.example` to `terraform.tfvars`:
```
cp terraform.tfvars.example terraform.tfvars
```
4. Edit `terraform.tfvars` using the appropriate values. You will need:
    * `fyre_root_password` - you must set this root password.
        * Go to https://fyre.ibm.com
        * Go to *Stacks*
        * Click on the cluster you want the Common Services to be installed on
        * Click on *Change Stack Password* - one of the small icons once you click on your cluster - an icon of a lock; right below the cluster's name;
        * Enter the new password for your cluster - this is the `fyre_root_password`
    * `fyre_inf_vm_nine_dot_ip` - the IP address of your cluster that starts with 9 (IP 1 Column). Click on your cluster to see it.
    * `repo_token` - your Artifactory API token
    * `repo_user` - your Artifactory username (your w3 email)
5. terraform init
    * If you do not have terraform installed:
        * https://www.terraform.io/downloads.html
6. terraform apply
    * When asked "Do you want to perform these actions?", enter `yes`.

_Note_: If you're going to re-run to a different cluster without doing a new clone be sure to run `rm -rf terraform.tfstate*` when in `tf_openshift_4_tools/fyre/ceph_and_inception_install` before running your next `terraform apply`

### (PreReq) Expose docker registry
Expose your OpenShift Docker registry by running:
```
oc patch configs.imageregistry.operator.openshift.io/cluster --patch '{"spec":{"defaultRoute":true}}' --type=merge
```
_(This can take about 5-10 minutes, cluster can become inaccessible while this happens)_

Copy the output of `oc registry login --skip-check` into your docker insecure registries (`Preferences > Daemon > Insecure Registries`)

### Build operator registry with our operator bundle, push to OpenShift registry
Run `make build_csv deploy  -C deploy` this will build and verify the OLM bundle, build a catalog source and push it to your OpenShift

To verify that local-operator has been correctly deployed:
```
oc get pods -n openshift-marketplace
oc get catalogsource -n openshift-marketplace
```

### Get your cluster CA certificate & import it into the Java KeyStore file
Get the certificate by running the command:
```
oc get secret <cluster_name>-cluster-ca-cert -o jsonpath='{.data.ca\.crt}' -n <name_of_your_namespace> | base64 -d > ca.crt
```

1. Save the password for the certificate
2. When asked whether to trust the certificate, enter `yes`
3. Import the certificate into the Java KeyStore file:
```
keytool -keystore client.truststore.jks -alias CARoot -import -file ca.crt
```

### Use a sample Java application for testing
Download locally a [sample Java application](https://github.ibm.com/qp-samples/vertx-kafka):
```
git clone git@github.ibm.com:qp-samples/vertx-kafka.git
```

1. To build the application, run
```
mvn install

```
2. To run the application:
```
java -jar target/demo-0.0.1-SNAPSHOT-fat.jar
```
go to `localhost:8080` in your browser to see it running
3. To connect and configure your application to the EventStreams instance, do the following:
    In `src/main/resources/kafka.properties`, update the following properties:
    1. `bootstrap.servers` - the bootstrap server address
    2. `ssl.truststore.location` - a path to your Java KeyStore file
    3. `ssl.truststore.password` - a password you have chosen during generation of the certificate

When the above steps have been completed, you will find an entry for Event Streams under "Operator Hub" in the OpenShift Console.
This can be used to deploy an instance of the operator and create new Event Streams instances.


## Deploying Event Streams Custom Resources
For an OpenShift user *without* the administrator roll, a cluster administrator will need to do the following steps:
1. Apply a ClusterRole with the required create permissions to the cluster. An example can be found at `examples/eventstreams-cluster-roles/eventstreams-cluster-admin.yaml`
This can be applied by running:
```
kubectl apply -f examples/eventstreams-cluster-roles/eventstreams-cluster-admin.yaml
```
2. Then bind this ClusterRole to the ServiceAccount of the OpenShift user that you want to give permissions to.
This can be done by running:
```
oc adm policy add-cluster-role-to-user eventstreams-cluster-admin <USER_NAME>
```
where `<USER_NAME>` is the username of the user you that you want to give permissions to.

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

## Writing Tests
Ensuring that the tests are written correctly, and that the underlying logic is leading to the intended outcome and no unintended side-effects, see below examples for guidance.

When adding new tests, please try to use: `.onComplete(context.failing(e -> context.verify(() -> { ... }` rather than `setHandler(ar -> { ... }`. It simplies the logic of the test, by not having to use conditional statement to fail the context of the test.

For good practice, see the two examples below. The _New_ example is easier to read and maintain, and correctly fails the context when the test fails. Therefore, it is recommended to use the _New_ way of writing tests.

_Old_:
```
@Test
public void testEventStreamsNameTooLong(VertxTestContext context) {
    mockRoutes();
    PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

    esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

    // 17 Characters long
    String clusterName = "long-instancename";

    EventStreams esCluster = createDefaultEventStreams(NAMESPACE, clusterName);
    ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
    Checkpoint async = context.checkpoint(1);

    esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, clusterName), esCluster).setHandler(ar -> {
        if (ar.failed()) {
            assertThat(ar.cause().toString(), containsString("Invalid Custom Resource: check status"));
            // check status
            verify(esResourceOperator).createOrUpdate(updatedEventStreams.capture());
            assertThat("Status is incorrect, found status : " + updatedEventStreams.getValue().getStatus(),
                    updatedEventStreams.getValue().getStatus().getConditions().get(0).getMessage().equals("Invalid custom resource: EventStreams metadata name too long. Maximum length is 16"));
            context.completeNow();
        } else {
            context.failNow(ar.cause());
        }
        async.flag();
    });
}
```
_New_:
```
@Test
public void testEventStreamsNameTooLongThrows(VertxTestContext context) {
    mockRoutes();
    PlatformFeaturesAvailability pfa = new PlatformFeaturesAvailability(true, KubernetesVersion.V1_9);

    esOperator = new EventStreamsOperator(vertx, mockClient, EventStreams.RESOURCE_KIND, pfa, esResourceOperator, cp4iResourceOperator, imageConfig, routeOperator, kafkaStatusReadyTimeoutMs);

    // 17 Characters long
    String clusterName = "long-instancename";

    EventStreams esCluster = createDefaultEventStreams(NAMESPACE, clusterName);
    ArgumentCaptor<EventStreams> updatedEventStreams = ArgumentCaptor.forClass(EventStreams.class);
    Checkpoint async = context.checkpoint();

    esOperator.createOrUpdate(new Reconciliation("test-trigger", EventStreams.RESOURCE_KIND, NAMESPACE, clusterName), esCluster)
            .onComplete(context.failing(e -> context.verify(() -> {
                assertThat(e.getMessage(), is("Invalid Custom Resource: check status"));
                // check status
                verify(esResourceOperator).createOrUpdate(updatedEventStreams.capture());
                assertThat("Status is incorrect, found status : " + updatedEventStreams.getValue().getStatus(),
                        updatedEventStreams.getValue().getStatus().getConditions().get(0).getMessage().equals("Invalid custom resource: EventStreams metadata name too long. Maximum length is 16"));
                async.flag();
            })));
}
```

## Tools
### Spotbugs
Spotbugs is a java static code analyser. It is run as part of `make all`, or can be run separately using the command `mvn compile spotbugs:spotbugs spotbugs:check`.
