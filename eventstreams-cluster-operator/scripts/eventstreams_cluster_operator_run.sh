#!/usr/bin/env bash
export JAVA_CLASSPATH=lib/com.ibm.eventstreams.@project.build.finalName@.@project.packaging@:@project.dist.classpath@
export JAVA_MAIN=com.ibm.eventstreams.Main
exec ${STRIMZI_HOME}/bin/launch_java.sh