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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ibm.eventstreams.Main;
import com.ibm.eventstreams.api.DefaultResourceRequirements;
import com.ibm.eventstreams.api.Listener;
import com.ibm.eventstreams.api.spec.ComponentSpec;
import com.ibm.eventstreams.api.spec.ComponentTemplate;
import com.ibm.eventstreams.api.spec.ContainerSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.ImagesSpec;
import com.ibm.eventstreams.api.spec.SchemaRegistrySpec;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;

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
import io.strimzi.api.kafka.model.storage.EphemeralStorage;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorage;
import io.strimzi.api.kafka.model.storage.Storage;
import io.strimzi.api.kafka.model.template.PodTemplate;

public class SchemaRegistryModel extends AbstractSecureEndpointModel {

    // static variables
    public static final String COMPONENT_NAME = "schema-registry";
    public static final String AVRO_SERVICE_CONTAINER_NAME = "avro-service";
    public static final int AVRO_SERVICE_PORT = 3080;
    public static final int DEFAULT_REPLICAS = 1;
    private static final String TEMP_DIR_NAME = "tempdir";
    private static final String SHARED_VOLUME_MOUNT_NAME = "shared";
    protected static final String LOG_LEVEL_ENV_NAME = "TRACE_LEVEL";
    protected static final String AVRO_LOG_LEVEL_ENV_NAME = "LOG_LEVEL";
    private static final String DEFAULT_LOG_STRING = "INFO";
    private static final String DEFAULT_AVRO_LOG_STRING = "info";
    private static final String DEFAULT_IBMCOM_SCHEMA_REGISTRY_IMAGE = "ibmcom/schema-registry:latest";
    private static final String DEFAULT_IBMCOM_AVRO_IMAGE = "ibmcom/avro:latest";

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


    private Storage storage;

    /**
     * This class is used to model the kube resources required for the correct deployment of the schema registry
     * @param instance
     * @param imageConfig
     */
    public SchemaRegistryModel(EventStreams instance,
                               EventStreamsOperatorConfig.ImageLookup imageConfig) {
        super(instance, instance.getMetadata().getNamespace(), COMPONENT_NAME);

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
                        .orElseGet(Collections::emptyList));
            setEncryption(Optional.ofNullable(instance.getSpec())
                        .map(EventStreamsSpec::getSecurity)
                        .map(SecuritySpec::getEncryption)
                        .orElse(DEFAULT_ENCRYPTION));

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

            setCustomImages(imageConfig.getSchemaRegistryImage(), imageConfig.getSchemaRegistryAvroImage());

            deployment = createDeployment(getContainers(), getVolumes());

            createInternalService();
            createExternalService();
            createRoutesFromListeners();

