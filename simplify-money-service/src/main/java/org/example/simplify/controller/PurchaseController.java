package org.example.simplify.controller;

import jakarta.validation.Valid;
import org.example.simplify.dto.ApiResponse;
import org.example.simplify.dto.PurchaseRequest;
import org.example.simplify.dto.PurchaseResponse;
import org.example.simplify.entity.TransactionEntity;
import org.example.simplify.repository.TransactionRepository;
import org.example.simplify.service.OrchestrationService;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gold")
public class PurchaseController {
    private final OrchestrationService orchestrationService;
    private final TransactionRepository txRepo;
    private static final Logger log = LoggerFactory.getLogger(PurchaseController.class);

    public PurchaseController(OrchestrationService orchestrationService, TransactionRepository txRepo) {
        this.orchestrationService = orchestrationService;
        this.txRepo = txRepo;
    }

    @PostMapping("/purchase")
    public ResponseEntity<ApiResponse<PurchaseResponse>> purchase(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                                   @Valid @RequestBody PurchaseRequest req) {
        String cid = MDC.get("correlationId");
        try {
            TransactionEntity tx = orchestrationService.initiatePurchase(req, idempotencyKey);
            PurchaseResponse resp = new PurchaseResponse();
            resp.setTransactionId(tx.getId());
            resp.setAmount(tx.getAmount());
            resp.setState(tx.getState().name());
            resp.setCorrelationId(cid);
            ApiResponse<PurchaseResponse> apiResp = new ApiResponse<>(true, resp, null, cid);
            return ResponseEntity.ok().header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Correlation-ID").body(apiResp);
        } catch (Exception ex) {
            log.error("Purchase failed userId={} error={} correlationId={}", req.getUserId(), ex.getMessage(), cid);
            ApiResponse<PurchaseResponse> apiResp = new ApiResponse<>(false, null, ex.getMessage(), cid);
            return ResponseEntity.status(500).body(apiResp);
        }
    }
}

