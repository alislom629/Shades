package com.example.shade.controller;

import com.example.shade.model.FeatureSettings;
import com.example.shade.service.FeatureService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;

/**
 * Date-8/11/2025
 * By Sardor Tokhirov
 * Time-4:49 AM (GMT+5)
 */

@RestController
@RequestMapping("/api/features")
@RequiredArgsConstructor
public class FeatureController {
    private static final Logger logger = LoggerFactory.getLogger(FeatureController.class);
    private final FeatureService featureService;

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000".equals(parts[1]);
        }
        return false;
    }

    @GetMapping
    public ResponseEntity<FeatureSettings> getSettings(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        logger.info("Fetching global feature settings");
        return ResponseEntity.ok(featureService.getGlobalSettings());
    }

    @PostMapping("/toggle/topup")
    public ResponseEntity<String> toggleTopUp(@RequestParam boolean enabled,HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // TODO: Add admin authentication (e.g., Spring Security @PreAuthorize)
        featureService.toggleTopUp(enabled);
        logger.info("Top-up set to {}", enabled);
        return ResponseEntity.ok("Hisob to'ldirish " + (enabled ? "yoqildi" : "o‘chirildi"));
    }

    @PostMapping("/toggle/withdraw")
    public ResponseEntity<String> toggleWithdraw(@RequestParam boolean enabled,HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // TODO: Add admin authentication (e.g., Spring Security @PreAuthorize)
        featureService.toggleWithdraw(enabled);
        logger.info("Withdraw set to {}", enabled);
        return ResponseEntity.ok("Pul chiqarish " + (enabled ? "yoqildi" : "o‘chirildi"));
    }

    @PostMapping("/toggle/bonus")
    public ResponseEntity<String> toggleBonus(@RequestParam boolean enabled,HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // TODO: Add admin authentication (e.g., Spring Security @PreAuthorize)
        featureService.toggleBonus(enabled);
        logger.info("Bonus set to {}", enabled);
        return ResponseEntity.ok("Bonus " + (enabled ? "yoqildi" : "o‘chirildi"));
    }
}