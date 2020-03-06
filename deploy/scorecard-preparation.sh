#!/bin/bash

set -e

	yq w -i -s - operator.yaml <<EOF
- command: update
  path: spec.template.spec.containers.(name==eventstreams-cluster-operator).env[+]
  value:
    name: KUBECONFIG
    value: /scorecard-secret/config
EOF

	yq w -i -s - operator.yaml <<EOF
- command: update
  path: spec.template.spec.containers.(name==eventstreams-cluster-operator).volumeMounts[+]
  value:
    name: scorecard-kubeconfig
    mountPath: /scorecard-secret
EOF

	yq w -i -s - operator.yaml <<EOF
- command: update
  path: spec.template.spec.volumes[+]
  value:
    name: scorecard-kubeconfig
    secret:
      secretName: scorecard-kubeconfig
      items:
      - key: kubeconfig
        path: config
EOF