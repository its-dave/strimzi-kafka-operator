# eventstreams-cluster-operator

Event Streams variant of the Strimzi Kafka Operator
This project combines the verticles of the Strimzi Kafka Operator and the now deprecated
[eventstreams-operator](github.ibm.com/mhub/qp-eventstreams-operator) into a single Vert.x instance.
This results in a single `Main` that runs two completely separate operators.

## Build

### Pre-reqs
If building the whole project you will need:
- all of the [pre-reqs listed for Strimzi](DEV_QUICK_START_MACOS.md#preparing-your-mac-for-work) or for [non-Mac users](DEV_QUICK_START.md#pre-requisites)

#### Brew packages
- yq v3.2.1 [source repo](https://github.com/mikefarah/yq)
- jq v1.6 [source repo](https://stedolan.github.io/jq/)
- operator-sdk v0.17.0 [source repo](https://github.com/operator-framework/operator-sdk/blob/master/doc/user/install-operator-sdk.md)
- python v3
- gnu-sed v4.7

```
brew install yq jq operator-sdk python3 gnu-sed
```
##### Notes
`brew upgrade <package>` can be used to upgrade any of your existing packages to latest.

`brew` always installs the latest version of packages - the versions listed above are the versions we've tested with. If you discover `brew` has installed a newer version that causes problems, you can downgrade to a specific version using the following steps:

- `brew log --oneline <package>` lists the commits to Homebrew that modify the package version. Copy the short commit id that has the message '<package>: update <version> bottle'
- `brew unlink <package>` will remove the link to the newer version
- `brew install https://raw.githubusercontent.com/Homebrew/homebrew-core/<short-commit>/Formula/<package>.rb`

The `make` build is using GNU versions of `find` and `sed` utilities and is not compatible with the BSD versions available on Mac OS. When using Mac OS, you have to install the GNU versions of `find` and `sed`. When using `brew`, you can do `brew install gnu-sed findutils grep coreutils`. This command will install the GNU versions as `gcp`, `ggrep`, `gsed` and `gfind` and our `make` build will automatically pick them up and use them.

#### Pip3 packages
- operator-courier v2.1.7 [source repo](https://github.com/operator-framework/operator-courier)
```
pip3 install operator-courier==2.1.7
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

## Deploying OLM bundle to your OpenShift environment

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

Make the following updates to `examples/eventstreams-extras/eventstreams-scc.yaml`:
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

### Create a secret for your cluster to enable access to Docker registry for operand images:
```
oc create secret docker-registry ibm-entitlement-key --docker-server=hyc-qp-stable-docker-local.artifactory.swg-devops.com --docker-username=<your_email> --docker-password=<Artifactory_API> --docker-email=<your_email> -n <name_of_your_namespace>
```

### Update the global pull secret for your cluster to enable access to Docker registry for operator images:

Add a docker config json for hyc-qp-stable-docker-local.artifactory.swg-devops.com to the `pull-secret` secret in the `openshift-config` namespace.

```
"hyc-qp-stable-docker-local.artifactory.swg-devops.com": {
	"auth": "base64creds",
	"email": "your.email@uk.ibm.com"
}
```

where `base64creds` is a base64-encoded version of your Artifactory `username:apikey`

[More background here](https://github.ibm.com/mhub/strimzi-kafka-operator/pull/375#issuecomment-20208595)

### (PreReq) Expose docker registry
Expose your OpenShift Docker registry by running:
```
oc patch configs.imageregistry.operator.openshift.io/cluster --patch '{"spec":{"defaultRoute":true}}' --type=merge
```
_(This can take about 5-10 minutes, cluster can become inaccessible while this happens)_

Copy the output of `oc registry login --skip-check` into your docker insecure registries (`Preferences > Daemon > Insecure Registries`)
Note: If you have a newer version of docker installed, this will need to be added to `Preferences > Docker Engine` instead.

### (PreReq) Install patched OPM tool
- Export ARTIFACTORY_USERNAME and ARTIFACTORY_PASSWORD as env vars (if not already in your bash profile).
- Run `make -C deploy get_opm` which downloads `opm` from artifactory and installs it into `/usr/local/bin`

### Build operator registry with our operator bundle, push to OpenShift registry
Run `make -C deploy deploy` this will build and verify the OLM bundle, build a catalog source and push it to your OpenShift

To verify that the local-operator and the opencloud-operators catalogsources have been correctly deployed:
```
oc get pods -n openshift-marketplace
oc get catalogsource -n openshift-marketplace
```

## Installing an Event Streams operator
Once the Event Streams and Common Services are present in the OperatorHub of your OpenShift you can install the EventStreams operator.

1. Click the Event Streams tile in OperatorHub
2. Choose either all namespaces or a single namespace
3. Wait for the Event Streams operator to be ready

### Waiting for the Event Streams operator
While the Event Streams operator is installing you can check the following:
1. A Common Services operator and ODLM operator have been installed
2. An OperandRequest has been created in the same namespace as the Event Streams operator
3. The status of the OperandRequest, you should see:
    ```yaml
    - name: ibm-iam-operator
      phase:
        operandPhase: Running
        operatorPhase: Running
    ```
4. The operators and pods coming up in the ibm-common-services namespace
5. The secret called `management-ingress-ibmcloud-cluster-ca-cert` in the Event Streams operator namespace has been created
6. The configmap called `management-ingress-ibmcloud-cluster-info` in the Event Streams operator namespace has been created
7. The progress of the init container by running `oc logs <eventstreams-operator-pod-name> -c init`

Note: The Event Streams operator might report it has timed out in the UI but if you check the pod status it will still be running the init 

## Producing and Consuming to Event Streams
### Get your cluster CA certificate & import it into the Java KeyStore file
Get the certificate by running the command:
```
oc get secret <cluster_name>-cluster-ca-cert -o jsonpath='{.data.ca\.crt}' -n <name_of_your_namespace> | base64 -d > ca.crt
```
You can run `oc get secrets` to find the cluster name.

1. Save the password for the certificate
```
oc get secret <cluster_name>-cluster-ca-cert -o jsonpath='{.data.ca\.password}' | base64 -D
```
2. Import the certificate into the Java KeyStore file:
```
keytool -keystore client.truststore.jks -alias CARoot -import -file ca.crt
```
3. When asked whether to trust the certificate, enter `yes`

### Use a sample Java application for testing
Download locally a [sample Java application](https://github.ibm.com/qp-samples/vertx-kafka):
```
git clone git@github.ibm.com:qp-samples/vertx-kafka.git
```

1. To build the application, run
```
mvn install
```
If this fails then add this snippet to `~/.m2/settings.xml` and try again
```
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                          https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <profiles>
    <profile>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <repositories>
       <repository>
         <id>snapshots-repo</id>
         <url>https://oss.sonatype.org/content/repositories/snapshots</url>
         <releases><enabled>false</enabled></releases>
         <snapshots><enabled>true</enabled></snapshots>
       </repository>
      </repositories>
     </profile>
  </profiles>
</settings>
```
2. To run the application:
```
java -jar target/demo-0.0.2-SNAPSHOT-all.jar
```
go to `localhost:8080` in your browser to see it running

3. To connect and configure your application to the EventStreams instance, do the following:

    In `src/main/resources/kafka.properties`, update the following properties:
    1. `bootstrap.servers` - the bootstrap server address
        - This can be found in the `External` section under `Cluster connection` in your cluster's UI
    2. `ssl.truststore.location` - a path to your Java KeyStore file
    3. `ssl.truststore.password` - a password you have chosen during generation of the certificate

When the above steps have been completed, you will find an entry for Event Streams under "Operator Hub" in the OpenShift Console.
This can be used to deploy an instance of the operator and create new Event Streams instances.

## Logging into the UI and CLI
To determine the username and password for the UI run the following:
```bash
oc -n ibm-common-services get secret platform-auth-idp-credentials -ojsonpath=‘{.data.admin_username}’ | base64 -d
oc -n ibm-common-services get secret platform-auth-idp-credentials -ojsonpath=‘{.data.admin_password}’ | base64 -d
```

To determine the endpoint to use for `cloudctl` run the command:
```bash
oc get routes -n ibm-common-services
```
Use the `cp-console` route with `https` as the protocol and `443` as the port. The console can also be used to get a new version of the CLI.
Go to the console in a browser and click in the top right and select `Configure client`, then choose `Get CLI tools`.

## Deploying Event Streams Custom Resources
For an OpenShift user *without* the administrator role, a cluster administrator will need to do the following steps:
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
