OPERATOR_NAME := ibm-eventstreams
CSV_VERSION = 2.0.0
CHANNELS = "v2.0"
DEFAULT_CHANNEL = v2.0

INSTALL_DIR := ../install/ibm-eventstreams-operator

WORKING_DIR := $(shell pwd)
BUILD_DIR := $(WORKING_DIR)/../build
TEMP_DIR := $(WORKING_DIR)/temp

CRD_DIR := $(WORKING_DIR)/crds
EVENTSTREAMS_CRD_FILE := $(CRD_DIR)/eventstreams.ibm.com_eventstreams_crd.yaml
EVENTSTREAMS_GEOREP_CRD_FILE := $(CRD_DIR)/eventstreams.ibm.com_georeplicator_crd.yaml
KAFKA_CRD_FILE := $(CRD_DIR)/eventstreams.ibm.com_kafka_crd.yaml
KAFKA_CONNECT_CRD_FILE := $(CRD_DIR)/eventstreams.ibm.com_kafkaconnect_crd.yaml
KAFKA_CONNECT_S2I_CRD_FILE := $(CRD_DIR)/eventstreams.ibm.com_kafkaconnects2i_crd.yaml
KAFKA_TOPIC_CRD_FILE := $(CRD_DIR)/eventstreams.ibm.com_kafkatopic_crd.yaml
KAFKA_USER_CRD_FILE := $(CRD_DIR)/eventstreams.ibm.com_kafkauser_crd.yaml
KAFKA_MIRROR_MAKER_CRD_FILE := $(CRD_DIR)/eventstreams.ibm.com_kafkamirrormaker_crd.yaml
KAFKA_BRIDGE_CRD_FILE := $(CRD_DIR)/eventstreams.ibm.com_kafkabridge_crd.yaml
KAFKA_CONNECTOR_CRD_FILE := $(CRD_DIR)/eventstreams.ibm.com_kafkaconnector_crd.yaml
KAFKA_MIRROR_MAKER_2_CRD_FILE := $(CRD_DIR)/eventstreams.ibm.com_kafkamirrormaker2_crd.yaml
KAFKA_REBALANCE_CRD_FILE := $(CRD_DIR)/eventstreams.ibm.com_kafkarebalance_crd.yaml

BUNDLE_DIR = $(WORKING_DIR)/olm-catalog/$(OPERATOR_NAME)/$(CSV_VERSION)
GENERATED_CSV := $(BUNDLE_DIR)/$(OPERATOR_NAME).v$(CSV_VERSION).clusterserviceversion.yaml

OPERATOR_FILE := operator.yaml
ROLE_FILE := role.yaml
ROLE_BINDING_FILE := role_binding.yaml
SERVICE_ACCOUNT_FILE := service_account.yaml

DATE := $(shell date "+%Y-%m-%dT%H:%M:%SZ")
IMAGE_TAG := latest
IMAGE_BASE_NAME := local-operator-registry
IMAGE_NAME := $(IMAGE_BASE_NAME):$(IMAGE_TAG)
REMOTE_REGISTRY := $(shell oc registry info)
MARKETPLACE_NAMESPACE := openshift-marketplace
REPO_ADDR := $(REMOTE_REGISTRY)/$(MARKETPLACE_NAMESPACE)/
B_OS ?= macos

LOCAL_REPO_PORT := 5000
LOCAL_REPO_ADDR := localhost:$(LOCAL_REPO_PORT)/
BUNDLE_IMAGE_NAME := olm-bundle:latest

include ../Makefile.os
.PHONY: all clean build_csv deploy delete_deploy verify docker_build docker_push

all: build_csv deploy

verify: build_csv docker_build

clean:
	rm -rf $(BUILD_DIR)
	rm -rf $(CRD_DIR)
	mkdir -p $(CRD_DIR)
	mkdir -p $(BUILD_DIR)
	touch $(BUILD_DIR)/Dockerfile
	rm -f $(OPERATOR_FILE)
	rm -f $(ROLE_FILE)
	rm -f $(ROLE_BINDING_FILE)
	rm -f $(SERVICE_ACCOUNT_FILE)

get_opm:
	# utility function for pulling patched version of OPM for build
	curl -o opm -u $(ARTIFACTORY_USERNAME):$(ARTIFACTORY_PASSWORD) https://eu.artifactory.swg-devops.com:443/artifactory/hyc-qp-artifacts-generic-local/opm-$(B_OS)
	chmod 755 opm
	sudo mv opm /usr/local/bin

start_local_registry:
	if [[ "$(shell docker ps --filter name=registry -q)" == "" ]]; then \
		docker run -d -p $(LOCAL_REPO_PORT):$(LOCAL_REPO_PORT) --restart=always --name registry registry:2; \
	fi;

run_scorecard: build_csv

	$(SED) -i s/"^kind: RoleBinding$$"/"kind: ClusterRoleBinding"/g $(ROLE_BINDING_FILE)
	yq w -i $(OPERATOR_FILE) spec.template.spec.serviceAccountName eventstreams-cluster-operator
	$(FIND) ../examples/eventstreams -type f -name '01-*.yaml' -exec yq w -i {} spec.license.accept true \;
	cd .. && operator-sdk scorecard --verbose --config deploy/.osdk-scorecard.yaml

	# cleanup
	$(FIND) ../examples/eventstreams -type f -name '01-*.yaml' -exec yq w -i {} spec.license.accept false \;


.PHONY: .EXPORT_ALL_VARIABLES
build_csv: .EXPORT_ALL_VARIABLES clean
	./build_csv.sh

build_bundle: build_csv start_local_registry
	echo "Generating bundle & image"
	cd .. && opm alpha bundle build --tag $(BUNDLE_IMAGE_NAME) --package $(OPERATOR_NAME) --directory deploy/olm-catalog/$(OPERATOR_NAME)/$(CSV_VERSION) --channels $(CHANNELS) --default $(DEFAULT_CHANNEL)
	docker tag $(BUNDLE_IMAGE_NAME) $(LOCAL_REPO_ADDR)$(BUNDLE_IMAGE_NAME)
	docker push $(LOCAL_REPO_ADDR)$(BUNDLE_IMAGE_NAME)
	opm alpha bundle validate --tag $(LOCAL_REPO_ADDR)$(BUNDLE_IMAGE_NAME) --image-builder docker
	cd .. && opm index add --bundles $(LOCAL_REPO_ADDR)$(BUNDLE_IMAGE_NAME) --container-tool docker --tag $(IMAGE_NAME)

docker_build: build_csv

docker_push:
	oc registry login --skip-check
	docker login -u any -p $(shell oc whoami -t) $(REMOTE_REGISTRY)
	docker tag $(IMAGE_NAME) $(REPO_ADDR)$(IMAGE_NAME)
	docker push $(REPO_ADDR)$(IMAGE_NAME)
	oc set image-lookup $(IMAGE_BASE_NAME) -n $(MARKETPLACE_NAMESPACE)

deploy: build_bundle docker_push
	$(SED) s/@image@/$(IMAGE_NAME)/ custom-catalog-source.yaml.template | oc apply -f -

delete_deploy:
	$(SED) s/@image@/dummy/ custom-catalog-source.yaml.template | oc delete -f -
