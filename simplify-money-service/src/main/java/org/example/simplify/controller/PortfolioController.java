package org.example.simplify.controller;

import org.example.simplify.dto.ApiResponse;
import org.example.simplify.entity.PortfolioEntity;
import org.example.simplify.repository.PortfolioRepository;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {
    private final PortfolioRepository portfolioRepository;

    public PortfolioController(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<PortfolioEntity>> getPortfolio(@PathVariable String userId) {
        PortfolioEntity p = portfolioRepository.findById(userId).orElse(null);
        ApiResponse<PortfolioEntity> resp = new ApiResponse<>(true, p, null, MDC.get("correlationId"));
        return ResponseEntity.ok(resp);
    }
}

