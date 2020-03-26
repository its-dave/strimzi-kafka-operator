# Event Streams Examples

This readme provides instructions on installing the EventStreams custom resource.

## Prerequisites

A Kubernetes cluster with the the IBM Event Streams Operator installed, including the EventStreams Custom Resource Definition.

## Installation

### Roles required

- ability to create a custom resource in a chosen namespace.

### Steps

1. Login to cluster
2. Choose which example instance of eventstreams to apply from the `files` directory. (example `eventstreams-quickstart.yaml`)
3. Apply instance of chosen custom resource `kubectl apply -f files/eventstreams-quickstart.yaml`.