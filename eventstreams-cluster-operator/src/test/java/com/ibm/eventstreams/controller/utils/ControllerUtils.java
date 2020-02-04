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
package com.ibm.eventstreams.controller.utils;

import com.ibm.eventstreams.controller.certifificates.EventStreamsCertificateManager;
import io.fabric8.kubernetes.api.model.Service;
import io.strimzi.certs.CertAndKey;
import io.strimzi.certs.Subject;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

public class ControllerUtils {
    public static X509Certificate checkCertificate(EventStreamsCertificateManager certificateManager, CertAndKey certAndKey) {
        try {
            EventStreamsCertificateManager.loadKey(certAndKey.key());
            X509Certificate certificate = EventStreamsCertificateManager.loadCert(certAndKey.cert());
            certificate.checkValidity(new Date());
            certificate.verify(certificateManager.getClusterCa().getPublicKey());
            return certificate;
        } catch (Exception e) {
            fail(e);
            return null;
        }
    }

    public static void checkSans(EventStreamsCertificateManager certificateManager, X509Certificate certificate, Service service, String additionalHost) {
        assertDoesNotThrow(() -> {
            List<String> sans = certificate.getSubjectAlternativeNames().stream()
                    .map(l -> l.get(1).toString())
                    .collect(Collectors.toList());
            List<String> additionalHosts = additionalHost.isEmpty() ? Collections.emptyList() : Collections.singletonList(additionalHost);
            Subject subject = certificateManager.createSubject(service, additionalHosts);
            assertThat("The certificate has the expected number of SANs", subject.subjectAltNames().size(), is(sans.size()));
            subject.subjectAltNames().values().forEach(sans::remove);
            assertThat("The certificate has the expected SANs", sans.size(), is(0));
        });
    }
}
