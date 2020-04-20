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

import com.ibm.eventstreams.Main;
import com.ibm.eventstreams.api.DefaultResourceRequirements;
import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.spec.ComponentSpec;
import com.ibm.eventstreams.api.spec.ComponentTemplate;
import com.ibm.eventstreams.api.spec.ContainerSpec;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.ImagesSpec;
import com.ibm.eventstreams.api.spec.SecuritySpec;
import com.ibm.eventstreams.controller.EventStreamsOperatorConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HTTPHeaderBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.strimzi.api.kafka.model.status.ListenerStatus;
import io.strimzi.api.kafka.model.template.PodTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RestProducerModel extends AbstractSecureEndpointsModel {

    public static final String COMPONENT_NAME = "recapi";
    public static final String APPLICATION_NAME = "rest-producer";
    public static final int DEFAULT_REPLICAS = 1;
    private static final String DEFAULT_IBMCOM_IMAGE = "ibmcom/rest-producer:latest";

    public static final String CLUSTER_CA_VOLUME_MOUNT_NAME = "cluster-ca";
    public static final String CERTS_VOLUME_MOUNT_NAME = "certs";
    public static final String IBMCLOUD_CA_VOLUME_MOUNT_NAME = "ibmcloud";
    public static final String CLIENT_CA_VOLUME_MOUNT_NAME = "client-ca";

    public static final String CERTIFICATE_PATH = "/certs";
    public static final String KAFKA_USER_CERTIFICATE_PATH = CERTIFICATE_PATH + File.separator + "p2p";
    public static final String CLUSTER_CERTIFICATE_PATH = CERTIFICATE_PATH + File.separator + "cluster";
    public static final String CLIENT_CA_CERTIFICATE_PATH = CERTIFICATE_PATH + File.separator + "client";
    public static final String IBMCLOUD_CA_CERTIFICATE_PATH = CERTIFICATE_PATH + File.separator + "ibmcloud";

    private static final String CLIENT_ID_KEY = "CLIENT_ID";
    private static final String CLIENT_SECRET_KEY = "CLIENT_SECRET";

    private String traceString = "info";
    private final String icpClusterName;
    private final String iamServerURL;
    private final String ibmcloudCASecretName;

    // Deployed resources
    private Deployment deployment;
    private ServiceAccount serviceAccount;
    private NetworkPolicy networkPolicy;
    private List<ListenerStatus> kafkaListeners;

    /**
     * This class is used to model the kube resources required for the deployment of the rest producer
     * @param instance
     * @param imageConfig
     * @param kafkaListeners
     */
    public RestProducerModel(EventStreams instance,
                             EventStreamsOperatorConfig.ImageLookup imageConfig,
                             List<ListenerStatus> kafkaListeners,
                             Map<String, String> icpClusterData) {
        super(instance, instance.getSpec().getRestProducer(), COMPONENT_NAME, APPLICATION_NAME);
        this.kafkaListeners = kafkaListeners != null ? new ArrayList<>(kafkaListeners) : new ArrayList<>();

        this.icpClusterName = icpClusterData.getOrDefault("cluster_name", "null");
        this.iamServerURL = icpClusterData.getOrDefault("cluster_endpoint", "null");

        ibmcloudCASecretName = ClusterSecretsModel.getIBMCloudSecretName(getInstanceName());

        Optional<ComponentSpec> restProducerSpec = Optional.ofNullable(instance.getSpec()).map(EventStreamsSpec::getRestProducer);

        if (restProducerSpec.isPresent()) {
            setOwnerReference(instance);
            setReplicas(restProducerSpec.map(ComponentSpec::getReplicas).orElse(DEFAULT_REPLICAS));
            setEnvVars(restProducerSpec.map(ContainerSpec::getEnvVars).orElseGet(ArrayList::new));
            setResourceRequirements(restProducerSpec.map(ComponentSpec::getResources).orElseGet(ResourceRequirements::new));
            setPodTemplate(restProducerSpec.map(ComponentSpec::getTemplate)
                            .map(ComponentTemplate::getPod)
                            .orElseGet(PodTemplate::new));
            setTlsVersion(Optional.ofNullable(instance.getSpec())
                            .map(EventStreamsSpec::getSecurity)
                            .map(SecuritySpec::getInternalTls)
                            .orElse(DEFAULT_INTERNAL_TLS));
            setGlobalPullSecrets(Optional.ofNullable(instance.getSpec())
                                    .map(EventStreamsSpec::getImages)
                                    .map(ImagesSpec::getPullSecrets)
                                    .orElseGet(imageConfig::getPullSecrets));
            setImage(firstDefinedImage(
                    DEFAULT_IBMCOM_IMAGE,
                            restProducerSpec.map(ContainerSpec::getImage),
                            imageConfig.getRestProducerImage()));
            setCustomImage(DEFAULT_IBMCOM_IMAGE, imageConfig.getRestProducerImage());
            setLivenessProbe(restProducerSpec.map(ComponentSpec::getLivenessProbe)
                    .orElseGet(io.strimzi.api.kafka.model.Probe::new));
            setReadinessProbe(restProducerSpec.map(ComponentSpec::getReadinessProbe)
                    .orElseGet(io.strimzi.api.kafka.model.Probe::new));

            deployment = createDeployment(getContainers(), getVolumes());
            serviceAccount = createServiceAccount();
            networkPolicy = createNetworkPolicy();

            createService(EndpointServiceType.INTERNAL);
            createService(EndpointServiceType.ROUTE);
            createService(EndpointServiceType.NODE_PORT);
            routes = createRoutesFromEndpoints();
        }
    }

    /**
     * 
     * @return A list of volumes to put into the rest producer pod
     */
    private List<Volume> getVolumes() {
        List<Volume> volumes =  getSecurityVolumes();

        // Add The IAM Specific Volumes.  If we need to build without IAM Support we can put a variable check
        // here.
        volumes.add(new VolumeBuilder()
            .withNewName(IBMCLOUD_CA_VOLUME_MOUNT_NAME)
            .withNewSecret()
            .withNewSecretName(ibmcloudCASecretName)
            .addNewItem().withNewKey(CA_CERT).withNewPath(CA_CERT).endItem()
            .endSecret()
            .build());

        return volumes;
    }

    /**
     * 
     * @return A list of containers to put in the rest producer pod
     */
    private List<Container> getContainers() {
        return Arrays.asList(getRestProducerContainer());
    }

    /**
     * 
     * @return A list of default environment variables to go into the rest producer container
     */
    private List<EnvVar> getDefaultEnvVars() {
        String internalBootstrap = getInternalKafkaBootstrap(kafkaListeners);
        String runasBootstrap = getRunAsKafkaBootstrap(kafkaListeners);
        String schemaRegistryEndpoint =  getInternalServiceName(getInstanceName(), SchemaRegistryModel.COMPONENT_NAME) + "." +  getNamespace() + ".svc." + Main.CLUSTER_NAME + ":" + Endpoint.getPodToPodPort(tlsEnabled());

        ArrayList<EnvVar> envVars = new ArrayList<>(Arrays.asList(
            new EnvVarBuilder().withName("RELEASE").withValue(getInstanceName()).build(),
            new EnvVarBuilder().withName("LICENSE").withValue("accept").build(),
            new EnvVarBuilder().withName("NAMESPACE").withValue(getNamespace()).build(),
            new EnvVarBuilder().withName("KAFKA_BOOTSTRAP_SERVERS").withValue(internalBootstrap).build(),
            new EnvVarBuilder().withName("RUNAS_KAFKA_BOOTSTRAP_SERVERS").withValue(runasBootstrap).build(),
            new EnvVarBuilder().withName("SCHEMA_REGISTRY_URL").withValue(schemaRegistryEndpoint).build(),
            new EnvVarBuilder().withName("SCHEMA_REGISTRY_SECURITY_PROTOCOL").withValue(getUrlProtocol()).build(),
            new EnvVarBuilder().withName("MAX_KEY_SIZE").withValue("4096").build(),
            new EnvVarBuilder().withName("MAX_MESSAGE_SIZE").withValue("65536").build(),
            new EnvVarBuilder().withName("PRODUCER_CACHE_SIZE").withValue("10").build(),
            new EnvVarBuilder().withName("MAX_BLOCK_MS").withValue("2000").build(),
            new EnvVarBuilder().withName("DELIVERY_TIMEOUT_MS").withValue("2000").build(),
            new EnvVarBuilder().withName("REQUEST_TIMEOUT_MS").withValue("1000").build(),
            new EnvVarBuilder().withName("SKIP_SSL_VALIDATION_SCHEMA_REGISTRY").withValue("true").build(),
            new EnvVarBuilder().withName("TRACE_SPEC").withValue(traceString).build(),
            // Add The IAM Specific Envars.  If we need to build without IAM Support we can put a variable check
            // here.
            new EnvVarBuilder().withName("IAM_CLUSTER_NAME").withValue(icpClusterName).build(),
            new EnvVarBuilder().withName("IAM_SERVER_URL").withValue(iamServerURL).build(),
            new EnvVarBuilder().withName("IAM_SERVER_CA_CERT").withValue(IBMCLOUD_CA_CERTIFICATE_PATH + File.separator + CA_CERT).build(),
            new EnvVarBuilder()
                .withName("CLIENT_ID")
                .withNewValueFrom()
                    .withNewSecretKeyRef()
                        .withName(getResourcePrefix() + "-oidc-secret")
                        .withKey(CLIENT_ID_KEY)
                    .endSecretKeyRef()
                .endValueFrom()
                .build(),
            new EnvVarBuilder()
                .withName("CLIENT_SECRET")
                .withNewValueFrom()
                    .withNewSecretKeyRef()
                        .withName(getResourcePrefix() + "-oidc-secret")
                        .withKey(CLIENT_SECRET_KEY)
                    .endSecretKeyRef()
                .endValueFrom()
                .build(),
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
        configureSecurityEnvVars(envVars);

        return envVars;
    }

    /**
     * 
     * @return The rest producer container
     */
    private Container getRestProducerContainer() {
        List<EnvVar> envVars = combineEnvVarListsNoDuplicateKeys(getDefaultEnvVars());

        ContainerBuilder container =  new ContainerBuilder()
            .withName(COMPONENT_NAME)
            .withImage(getImage())
            .withEnv(envVars)
            .withSecurityContext(getSecurityContext(false))
            .withResources(getResourceRequirements(DefaultResourceRequirements.REST_PRODUCER))
            .withLivenessProbe(createLivenessProbe())
            .withReadinessProbe(createReadinessProbe())
            .addNewVolumeMount()
                .withNewName(IBMCLOUD_CA_VOLUME_MOUNT_NAME)
                .withMountPath(IBMCLOUD_CA_CERTIFICATE_PATH)
                .withReadOnly(true)
            .endVolumeMount();


        configureSecurityVolumeMounts(container);

        return container.build();
    }

    /**
     * 
     * @return The liveness probe for the rest producer container
     */
    protected Probe createLivenessProbe() {
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
            .withInitialDelaySeconds(240)
            .withPeriodSeconds(30)
            .withTimeoutSeconds(10)
            .withSuccessThreshold(1)
            .withFailureThreshold(10)
            .build();
        return combineProbeDefinitions(defaultLivenessProbe, super.getLivenessProbe());
    }

    /**
     * 
     * @return The readiness probe for the rest producer container
     */
    protected Probe createReadinessProbe() {
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
            .withInitialDelaySeconds(60)
            .withPeriodSeconds(10)
            .withTimeoutSeconds(10)
            .withSuccessThreshold(1)
            .withFailureThreshold(3)
            .build();
        return combineProbeDefinitions(defaultReadinessProbe, super.getReadinessProbe());
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

        endpoints.forEach(endpoint -> ingressRules.add(createIngressRule(endpoint.getPort(), new HashMap<>())));

        return createNetworkPolicy(createLabelSelector(COMPONENT_NAME), ingressRules, null);
    }

}
