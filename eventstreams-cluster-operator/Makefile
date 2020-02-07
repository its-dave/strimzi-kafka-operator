PROJECT_NAME=eventstreams-cluster-operator
export MVN_ARGS := ${MVN_ARGS} -DskipITs

docker_build: java_build

docker_tag:

docker_push:

all: docker_build docker_push
clean: java_clean

include ../Makefile.maven

.PHONY: build clean release



# .PHONY: all build build_mvn build_docker generate_crd generate_helm local roll generate_olm
#
# all: build spotbugs generate_crd generate_helm
#
# build: build_mvn build_docker
#
# build_mvn:
# 	mvn package -s ./settings.xml ${MVN_ARGS} -Dcheckstyle.skip -Dmdep.analyze.skip
# 	mvn spotbugs:spotbugs spotbugs:check

# build_docker:
# 	docker build -t hyc-qp-stable-docker-local.artifactory.swg-devops.com/eventstreams-operator-icp-linux-amd64:latest -f Dockerfile.ubi .

# generate_crd:
# 	mvn exec:exec && echo "success!" || echo "failure!"
#
# generate_helm:
# 	$(MAKE) -C install
#
# generate_olm:
# 	$(MAKE) -C deploy verify
#
# roll:
# 	kubectl delete $(shell kubectl get pods -n myproject -o name | grep "eventstreams-operator")
#
# spotbugs:
# 	mvn compile spotbugs:spotbugs spotbugs:check