            serviceAccount = createServiceAccount();
            networkPolicy = createNetworkPolicy();
            if (storage instanceof PersistentClaimStorage) {
                pvc = createSchemaRegistryPersistentVolumeClaim(instance.getMetadata().getNamespace(), replicas, (PersistentClaimStorage) storage);
            } else {
                pvc = null;
            }
        }
    }

    protected void setCustomImages(Optional<String> defaultEnvSchemaImage, Optional<String> defaultEnvAvroImage) {
        List<String> defaultSchemaImages = new ArrayList<>();
        defaultSchemaImages.add(DEFAULT_IBMCOM_SCHEMA_REGISTRY_IMAGE);
        defaultSchemaImages.add(defaultEnvSchemaImage.isPresent() ? defaultEnvSchemaImage.get() : "");
        boolean schemaCustomImage = defaultSchemaImages
                .stream()
                .filter(image -> this.image.equals(image))
                .findFirst().isPresent() ? false : true;

        List<String> defaultAvroImages = new ArrayList<>();
        defaultAvroImages.add(DEFAULT_IBMCOM_AVRO_IMAGE);
        defaultAvroImages.add(defaultEnvAvroImage.isPresent() ? defaultEnvAvroImage.get() : "");
        boolean avroCustomImage = defaultAvroImages
                .stream()
                .filter(image -> avroImage.equals(image))
                .findFirst().isPresent() ? false : true;

        this.customImage = schemaCustomImage || avroCustomImage;
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

        Volume certsVolume = new VolumeBuilder()
                .withNewName(CERTS_VOLUME_MOUNT_NAME)
                .withNewSecret()
                    .withNewSecretName(getCertSecretName()) //mount everything in the secret into this volume
                .endSecret()
                .build();
        volumes.add(certsVolume);
        volumes.add(createKafkaUserCertVolume());

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

        return volumes;
    }

    /**
     * 
     * @return A list of containers to put in the schema registry pod
     */
    private List<Container> getContainers() {
        return Arrays.asList(getSchemaRegistryContainer(), getAvroServiceContainer());
    }

    /**
     * 
     * @return The schema registry container
     */
    private Container getSchemaRegistryContainer() {
        List<Listener> listeners = getListeners();
        listeners.add(Listener.podToPodListener(tlsEnabled()));
        List<EnvVar> envVarDefaults = Arrays.asList(
            new EnvVarBuilder().withName("ENDPOINTS").withValue(Listener.createEndpointsString(listeners)).build(),
            new EnvVarBuilder().withName("LICENSE").withValue("accept").build(),
            new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build(),
            new EnvVarBuilder().withName("CONFIGMAP").withValue("releaseConfigMap").build(),
            new EnvVarBuilder().withName("CLUSTER_NAME").withValue(Main.CLUSTER_NAME).build(),
            new EnvVarBuilder().withName("TRACE_LEVEL").withValue(logString).build(),
            new EnvVarBuilder().withName("NODE_ENV").withValue("production").build(),
            new EnvVarBuilder().withName("AVRO_CONTAINER_HOST").withValue("127.0.0.1").build(),
            new EnvVarBuilder().withName("AVRO_CONTAINER_PORT").withValue(Integer.toString(AVRO_SERVICE_PORT)).build(),
            new EnvVarBuilder().withName("SCHEMA_DATA_DIRECTORY").withValue("/var/lib/schemas").build(),
            new EnvVarBuilder().withName("SCHEMA_TEMP_DIRECTORY").withValue("/var/lib/tmp").build(),
            new EnvVarBuilder().withName("ID").withValue("id").build(),
            new EnvVarBuilder().withName("ESFF_SECURITY_AUTHZ").withValue("false").build()
        );

        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(envVarDefaults);

        return new ContainerBuilder()
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
            .addNewVolumeMount()
                .withNewName(CERTS_VOLUME_MOUNT_NAME)
                .withMountPath("/certs")
                .withNewReadOnly(true)
            .endVolumeMount()
            .addNewVolumeMount()
                .withNewName(KAFKA_USER_SECRET_VOLUME_NAME)
                .withMountPath("/certs/p2p")
                .withNewReadOnly(true)
            .endVolumeMount()
            .withLivenessProbe(createLivenessProbe())
            .withReadinessProbe(createReadinessProbe())
            .build();
    }

    /**
     * 
     * @return The liveness probe for the schema registry container
     */
    protected Probe createLivenessProbe() {
        Probe defaultLivenessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/live")
                .withNewPort(Listener.podToPodListener(tlsEnabled()).getPort())
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
     * @return The liveness probe for the schema registry container
     */
    protected Probe createReadinessProbe() {
        Probe defaultReadinessProbe = new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/ready")
                .withNewPort(Listener.podToPodListener(tlsEnabled()).getPort())
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
                    .addToLabels(getComponentLabels())
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
        if (certGenerationID != null) {
            deployment.getMetadata().getLabels().put(CERT_GENERATION_KEY, certGenerationID);
            deployment.getSpec().getTemplate().getMetadata().getLabels().put(CERT_GENERATION_KEY, certGenerationID);
        }
        return deployment;
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
        List<NetworkPolicyIngressRule> ingressRules = new ArrayList<>(1);
        List<Listener> listeners = getListeners();
        listeners.add(Listener.podToPodListener(tlsEnabled()));
        listeners.forEach(listener -> {
            ingressRules.add(createIngressRule(listener.getPort(), new HashMap<>()));
        });

        return createNetworkPolicy(createLabelSelector(COMPONENT_NAME), ingressRules, null);
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
            String firstKey = loggers.keySet().stream().findFirst().orElse(null);
            if (firstKey != null) {
                loggingString = loggers.get(firstKey);
            }
        }
        return loggingString;
    }
}