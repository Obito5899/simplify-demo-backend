package org.example.simplify.dto;

import java.math.BigDecimal;

public class PurchaseResponse {
    private String transactionId;
    private BigDecimal amount;
    private String state;
    private String correlationId;

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
}

