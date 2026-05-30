package org.example.simplify.entity;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "portfolios")
public class PortfolioEntity {
    @Id
    private String id; // userId
    private String userId;
    private BigDecimal totalInvestment = BigDecimal.ZERO;
    private BigDecimal totalGrams = BigDecimal.ZERO;
    private BigDecimal latestValuation = BigDecimal.ZERO;
    @org.springframework.data.annotation.Version
    private Long version;

    // getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public BigDecimal getTotalInvestment() { return totalInvestment; }
    public void setTotalInvestment(BigDecimal totalInvestment) { this.totalInvestment = totalInvestment; }
    public BigDecimal getTotalGrams() { return totalGrams; }
    public void setTotalGrams(BigDecimal totalGrams) { this.totalGrams = totalGrams; }
    public BigDecimal getLatestValuation() { return latestValuation; }
    public void setLatestValuation(BigDecimal latestValuation) { this.latestValuation = latestValuation; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

