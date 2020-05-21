{{- define "eventstreams.clusterrole.namespaced" }}
- apiGroups:
    - ""
  # The Event Streams Operator requires the "get" and "list" permissions for namespaces
  # This is to allow the operator to query the namespaces in the cluster and watch all that it can see when
  # WATCHED_NAMESPACES has the value '*'
  resources:
    - namespaces
  verbs:
    - get
    - list


- apiGroups:
    - ""
 # The Event Streams Operator requires the "patch" permissions for pods
 # This is to allow the operator to delegate the same permissions to the Admin API
  resources:
    - pods
  verbs:
    - patch

- apiGroups:
    - admissionregistration.k8s.io
  # The Event Streams Operator requires the "get", "create", "patch" and "delete" permissions for validatingwebhookconfigurations
  # This is to allow the operator to create and manage validation webhooks required for an Event Streams custom resources to
  # be validated prior to application to the Kubernetes server
  resources:
    - validatingwebhookconfigurations
  verbs:
    - get
    - create
    - patch
    - delete

- apiGroups:
    - rbac.authorization.k8s.io
  # The Event Streams Operator requires the "get" and "list" permissions for clusterroles and clusterrolebindings
  # This is to allow the operator to create and manage rolebindings that bind to clusterroles required for components of Event Streams
  resources:
    - clusterroles
    - clusterrolebindings
  verbs:
    - get
    - list

- apiGroups:
    - console.openshift.io
  # The Event Streams Operator requires the "get", "create", "delete" and "patch" permissions for consoleyamlsamples
  # This is to allow the operator to create and manage Event Streams sample yamls for use by OLM
  resources:
    - consoleyamlsamples
  verbs:
    - get
    - create
    - patch
    - delete

- apiGroups:
    - monitoring.coreos.com
  # The Event Streams Operator requires the "get", "create" and "patch" permissions for servicemonitors
  # This is to allow the operator to create and manage the resources required for an Event Streams instance
  resources:
    - servicemonitors
  verbs:
    - get
    - create
    - patch

- apiGroups:
    - eventstreams.ibm.com
  # The Event Streams Operator requires the "*" permissions for eventstreams, eventstreamsgeoreplicator and kafkausers
  # This is to allow the operator to create and manage the resources required for an Event Streams and Event Streams geo-replicator instance
  resources:
    - "eventstreams"
    - "eventstreams/status"
    - "eventstreamsgeoreplicators"
    - "eventstreamsgeoreplicators/status"
  verbs:
    - get
    - list
    - watch
    - create
    - delete
    - patch
    - update

- apiGroups:
    - "oidc.security.ibm.com"
  # The Event Streams Operator requires the "get", "create", "patch" and "delete" permissions for oidc clients
  # This is to allow the operator to create and manage an oidc client request required for authentication in Event Streams
  resources:
    - clients
  verbs:
    - get
    - create
    - patch
    - delete

- apiGroups:
    - "cp4i.ibm.com"
  # The Event Streams Operator requires the "get", "create", "patch" and "delete" permissions for oidc clients
  # This is to allow the operator to create and manage an oidc client request required for authentication in Event Streams
  resources:
    - cp4iservicesbindings
  verbs:
    - get
    - create
    - patch
    - delete

- apiGroups:
    - "operator.ibm.com"
  resources:
    - operandrequests
  verbs:
    - get
    - list
    - create
    - patch
- apiGroups:
  - apiextensions.k8s.io
  # The Event Streams Operator requires the "get", "create" and "patch" permissions for custom resource definitions
  # This is to allow the operator to query the kubernetes server for whether certain CRDs are present
  resources:
  - customresourcedefinitions
  verbs:
  - get
  - list
{{- end -}}

{{- define "eventstreams.clusterrole.global" }}
- apiGroups:
    - admissionregistration.k8s.io
  # The Event Streams Operator requires the "get", "create", "patch" and "delete" permissions for validatingwebhookconfigurations
  # This is to allow the operator to create and manage validation webhooks required for an Event Streams custom resources to
  # be validated prior to application to the Kubernetes server
  resources:
    - validatingwebhookconfigurations
  verbs:
    - get
    - create
    - patch
    - delete

- apiGroups:
    - rbac.authorization.k8s.io
  # The Event Streams Operator requires the "get" and "list" permissions for clusterroles and clusterrolebindings
  # This is to allow the operator to query the Kubernetes server for whether the required cluster roles and bindings are present
  resources:
    - clusterroles
    - clusterrolebindings
  verbs:
    - get
    - list

- apiGroups:
    - console.openshift.io
  # The Event Streams Operator requires the "get", "create", "patch" and "delete" permissions for consoleyamlsamples
  # This is to allow the operator to setup samples for the OLM UI
  resources:
    - consoleyamlsamples
  verbs:
    - get
    - create
    - patch
    - delete

- apiGroups:
  - apiextensions.k8s.io
  # The Event Streams Operator requires the "get", "create" and "patch" permissions for custom resource definitions
  # This is to allow the operator to query the kubernetes server for whether certain CRDs are present
  resources:
  - customresourcedefinitions
  verbs:
  - get
  - list
{{- end -}}
