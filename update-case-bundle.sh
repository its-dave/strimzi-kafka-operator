#!/bin/bash
set -e

updateGitSubmodule() {
    TAG=$1
    REPO="ibm-eventstreams-case-bundle"
    ROOT="$(pwd)"
    echo "Cloning ${REPO}..."
    git clone -b master "https://github.ibm.com/mhub/${REPO}.git"

    echo "Entering ${REPO}..."
    cd "${REPO}"

    echo "Updating submodule..."
    ./scripts/pre-install.sh
    cd "${ROOT}"
}

updateGitSubmodule "${TAG}"
