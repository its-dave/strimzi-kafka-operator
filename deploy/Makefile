PROJECT_NAME=deploy

CSV_VERSION = 2020.2.1

# TODO verfy this is still true
OPERATOR_NAME := ibm-eventstreams-operator
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

BUNDLE_DIR = $(WORKING_DIR)/olm-catalog/$(OPERATOR_NAME)/$(CSV_VERSION)
GENERATED_CSV := $(BUNDLE_DIR)/$(OPERATOR_NAME).v$(CSV_VERSION).clusterserviceversion.yaml
OPERATOR_FILE := $(TEMP_DIR)/operator.yaml
ROLE_FILE := $(TEMP_DIR)/role.yaml
SCORECARD_OPERATOR_FILE := $(WORKING_DIR)/operator.yaml
SCORECARD_ROLE_FILE := $(WORKING_DIR)/role.yaml
SCORECARD_ROLE_BINDING_FILE := $(WORKING_DIR)/role_binding.yaml
SCORECARD_SERVICE_ACCOUNT_FILE := $(WORKING_DIR)/service_account.yaml

DATE := $(shell date "+%Y-%m-%dT%H:%M:%SZ")
IMAGE_TAG := $(shell date "+%Y-%m-%d-%H.%M.%S")
IMAGE_BASE_NAME := local-operator-registry
IMAGE_NAME := $(IMAGE_BASE_NAME):$(IMAGE_TAG)
REMOTE_REGISTRY := $(shell oc registry info)
MARKETPLACE_NAMESPACE := openshift-marketplace
REPO_ADDR := $(REMOTE_REGISTRY)/${MARKETPLACE_NAMESPACE}/

include ../Makefile.os
.PHONY: all clean build_csv deploy delete_deploy verify docker_build docker_push

all: build_csv deploy

verify: build_csv docker_build

clean_crds:
	rm -rf ${CRD_DIR};

clean: clean_crds
	rm -rf $(TEMP_DIR)
	rm -rf $(BUILD_DIR)
	mkdir -p $(TEMP_DIR)
	mkdir -p $(CRD_DIR)
	mkdir -p $(BUILD_DIR)
	touch $(BUILD_DIR)/Dockerfile

