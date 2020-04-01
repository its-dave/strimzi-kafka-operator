PROJECT_NAME=deploy

CSV_VERSION = 2020.2.1

WORKING_DIR := $(shell pwd)
BASE_DIR := $(WORKING_DIR)/..
OPERATOR_NAME := ibm-eventstreams-operator
CASE_BUNDLE_NAME := ibm-eventstreams-case-bundle
INVENTORY_DIR := stable/$(CASE_BUNDLE_NAME)/case/$(OPERATOR_NAME)/inventory
INVENTORY_FILES_TO_BASE := ../../../../../../../..



include ../Makefile.os
.PHONY: all clean build_csv deploy delete_deploy verify docker_build docker_push

all:

verify:

clean:
	rm -rf $(INVENTORY_DIR)/*/files/*
	yq w -i $(INVENTORY_DIR)/ibmEventstreamsExamples/resources.yaml resources.resourceDefs.files ""
	yq w -i $(INVENTORY_DIR)/ibmEventstreamsOperatorInstall/resources.yaml resources.resourceDefs.files ""
	rm -rf $(BASE_DIR)/stable

build_case: clean
	# symlink example CR's to case
	cd $(INVENTORY_DIR)/ibmEventstreamsExamples/files ;\
	ln -sf $(INVENTORY_FILES_TO_BASE)/examples/eventstreams/* ./

	$(FIND) $(BASE_DIR)/examples/eventstreams -type f -name '*.yaml' | sort -d | xargs -n1 $(WORKING_DIR)/generateResourceFilesList.sh $(INVENTORY_DIR)/ibmEventstreamsExamples/resources.yaml

	# # symlink install files to case
	cd $(INVENTORY_DIR)/ibmEventstreamsOperatorInstall/files ;\
	ln -sf $(INVENTORY_FILES_TO_BASE)/install/ibm-eventstreams-operator/* ./

	$(FIND) $(BASE_DIR)/install/ibm-eventstreams-operator -type f -name '*.yaml' | sort -d | xargs -n1 $(WORKING_DIR)/generateResourceFilesList.sh $(INVENTORY_DIR)/ibmEventstreamsOperatorInstall/resources.yaml

	$(CP) $(BASE_DIR)/EVENTSTREAMS.md $(INVENTORY_DIR)/ibmEventstreamsOperatorInstall/README.md

prepare_stable:
	$(CP) -R stable $(BASE_DIR)/stable

docker_build: build_case
