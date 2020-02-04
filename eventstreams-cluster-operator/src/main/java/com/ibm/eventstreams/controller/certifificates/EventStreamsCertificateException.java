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
package com.ibm.eventstreams.controller.certifificates;

import java.util.function.Supplier;

public class EventStreamsCertificateException extends Exception {

    public EventStreamsCertificateException() {
    }

    public EventStreamsCertificateException(String message) {
        super(message);
    }

    public EventStreamsCertificateException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventStreamsCertificateException(Throwable cause) {
        super(cause);
    }

    public EventStreamsCertificateException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public static Supplier<EventStreamsCertificateException> exceptionSupplier(String message) {
        return () -> new EventStreamsCertificateException(message);
    }
}
