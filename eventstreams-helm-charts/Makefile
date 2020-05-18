PROJECT_NAME=eventstreams-helm-charts

include ../Makefile.os

CHART_SEMANTIC_RELEASE_VERSION ?= 2020.2.1

EVENTSTREAMS_CHART_NAME=ibm-eventstreams-operator
EVENTSTREAMS_CHART_PATH=./$(EVENTSTREAMS_CHART_NAME)
STRIMZI_CHART_NAME=strimzi-kafka-operator
STRIMZI_CHART_PATH=../helm-charts/$(STRIMZI_CHART_NAME)

TARGET_FOLDER=./target
CHART_RENDERED_TEMPLATES=$(TARGET_FOLDER)/$(EVENTSTREAMS_CHART_NAME)
CHART_RENDERED_TEMPLATES_TMP_DIR=$(TARGET_FOLDER)/pre-templated-charts/$(EVENTSTREAMS_CHART_NAME)
CHART_RENDERED_TEMPLATES_INSTALL=../install/$(EVENTSTREAMS_CHART_NAME)/


.PHONY: helm_clean
helm_clean:
	rm -rf target


# Lint Helm Charts for errors
.PHONY: helm_lint
helm_lint:
	helm lint --debug $(CHART_RENDERED_TEMPLATES_TMP_DIR)


# Merge the EventStreams and the Strimzi OpenSource charts
.PHONY: helm_merge
helm_merge: helm_clean
	mkdir -p $(CHART_RENDERED_TEMPLATES_TMP_DIR)

	# Copy over charts into the TMP DIR
	$(CP) -r $(EVENTSTREAMS_CHART_PATH)/* $(CHART_RENDERED_TEMPLATES_TMP_DIR)
	$(CP) -r $(STRIMZI_CHART_PATH)/templates $(CHART_RENDERED_TEMPLATES_TMP_DIR)

	# edit files to template out Strimzi reference and replace with EventStreams
	$(SED) -E -i 's+strimzi+eventstreams+g' $(CHART_RENDERED_TEMPLATES_TMP_DIR)/templates/_helpers.tpl
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP_DIR)/templates -type f -name '*.yaml' -exec $(SED) -E -i 's+\{\{ template "strimzi.name"+\{\{ template "eventstreams.name"+g' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP_DIR)/templates -type f -name '*.yaml' -exec $(SED) -E -i 's+\{\{ template "strimzi.chart"+\{\{ template "eventstreams.chart"+g' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP_DIR)/templates -type f -name '020-ClusterRole-strimzi-cluster-operator-role.yaml' -exec $(SED) -E -i '$$i \{\{ template "eventstreams.clusterrole.namespaced" \}\}' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP_DIR)/templates -type f -name '021-ClusterRole-strimzi-cluster-operator-role.yaml' -exec $(SED) -E -i '$$i \{\{ template "eventstreams.clusterrole.global" \}\}' {} \;


	# run kafka-version-tpl.sh in strimzi helm charts repo to generate kafka versions template
	$(STRIMZI_CHART_PATH)/../kafka-version-tpl.sh $(CHART_RENDERED_TEMPLATES_TMP_DIR)/templates/_kafka_image_map.tpl
	$(SED) -E -i 's/STRIMZI/EVENTSTREAMS/g' $(CHART_RENDERED_TEMPLATES_TMP_DIR)/templates/_kafka_image_map.tpl

	# delete strimzi deployment and minor edits to files
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP_DIR)/templates -type f -name '050-Deployment-strimzi-cluster-operator.yaml' -exec rm {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP_DIR)/templates -type f -name '*.yaml' -exec $(SED) -E -i 's+(name: )(strimzi)(.*)+\1eventstreams\3+g' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP_DIR)/templates -type f -name '*.yaml' -exec $(SED) -E -i 's+kafka.strimzi.io+eventstreams.ibm.com+g' {} \;


.PHONY: helm_template
helm_template: helm_merge helm_lint
	helm template --namespace myproject --debug --output-dir $(TARGET_FOLDER) $(CHART_RENDERED_TEMPLATES_TMP_DIR)


.PHONY: helm_install
helm_install: helm_template
	# Remove Helm-related labels
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '*.yaml' -exec $(SED) -i '/^---/d' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '*.yaml' -exec $(SED) -i '/^# Source:/d' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '*.yaml' -exec yq d -i {} metadata.labels.chart \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '*.yaml' -exec yq d -i {} metadata.labels.component \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '*.yaml' -exec yq d -i {} metadata.labels.release \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '*.yaml' -exec yq d -i {} metadata.labels.heritage \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '*.yaml' -exec yq d -i {} metadata.namespace \;

     # Add required metadata labels
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '031-RoleBinding-strimzi-cluster-operator-entity-operator-delegation.yaml' -exec yq w -i {} 'metadata.labels.[app.kubernetes.io/instance]' eventstreams-operator \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '031-RoleBinding-strimzi-cluster-operator-entity-operator-delegation.yaml' -exec yq w -i {} 'metadata.labels.[app.kubernetes.io/managed-by]' olm \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '031-RoleBinding-strimzi-cluster-operator-entity-operator-delegation.yaml' -exec yq w -i {} 'metadata.labels.[app.kubernetes.io/name]' eventstreams-operator \;

	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '032-RoleBinding-strimzi-cluster-operator-topic-operator-delegation.yaml' -exec yq w -i {} 'metadata.labels.[app.kubernetes.io/instance]' eventstreams-operator \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '032-RoleBinding-strimzi-cluster-operator-topic-operator-delegation.yaml' -exec yq w -i {} 'metadata.labels.[app.kubernetes.io/managed-by]' olm \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '032-RoleBinding-strimzi-cluster-operator-topic-operator-delegation.yaml' -exec yq w -i {} 'metadata.labels.[app.kubernetes.io/name]' eventstreams-operator \;

	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '020-RoleBinding-strimzi-cluster-operator.yaml' -exec yq w -i {} 'metadata.labels.[app.kubernetes.io/instance]' eventstreams-operator \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '020-RoleBinding-strimzi-cluster-operator.yaml' -exec yq w -i {} 'metadata.labels.[app.kubernetes.io/managed-by]' olm \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '020-RoleBinding-strimzi-cluster-operator.yaml' -exec yq w -i {} 'metadata.labels.[app.kubernetes.io/name]' eventstreams-operator \;

	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '010-ServiceAccount-strimzi-cluster-operator.yaml' -exec yq w -i {} 'metadata.labels.[app.kubernetes.io/instance]' eventstreams-operator \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '010-ServiceAccount-strimzi-cluster-operator.yaml' -exec yq w -i {} 'metadata.labels.[app.kubernetes.io/managed-by]' olm \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '010-ServiceAccount-strimzi-cluster-operator.yaml' -exec yq w -i {} 'metadata.labels.[app.kubernetes.io/name]' eventstreams-operator \;

	mkdir -p $(CHART_RENDERED_TEMPLATES_INSTALL)

	$(CP) -R $(CHART_RENDERED_TEMPLATES)/templates/* $(CHART_RENDERED_TEMPLATES_INSTALL)


.PHONY: helm_pkg
helm_pkg: helm_install
	helm package --version $(CHART_SEMANTIC_RELEASE_VERSION) --app-version $(CHART_SEMANTIC_RELEASE_VERSION) --destination ../ $(CHART_RENDERED_TEMPLATES_TMP_DIR)


.PHONY: docker_build docker_tag docker_push all clean

docker_build: helm_pkg
docker_tag:
docker_push:

all: docker_build
clean: helm_clean

