#!/usr/bin/env bash
# Clone WATCHED_NAMESPACE as the EVENTSTREAMS_NAMESPACE env var
export EVENTSTREAMS_NAMESPACE="${WATCHED_NAMESPACE}"
# Search for all EVENTSTREAMS environmental variables, clone them as STRIMZI env vars and export
eval "$(env | grep '^EVENTSTREAMS' | sed 's/^EVENTSTREAMS/export STRIMZI/g')"

export JAVA_CLASSPATH=lib/com.ibm.eventstreams.@project.build.finalName@.@project.packaging@:@project.dist.classpath@
export JAVA_MAIN=com.ibm.eventstreams.Main
exec "${STRIMZI_HOME}/bin/launch_java.sh"