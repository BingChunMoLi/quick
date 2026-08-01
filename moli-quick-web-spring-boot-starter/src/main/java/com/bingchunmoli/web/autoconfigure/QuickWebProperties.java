package com.bingchunmoli.web.autoconfigure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.core.Ordered;

/**
 * Configuration properties for reusable Spring MVC conventions.
 */
@ConfigurationProperties(prefix = "moli.web")
@Validated
public class QuickWebProperties {

    private boolean enabled = true;
    @Valid
    private final RequestId requestId = new RequestId();
    @Valid
    private final ExceptionHandling exceptionHandling = new ExceptionHandling();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RequestId getRequestId() {
        return requestId;
    }

    public ExceptionHandling getExceptionHandling() {
        return exceptionHandling;
    }

    public static class RequestId {

        private boolean enabled = true;
        @NotBlank
        private String headerName = "X-Request-Id";
        @NotBlank
        private String mdcKey = "requestId";
        private boolean acceptIncoming = true;
        @Min(1)
        private int maxLength = 128;
        private int filterOrder = Ordered.HIGHEST_PRECEDENCE + 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getMdcKey() {
            return mdcKey;
        }

        public void setMdcKey(String mdcKey) {
            this.mdcKey = mdcKey;
        }

        public boolean isAcceptIncoming() {
            return acceptIncoming;
        }

        public void setAcceptIncoming(boolean acceptIncoming) {
            this.acceptIncoming = acceptIncoming;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(int maxLength) {
            this.maxLength = maxLength;
        }

        public int getFilterOrder() {
            return filterOrder;
        }

        public void setFilterOrder(int filterOrder) {
            this.filterOrder = filterOrder;
        }
    }

    public static class ExceptionHandling {

        private boolean enabled = true;
        private boolean includeExceptionMessage;
        @NotBlank
        private String clientErrorCode = "A0001";
        @NotBlank
        private String validationErrorCode = "A0400";
        @NotBlank
        private String malformedJsonCode = "A0427";
        @NotBlank
        private String notFoundCode = "C0113";
        @NotBlank
        private String methodNotAllowedCode = "A0001";
        @NotBlank
        private String systemErrorCode = "B0001";
        @NotBlank
        private String duplicateRequestCode = "A0506";
        @NotBlank
        private String rateLimitCode = "A0501";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isIncludeExceptionMessage() {
            return includeExceptionMessage;
        }

        public void setIncludeExceptionMessage(boolean includeExceptionMessage) {
            this.includeExceptionMessage = includeExceptionMessage;
        }

        public String getClientErrorCode() {
            return clientErrorCode;
        }

        public void setClientErrorCode(String clientErrorCode) {
            this.clientErrorCode = clientErrorCode;
        }

        public String getValidationErrorCode() {
            return validationErrorCode;
        }

        public void setValidationErrorCode(String validationErrorCode) {
            this.validationErrorCode = validationErrorCode;
        }

        public String getMalformedJsonCode() {
            return malformedJsonCode;
        }

        public void setMalformedJsonCode(String malformedJsonCode) {
            this.malformedJsonCode = malformedJsonCode;
        }

        public String getNotFoundCode() {
            return notFoundCode;
        }

        public void setNotFoundCode(String notFoundCode) {
            this.notFoundCode = notFoundCode;
        }

        public String getMethodNotAllowedCode() {
            return methodNotAllowedCode;
        }

        public void setMethodNotAllowedCode(String methodNotAllowedCode) {
            this.methodNotAllowedCode = methodNotAllowedCode;
        }

        public String getSystemErrorCode() {
            return systemErrorCode;
        }

        public void setSystemErrorCode(String systemErrorCode) {
            this.systemErrorCode = systemErrorCode;
        }

        public String getDuplicateRequestCode() {
            return duplicateRequestCode;
        }

        public void setDuplicateRequestCode(String duplicateRequestCode) {
            this.duplicateRequestCode = duplicateRequestCode;
        }

        public String getRateLimitCode() {
            return rateLimitCode;
        }

        public void setRateLimitCode(String rateLimitCode) {
            this.rateLimitCode = rateLimitCode;
        }
    }
}
