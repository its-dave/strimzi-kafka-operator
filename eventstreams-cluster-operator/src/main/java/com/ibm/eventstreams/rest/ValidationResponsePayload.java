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


/**
 * Represents the response payload for a validating webhook as defined in
 * https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.14/#status-v1-meta
 */
public class ValidationResponsePayload {

    private ValidationResponse response;

    public static ValidationResponsePayload createSuccessResponse() {
        ValidationResponseStatus vrs = new ValidationResponseStatus(ValidationStatusType.Success, 200, "ok", "ok");
        ValidationResponse vr = new ValidationResponse(true, vrs);
        return new ValidationResponsePayload(vr);
    }
    public static ValidationResponsePayload createFailureResponse(String message, String reason) {
        ValidationResponseStatus vrs = new ValidationResponseStatus(ValidationStatusType.Failure, 400, message, reason);
        ValidationResponse vr = new ValidationResponse(false, vrs);
        return new ValidationResponsePayload(vr);
    }




    ValidationResponsePayload(ValidationResponse response) {
        this.response = response;
    }

    public ValidationResponse getResponse() {
        return response;
    }

    public void setResponse(ValidationResponse response) {
        this.response = response;
    }

    public static class ValidationResponse {
        public ValidationResponse(boolean allowed, ValidationResponseStatus status) {
            this.allowed = allowed;
            this.status = status;
        }

        private boolean allowed;
        private ValidationResponseStatus status;

        public boolean isAllowed() {
            return allowed;
        }

        public void setAllowed(boolean allowed) {
            this.allowed = allowed;
        }

        public ValidationResponseStatus getStatus() {
            return status;
        }

        public void setStatus(ValidationResponseStatus status) {
            this.status = status;
        }
    }
    public enum ValidationStatusType {
        Success, Failure
    }
    public static class ValidationResponseStatus {
        public ValidationResponseStatus(ValidationStatusType status, int httpStatusCode, String message, String reason) {
            this.status = status;
            this.code = httpStatusCode;
            this.message = message;
            this.reason = reason;
        }

        private ValidationStatusType status;
        private String message;
        private String reason;
        private int code;

        public ValidationStatusType getStatus() {
            return status;
        }

        public void setStatus(ValidationStatusType status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }
    }
}
