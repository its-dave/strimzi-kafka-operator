/*
 * IBM Confidential
 * OCO Source Materials
 *
 * 5737-H33
 *
 * (C) Copyright IBM Corp. 2020  All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 */
package com.ibm.eventstreams.controller.certificates;

import com.ibm.eventstreams.api.model.CertificateSecretModel;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.strimzi.api.kafka.model.CertAndKeySecretSource;
import io.strimzi.api.kafka.model.CertificateAuthority;
import io.strimzi.certs.CertAndKey;
import io.strimzi.certs.OpenSslCertManager;
import io.strimzi.certs.Subject;
import io.strimzi.operator.cluster.model.AbstractModel;
import io.strimzi.operator.cluster.model.Ca;
import io.strimzi.operator.cluster.model.ModelUtils;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class EventStreamsCertificateManager {
    private static final Logger log = LogManager.getLogger(EventStreamsCertificateManager.class.getName());
    private static final String ORGANISATION_NAME = "eventstreams.ibm.com";

    public static final int MAX_CHARS_IN_COMMON_NAME = 64;

    private SecretOperator secretOperator;
    private String namespace;
    private String kafkaInstanceName;

    public EventStreamsCertificateManager(SecretOperator secretOperator, String namespace, String kafkaInstanceName) {
        this.secretOperator = secretOperator;
        this.namespace = namespace;
        this.kafkaInstanceName = kafkaInstanceName;
    }

    public CertAndKey generateCertificateAndKey(Service service, List<String> additionalHosts, String componentName) throws EventStreamsCertificateException {
        try {
            OpenSslCertManager certManager = new OpenSslCertManager();
            if (service == null && additionalHosts.isEmpty()) {
                throw new EventStreamsCertificateException("No hosts provided for certificate generation when service is null");
            }
            Subject subject = createSubject(service, additionalHosts, componentName);
            File keyFile = File.createTempFile("tls", "temp-key");
            File csrFile = File.createTempFile("tls", "temp-csr");
            File certFile = File.createTempFile("tls", "temp-cert");
            certManager.generateCsr(keyFile, csrFile, subject);
            certManager.generateCert(csrFile, getClusterCaKeyData(), getClusterCaData(), certFile, subject, CertificateAuthority.DEFAULT_CERTS_VALIDITY_DAYS);

            CertAndKey certAndKey = new CertAndKey(Files.readAllBytes(keyFile.toPath()), Files.readAllBytes(certFile.toPath()));
            delete(keyFile);
            delete(csrFile);
            delete(certFile);
            return certAndKey;
        } catch (IOException e) {
            throw new EventStreamsCertificateException(e);
        }
    }

    public X509Certificate getClusterCa() throws EventStreamsCertificateException {
        return loadCert(getClusterCaData());
    }

    private byte[] getClusterCaData() throws EventStreamsCertificateException {
        String clusterCaCertName = AbstractModel.clusterCaCertSecretName(kafkaInstanceName);
        Secret clusterCaSecret = getSecret(clusterCaCertName).orElseThrow(() -> new EventStreamsCertificateException("Cluster CA cert secret could not be found"));
        return getBase64DecodedSecretData(clusterCaSecret, Ca.CA_CRT);
    }

    private byte[] getClusterCaKeyData() throws EventStreamsCertificateException {
        String clusterCaKeyName = AbstractModel.clusterCaKeySecretName(kafkaInstanceName);
        Secret clusterCaKeySecret = getSecret(clusterCaKeyName).orElseThrow(() -> new EventStreamsCertificateException("Cluster CA key secret " + clusterCaKeyName + " could not be found"));
        return getBase64DecodedSecretData(clusterCaKeySecret, Ca.CA_KEY);
    }

    public Optional<Secret> getSecret(String secretName) {
        return Optional.ofNullable(secretOperator.get(namespace, secretName));
    }

    private byte[] getBase64DecodedSecretData(Secret secret, String key) {
        return Base64.getDecoder().decode(getSecretDataForKey(secret, key));
    }

    private String getSecretDataForKey(Secret secret, String key)  {
        Map<String, String> secretData = secret.getData();
        return Optional.ofNullable(secretData)
                .map(data -> data.get(key))
                .orElse("");
    }

    private void delete(File file) {
        if (!file.delete()) {
            log.warn("{} cannot be deleted", file.getName());
        }
    }

    public Subject createSubject(Service service, List<String> additionalHosts, String componentName) {
        Subject subject = new Subject();
        Map<String, String> sbjAltNames = new HashMap<>();
        String commonName = getCommonName(service, additionalHosts, componentName);
        int dnsNumber = 1;

        if (commonName != null) {
            subject.setOrganizationName(ORGANISATION_NAME);
            subject.setCommonName(commonName);

            for (String additionalHost : additionalHosts) {
                dnsNumber = putSubjectAlternativeNameAndIncrementDnsNumber(sbjAltNames, dnsNumber, additionalHost);
            }

            if (service != null) {
                String serviceName = service.getMetadata().getName();
                String namespace = service.getMetadata().getNamespace();

                dnsNumber = putSubjectAlternativeNameAndIncrementDnsNumber(sbjAltNames, dnsNumber, serviceName);
                dnsNumber = putSubjectAlternativeNameAndIncrementDnsNumber(sbjAltNames, dnsNumber, String.format("%s.%s", serviceName, namespace));
                dnsNumber = putSubjectAlternativeNameAndIncrementDnsNumber(sbjAltNames, dnsNumber, ModelUtils.serviceDnsNameWithoutClusterDomain(namespace, serviceName));
                putSubjectAlternativeNameAndIncrementDnsNumber(sbjAltNames, dnsNumber, ModelUtils.serviceDnsName(namespace, serviceName));
            }
        }

        subject.setSubjectAltNames(sbjAltNames);
        return subject;
    }

    private int putSubjectAlternativeNameAndIncrementDnsNumber(Map<String, String> sbjAltNames, int dnsNumber, String subjectAlternativeName) {
        sbjAltNames.put(String.format("DNS.%d", dnsNumber), subjectAlternativeName);
        return dnsNumber + 1;
    }

    private String getCommonName(Service service, List<String> additionalHosts, String componentName) {
        if (additionalHosts.size() > 0) {
            return getRouteCommonName(additionalHosts.get(0), componentName);
        }

        return service == null ? null : truncateCommonName(service.getMetadata().getName());
    }

    public String getRouteCommonName(String routeName, String componentName) {
        String[] routeNameParts = routeName.split(componentName);
        return routeNameParts.length > 1 ? truncateCommonName("*." + componentName + routeNameParts[1]) : truncateCommonName(routeName);
    }

    public String truncateCommonName(String name) {
        return name.length() <= MAX_CHARS_IN_COMMON_NAME ? name : "*" + name.substring(name.length() - MAX_CHARS_IN_COMMON_NAME + 1);
    }

    public static PrivateKey loadKey(byte[] keyData) throws EventStreamsCertificateException {
        try {
            String pemData = new String(keyData, StandardCharsets.UTF_8);
            String[] pemDataArr = pemData.split("-----END PRIVATE KEY-----");
            pemData = pemDataArr[0].replace("-----BEGIN PRIVATE KEY-----", "");
            pemData = pemData.replace("\n", "");
            pemData = pemData.trim();
            byte[] bytes = Base64.getDecoder().decode(pemData);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
            return factory.generatePrivate(spec);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new EventStreamsCertificateException(e);
        }
    }

    public static X509Certificate loadCert(byte[] certData) throws EventStreamsCertificateException {
        try {
            InputStream certPem = new ByteArrayInputStream(certData);
            CertificateFactory factory = CertificateFactory.getInstance("x509");
            return (X509Certificate) factory.generateCertificate(certPem);
        } catch (CertificateException e) {
            throw new EventStreamsCertificateException(e);
        }
    }

    public boolean shouldGenerateOrRenewCertificate(Secret certSecret, String certName, Supplier<Date> dateSupplier, Service service, List<String> additionalHosts, String componentName) {
        log.traceEntry(() -> certSecret, () -> certName, () -> dateSupplier, () -> service, () -> additionalHosts, () -> componentName);
        boolean isCertKeyPresent = Optional.ofNullable(certSecret.getData())
            .map(data -> data.containsKey(CertificateSecretModel.formatCertID(certName)))
            .orElse(false);
        if (!isCertKeyPresent) {
            return log.traceExit(true);
        }

        byte[] certData = getBase64DecodedSecretData(certSecret, CertificateSecretModel.formatCertID(certName));
        byte[] keyData = getBase64DecodedSecretData(certSecret, CertificateSecretModel.formatKeyID(certName));

        CertAndKey certAndKey = new CertAndKey(keyData, certData);
        try {
            testCertAndKey(certAndKey, dateSupplier, service, additionalHosts, componentName);
        } catch (EventStreamsCertificateException e) {
            log.debug(e);
            return log.traceExit(true);
        }
        return log.traceExit(false);
    }

    private void testCertAndKey(CertAndKey certAndKey, Supplier<Date> dateSupplier, Service service, List<String> additionalHosts, String componentName) throws EventStreamsCertificateException {
        try {
            X509Certificate cert = EventStreamsCertificateManager.loadCert(certAndKey.cert());
            EventStreamsCertificateManager.loadKey(certAndKey.key());
            cert.checkValidity(dateSupplier.get());
            cert.verify(getClusterCa().getPublicKey());
            checkSans(cert, createSubject(service, additionalHosts, componentName));
        } catch (SignatureException | NoSuchProviderException | InvalidKeyException | CertificateException | NoSuchAlgorithmException e) {
            throw new EventStreamsCertificateException(e);
        }
    }

    private void checkSans(X509Certificate certificate, Subject subject) throws EventStreamsCertificateException {
        try {
            List<String> sans = certificate.getSubjectAlternativeNames().stream()
                    .map(l -> l.get(1).toString())
                    .collect(Collectors.toList());
            if (subject.subjectAltNames().size() != sans.size()) {
                throw new EventStreamsCertificateException("Certificate has an unexpected number of SANs");
            }
            subject.subjectAltNames().values().forEach(sans::remove);
            if (sans.size() != 0) {
                throw new EventStreamsCertificateException("Certificate has incorrect SANs");
            }
        } catch (CertificateParsingException e) {
            throw new EventStreamsCertificateException(e);
        }
    }

    /**
     * If retrieved keys do not exist in the secret, fields are empty byte arrays
     */
    public CertAndKey certificateAndKey(Secret certSecret, String certKey, String keyKey) {
        byte[] keyData = getBase64DecodedSecretData(certSecret, keyKey);
        byte[] certData = getBase64DecodedSecretData(certSecret, certKey);
        return new CertAndKey(keyData, certData);
    }

    public Optional<CertAndKey> certificateAndKey(CertAndKeySecretSource secretSource) {
        String keyKey = secretSource.getKey();
        String certKey = secretSource.getCertificate();
        String secretName = secretSource.getSecretName();
        Optional<Secret> certSecret = getSecret(secretName);
        return certSecret.map(secret -> certificateAndKey(secret, certKey, keyKey));
    }

    public boolean sameCertAndKey(CertAndKey certAndKeyA, CertAndKey certAndKeyB) {
        return certAndKeyA.certAsBase64String().equals(certAndKeyB.certAsBase64String()) &&
                certAndKeyA.keyAsBase64String().equals(certAndKeyB.keyAsBase64String());
    }
}
