#!/bin/bash
# This script contains functions used to build Event Streams, it is a separate file such that it can be sourced so values do not need to be hard-coded in qp-jenkins-jobs

get_yq() {
  echo "Attempting to curl yq..."
  sudo curl -Lvo /usr/local/bin/yq "https://github.com/mikefarah/yq/releases/download/3.2.1/yq_linux_${B_ARCH:?required}"
  sudo chmod +x /usr/local/bin/yq
}

get_kafka_versions() {
  yq r ./eventstreams-kafka-versions.yaml [*].version
}
