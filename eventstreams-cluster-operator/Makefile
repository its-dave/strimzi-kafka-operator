PROJECT_NAME=eventstreams-cluster-operator
export MVN_ARGS := ${MVN_ARGS} -DskipITs -s settings.xml -Dmaven.javadoc.skip=true

docker_build: java_build generate_crd

docker_tag:

docker_push:

all: docker_build docker_push
clean: java_clean

include ../Makefile.maven

.PHONY: build clean release

generate_crd:
	mvn exec:exec && echo "success!" || echo "check for expected errors"

# spotbugs:
# 	mvn compile spotbugs:spotbugs spotbugs:check
