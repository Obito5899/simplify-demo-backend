package org.example.simplify.controller;

import org.example.simplify.client.GoldPartnerClient;
import org.example.simplify.dto.ApiResponse;
import org.example.simplify.dto.PurchaseRequest;
import org.example.simplify.dto.PurchaseResponse;
import org.example.simplify.entity.PortfolioEntity;
import org.example.simplify.entity.TransactionEntity;
import org.example.simplify.entity.TransactionState;
import org.example.simplify.repository.PortfolioRepository;
import org.example.simplify.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class PurchaseIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.5");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TransactionRepository txRepo;

    @Autowired
    private PortfolioRepository portfolioRepo;

    @MockBean
    private GoldPartnerClient goldPartnerClient;

    @MockBean(name = "paymentWebClient")
    private WebClient paymentWebClient;

    @MockBean(name = "goldWebClient")
    private WebClient goldWebClient;

    // WebClient mock chains
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    private WebClient.RequestBodySpec requestBodySpec;
    private WebClient.ResponseSpec paymentResponseSpec;

    @BeforeEach
    void setUpMocks() {
        txRepo.deleteAll();
        portfolioRepo.deleteAll();

        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(WebClient.RequestBodySpec.class);
        paymentResponseSpec = mock(WebClient.ResponseSpec.class);
    }

    @Test
    void testPurchaseEndpoint_Success() {
        // Mock goldClient GET /api/v1/gold-rate
        when(goldWebClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/api/v1/gold-rate");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("pricePerGram", "6000.00")));

        // Mock paymentClient POST /api/v1/pay
        when(paymentWebClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/api/v1/pay");
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        when(requestBodySpec.retrieve()).thenReturn(paymentResponseSpec);
        when(paymentResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("success", "true", "txnId", "pay-123")));

        // Mock goldPartnerClient allocateGoldWithRetry
        Map<String, Object> allotMap = Map.of("success", "true", "grams", "0.016667", "txnId", "allot-123");
        when(goldPartnerClient.allocateGoldWithRetry(any(), any(), any(), any()))
                .thenReturn(allotMap);

        PurchaseRequest req = new PurchaseRequest();
        req.setUserId("user-int-1");
        req.setAmount(new BigDecimal("100.00"));
        req.setPaymentMethod("UPI");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "idemp-int-1");
        headers.set("X-Correlation-ID", "corr-int-1");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<PurchaseRequest> entity = new HttpEntity<>(req, headers);

        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/gold/purchase",
                entity,
                ApiResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        // Verify database state
        var txs = txRepo.findByUserIdOrderByCreatedAtDesc("user-int-1");
        assertThat(txs).isNotEmpty();
        var tx = txs.get(0);
        assertThat(tx.getState()).isEqualTo(TransactionState.COMPLETED);
        assertThat(tx.getAmount()).isEqualByComparingTo("100.00");
        assertThat(tx.getCorrelationId()).isEqualTo("corr-int-1");
        assertThat(tx.getIdempotencyKey()).isEqualTo("idemp-int-1");

        var portfolioOpt = portfolioRepo.findById("user-int-1");
        assertThat(portfolioOpt).isPresent();
        assertThat(portfolioOpt.get().getTotalInvestment()).isEqualByComparingTo("100.00");
        assertThat(portfolioOpt.get().getTotalGrams()).isEqualByComparingTo("0.016667");
    }
}
