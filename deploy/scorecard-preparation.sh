#!/usr/bin/env bash

set -e

	yq w -i -s - operator.yaml <<EOF
- command: update
  path: spec.template.spec.containers.(name==eventstreams-cluster-operator).env[+]
  value:
    name: KUBECONFIG
    value: /scorecard-secret/config
EOF
