#!/bin/bash

set -e

MASTER_BRANCH="master"
MASTER_IBM_BRANCH="master-ibm"
STRIMZI_REPO="https://github.com/strimzi/strimzi-kafka-operator.git"

echo "Add remote Strimzi repo to upstream..."
git remote add upstream ${STRIMZI_REPO}
commandResult=$?

if [ $commandResult != 0 ]; then
  echo "Failed to add remote Strimzi repo to upstream!"
  exit 1
fi

echo "Fetch the upstream branches..."
git fetch upstream
commandResult=$?

if [ $commandResult != 0 ]; then
  echo "Failed to fetch the upstream branches!"
  exit 1
fi

echo "Track the upstream ${MASTER_BRANCH} branch..."
git checkout --track upstream/${MASTER_BRANCH}
commandResult=$?

if [ $commandResult != 0 ]; then
  echo "Failed to track the upstream Strimzi ${MASTER_BRANCH}!"
  exit 1
fi

echo "Running git checkout ${MASTER_IBM_BRANCH}..."
git checkout ${MASTER_IBM_BRANCH}
commandResult=$?

if [ $commandResult != 0 ]; then
  echo "Failed to checkout ${MASTER_IBM_BRANCH} branch!"
  exit 1
fi

echo "Running git pull on ${MASTER_IBM_BRANCH} branch..."
git pull
commandResult=$?

if [ $commandResult != 0 ]; then
  echo "Failed to pull from ${MASTER_IBM_BRANCH}!"
  exit 1
fi

echo "Running checkout ${MASTER_BRANCH}..."
git checkout ${MASTER_BRANCH}
commandResult=$?

if [ $commandResult != 0 ]; then
  echo "Failed to checkout ${MASTER_BRANCH} branch!"
  exit 1
fi

echo "Running git pull on ${MASTER_BRANCH} branch..."
git pull
commandResult=$?

if [ $commandResult != 0 ]; then
  echo "Failed to pull from ${MASTER_BRANCH} branch!"
  exit 1
fi

echo "Running checkout ${MASTER_IBM_BRANCH}..."
git checkout ${MASTER_IBM_BRANCH}
commandResult=$?

if [ $commandResult != 0 ]; then
  echo "Failed to checkout ${MASTER_IBM_BRANCH}"
  exit 1
fi

echo "Running rebase on ${MASTER_BRANCH}..."
git rebase ${MASTER_BRANCH}
commandResult=$?

if [ $commandResult != 0 ]; then
  echo "Failed to rebase ${MASTER_BRANCH} branch!"
  exit 1
fi

echo "Running push origin +${MASTER_BRANCH}..."
git push origin +${MASTER_BRANCH}
commandResult=$?

if [ $commandResult != 0 ]; then
  echo "Failed to rebase ${MASTER_BRANCH}"
  exit 1
fi

echo "Running push origin +${MASTER_IBM_RANCH}..."
git push origin +${MASTER_IBM_BRANCH}
commandResult=$?

if [ $commandResult != 0 ]; then
  echo "Failed to rebase ${MASTER_IBM_BRANCH}"
  exit 1
fi