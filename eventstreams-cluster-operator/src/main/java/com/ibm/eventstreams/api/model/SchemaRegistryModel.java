/*
 * IBM Confidential
 * OCO Source Materials
 *
 * 5737-H33
 *
 * (C) Copyright IBM Corp. 2019  All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 */
package com.ibm.eventstreams.api.model;

import com.ibm.commonservices.CommonServicesConfig;
import com.ibm.eventstreams.api.DefaultResourceRequirements;
import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.spec.ComponentSpec;
import com.ibm.eventstreams.api.spec.ComponentTemplate;
import com.ibm.eventstreams.api.spec.ContainerSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.ImagesSpec;
import com.ibm.eventstreams.api.spec.SchemaRegistrySpec;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import com.ibm.commonservices.api.model.ClientModel;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HTTPHeaderBuilder;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.strimzi.api.kafka.model.ContainerEnvVar;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.Logging;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.storage.EphemeralStorage;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorage;
import io.strimzi.api.kafka.model.storage.Storage;
import io.strimzi.api.kafka.model.template.PodTemplate;
import io.strimzi.operator.cluster.model.ModelUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SchemaRegistryModel extends AbstractSecureEndpointsModel {

    // static variables
    public static final String COMPONENT_NAME = "schema";
    public static final String APPLICATION_NAME = "schema-registry";
    public static final String AVRO_SERVICE_CONTAINER_NAME = "avro";
    public static final String SCHEMA_REGISTRY_PROXY_CONTAINER_NAME = "proxy";
    public static final int SCHEMA_REGISTRY_PORT = 3000;
    public static final int AVRO_SERVICE_PORT = 3080;
    public static final int DEFAULT_REPLICAS = 1;
    public static final String TEMP_DIR_NAME = "tempdir";
    public static final String SHARED_VOLUME_MOUNT_NAME = "shared";
    protected static final String LOG_LEVEL_ENV_NAME = "TRACE_LEVEL";
    protected static final String AVRO_LOG_LEVEL_ENV_NAME = "LOG_LEVEL";
    private static final String DEFAULT_LOG_STRING = "INFO";
    private static final String DEFAULT_AVRO_LOG_STRING = "info";
    private static final String DEFAULT_IBMCOM_SCHEMA_REGISTRY_IMAGE = "ibmcom/schema-registry:latest";
    private static final String DEFAULT_IBMCOM_AVRO_IMAGE = "ibmcom/avro:latest";
    private static final String DEFAULT_IBMCOM_SCHEMA_REGISTRY_PROXY_IMAGE = "ibmcom/schema-proxy:latest";

    private static final String CERTS_VOLUME_MOUNT_NAME = "certs";

    private String logString;
    private String avroLogString;

    // deployable objects
    private Deployment deployment;
    private ServiceAccount serviceAccount;
    private NetworkPolicy networkPolicy;
    private PersistentVolumeClaim pvc;
    private String avroImage;
    private List<ContainerEnvVar> avroEnvVars;
    private ResourceRequirements avroResourceRequirements;
    private io.strimzi.api.kafka.model.Probe avroLivenessProbe;
    private io.strimzi.api.kafka.model.Probe avroReadinessProbe;
    private String schemaRegistryProxyImage;
    private List<ContainerEnvVar> schemaRegistryProxyEnvVars;
    private ResourceRequirements schemaRegistryProxyResourceRequirements;
    private String defaultProxyTraceString = "info";
    private final String iamClusterName;
    private final String iamServerURL;
    private final String ibmcloudCASecretName;
    private final String internalKafkaUsername;
    private final boolean kafkaAuthorizationEnabled;

    private List<ListenerStatus> kafkaListeners;

    private Storage storage;

    /**
     * This class is used to model the kube resources required for the correct deployment of the schema registry
     * @param instance
     * @param imageConfig
     * @param kafkaListeners
     * @param commonServicesConfig
     * @param internalKafkaUsername
     */
    public SchemaRegistryModel(EventStreams instance,
                               EventStreamsOperatorConfig.ImageLookup imageConfig,
                               List<ListenerStatus> kafkaListeners,
                               CommonServicesConfig commonServicesConfig,
                               String internalKafkaUsername) {

        super(instance, COMPONENT_NAME, APPLICATION_NAME);
        this.kafkaListeners = kafkaListeners != null ? new ArrayList<>(kafkaListeners) : new ArrayList<>();
        this.iamClusterName = commonServicesConfig.getClusterName();
        this.iamServerURL = commonServicesConfig.getIngressEndpoint();
        this.internalKafkaUsername = internalKafkaUsername;
        this.kafkaAuthorizationEnabled = isKafkaAuthorizationEnabled(instance);

        ibmcloudCASecretName = ClusterSecretsModel.getIBMCloudSecretName(getInstanceName());

        Optional<SchemaRegistrySpec> schemaRegistrySpec = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getSchemaRegistry);

        if (schemaRegistrySpec.isPresent()) {
            setOwnerReference(instance);
            int replicas = schemaRegistrySpec.map(ComponentSpec::getReplicas).orElse(DEFAULT_REPLICAS);
            setReplicas(replicas);
            setEnvVars(schemaRegistrySpec.map(ContainerSpec::getEnvVars).orElseGet(ArrayList::new));
            setResourceRequirements(schemaRegistrySpec.map(ComponentSpec::getResources).orElseGet(ResourceRequirements::new));
            setPodTemplate(schemaRegistrySpec.map(ComponentSpec::getTemplate)
                        .map(ComponentTemplate::getPod)
                        .orElseGet(PodTemplate::new));
            setGlobalPullSecrets(Optional.ofNullable(instance.getSpec())
                        .map(EventStreamsSpec::getImages)
                        .map(ImagesSpec::getPullSecrets)
                        .orElseGet(imageConfig::getPullSecrets));
            setTlsVersion(getInternalTlsVersion(instance));

            storage = schemaRegistrySpec.map(SchemaRegistrySpec::getStorage)
                    .orElseGet(EphemeralStorage::new);


            setImage(firstDefinedImage(
                DEFAULT_IBMCOM_SCHEMA_REGISTRY_IMAGE,
                            schemaRegistrySpec.map(ComponentSpec::getImage),
                            imageConfig.getSchemaRegistryImage()));
            setLivenessProbe(schemaRegistrySpec.map(ComponentSpec::getLivenessProbe)
                    .orElseGet(io.strimzi.api.kafka.model.Probe::new));
            setReadinessProbe(schemaRegistrySpec.map(ComponentSpec::getReadinessProbe)
                    .orElseGet(io.strimzi.api.kafka.model.Probe::new));
            logString = getLoggingString(schemaRegistrySpec.map(ComponentSpec::getLogging).orElse(null), DEFAULT_LOG_STRING);

            Optional<ContainerSpec> avroSpec = schemaRegistrySpec.map(SchemaRegistrySpec::getAvro);

            avroLogString = getLoggingString(avroSpec.map(ContainerSpec::getLogging).orElse(null), DEFAULT_AVRO_LOG_STRING);

            avroEnvVars = avroSpec.map(ContainerSpec::getEnvVars).orElseGet(ArrayList::new);
            avroImage = firstDefinedImage(
                DEFAULT_IBMCOM_AVRO_IMAGE,
                            avroSpec.map(ContainerSpec::getImage),
                            imageConfig.getSchemaRegistryAvroImage());
            avroResourceRequirements = avroSpec.map(ContainerSpec::getResources).orElseGet(ResourceRequirements::new);
            avroLivenessProbe = avroSpec.map(ContainerSpec::getLivenessProbe)
                    .orElseGet(io.strimzi.api.kafka.model.Probe::new);
            avroReadinessProbe = avroSpec.map(ContainerSpec::getLivenessProbe)
                    .orElseGet(io.strimzi.api.kafka.model.Probe::new);

            Optional<ContainerSpec> schemaRegistryProxySpec = schemaRegistrySpec.map(SchemaRegistrySpec::getProxy);
            schemaRegistryProxyEnvVars = schemaRegistryProxySpec.map(ContainerSpec::getEnvVars).orElseGet(ArrayList::new);
            schemaRegistryProxyImage = firstDefinedImage(
                DEFAULT_IBMCOM_SCHEMA_REGISTRY_PROXY_IMAGE,
                schemaRegistryProxySpec.map(ContainerSpec::getImage),
                imageConfig.getSchemaRegistryProxyImage());
            schemaRegistryProxyResourceRequirements = schemaRegistryProxySpec.map(ContainerSpec::getResources).orElseGet(ResourceRequirements::new);
            setCustomImages(imageConfig.getSchemaRegistryImage(), imageConfig.getSchemaRegistryAvroImage(), imageConfig.getSchemaRegistryProxyImage());

            endpoints = createEndpoints(instance, schemaRegistrySpec.orElse(null));
            deployment = createDeployment(getContainers(), getVolumes());

            createService(EndpointServiceType.INTERNAL, Collections.emptyMap());
            createService(EndpointServiceType.ROUTE, Collections.emptyMap());
            createService(EndpointServiceType.NODE_PORT, Collections.emptyMap());
            routes = createRoutesFromEndpoints();

            serviceAccount = createServiceAccount();
            networkPolicy = createNetworkPolicy();
            if (storage instanceof PersistentClaimStorage) {
                pvc = createSchemaRegistryPersistentVolumeClaim(instance.getMetadata().getNamespace(), replicas, (PersistentClaimStorage) storage);
            } else {
                pvc = null;
            }
        }
    }

    protected void setCustomImages(Optional<String> defaultEnvSchemaImage, Optional<String> defaultEnvAvroImage, Optional<String> defaultEnvSchemaProxyImage) {
        List<String> defaultSchemaImages = new ArrayList<>();
        defaultSchemaImages.add(DEFAULT_IBMCOM_SCHEMA_REGISTRY_IMAGE);
        defaultSchemaImages.add(defaultEnvSchemaImage.orElse(""));
        boolean schemaCustomImage = defaultSchemaImages
                .stream()
                .filter(image -> this.image.equals(image))
                .findFirst().isPresent() ? false : true;

        List<String> defaultAvroImages = new ArrayList<>();
        defaultAvroImages.add(DEFAULT_IBMCOM_AVRO_IMAGE);
        defaultAvroImages.add(defaultEnvAvroImage.orElse(""));
        boolean avroCustomImage = defaultAvroImages
                .stream()
                .filter(image -> avroImage.equals(image))
                .findFirst().isPresent() ? false : true;

        List<String> defaultSchemaProxyImages = new ArrayList<>();
        defaultSchemaProxyImages.add(DEFAULT_IBMCOM_SCHEMA_REGISTRY_PROXY_IMAGE);
        defaultSchemaProxyImages.add(defaultEnvSchemaProxyImage.orElse(""));
        boolean schemaRegistryProxyCustomImage = defaultSchemaProxyImages
            .stream()
            .filter(image -> schemaRegistryProxyImage.equals(image))
            .findFirst().isPresent() ? false : true;

        this.customImage = schemaCustomImage || avroCustomImage || schemaRegistryProxyCustomImage;
    }

    public static boolean isSchemaRegistryEnabled(EventStreams instance) {
        return instance.getSpec().getSchemaRegistry() != null &&
            Optional.ofNullable(instance.getSpec())
                .map(EventStreamsSpec::getSchemaRegistry)
                .map(SchemaRegistrySpec::getReplicas)
                .orElse(DEFAULT_REPLICAS) > 0;
    }

    /**
     * 
     * @return A list of volumes to put in the schema registry pod
     */
    private List<Volume> getVolumes() {
        List<Volume> volumes = new ArrayList<>();

        Volume temp = new VolumeBuilder()
            .withName(TEMP_DIR_NAME)
            .withNewEmptyDir()
            .endEmptyDir()
            .build();
        volumes.add(temp);

        // Add The IAM Specific Volumes.  If we need to build without IAM Support we can put a variable check
        // here.
        volumes.add(new VolumeBuilder()
            .withNewName(IBMCLOUD_CA_VOLUME_MOUNT_NAME)
            .withNewSecret()
            .withNewSecretName(ibmcloudCASecretName)
            .addNewItem().withNewKey(CA_CERT).withNewPath(CA_CERT).endItem()
            .endSecret()
            .build());

        if (storage instanceof PersistentClaimStorage) {
            Volume sharedPersistentVolume = new VolumeBuilder()
                    .withName(SHARED_VOLUME_MOUNT_NAME)
                    .withNewPersistentVolumeClaim()
                        .withClaimName(getDefaultResourceName())
                    .endPersistentVolumeClaim()
                    .build();
            volumes.add(sharedPersistentVolume);
        } else {
            Volume sharedEphemeralVolume = new VolumeBuilder()
                    .withName(SHARED_VOLUME_MOUNT_NAME)
                    .withNewEmptyDir()
                    .endEmptyDir()
                    .build();
            volumes.add(sharedEphemeralVolume);
        }

        volumes.addAll(getSecurityVolumes());

        return volumes;
    }

    /**
     * 
     * @return A list of containers to put in the schema registry pod
     */
    private List<Container> getContainers() {
        return Arrays.asList(getSchemaRegistryContainer(), getAvroServiceContainer(), getSchemaRegistryProxyContainer());
    }

    /**
     * 
     * @return The schema registry container
     */
    private Container getSchemaRegistryContainer() {
        ArrayList<EnvVar> envVarDefaults = new ArrayList<>(Arrays.asList(
            new EnvVarBuilder().withName("LICENSE").withValue("accept").build(),
            new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build(),
            new EnvVarBuilder().withName("CLUSTER_NAME").withValue(ModelUtils.KUBERNETES_SERVICE_DNS_DOMAIN).build(),
            new EnvVarBuilder().withName("TRACE_LEVEL").withValue(logString).build(),
            new EnvVarBuilder().withName("NODE_ENV").withValue("production").build(),
            new EnvVarBuilder().withName("AVRO_CONTAINER_HOST").withValue("127.0.0.1").build(),
            new EnvVarBuilder().withName("AVRO_CONTAINER_PORT").withValue(Integer.toString(AVRO_SERVICE_PORT)).build(),
            new EnvVarBuilder().withName("SCHEMA_DATA_DIRECTORY").withValue("/var/lib/schemas").build(),
            new EnvVarBuilder().withName("SCHEMA_TEMP_DIRECTORY").withValue("/var/lib/tmp").build(),
            new EnvVarBuilder().withName("ESFF_SECURITY_AUTHZ").withValue("false").build(),
            new EnvVarBuilder().withName("ENDPOINTS").withValue(SCHEMA_REGISTRY_PORT + ":").build(),
            new EnvVarBuilder()
                .withName("HMAC_SECRET")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(MessageAuthenticationModel.getSecretName(getInstanceName()))
                .withKey(MessageAuthenticationModel.HMAC_SECRET)
                .endSecretKeyRef()
                .endValueFrom()
                .build()
        ));

        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(envVarDefaults);

        ContainerBuilder builder = new ContainerBuilder()
            .withName(COMPONENT_NAME)
            .withImage(getImage())
            .withEnv(envVars)
            .withSecurityContext(getSecurityContext(false))
            .withResources(getResourceRequirements(DefaultResourceRequirements.SCHEMA_REGISTRY))
            .addNewVolumeMount()
                .withName(TEMP_DIR_NAME)
                .withMountPath("/var/lib/tmp")
            .endVolumeMount()
            .addNewVolumeMount()
                .withName(SHARED_VOLUME_MOUNT_NAME)
                .withMountPath("/var/lib/schemas")
            .endVolumeMount()
            .withLivenessProbe(createLivenessProbe())
            .withReadinessProbe(createReadinessProbe());

        configureSecurityVolumeMounts(builder);
        return builder.build();
    }

    /**
     * 
     * @return The liveness probe for the schema registry container
     */
    protected Probe createLivenessProbe() {
        Probe defaultLivenessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/live")
                .withNewPort(SCHEMA_REGISTRY_PORT)
                .withScheme("HTTP")
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(10)
                .withPeriodSeconds(30)
                .withTimeoutSeconds(10)
                .withSuccessThreshold(1)
                .withFailureThreshold(3)
                .build();
        return combineProbeDefinitions(defaultLivenessProbe, super.getLivenessProbe());
    }

    /**
     * 
     * @return The liveness probe for the schema registry container
     */
    protected Probe createReadinessProbe() {
        Probe defaultReadinessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/ready")
                .withNewPort(SCHEMA_REGISTRY_PORT)
                .withScheme("HTTP")
                .withHttpHeaders(new HTTPHeaderBuilder()
                        .withName("Accept")
                        .withValue("*/*")
                        .build())
                .endHttpGet()
                .withInitialDelaySeconds(10)
                .withPeriodSeconds(60)
                .withTimeoutSeconds(10)
                .withSuccessThreshold(1)
                .withFailureThreshold(2)
                .build();
        return combineProbeDefinitions(defaultReadinessProbe, super.getReadinessProbe());
    }

    /**
     * 
     * @return The avro service container
     */
    private Container getAvroServiceContainer() {

        List<EnvVar> envVarDefaults = Arrays.asList(
            new EnvVarBuilder().withName("LICENSE").withValue("accept").build(),
            new EnvVarBuilder().withName("NODE_ENV").withValue("production").build(),
            new EnvVarBuilder().withName("AVRO_CONTAINER_PORT").withValue(Integer.toString(AVRO_SERVICE_PORT)).build(),
            new EnvVarBuilder().withName("SCHEMA_DATA_DIRECTORY").withValue("/var/lib/schemas").build(),
            new EnvVarBuilder().withName("SCHEMA_TEMP_DIRECTORY").withValue("/var/lib/tmp").build(),
            new EnvVarBuilder()
                .withName("RELEASE_CM_MOUNT_LOCATION")
                .withValue("/var/lib/config/restProxyExternalPort")
                .build(),
            new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build(),
            new EnvVarBuilder().withName("EXTERNAL_IP").withValue("123.456.789").build(),
            new EnvVarBuilder().withName("LOG_LEVEL").withValue(avroLogString).build()
            // new EnvVarBuilder().withName("EXTERNAL_IP").withNewValueFrom().withNewSecretKeyRef("proxy", "externalHostOrIp", false).endValueFrom().build()
        );

        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(envVarDefaults, avroEnvVars);

        return new ContainerBuilder()
            .withName(AVRO_SERVICE_CONTAINER_NAME)
            .withImage(avroImage)
            .withEnv(envVars)
            .withSecurityContext(getSecurityContext(false))
            .withResources(getResourceRequirements(avroResourceRequirements, DefaultResourceRequirements.AVRO_SERVICE))
            .addNewVolumeMount()
                .withName(TEMP_DIR_NAME)
                .withMountPath("/var/lib/tmp")
            .endVolumeMount()
            .addNewVolumeMount()
                .withName(SHARED_VOLUME_MOUNT_NAME)
                .withMountPath("/var/lib/schemas")
            .endVolumeMount()
            .withLivenessProbe(createAvroLivenessProbe())
            .withReadinessProbe(createAvroReadinessProbe())
            .build();
    }

    /**
     * 
     * @return The liveness probe for the avro service
     */
    protected Probe createAvroLivenessProbe() {
        Probe defaultLivenessProbe = new ProbeBuilder()
                .withNewTcpSocket()
                .withNewPort(AVRO_SERVICE_PORT)
                .endTcpSocket()
                .withInitialDelaySeconds(10)
                .withPeriodSeconds(30)
                .withTimeoutSeconds(10)
                .withSuccessThreshold(1)
                .withFailureThreshold(2)
                .build();
        return combineProbeDefinitions(defaultLivenessProbe, avroLivenessProbe);
    }

    /**
     * 
     * @return The readiness probe for the avro service container
     */
    protected Probe createAvroReadinessProbe() {
        Probe defaultReadinessProbe = new ProbeBuilder()
                .withNewTcpSocket()
                .withNewPort(AVRO_SERVICE_PORT)
                .endTcpSocket()
                .withInitialDelaySeconds(30)
                .withPeriodSeconds(60)
                .withTimeoutSeconds(10)
                .withSuccessThreshold(1)
                .withFailureThreshold(2)
                .build();
        return combineProbeDefinitions(defaultReadinessProbe, avroReadinessProbe);
    }

    /**
     *
     * @return The Schema Registry Proxy Container
     */
    private Container getSchemaRegistryProxyContainer() {

        List<EnvVar> schemaRegistryProxyDefaultEnvVars = getSchemaRegistryProxyEnvVars();
        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(schemaRegistryProxyDefaultEnvVars, schemaRegistryProxyEnvVars);

        ContainerBuilder containerBuilder = new ContainerBuilder()
            .withName(SCHEMA_REGISTRY_PROXY_CONTAINER_NAME)
            .withImage(schemaRegistryProxyImage)
            .withEnv(envVars)
            .withSecurityContext(getSecurityContext(false))
            .withResources(getResourceRequirements(schemaRegistryProxyResourceRequirements, DefaultResourceRequirements.ADMIN_API))
            .withLivenessProbe(createProxyLivenessProbe())
            .withReadinessProbe(createProxyReadinessProbe());

        // Add The IAM Specific Volume mount. If we need to build without IAM Support we can put a variable check
        // here.
        containerBuilder.addNewVolumeMount()
            .withNewName(IBMCLOUD_CA_VOLUME_MOUNT_NAME)
            .withMountPath(IBMCLOUD_CA_CERTIFICATE_PATH)
            .withNewReadOnly(true)
            .endVolumeMount();

        configureSecurityVolumeMounts(containerBuilder);

        return containerBuilder.build();
    }

    /**
     *
     * @return A list of default EnvVars for the schema registry proxy container
     */
    private List<EnvVar> getSchemaRegistryProxyEnvVars() {

        String oidcSecretName = ClientModel.getSecretName(getInstanceName());

        List<EnvVar> envVars = new ArrayList<>();
        envVars.addAll(Arrays.asList(
            new EnvVarBuilder().withName("RELEASE").withValue(getInstanceName()).build(),
            new EnvVarBuilder().withName("LICENSE").withValue("accept").build(),
            new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build(),
            new EnvVarBuilder().withName("AUTHORIZATION_ENABLED").withValue(Boolean.toString(kafkaAuthorizationEnabled)).build(),
            new EnvVarBuilder().withName("TRACE_SPEC").withValue(defaultProxyTraceString).build(),
            new EnvVarBuilder().withName("KAFKA_PRINCIPAL").withValue(internalKafkaUsername).build(),
            new EnvVarBuilder()
                .withName("HMAC_SECRET")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(MessageAuthenticationModel.getSecretName(getInstanceName()))
                .withKey(MessageAuthenticationModel.HMAC_SECRET)
                .endSecretKeyRef()
                .endValueFrom()
                .build(),
            // Add The IAM Specific Envars.  If we need to build without IAM Support we can put a variable check
            // here.
            new EnvVarBuilder().withName("IAM_CLUSTER_NAME").withValue(iamClusterName).build(),
            new EnvVarBuilder().withName("IAM_SERVER_URL").withValue(iamServerURL).build(),
            new EnvVarBuilder().withName("IAM_SERVER_CA_CERT").withValue(IBMCLOUD_CA_CERTIFICATE_PATH + File.separator + CA_CERT).build(),
            new EnvVarBuilder()
                .withName("CLIENT_ID")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(oidcSecretName)
                .withKey(CLIENT_ID_KEY)
                .endSecretKeyRef()
                .endValueFrom()
                .build(),
            new EnvVarBuilder()
                .withName("CLIENT_SECRET")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(oidcSecretName)
                .withKey(CLIENT_SECRET_KEY)
                .endSecretKeyRef()
                .endValueFrom()
                .build()
        ));

        // Optionally add the kafka bootstrap URLs if present
        getInternalKafkaBootstrap(kafkaListeners).ifPresent(internalBootstrap ->
            envVars.add(new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(internalBootstrap).build()));

        getRunAsKafkaBootstrap(kafkaListeners).ifPresent(runasBootstrap ->
            envVars.add(new EnvVarBuilder().withName("RUNAS_KAFKA_BOOTSTRAP_SERVERS").withValue(runasBootstrap).build()));

        configureSecurityEnvVars(envVars);

        return envVars;
    }

    /**
     *
     * @return The liveness probe for the schema registry proxy container
     */
    protected Probe createProxyLivenessProbe() {
        Probe defaultLivenessProbe = new ProbeBuilder()
            .withNewHttpGet()
            .withPath("/liveness")
            .withNewPort(Endpoint.getPodToPodPort(tlsEnabled()))
            .withScheme(getHealthCheckProtocol())
            .withHttpHeaders(new HTTPHeaderBuilder()
                .withName("Accept")
                .withValue("*/*")
                .build())
            .endHttpGet()
            .withInitialDelaySeconds(10)
            .withPeriodSeconds(30)
            .withTimeoutSeconds(10)
            .withSuccessThreshold(1)
            .withFailureThreshold(3)
            .build();
        return combineProbeDefinitions(defaultLivenessProbe, super.getLivenessProbe());
    }

    /**
     *
     * @return The readiness probe for the schema registry proxy container
     */
    protected Probe createProxyReadinessProbe() {
        Probe defaultReadinessProbe = new ProbeBuilder()
            .withNewHttpGet()
            .withPath("/liveness")
            .withNewPort(Endpoint.getPodToPodPort(tlsEnabled()))
            .withScheme(getHealthCheckProtocol())
            .withHttpHeaders(new HTTPHeaderBuilder()
                .withName("Accept")
                .withValue("*/*")
                .build())
            .endHttpGet()
            .withInitialDelaySeconds(10)
            .withPeriodSeconds(60)
            .withTimeoutSeconds(10)
            .withSuccessThreshold(1)
            .withFailureThreshold(2)
            .build();
        return combineProbeDefinitions(defaultReadinessProbe, super.getReadinessProbe());
    }

    /**
     * 
     * @param namespace
     * @param replicas
     * @param storage
     * @return The PersistentVolumeClaim for the schema registry pod
     */
    private PersistentVolumeClaim createSchemaRegistryPersistentVolumeClaim(String namespace, int replicas, PersistentClaimStorage storage) {
        Map<String, Quantity> requests = new HashMap<>();
        requests.put("storage", new Quantity(Optional.ofNullable(storage.getSize()).orElse("1Gi"), null));

        LabelSelector selector = null;
        if (storage.getSelector() != null && !storage.getSelector().isEmpty()) {
            selector = new LabelSelector(null, storage.getSelector());
        }

        String storageClass = Optional.ofNullable(storage.getStorageClass()).orElse("");
        
        String accessMode = Optional.ofNullable(storage.getAdditionalProperties())
            .map(ap -> ap.get("accessMode"))
            .map(obj -> obj.toString())
            .orElse(replicas > 1 ? "ReadWriteMany" : "ReadWriteOnce");

        PersistentVolumeClaimBuilder pvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                    .withName(getDefaultResourceName())
                    .withNamespace(namespace)
                    .addToLabels(labels().toMap())
                .endMetadata()
                .withNewSpec()
                    .withAccessModes(accessMode)
                    .withNewResources()
                        .addToRequests(requests)
                    .endResources()
                    .withStorageClassName(storageClass)
                    .withSelector(selector)
                .endSpec();

        if (storage.isDeleteClaim()) {
            pvc = pvc.editMetadata()
                    .withOwnerReferences(getEventStreamsOwnerReference())
                .endMetadata();
        }

        return pvc.build();
    }

    /**
     * @return Deployment return the deployment with the specified generation id this is used
     * to control rolling updates, for example when the cert secret changes.
     */
    public Deployment getDeployment(String certGenerationID) {
        if (certGenerationID != null && deployment != null) {
            deployment.getMetadata().getLabels().put(CERT_GENERATION_KEY, certGenerationID);
            deployment.getSpec().getTemplate().getMetadata().getLabels().put(CERT_GENERATION_KEY, certGenerationID);
        }
        return deployment;
    }

    @Override
    protected List<Endpoint> createDefaultEndpoints(boolean kafkaAuthenticationEnabled) {
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(Endpoint.createDefaultExternalEndpoint(kafkaAuthenticationEnabled));
        return endpoints;
    }

    @Override
    protected List<Endpoint> createP2PEndpoints(EventStreams instance) {
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(Endpoint.createP2PEndpoint(instance, getP2PAuthenticationMechanisms(instance), Collections.singletonList(uniqueInstanceLabels())));
        return endpoints;
    }

    public List<String> getP2PAuthenticationMechanisms(EventStreams instance) {
        return isKafkaAuthenticationEnabled(instance) ? Arrays.asList(Endpoint.IAM_BEARER_KEY, Endpoint.MAC_KEY) : Collections.emptyList();
    }

    /**
     * @return Deployment return the deployment with an empty generation id
     */
    public Deployment getDeployment() {
        return getDeployment("");
    }

    /**
     * @return getServiceAccount return the service account
     */
    public ServiceAccount getServiceAccount() {
        return this.serviceAccount;
    }

    /**
     * @return NetworkPolicy return the network policy
     */
    public NetworkPolicy getNetworkPolicy() {
        return this.networkPolicy;
    }

    private NetworkPolicy createNetworkPolicy() {
        List<NetworkPolicyIngressRule> ingressRules = new ArrayList<>();

        endpoints.forEach(endpoint -> ingressRules.add(createIngressRule(endpoint.getPort(), endpoint.getEndpointIngressLabels())));

        return createNetworkPolicy(createLabelSelector(APPLICATION_NAME), ingressRules, null);
    }

    /**
     * @return PersistentVolumeClaim return the persistentVolumeClaim
     */
    public PersistentVolumeClaim getPersistentVolumeClaim() {
        return this.pvc;
    }

    private String getLoggingString(Logging logging, String defaultString) {
        String loggingString = defaultString;
        if (logging != null && InlineLogging.TYPE_INLINE.equals(logging.getType())) {
            Map<String, String> loggers = ((InlineLogging) logging).getLoggers();
            if (loggers != null) {
                String firstKey = loggers.keySet().stream().findFirst().orElse(null);
                if (firstKey != null) {
                    loggingString = loggers.get(firstKey);
                }
            }
        }
        return loggingString;
    }
}