build_scorecard:
	if [ -d "${CRD_DIR}" ]; then \
		rm -rf ${CRD_DIR}; \
	fi

	mkdir ${CRD_DIR}

	# populate the crds folder with the contents of the install folder
	$(CP) ${INSTALL_DIR}/010-ServiceAccount-strimzi-cluster-operator.yaml ${CRD_DIR}
	$(CP) ${INSTALL_DIR}/020-ClusterRole-strimzi-cluster-operator-role.yaml ${CRD_DIR}
	$(CP) ${INSTALL_DIR}/020-RoleBinding-strimzi-cluster-operator.yaml ${CRD_DIR}
	$(CP) ${INSTALL_DIR}/021-ClusterRole-strimzi-cluster-operator-role.yaml ${CRD_DIR}
	$(CP) ${INSTALL_DIR}/021-ClusterRoleBinding-strimzi-cluster-operator.yaml ${CRD_DIR}
	$(CP) ${INSTALL_DIR}/030-ClusterRole-strimzi-kafka-broker.yaml ${CRD_DIR}
	$(CP) ${INSTALL_DIR}/030-ClusterRoleBinding-strimzi-cluster-operator-kafka-broker-delegation.yaml ${CRD_DIR}
	$(CP) ${INSTALL_DIR}/031-ClusterRole-strimzi-entity-operator.yaml ${CRD_DIR}
	$(CP) ${INSTALL_DIR}/032-ClusterRole-strimzi-topic-operator.yaml ${CRD_DIR}
	$(CP) ${INSTALL_DIR}/032-RoleBinding-strimzi-cluster-operator-topic-operator-delegation.yaml ${CRD_DIR}
	$(CP) ${INSTALL_DIR}/040-Crd-kafka.yaml $(CRD_DIR)
	$(CP) ${INSTALL_DIR}/041-Crd-kafkaconnect.yaml $(CRD_DIR)
	$(CP) ${INSTALL_DIR}/042-Crd-kafkaconnects2i.yaml $(CRD_DIR)
	$(CP) ${INSTALL_DIR}/043-Crd-kafkatopic.yaml $(CRD_DIR)
	$(CP) ${INSTALL_DIR}/044-Crd-kafkauser.yaml $(CRD_DIR)
	# ignoring Mirror Maker and Bridge CRDs as we don't use them in Event Streams
	# $(CP) ${INSTALL_DIR}/045-Crd-kafkamirrormaker.yaml $(CRD_DIR)
	# $(CP) ${INSTALL_DIR}/046-Crd-kafkabridge.yaml $(CRD_DIR)
	$(CP) ${INSTALL_DIR}/047-Crd-kafkaconnector.yaml $(CRD_DIR)
	$(CP) ${INSTALL_DIR}/048-Crd-kafkamirrormaker2.yaml $(CRD_DIR)
	$(CP) ${INSTALL_DIR}/100-SecurityContextConstraints-eventstreams.yaml ${CRD_DIR}
	$(CP) ${INSTALL_DIR}/122-ClusterRole-eventstreams-ui.yaml ${CRD_DIR}
	$(CP) ${INSTALL_DIR}/123-ClusterRole-eventstreams-admin.yaml ${CRD_DIR}
	$(CP) ${INSTALL_DIR}/140-Crd-eventstreams.yaml $(CRD_DIR)
	$(CP) ${INSTALL_DIR}/143-Crd-eventstreamsreplicator.yaml $(CRD_DIR)

	# mandatory files in order for scorecard to work
	$(CP) ${INSTALL_DIR}/020-ClusterRole-strimzi-cluster-operator-role.yaml $(SCORECARD_ROLE_FILE)
	$(CP) ${INSTALL_DIR}/150-Deployment-eventstreams-cluster-operator.yaml $(SCORECARD_OPERATOR_FILE)
	# keep the roleRef as namespaced, but change the kind to ClusterRoleBinding in order for the Scorecard to work
	${SED} s/"RoleBinding.*"/"ClusterRoleBinding"/g ${CRD_DIR}/020-RoleBinding-strimzi-cluster-operator.yaml > ${SCORECARD_ROLE_BINDING_FILE}
	$(CP) ${INSTALL_DIR}/010-ServiceAccount-strimzi-cluster-operator.yaml $(SCORECARD_SERVICE_ACCOUNT_FILE)

	# prepare the operator.yaml file in order to accept the scorecard-proxy container and run the scorecard tests
	# run with a script in order to avoid the complaints from the Makefile regarding the indentation since yq is very strict on that
	${WORKING_DIR}/scorecard-preparation.sh

