package org.example.simplify.dto;

public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String error;
    private String correlationId;

    public ApiResponse() {}
    public ApiResponse(boolean success, T data, String error, String correlationId) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.correlationId = correlationId;
    }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
}

