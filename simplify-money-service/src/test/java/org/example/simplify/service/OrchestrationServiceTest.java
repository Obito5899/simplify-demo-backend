package org.example.simplify.service;

import org.example.simplify.client.GoldPartnerClient;
import org.example.simplify.dto.PurchaseRequest;
import org.example.simplify.entity.IdempotencyKeyEntity;
import org.example.simplify.entity.PortfolioEntity;
import org.example.simplify.entity.TransactionEntity;
import org.example.simplify.entity.TransactionState;
import org.example.simplify.repository.IdempotencyRepository;
import org.example.simplify.repository.PortfolioRepository;
import org.example.simplify.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrchestrationServiceTest {

    @Mock
    private TransactionRepository txRepo;
    @Mock
    private PortfolioRepository portfolioRepo;
    @Mock
    private IdempotencyRepository idempotencyRepo;
    @Mock
    private GoldPartnerClient goldPartnerClient;
    @Mock
    private WebClient paymentClient;
    @Mock
    private WebClient goldClient;

    private OrchestrationService orchestrationService;

    // WebClient fluent mocks
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;
    @Mock
    private WebClient.ResponseSpec paymentResponseSpec;

    @BeforeEach
    void setUp() {
        orchestrationService = new OrchestrationService(txRepo, portfolioRepo, idempotencyRepo, paymentClient, goldClient, goldPartnerClient);
    }

    @Test
    void testInitiatePurchase_HappyPath() {
        PurchaseRequest req = new PurchaseRequest();
        req.setUserId("user-1");
        req.setAmount(new BigDecimal("100.00"));
        req.setPaymentMethod("UPI");

        TransactionEntity tx = new TransactionEntity();
        tx.setId("tx-123");
        tx.setUserId("user-1");
        tx.setAmount(new BigDecimal("100.00"));
        tx.setState(TransactionState.INITIATED);

        when(txRepo.save(any(TransactionEntity.class))).thenAnswer(inv -> {
            TransactionEntity saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId("tx-123");
            }
            return saved;
        });

        // Mock goldClient GET /api/v1/gold-rate
        when(goldClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/api/v1/gold-rate");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        Map<String, Object> rateMap = Map.of("pricePerGram", "6000.00");
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(rateMap));

        // Mock paymentClient POST /api/v1/pay
        when(paymentClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/api/v1/pay");
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        when(requestBodySpec.retrieve()).thenReturn(paymentResponseSpec);
        Map<String, Object> payMap = Map.of("success", "true", "txnId", "pay-123");
        when(paymentResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(payMap));

        // Mock goldPartnerClient allocateGoldWithRetry
        Map<String, Object> allotMap = Map.of("success", "true", "grams", "0.016667", "txnId", "allot-123");
        when(goldPartnerClient.allocateGoldWithRetry(eq("tx-123"), eq(new BigDecimal("100.00")), eq(new BigDecimal("6000.00")), eq("user-1")))
                .thenReturn(allotMap);

        // Mock portfolioRepo
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId("user-1");
        portfolio.setUserId("user-1");
        when(portfolioRepo.findById("user-1")).thenReturn(Optional.of(portfolio));
        when(portfolioRepo.save(any(PortfolioEntity.class))).thenReturn(portfolio);

        TransactionEntity result = orchestrationService.initiatePurchase(req, "idemp-key-1");

        assertThat(result).isNotNull();
        assertThat(result.getState()).isEqualTo(TransactionState.COMPLETED);
        verify(txRepo, atLeastOnce()).save(any(TransactionEntity.class));
        verify(portfolioRepo).save(any(PortfolioEntity.class));
    }

    @Test
    void testInitiatePurchase_GoldAllocationFails_RefundSagaTriggers() {
        PurchaseRequest req = new PurchaseRequest();
        req.setUserId("user-1");
        req.setAmount(new BigDecimal("100.00"));
        req.setPaymentMethod("UPI");

        TransactionEntity tx = new TransactionEntity();
        tx.setId("tx-123");
        tx.setUserId("user-1");
        tx.setAmount(new BigDecimal("100.00"));
        tx.setState(TransactionState.INITIATED);

        when(txRepo.save(any(TransactionEntity.class))).thenAnswer(inv -> {
            TransactionEntity saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId("tx-123");
            }
            return saved;
        });

        // Mock goldClient GET /api/v1/gold-rate
        when(goldClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/api/v1/gold-rate");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        Map<String, Object> rateMap = Map.of("pricePerGram", "6000.00");
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(rateMap));

        // Mock paymentClient POST /api/v1/pay and POST /api/v1/refund
        when(paymentClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/api/v1/pay");
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        when(requestBodySpec.retrieve()).thenReturn(paymentResponseSpec);
        Map<String, Object> payMap = Map.of("success", "true", "txnId", "pay-123");
        when(paymentResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(payMap));

        // Mock refund call
        WebClient.RequestBodySpec refundBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec refundResponseSpec = mock(WebClient.ResponseSpec.class);
        doReturn(refundBodySpec).when(requestBodyUriSpec).uri("/api/v1/refund");
        doReturn(refundBodySpec).when(refundBodySpec).bodyValue(any());
        when(refundBodySpec.retrieve()).thenReturn(refundResponseSpec);
        Map<String, Object> refundMap = Map.of("success", "true");
        when(refundResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(refundMap));

        // Mock goldPartnerClient to throw exception after retries
        when(goldPartnerClient.allocateGoldWithRetry(eq("tx-123"), eq(new BigDecimal("100.00")), eq(new BigDecimal("6000.00")), eq("user-1")))
                .thenThrow(new RuntimeException("Gold partner connection timeout"));

        assertThatThrownBy(() -> orchestrationService.initiatePurchase(req, "idemp-key-2"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Gold allocation failed and transaction was refunded");

        // Verify state is FAILED
        verify(txRepo, atLeast(1)).save(argThat(t -> t.getState() == TransactionState.FAILED));
        verify(portfolioRepo, never()).save(any());
    }

    @Test
    void testInitiatePurchase_PortfolioOptimisticLockingRetry() {
        PurchaseRequest req = new PurchaseRequest();
        req.setUserId("user-1");
        req.setAmount(new BigDecimal("100.00"));
        req.setPaymentMethod("UPI");

        when(txRepo.save(any(TransactionEntity.class))).thenAnswer(inv -> {
            TransactionEntity saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId("tx-123");
            }
            return saved;
        });

        when(goldClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/api/v1/gold-rate");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("pricePerGram", "6000.00")));

        when(paymentClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/api/v1/pay");
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        when(requestBodySpec.retrieve()).thenReturn(paymentResponseSpec);
        when(paymentResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("success", "true", "txnId", "pay-123")));

        Map<String, Object> allotMap = Map.of("success", "true", "grams", "0.016667", "txnId", "allot-123");
        when(goldPartnerClient.allocateGoldWithRetry(any(), any(), any(), any()))
                .thenReturn(allotMap);

        // Portfolio save will fail once with OptimisticLockingFailureException, then succeed
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setId("user-1");
        portfolio.setUserId("user-1");
        when(portfolioRepo.findById("user-1")).thenReturn(Optional.of(portfolio));
        
        when(portfolioRepo.save(any(PortfolioEntity.class)))
                .thenThrow(new OptimisticLockingFailureException("Version mismatch"))
                .thenReturn(portfolio);

        TransactionEntity result = orchestrationService.initiatePurchase(req, "idemp-key-3");

        assertThat(result).isNotNull();
        assertThat(result.getState()).isEqualTo(TransactionState.COMPLETED);
        verify(portfolioRepo, times(2)).save(any(PortfolioEntity.class));
    }
}
