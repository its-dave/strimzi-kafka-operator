{{/* vim: set filetype=mustache: */}}

{{/* This file is generated in helm-charts/Makefile */}}
{{/* DO NOT EDIT BY HAND                            */}}

{{/* Generate the kafka image map */}}
{{- define "strimzi.kafka.image.map" }}
            - name: STRIMZI_DEFAULT_TLS_SIDECAR_ENTITY_OPERATOR_IMAGE
              value: {{ default .Values.tlsSidecarEntityOperator.image.repository .Values.imageRepositoryOverride }}/{{ .Values.tlsSidecarEntityOperator.image.name }}:{{ default .Values.tlsSidecarEntityOperator.image.tagPrefix .Values.imageTagOverride }}-kafka-2.5.0
            - name: STRIMZI_DEFAULT_TLS_SIDECAR_KAFKA_IMAGE
              value: {{ default .Values.tlsSidecarKafka.image.repository .Values.imageRepositoryOverride }}/{{ .Values.tlsSidecarKafka.image.name }}:{{ default .Values.tlsSidecarKafka.image.tagPrefix .Values.imageTagOverride }}-kafka-2.5.0
            - name: STRIMZI_DEFAULT_KAFKA_EXPORTER_IMAGE
              value: {{ default .Values.kafkaExporter.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaExporter.image.name }}:{{ default .Values.kafkaExporter.image.tagPrefix .Values.imageTagOverride }}-kafka-2.5.0
            - name: STRIMZI_DEFAULT_CRUISE_CONTROL_IMAGE
              value: {{ default .Values.cruiseControl.image.repository .Values.imageRepositoryOverride }}/{{ .Values.cruiseControl.image.name }}:{{ default .Values.cruiseControl.image.tagPrefix .Values.imageTagOverride }}-kafka-2.5.0
            - name: STRIMZI_DEFAULT_TLS_SIDECAR_CRUISE_CONTROL_IMAGE
              value: {{ default .Values.tlsSidecarCruiseControl.image.repository .Values.imageRepositoryOverride }}/{{ .Values.tlsSidecarCruiseControl.image.name }}:{{ default .Values.tlsSidecarCruiseControl.image.tagPrefix .Values.imageTagOverride }}-kafka-2.5.0
            - name: STRIMZI_KAFKA_IMAGES
              value: |                 
                2.3.0={{ default .Values.kafka.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafka.image.name }}:{{ default .Values.kafka.image.tagPrefix .Values.imageTagOverride }}-kafka-2.3.0
                2.3.1={{ default .Values.kafka.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafka.image.name }}:{{ default .Values.kafka.image.tagPrefix .Values.imageTagOverride }}-kafka-2.3.1
                2.4.0={{ default .Values.kafka.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafka.image.name }}:{{ default .Values.kafka.image.tagPrefix .Values.imageTagOverride }}-kafka-2.4.0
                2.4.1={{ default .Values.kafka.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafka.image.name }}:{{ default .Values.kafka.image.tagPrefix .Values.imageTagOverride }}-kafka-2.4.1
                2.5.0={{ default .Values.kafka.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafka.image.name }}:{{ default .Values.kafka.image.tagPrefix .Values.imageTagOverride }}-kafka-2.5.0
            - name: STRIMZI_KAFKA_CONNECT_IMAGES
              value: |                 
                2.3.0={{ default .Values.kafkaConnect.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaConnect.image.name }}:{{ default .Values.kafkaConnect.image.tagPrefix .Values.imageTagOverride }}-kafka-2.3.0
                2.3.1={{ default .Values.kafkaConnect.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaConnect.image.name }}:{{ default .Values.kafkaConnect.image.tagPrefix .Values.imageTagOverride }}-kafka-2.3.1
                2.4.0={{ default .Values.kafkaConnect.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaConnect.image.name }}:{{ default .Values.kafkaConnect.image.tagPrefix .Values.imageTagOverride }}-kafka-2.4.0
                2.4.1={{ default .Values.kafkaConnect.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaConnect.image.name }}:{{ default .Values.kafkaConnect.image.tagPrefix .Values.imageTagOverride }}-kafka-2.4.1
                2.5.0={{ default .Values.kafkaConnect.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaConnect.image.name }}:{{ default .Values.kafkaConnect.image.tagPrefix .Values.imageTagOverride }}-kafka-2.5.0
            - name: STRIMZI_KAFKA_CONNECT_S2I_IMAGES
              value: |                 
                2.3.0={{ default .Values.kafkaConnects2i.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaConnects2i.image.name }}:{{ default .Values.kafkaConnects2i.image.tagPrefix .Values.imageTagOverride }}-kafka-2.3.0
                2.3.1={{ default .Values.kafkaConnects2i.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaConnects2i.image.name }}:{{ default .Values.kafkaConnects2i.image.tagPrefix .Values.imageTagOverride }}-kafka-2.3.1
                2.4.0={{ default .Values.kafkaConnects2i.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaConnects2i.image.name }}:{{ default .Values.kafkaConnects2i.image.tagPrefix .Values.imageTagOverride }}-kafka-2.4.0
                2.4.1={{ default .Values.kafkaConnects2i.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaConnects2i.image.name }}:{{ default .Values.kafkaConnects2i.image.tagPrefix .Values.imageTagOverride }}-kafka-2.4.1
                2.5.0={{ default .Values.kafkaConnects2i.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaConnects2i.image.name }}:{{ default .Values.kafkaConnects2i.image.tagPrefix .Values.imageTagOverride }}-kafka-2.5.0
            - name: STRIMZI_KAFKA_MIRROR_MAKER_IMAGES
              value: |                 
                2.3.0={{ default .Values.kafkaMirrorMaker.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaMirrorMaker.image.name }}:{{ default .Values.kafkaMirrorMaker.image.tagPrefix .Values.imageTagOverride }}-kafka-2.3.0
                2.3.1={{ default .Values.kafkaMirrorMaker.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaMirrorMaker.image.name }}:{{ default .Values.kafkaMirrorMaker.image.tagPrefix .Values.imageTagOverride }}-kafka-2.3.1
                2.4.0={{ default .Values.kafkaMirrorMaker.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaMirrorMaker.image.name }}:{{ default .Values.kafkaMirrorMaker.image.tagPrefix .Values.imageTagOverride }}-kafka-2.4.0
                2.4.1={{ default .Values.kafkaMirrorMaker.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaMirrorMaker.image.name }}:{{ default .Values.kafkaMirrorMaker.image.tagPrefix .Values.imageTagOverride }}-kafka-2.4.1
                2.5.0={{ default .Values.kafkaMirrorMaker.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaMirrorMaker.image.name }}:{{ default .Values.kafkaMirrorMaker.image.tagPrefix .Values.imageTagOverride }}-kafka-2.5.0
            - name: STRIMZI_KAFKA_MIRROR_MAKER_2_IMAGES
              value: |                 
                2.3.0={{ default .Values.kafkaMirrorMaker2.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaMirrorMaker2.image.name }}:{{ default .Values.kafkaMirrorMaker2.image.tagPrefix .Values.imageTagOverride }}-kafka-2.3.0
                2.3.1={{ default .Values.kafkaMirrorMaker2.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaMirrorMaker2.image.name }}:{{ default .Values.kafkaMirrorMaker2.image.tagPrefix .Values.imageTagOverride }}-kafka-2.3.1
                2.4.0={{ default .Values.kafkaMirrorMaker2.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaMirrorMaker2.image.name }}:{{ default .Values.kafkaMirrorMaker2.image.tagPrefix .Values.imageTagOverride }}-kafka-2.4.0
                2.4.1={{ default .Values.kafkaMirrorMaker2.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaMirrorMaker2.image.name }}:{{ default .Values.kafkaMirrorMaker2.image.tagPrefix .Values.imageTagOverride }}-kafka-2.4.1
                2.5.0={{ default .Values.kafkaMirrorMaker2.image.repository .Values.imageRepositoryOverride }}/{{ .Values.kafkaMirrorMaker2.image.name }}:{{ default .Values.kafkaMirrorMaker2.image.tagPrefix .Values.imageTagOverride }}-kafka-2.5.0
{{- end -}}
