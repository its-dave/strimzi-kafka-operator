#!/usr/bin/env bash
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

# Clone WATCHED_NAMESPACE as the EVENTSTREAMS_NAMESPACE env var
export EVENTSTREAMS_NAMESPACE="${WATCHED_NAMESPACE}"
# Search for all EVENTSTREAMS environmental variables, clone them as STRIMZI env vars and export
# Quote envars to avoid export errors
eval "$(env | grep '^EVENTSTREAMS' | sed 's/^EVENTSTREAMS/export STRIMZI/g' | sed 's/=/="/1; s/$/"/1')"

export JAVA_CLASSPATH=lib/com.ibm.eventstreams.@project.build.finalName@.@project.packaging@:@project.dist.classpath@
export JAVA_MAIN=com.ibm.eventstreams.Main
exec "${STRIMZI_HOME}/bin/launch_java.sh"