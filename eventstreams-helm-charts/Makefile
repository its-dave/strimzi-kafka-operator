PROJECT_NAME=eventstreams-helm-charts

include ../Makefile.os

CHART_SEMANTIC_RELEASE_VERSION ?= 2020.1.1
CHART_NAME=ibm-eventstreams-operator
CHART_PATH=./$(CHART_NAME)
TARGET_FOLDER=./target
CHART_RENDERED_TEMPLATES=$(TARGET_FOLDER)/$(CHART_NAME)
CHART_RENDERED_TEMPLATES_TMP=$(TARGET_FOLDER)/pre-templated-charts/$(CHART_NAME)
CHART_RENDERED_TEMPLATES_INSTALL=../install/$(CHART_NAME)/
STRIMZI_CHART_NAME=strimzi-kafka-operator
STRIMZI_CHART_PATH=../helm-charts/$(STRIMZI_CHART_NAME)


helm_clean:
	rm -rf target

helm_lint:
	# Linting Helm Chart
	helm lint --debug $(CHART_RENDERED_TEMPLATES_TMP)

helm_merge: helm_clean
	# edit script to point to eventstreams-kafka-versions
	$(SED) -E -i 's+(VERSIONS_FILE=")(.*)(kafka-versions.yaml")+\1\2eventstreams-\3+g' ../tools/kafka-versions-tools.sh

	mkdir -p $(CHART_RENDERED_TEMPLATES_TMP)

	# copy over charts
	$(CP) -r $(CHART_PATH)/* $(CHART_RENDERED_TEMPLATES_TMP)
	$(CP) -r $(STRIMZI_CHART_PATH)/templates $(CHART_RENDERED_TEMPLATES_TMP)

	# edit files to template out eventstreams instead of strimzi
	$(SED) -E -i 's+strimzi+eventstreams+g' $(CHART_RENDERED_TEMPLATES_TMP)/templates/_helpers.tpl
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP)/templates -type f -name '*.yaml' -exec $(SED) -E -i 's+\{\{ template "strimzi.name"+\{\{ template "eventstreams.name"+g' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP)/templates -type f -name '*.yaml' -exec $(SED) -E -i 's+\{\{ template "strimzi.chart"+\{\{ template "eventstreams.chart"+g' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP)/templates -type f -name '020-ClusterRole-strimzi-cluster-operator-role.yaml' -exec $(SED) -E -i '$$i \{\{ template "eventstreams.clusterrole" \}\}' {} \;


	# run kafka-version-tpl.sh in strimzi helm charts repo to generate kafka versions template
	$(STRIMZI_CHART_PATH)/../kafka-version-tpl.sh $(CHART_RENDERED_TEMPLATES_TMP)/templates/_kafka_image_map.tpl

	# clean up edit to kafka-versions-tools.sh script
	$(SED) -E -i 's+(VERSIONS_FILE=")(.*)(eventstreams-)(kafka-versions.yaml")+\1\2\4+g' ../tools/kafka-versions-tools.sh

	# delete strimzi deployment and minor edits to files
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP)/templates -type f -name '050-Deployment-strimzi-cluster-operator.yaml' -exec rm {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP)/templates -type f -name '*.yaml' -exec $(SED) -E -i 's+(name: )(strimzi)(.*)+\1eventstreams\3+g' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES_TMP)/templates -type f -name '*.yaml' -exec $(SED) -E -i 's+kafka.strimzi.io+eventstreams.ibm.com+g' {} \;

helm_template: helm_merge helm_lint

	helm template --namespace myproject --debug --output-dir $(TARGET_FOLDER) $(CHART_RENDERED_TEMPLATES_TMP)


helm_install: helm_template

	# Remove Helm-related labels
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '*.yaml' -exec $(SED) -i '/^---/d' {} \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '*.yaml' -exec yq d -i {} metadata.labels.chart \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '*.yaml' -exec yq d -i {} metadata.labels.component \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '*.yaml' -exec yq d -i {} metadata.labels.release \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '*.yaml' -exec yq d -i {} metadata.labels.heritage \;
	$(FIND) $(CHART_RENDERED_TEMPLATES)/templates -type f -name '*.yaml' -exec yq d -i {} metadata.namespace \;

	mkdir -p $(CHART_RENDERED_TEMPLATES_INSTALL)

	$(CP) -R $(CHART_RENDERED_TEMPLATES)/templates/* $(CHART_RENDERED_TEMPLATES_INSTALL)

helm_pkg: helm_install
	helm package --version $(CHART_SEMANTIC_RELEASE_VERSION) --app-version $(CHART_SEMANTIC_RELEASE_VERSION) --destination ../ $(CHART_RENDERED_TEMPLATES_TMP)

docker_build: helm_pkg
docker_tag:
docker_push:

all: docker_build
clean: helm_clean

.PHONY: build clean release spotbugs

