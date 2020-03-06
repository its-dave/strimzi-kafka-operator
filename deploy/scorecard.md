# Operator SDK Scorecard

Before you run the `OSDK Scorecard`, you can have a look [here](https://github.com/operator-framework/operator-sdk/blob/master/doc/test-framework/scorecard.md#running-the-scorecard-with-an-olm-managed-operator) in order to better understand what it is.

## How to run the `OSDK Scorecard`

To run the `OSDK Scorecard`, follow the following steps:

- `oc` or `cloudctl` login to your OpenShift machine. This is important in order for the `kubeconfig` value to be properly retrieved or updated.
- Make sure that you don't have any Event Streams Operator-related resources installed in the cluster. If you do, the scorecard will fail.
- From with the `deploy` folder, run `make build_scorecard`. This step will prepare for you all the files expected from the `operator-sdk` binary in order to properly deploy the Operator for you in the selected namespace.
- From the root folder of the repo, run `operator-sdk scorecard --verbose`. This start the test categories described in the `.osdk-scorecard.yaml` file (separated in `basic` and `olm`).

If you need to change the namespace you are using to run the scorecard, please do it in the `.osdk-scorecard.yaml` file.