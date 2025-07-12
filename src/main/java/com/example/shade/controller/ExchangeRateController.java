package com.example.shade.controller;

import com.example.shade.model.ExchangeRate;
import com.example.shade.repository.ExchangeRateRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@RestController
@RequestMapping("/api/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateController(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000".equals(parts[1]);
        }
        return false;
    }

    @GetMapping("/latest")
    public ResponseEntity<ExchangeRate> getLatestExchangeRate(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<ExchangeRate> latestRate = exchangeRateRepository.findLatest();
        return latestRate.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/update")
    public ResponseEntity<ExchangeRate> updateExchangeRate(HttpServletRequest request, @Valid @RequestBody ExchangeRateRequest rateRequest) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ExchangeRate exchangeRate = ExchangeRate.builder()
                .uzsToRub(rateRequest.getUzsToRub())
                .rubToUzs(rateRequest.getRubToUzs())
                .createdAt(LocalDateTime.now())
                .build();
        ExchangeRate savedRate = exchangeRateRepository.save(exchangeRate);
        return ResponseEntity.ok(savedRate);
    }

    @Data
    public static class ExchangeRateRequest {
        @NotNull(message = "UZS to RUB rate is required")
        @Positive(message = "UZS to RUB rate must be positive")
        private BigDecimal uzsToRub;

        @NotNull(message = "RUB to UZS rate is required")
        @Positive(message = "RUB to UZS rate must be positive")
        private BigDecimal rubToUzs;
    }
}