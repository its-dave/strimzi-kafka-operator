{{- define "eventstreams.clusterrole.namespaced" }}
- apiGroups:
    - ""
  resources:
    - services
    - configmaps
    - pods
    - serviceaccounts
    - persistentvolumeclaims
  verbs:
    - "*"
- apiGroups:
    - ""
  resources:
    - namespaces
  verbs:
    - get
    - list
    - patch
    - delete
- apiGroups:
    - apps
  resources:
    - statefulsets
    - replicasets
    - deployments
  verbs:
    - "*"
- apiGroups:
    - extensions
  resources:
    - statefulsets
    - deployments
    - networkpolicies
  verbs:
    - "*"
- apiGroups:
    - rbac.authorization.k8s.io
  resources:
    - rolebindings
  verbs:
    - get
    - create
    - patch
- apiGroups:
    - networking.k8s.io
  resources:
    - networkpolicies
  verbs:
    - get
    - create
    - patch
- apiGroups:
    - admissionregistration.k8s.io
  resources:
    - validatingwebhookconfigurations
  verbs:
    - get
    - create
    - patch
    - delete
- apiGroups:
    - rbac.authorization.k8s.io
  resources:
    - clusterroles
    - clusterrolebindings
  verbs:
    - get
    - list
- apiGroups:
    - console.openshift.io
  resources:
    - consoleyamlsamples
  verbs:
    - get
    - create
    - patch
    - delete
- apiGroups:
    - monitoring.coreos.com
  resources:
    - servicemonitors
  verbs:
    - get
    - create
- apiGroups:
    - eventstreams.ibm.com
  resources:
    - "*"
  verbs:
    - "*"
- apiGroups:
    - eventstreams.ibm.com
  resources:
    - "*"
    - "kafkausers"
    - "kafkausers/status"
  verbs:
    - "*"
- apiGroups:
    - route.openshift.io
  resources:
    - "routes"
  verbs:
    - "*"
- apiGroups:
    - "oidc.security.ibm.com"
  resources:
    - clients
  verbs:
    - get
    - list
    - create
    - patch
    - delete
    - update
- apiGroups:
    - "cp4i.ibm.com"
  resources:
    - cp4iservicesbindings
  verbs:
    - get
    - list
    - create
- apiGroups:
  - apiextensions.k8s.io
  resources:
  - customresourcedefinitions
  verbs:
  - get
  - list
{{- end -}}

{{- define "eventstreams.clusterrole.global" }}
- apiGroups:
    - admissionregistration.k8s.io
  resources:
    - validatingwebhookconfigurations
  verbs:
    - get
    - create
    - patch
    - delete
- apiGroups:
    - rbac.authorization.k8s.io
  resources:
    - clusterroles
    - clusterrolebindings
  verbs:
    - get
    - list
- apiGroups:
    - console.openshift.io
  resources:
    - consoleyamlsamples
  verbs:
    - get
    - create
    - patch
    - delete
- apiGroups:
  - apiextensions.k8s.io
  resources:
  - customresourcedefinitions
  verbs:
  - get
  - list
{{- end -}}
