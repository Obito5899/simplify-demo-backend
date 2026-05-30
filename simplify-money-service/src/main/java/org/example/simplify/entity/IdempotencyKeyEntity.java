package org.example.simplify.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "idempotency_keys")
public class IdempotencyKeyEntity {
    @Id
    private String id; // idempotency key
    private String userId;
    private String requestPath;
    private Instant createdAt = Instant.now();
    private String resultTransactionId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRequestPath() { return requestPath; }
    public void setRequestPath(String requestPath) { this.requestPath = requestPath; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getResultTransactionId() { return resultTransactionId; }
    public void setResultTransactionId(String resultTransactionId) { this.resultTransactionId = resultTransactionId; }
}

