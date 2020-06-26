#!/bin/bash

set -e
DASHBOARD_DIR="./eventstreams-dashboards"
DASHBOARD_JSON_DIR="$DASHBOARD_DIR/json"
MONITORING_DASHBOARD_CR_DIR="$DASHBOARD_DIR/monitoring-dashboard-crs"

for file in "$DASHBOARD_JSON_DIR"/*
do
  fileName="${file##*/}"
  fileName=${fileName%.*}
  YAML=$(jq '.panels[].datasource |= "prometheus" | .id = null' "$file" | sed -e 's/^/    /g')
  echo "apiVersion: monitoringcontroller.cloud.ibm.com/v1
kind: MonitoringDashboard
metadata:
  name: $fileName
spec:
  enabled: true
  data: |-
${YAML}" > "$MONITORING_DASHBOARD_CR_DIR/$fileName-monitoring-dashboard.yaml"
done
