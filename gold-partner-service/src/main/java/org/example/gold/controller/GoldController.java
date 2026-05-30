package org.example.gold.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/v1")
public class GoldController {
    private static final Logger log = LoggerFactory.getLogger(GoldController.class);
    private final Random rnd = new Random();

    @Value("${gold.base.price:6000}")
    private BigDecimal basePrice;

    @Value("${gold.failure.rate:0.1}")
    private double failureRate;

    @GetMapping("/gold-rate")
    public ResponseEntity<Map<String, Object>> rate() {
        // simulate slight fluctuation
        double factor = 0.95 + (0.1 * rnd.nextDouble());
        BigDecimal price = basePrice.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> resp = new HashMap<>();
        resp.put("pricePerGram", price);
        resp.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/allot-gold")
    public ResponseEntity<Map<String, Object>> allot(@RequestBody Map<String, Object> req) {
        boolean success = Math.random() > failureRate;
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", success);
        if (!success) {
            resp.put("message", "Allocation failed due to partner error");
            log.warn("Allocation failed for txn={}", req.get("txnId"));
            return ResponseEntity.ok(resp);
        }
        BigDecimal amount = new BigDecimal(String.valueOf(req.getOrDefault("amount", "0")));
        BigDecimal price = new BigDecimal(String.valueOf(req.getOrDefault("pricePerGram", basePrice.toString())));
        BigDecimal grams = amount.divide(price, 6, RoundingMode.HALF_UP);
        resp.put("grams", grams);
        resp.put("message", "Allocated");
        resp.put("txnId", req.get("txnId"));
        log.info("Allocated grams={} for txn={}", grams, req.get("txnId"));
        return ResponseEntity.ok(resp);
    }
}

