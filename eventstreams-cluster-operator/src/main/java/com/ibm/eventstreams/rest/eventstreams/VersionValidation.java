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
package com.ibm.eventstreams.rest.eventstreams;

import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicator;
import com.ibm.eventstreams.api.status.EventStreamsVersions;
import com.ibm.eventstreams.controller.models.StatusCondition;
import com.ibm.eventstreams.rest.Validation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

public class VersionValidation implements Validation {
    private static final Logger log = LogManager.getLogger(VersionValidation.class.getName());

    public static final List<String> VALID_APP_VERSIONS = unmodifiableList(asList(EventStreamsVersions.OPERAND_VERSION, EventStreamsVersions.AUTO_UPGRADE_VERSION));
    public static final String INVALID_VERSION_REASON = "InvalidVersion";
    public static final String INVALID_VERSION_MESSAGE = "Invalid version '%s'. "
        + "Valid versions are " + VersionValidation.VALID_APP_VERSIONS.toString() + ". "
        + "Edit spec.version to provide one of the valid versions.";

    public List<StatusCondition> validateCr(EventStreamsGeoReplicator spec) {
        log.traceEntry(() -> spec);
        return log.traceExit(!VALID_APP_VERSIONS.contains(spec.getSpec().getVersion())
            ? Collections.singletonList(StatusCondition.createErrorCondition(INVALID_VERSION_REASON, String.format(INVALID_VERSION_MESSAGE, spec.getSpec().getVersion())))
            : Collections.emptyList());
    }

    @Override
    public List<StatusCondition> validateCr(EventStreams spec) {
        log.traceEntry(() -> spec);
        return log.traceExit(!VALID_APP_VERSIONS.contains(spec.getSpec().getVersion())
            ? Collections.singletonList(StatusCondition.createErrorCondition(INVALID_VERSION_REASON, String.format(INVALID_VERSION_MESSAGE, spec.getSpec().getVersion())))
            : Collections.emptyList());
    }
}
