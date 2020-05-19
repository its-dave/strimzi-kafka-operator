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
import com.ibm.eventstreams.controller.models.StatusCondition;
import com.ibm.eventstreams.rest.Validation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class NameValidation implements Validation {

    private static final Logger log = LogManager.getLogger(NameValidation.class.getName());

    public static final int MAX_NAME_LENGTH = 16;

    private static final String VALID_NAME_REGEX = "[a-z]([-a-z0-9]*[a-z0-9])?";
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile(VALID_NAME_REGEX);

    public static final String INVALID_INSTANCE_NAME_REASON = "InvalidInstanceName";

    public static final String INSTANCE_NAME_TOO_LONG_MESSAGE = "'%s' instance name is too long."
        + String.format("Instance names cannot be longer than %d characters. ", MAX_NAME_LENGTH)
        + "Edit metadata.name to provide a valid instance name.";

    public static final String INSTANCE_NAME_DOES_NOT_FOLLOW_REGEX_MESSAGE = "'%s' instance name is an invalid name."
        + String.format("Instance names are lower case alphanumeric characters or dashes (-), and must start and end with an alphabetic character (%s). ", VALID_NAME_REGEX)
        + "Edit metadata.name to provide a valid instance name.";

    public List<StatusCondition> validateCr(EventStreams customResourceSpec) {
        log.traceEntry(() -> customResourceSpec);
        List<StatusCondition> conditions = new ArrayList<>();

        String name = customResourceSpec.getMetadata().getName();
        if (name.length() > MAX_NAME_LENGTH) {
            conditions.add(StatusCondition.createErrorCondition(INVALID_INSTANCE_NAME_REASON, String.format(INSTANCE_NAME_TOO_LONG_MESSAGE, name)));
        }
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            conditions.add(StatusCondition.createErrorCondition(INVALID_INSTANCE_NAME_REASON, String.format(INSTANCE_NAME_DOES_NOT_FOLLOW_REGEX_MESSAGE, name)));
        }
        return log.traceExit(conditions);
    }
}
