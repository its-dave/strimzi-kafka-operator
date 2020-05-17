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
package com.ibm.iam.api.controller;

import com.ibm.iam.api.spec.Cp4iServicesBinding;
import com.ibm.iam.api.spec.Cp4iServicesBindingDoneable;
import com.ibm.iam.api.spec.Cp4iServicesBindingList;
import com.ibm.iam.api.status.Cp4iServicesBindingStatus;
import com.ibm.eventstreams.api.Crds;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.operator.common.operator.resource.AbstractWatchableResourceOperator;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public class Cp4iServicesBindingResourceOperator extends
        AbstractWatchableResourceOperator<KubernetesClient, Cp4iServicesBinding, Cp4iServicesBindingList, Cp4iServicesBindingDoneable, Resource<Cp4iServicesBinding, Cp4iServicesBindingDoneable>> {

    private static final Logger log = LogManager.getLogger(Cp4iServicesBindingResourceOperator.class.getName());

    private KubernetesClient client;

    public Cp4iServicesBindingResourceOperator(Vertx vertx, KubernetesClient client, String resourceKind) {
        super(vertx, client, resourceKind);
        log.info("Creating Cp4iServicesBindingResourceOperator");
        this.client = client;
    }

    @Override
    protected MixedOperation<Cp4iServicesBinding, Cp4iServicesBindingList, Cp4iServicesBindingDoneable, Resource<Cp4iServicesBinding, Cp4iServicesBindingDoneable>> operation() {
        return Crds.cp4iServicesBindingOperation(client);
    }

    /**
     * Returns the Cp4i header URL from the CP4I Services Binding Status to be used in the UI as a header
     * Returns an empty string if not found as CP4I is not a requirement
     *
     * @param namespace         Namespace.
     * @param cp4iInstanceName  Name of the Cp4i instance.
     * @param pollIntervalMs    Interval in which we poll.
     * @param timeoutMs         Timeout.
     * @return A future that succeeds regardless of whether the Cp4i Services Binding Status has a URL or not
     */
    public Future<Void> waitForCp4iServicesBindingStatusAndMaybeGetUrl(String namespace, String cp4iInstanceName, long pollIntervalMs, long timeoutMs) {
        return waitFor(namespace, cp4iInstanceName, pollIntervalMs, timeoutMs, this::cp4iServicesBindingStatusHasUrl);
    }

    /**
     * Checks if the Cp4i Services Binding Status has a URL
     *
     * @param namespace     The namespace.
     * @param name          The name of the Cp4i instance.
     * @return              Whether the Cp4i Services Binding Status has a URL
     */
    private boolean cp4iServicesBindingStatusHasUrl(String namespace, String name) {
        return getCp4iHeaderUrl(namespace, name)
            .isPresent();
    }

    /**
     * Returns the the header url from the cp4i status for the given namespace and name.
     *
     * @param namespace         The namespace.
     * @param cp4iInstanceName  The name of the cp4i instance.
     * @return                  Optional String containing the url, should it exist
     */
    public Optional<String> getCp4iHeaderUrl(String namespace, String cp4iInstanceName) {
        Cp4iServicesBinding cp4iServicesBinding = get(namespace, cp4iInstanceName);
        Optional<String> headerUrl = Optional.ofNullable(cp4iServicesBinding)
            .map(Cp4iServicesBinding::getStatus)
            .map(Cp4iServicesBindingStatus::getEndpoints)
            .map(endpoints -> endpoints.get(Cp4iServicesBindingStatus.URL_KEY));
        return headerUrl;
    }
}