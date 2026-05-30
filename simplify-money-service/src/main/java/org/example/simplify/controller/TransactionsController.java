package org.example.simplify.controller;

import org.example.simplify.dto.ApiResponse;
import org.example.simplify.entity.TransactionEntity;
import org.example.simplify.repository.TransactionRepository;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionsController {
    private final TransactionRepository txRepo;

    public TransactionsController(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<List<TransactionEntity>>> getTxs(@PathVariable String userId) {
        List<TransactionEntity> txs = txRepo.findByUserIdOrderByCreatedAtDesc(userId);
        ApiResponse<List<TransactionEntity>> resp = new ApiResponse<>(true, txs, null, MDC.get("correlationId"));
        return ResponseEntity.ok(resp);
    }
}

