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
package com.ibm.eventstreams.rest;

import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.LicenseSpec;
import com.ibm.eventstreams.controller.models.StatusCondition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Optional;


public class LicenseValidation implements Validation {

    private static final Logger log = LogManager.getLogger(NameValidation.class.getName());
    public static final String LICENSE_NOT_ACCEPTED_REASON = "LicenseNotAccepted";
    public static final String LICENSE_NOT_ACCEPTED_MESSAGE = "The license has not been accepted. "
        + "To indicate that you have accepted the terms of the IBM Event Streams license, edit spec.license.accept and provide value true";

    public List<StatusCondition> validateCr(EventStreams spec) {
        log.traceEntry(() -> spec);

        boolean licenseNotAccepted = Optional.ofNullable(spec.getSpec())
            .map(EventStreamsSpec::getLicense)
            .map(LicenseSpec::isAccept)
            .map(accepted -> !accepted)
            .orElse(true);

        return log.traceExit(licenseNotAccepted ? Collections.singletonList(StatusCondition.createWarningCondition(LICENSE_NOT_ACCEPTED_REASON, LICENSE_NOT_ACCEPTED_MESSAGE)) : Collections.emptyList());
    }
}
