#!/bin/bash

set -e

update="
- command: update
  path: resources.resourceDefs.files[+]
  value:
    ref: $(basename $2)
    mediaType: application/vnd.case.resource.k8s.v1+yaml
"

yq w -i -s <(cat <<< "$update") $1