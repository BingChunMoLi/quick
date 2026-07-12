package com.bingchunmoli.security.web;

/**
 * Standard JSON response for quick security endpoints.
 *
 * @author MoLi
 */
public class QuickSecurityResponse<T> {

    private boolean success;

    private String message;

    private T data;

    public QuickSecurityResponse() {
    }

    public QuickSecurityResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> QuickSecurityResponse<T> ok(T data) {
        return new QuickSecurityResponse<>(true, "success", data);
    }

    public static <T> QuickSecurityResponse<T> fail(String message) {
        return new QuickSecurityResponse<>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
