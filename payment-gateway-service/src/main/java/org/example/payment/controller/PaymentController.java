package org.example.payment.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Value("${payment.success.rate:0.8}")
    private double successRate;

    private final Map<String, String> status = new ConcurrentHashMap<>();

    @GetMapping("/payment-methods")
    public ResponseEntity<Map<String, Object>> methods() {
        Map<String, Object> m = new HashMap<>();
        m.put("methods", new String[]{"UPI","NET_BANKING","CARD"});
        return ResponseEntity.ok(m);
    }

    @PostMapping("/pay")
    public ResponseEntity<Map<String, Object>> pay(@RequestBody Map<String, Object> req) {
        String txnId = UUID.randomUUID().toString();
        boolean success = Math.random() < successRate;
        status.put(txnId, success ? "SUCCESS" : "FAILED");
        Map<String, Object> resp = new HashMap<>();
        resp.put("txnId", txnId);
        resp.put("success", success);
        resp.put("message", success ? "Payment processed" : "Payment failed");
        log.info("Processed pay request amount={} method={} txnId={} success={}", req.get("amount"), req.get("method"), txnId, success);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/payment-status/{txnId}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String txnId) {
        String s = status.getOrDefault(txnId, "UNKNOWN");
        Map<String, Object> resp = new HashMap<>();
        resp.put("txnId", txnId);
        resp.put("status", s);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/refund")
    public ResponseEntity<Map<String, Object>> refund(@RequestBody Map<String, Object> req) {
        String txnId = String.valueOf(req.get("txnId"));
        java.math.BigDecimal amount = new java.math.BigDecimal(String.valueOf(req.getOrDefault("amount", "0")));
        log.info("Processed refund request for txnId={} amount={}", txnId, amount);
        Map<String, Object> resp = new HashMap<>();
        resp.put("txnId", txnId);
        resp.put("success", true);
        resp.put("message", "Refund processed successfully");
        return ResponseEntity.ok(resp);
    }
}

