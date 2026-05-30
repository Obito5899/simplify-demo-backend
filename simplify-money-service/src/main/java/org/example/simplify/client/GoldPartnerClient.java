package org.example.simplify.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class GoldPartnerClient {
    private static final Logger log = LoggerFactory.getLogger(GoldPartnerClient.class);
    private final WebClient goldClient;

    public GoldPartnerClient(@Qualifier("goldWebClient") WebClient goldClient) {
        this.goldClient = goldClient;
    }

    @Retryable(value = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public Map<String, Object> allocateGoldWithRetry(String txId, BigDecimal amount, BigDecimal pricePerGram, String userId) {
        log.info("Allocating gold txId={} attempt correlationId={}", txId, MDC.get("correlationId"));
        Map<String, Object> resp = goldClient.post()
                .uri("/api/v1/allot-gold")
                .bodyValue(Map.of("txnId", txId, "amount", amount, "pricePerGram", pricePerGram, "userId", userId))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        boolean success = Boolean.parseBoolean(String.valueOf(resp.getOrDefault("success", "false")));
        if (!success) {
            log.warn("Gold allocation failed txId={} resp={}", txId, resp);
            throw new RuntimeException("Gold allocation failed");
        }
        return resp;
    }
}
