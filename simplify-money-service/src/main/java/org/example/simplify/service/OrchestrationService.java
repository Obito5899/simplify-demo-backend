package org.example.simplify.service;

import org.example.simplify.client.GoldPartnerClient;
import org.springframework.dao.OptimisticLockingFailureException;
import org.example.simplify.client.ExternalClientsConfig;
import org.example.simplify.dto.PurchaseRequest;
import org.example.simplify.entity.IdempotencyKeyEntity;
import org.example.simplify.entity.PortfolioEntity;
import org.example.simplify.entity.TransactionEntity;
import org.example.simplify.entity.TransactionState;
import org.example.simplify.repository.IdempotencyRepository;
import org.example.simplify.repository.PortfolioRepository;
import org.example.simplify.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

    private final TransactionRepository txRepo;
    private final PortfolioRepository portfolioRepo;
    private final IdempotencyRepository idempotencyRepo;
    private final WebClient paymentClient;
    private final WebClient goldClient;
    private final GoldPartnerClient goldPartnerClient;

    public OrchestrationService(TransactionRepository txRepo, PortfolioRepository portfolioRepo, IdempotencyRepository idempotencyRepo,
                                @Qualifier("paymentWebClient") WebClient paymentClient,
                                @Qualifier("goldWebClient") WebClient goldClient,
                                GoldPartnerClient goldPartnerClient) {
        this.txRepo = txRepo;
        this.portfolioRepo = portfolioRepo;
        this.idempotencyRepo = idempotencyRepo;
        this.paymentClient = paymentClient;
        this.goldClient = goldClient;
        this.goldPartnerClient = goldPartnerClient;
    }

    public TransactionEntity initiatePurchase(PurchaseRequest req, String idempotencyKey) {
        String cid = MDC.get("correlationId");
        log.info("Initiating purchase userId={} amount={} idempotencyKey={} correlationId={}", req.getUserId(), req.getAmount(), idempotencyKey, cid);

        // Idempotency check
        if (idempotencyKey != null) {
            Optional<IdempotencyKeyEntity> existing = idempotencyRepo.findById(idempotencyKey);
            if (existing.isPresent()) {
                String txId = existing.get().getResultTransactionId();
                log.info("Idempotency hit key={} mappedTx={}", idempotencyKey, txId);
                return txRepo.findById(txId).orElseThrow(() -> new RuntimeException("Transaction not found for idempotency"));
            }
        }

        TransactionEntity tx = new TransactionEntity();
        tx.setUserId(req.getUserId());
        tx.setAmount(req.getAmount());
        tx.setState(TransactionState.INITIATED);
        tx.setPaymentMethod(req.getPaymentMethod());
        tx.setCreatedAt(Instant.now());
        tx.setUpdatedAt(Instant.now());
        tx.setCorrelationId(cid);
        tx.setIdempotencyKey(idempotencyKey);
        tx = txRepo.save(tx);

        if (idempotencyKey != null) {
            IdempotencyKeyEntity keyEntity = new IdempotencyKeyEntity();
            keyEntity.setId(idempotencyKey);
            keyEntity.setUserId(req.getUserId());
            keyEntity.setRequestPath("/api/v1/gold/purchase");
            keyEntity.setResultTransactionId(tx.getId());
            idempotencyRepo.save(keyEntity);
        }

        // Fetch gold rate
        Map<String, Object> rateResp = goldClient.get()
                .uri("/api/v1/gold-rate")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        BigDecimal pricePerGram = new BigDecimal(String.valueOf(rateResp.getOrDefault("pricePerGram", "0")));
        log.info("Fetched gold rate pricePerGram={} txId={}", pricePerGram, tx.getId());

        // initiate payment
        tx.setState(TransactionState.PAYMENT_PENDING);
        tx.setUpdatedAt(Instant.now());
        tx = txRepo.save(tx);

        Map<String, Object> paymentReq = Map.of(
                "txnId", tx.getId(),
                "amount", req.getAmount(),
                "method", req.getPaymentMethod(),
                "userId", req.getUserId()
        );

        Map<String, Object> paymentResp = paymentClient.post()
                .uri("/api/v1/pay")
                .bodyValue(paymentReq)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        boolean paymentSuccess = Boolean.parseBoolean(String.valueOf(paymentResp.getOrDefault("success", "false")));
        log.info("Payment response success={} txnId={}", paymentSuccess, tx.getId());

        if (!paymentSuccess) {
            tx.setState(TransactionState.PAYMENT_FAILED);
            tx.setUpdatedAt(Instant.now());
            tx = txRepo.save(tx);
            throw new RuntimeException("Payment failed");
        }

        tx.setState(TransactionState.PAYMENT_SUCCESS);
        tx.setUpdatedAt(Instant.now());
        tx = txRepo.save(tx);

        // allocate gold with retry
        tx.setState(TransactionState.GOLD_ALLOCATION_PENDING);
        tx.setUpdatedAt(Instant.now());
        tx = txRepo.save(tx);

        Map<String, Object> allotResp;
        try {
            allotResp = goldPartnerClient.allocateGoldWithRetry(tx.getId(), req.getAmount(), pricePerGram, req.getUserId());
        } catch (Exception ex) {
            log.error("Gold allocation failed completely for txId={} after all retry attempts. Initiating refund saga.", tx.getId(), ex);
            try {
                Map<String, Object> refundReq = Map.of(
                        "txnId", tx.getId(),
                        "amount", req.getAmount()
                );
                Map<String, Object> refundResp = paymentClient.post()
                        .uri("/api/v1/refund")
                        .bodyValue(refundReq)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
                boolean refundSuccess = Boolean.parseBoolean(String.valueOf(refundResp.getOrDefault("success", "false")));
                log.info("Refund request response success={} for txId={}", refundSuccess, tx.getId());
            } catch (Exception refundEx) {
                log.error("Failed to execute refund compensation for txId={}", tx.getId(), refundEx);
            }

            tx.setState(TransactionState.FAILED);
            tx.setUpdatedAt(Instant.now());
            tx = txRepo.save(tx);
            throw new RuntimeException("Gold allocation failed and transaction was refunded", ex);
        }

        BigDecimal grams = new BigDecimal(String.valueOf(allotResp.getOrDefault("grams", "0")));

        // update portfolio with optimistic locking retry
        int maxRetries = 5;
        int attempt = 0;
        while (true) {
            try {
                final String userId = req.getUserId();
                PortfolioEntity portfolio = portfolioRepo.findById(userId).orElseGet(() -> {
                    PortfolioEntity p = new PortfolioEntity();
                    p.setId(userId);
                    p.setUserId(userId);
                    return p;
                });
                portfolio.setTotalInvestment(portfolio.getTotalInvestment().add(req.getAmount()));
                portfolio.setTotalGrams(portfolio.getTotalGrams().add(grams));
                portfolio.setLatestValuation(portfolio.getTotalGrams().multiply(pricePerGram));
                portfolioRepo.save(portfolio);
                break;
            } catch (OptimisticLockingFailureException ex) {
                attempt++;
                if (attempt >= maxRetries) {
                    log.error("Failed to update portfolio for user {} after {} attempts due to concurrent updates", req.getUserId(), maxRetries, ex);
                    throw ex;
                }
                log.warn("Optimistic locking failure updating portfolio for user {}, retrying attempt {}...", req.getUserId(), attempt);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Portfolio update retry interrupted", ie);
                }
            }
        }

        tx.setState(TransactionState.GOLD_ALLOCATED);
        tx.setUpdatedAt(Instant.now());
        tx = txRepo.save(tx);

        tx.setState(TransactionState.COMPLETED);
        tx.setUpdatedAt(Instant.now());
        tx = txRepo.save(tx);

        log.info("Completed transaction txId={} userId={} amount={} grams={}", tx.getId(), tx.getUserId(), tx.getAmount(), grams);
        return tx;
    }
}

