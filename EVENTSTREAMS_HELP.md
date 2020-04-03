# Event Streams Help

The purpose of this file is to document the fixes to various problems encountered when building the operator.
Just add a new section when a new bug is found.

## Random Java class not found

The upstream source code for Strimzi Kafka Operator is updated regularly, this means if you're building the operator for
the first time in a few days it is recommended you do a full clean build by running the command:
```
make clean eventstreams_build
```
This rebuilds all the underlying Strimzi dependencies, unlike the `make eventstreams_operator_build` command

## Stunnel Compilation issues

If for any reason a `Dockerfile` fails to build on a run with a `c` compilation error, it is quite likely that the base
image has fallen out of date locally.
To re-pull this image run:
```
docker pull hyc-qp-stable-docker-local.artifactory.swg-devops.com/openjdk-8-jre-ubi7-icp-linux-amd64:latest
```

## eventstreamses not found

If you see errors like this in response to `oc` commands, the likely cause is that you're using an old version of `oc`.

```
the server could not find the requested resource (post eventstreamses.eventstreams.ibm.com)
```