build_csv: clean
	$(CP) ${INSTALL_DIR}/040-Crd-kafka.yaml $(KAFKA_CRD_FILE)
	$(CP) ${INSTALL_DIR}/041-Crd-kafkaconnect.yaml $(KAFKA_CONNECT_CRD_FILE)
	$(CP) ${INSTALL_DIR}/042-Crd-kafkaconnects2i.yaml $(KAFKA_CONNECT_S2I_CRD_FILE)
	$(CP) ${INSTALL_DIR}/043-Crd-kafkatopic.yaml $(KAFKA_TOPIC_CRD_FILE)
	$(CP) ${INSTALL_DIR}/044-Crd-kafkauser.yaml $(KAFKA_USER_CRD_FILE)
	# ignoring Mirror Maker and Bridge CRDs as we don't use them in Event Streams
	# $(CP) ${INSTALL_DIR}/045-Crd-kafkamirrormaker.yaml $(KAFKA_MIRROR_MAKER_CRD_FILE)
	# $(CP) ${INSTALL_DIR}/046-Crd-kafkabridge.yaml $(KAFKA_BRIDGE_CRD_FILE)
	$(CP) ${INSTALL_DIR}/047-Crd-kafkaconnector.yaml $(KAFKA_CONNECTOR_CRD_FILE)
	$(CP) ${INSTALL_DIR}/048-Crd-kafkamirrormaker2.yaml $(KAFKA_MIRROR_MAKER_2_CRD_FILE)
	$(CP) ${INSTALL_DIR}/140-Crd-eventstreams.yaml $(EVENTSTREAMS_CRD_FILE)
	$(CP) ${INSTALL_DIR}/143-Crd-eventstreamsreplicator.yaml $(EVENTSTREAMS_GEOREP_CRD_FILE)
	yq w -i $(EVENTSTREAMS_CRD_FILE) spec.version v1beta1
	yq w -i $(EVENTSTREAMS_GEOREP_CRD_FILE) spec.version v1beta1
	$(CP) ../examples/eventstreams/*.yaml $(CRD_DIR)
	$(eval LAST_CREATED_DATE:=$(shell yq r $(GENERATED_CSV) metadata.annotations.createdAt))
	$(CP) ${INSTALL_DIR}/020-ClusterRole-strimzi-cluster-operator-role.yaml $(ROLE_FILE)
	$(CP) ${INSTALL_DIR}/150-Deployment-eventstreams-cluster-operator.yaml $(OPERATOR_FILE)
	yq r $(ROLE_FILE) metadata.name | xargs yq w -i $(OPERATOR_FILE) spec.template.spec.serviceAccountName
	$(SED) -i "s/metadata.namespace/metadata.annotations['olm.targetNamespaces']/" $(OPERATOR_FILE)
	$(SED) -i "0,/metadata.annotations\['olm.targetNamespaces'\]/{s/metadata.annotations\['olm.targetNamespaces'\]/metadata.namespace/}" $(OPERATOR_FILE)

	cd .. && operator-sdk generate csv --csv-version $(CSV_VERSION) --operator-name $(OPERATOR_NAME) --update-crds
	rm -rf $(BUILD_DIR)

	yq m -ix $(GENERATED_CSV) csv_manual_fields.yaml
	yq w -i $(GENERATED_CSV) metadata.annotations.createdAt $(DATE)

	$(CP) ${INSTALL_DIR}/031-ClusterRole-strimzi-entity-operator.yaml $(BUNDLE_DIR)/entityoperator.clusterrole.yaml
	$(CP) ${INSTALL_DIR}/122-ClusterRole-eventstreams-ui.yaml $(BUNDLE_DIR)/adminui.clusterrole.yaml
	$(CP) ${INSTALL_DIR}/123-ClusterRole-eventstreams-admin.yaml $(BUNDLE_DIR)/adminapi.clusterrole.yaml
	operator-courier verify olm-catalog/$(OPERATOR_NAME)

ifdef TRAVIS
	# In travis reset the created date so we can perform a git status --porcelain
	yq w -i $(GENERATED_CSV) metadata.annotations.createdAt $(LAST_CREATED_DATE)
endif

docker_build: build_csv
	docker build -t $(IMAGE_NAME) -f ${IMAGE_BASE_NAME}.Dockerfile .

docker_push:
	oc registry login --skip-check
	docker login -u any -p $(shell oc whoami -t) $(REMOTE_REGISTRY)
	docker tag $(IMAGE_NAME) $(REPO_ADDR)$(IMAGE_NAME)
	docker push $(REPO_ADDR)$(IMAGE_NAME)
	oc set image-lookup $(IMAGE_BASE_NAME) -n ${MARKETPLACE_NAMESPACE}

deploy: docker_build docker_push
	$(SED) s/@image@/$(IMAGE_NAME)/ local-operator-registry-deploy.yaml.template | oc apply -f -

delete_deploy:
	$(SED) s/@image@/dummy/ local-operator-registry-deploy.yaml.template | oc delete -f -
