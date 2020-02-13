## Deploying OLM bundle to your openshift environment

### (PreReq)
- yq [source repo](https://github.com/mikefarah/yq)
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

- strimzi-kafka-operator [source repo](https://github.ibm.com/mhub/strimzi-kafka-operator)
```
git clone https://github.ibm.com/mhub/strimzi-kafka-operator
```

### Log into your OpenShift cluster
1. Click your username in the top right corner, and click **Copy Login Command** to access the API token. 
2. Click **Display Token** and copy the command provided to your terminal.
3. Run the command in your terminal to log in.

### Create a new namespace 
Use a new namespace, rather than a default one. 
To create a new namespace: `oc create ns <name_of_your_namespace>`

<!-- TODO Remove with scc -->
### Update the Security Context Constraints
You need to have the `strimzi-kafka-operator` directory locally. 
The repository can be found [here](https://github.ibm.com/mhub/strimzi-kafka-operator)

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
