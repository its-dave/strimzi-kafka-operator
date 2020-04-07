#!/bin/bash
# This function outputs the latest supported kafka versions, its purpose is so these versions do not need to be hard-coded in qp-jenkins-jobs
function get_kafka_versions() {
  yq r ./eventstreams-kafka-versions.yaml [*].version
}
