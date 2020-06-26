PROJECT_NAME=examples

include ../Makefile.os

RELEASE_VERSION ?= latest
RELEASE_PATH ?= ../strimzi-$(RELEASE_VERSION)/$(PROJECT_NAME)

release:
	mkdir -p $(RELEASE_PATH)
	$(CP) -r $$(ls | $(GREP) -Ev '^Makefile$$') $(RELEASE_PATH)/

dashboard_cr:
	./convert-json-monitoring-dasbhoard-cr.sh

.PHONY: all build clean docker_build docker_push docker_tag spotbugs
