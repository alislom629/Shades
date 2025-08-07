package com.example.shade.controller;

import com.example.shade.model.AdminCard;
import com.example.shade.model.OsonConfig;
import com.example.shade.repository.AdminCardRepository;
import com.example.shade.repository.OsonConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminCardController {
    private static final Logger logger = LoggerFactory.getLogger(AdminCardController.class);
    private final AdminCardRepository adminCardRepository;
    private final OsonConfigRepository osonConfigRepository;

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000".equals(parts[1]);
        }
        return false;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(HttpServletRequest request) {
        if (authenticate(request)) {
            logger.info("Admin authenticated successfully");
            return ResponseEntity.ok(Map.of("success", true, "message", "Successfully authenticated"));
        }
        logger.warn("Admin authentication failed");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
    }

    @GetMapping("/cards")
    public ResponseEntity<List<AdminCard>> getCards(HttpServletRequest request) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized access to get cards");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        logger.info("Fetching all admin cards");
        return ResponseEntity.ok(adminCardRepository.findAll());
    }

    @GetMapping("/cards/oson/{osonConfigId}")
    public ResponseEntity<List<AdminCard>> getCardsByOsonConfig(HttpServletRequest request, @PathVariable Long osonConfigId) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized access to get cards for OsonConfig ID: {}", osonConfigId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return osonConfigRepository.findById(osonConfigId)
                .map(osonConfig -> {
                    logger.info("Fetching cards for OsonConfig ID: {}", osonConfigId);
                    return ResponseEntity.ok(adminCardRepository.findByOsonConfig(osonConfig));
                })
                .orElseGet(() -> {
                    logger.warn("OsonConfig not found for ID: {}", osonConfigId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
                });
    }

    @GetMapping("/cards/{id}")
    public ResponseEntity<AdminCard> getCard(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized access to get card ID: {}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        logger.info("Fetching card with ID: {}", id);
        return adminCardRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    logger.warn("Card not found with ID: {}", id);
                    return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
                });
    }

    @PostMapping("/cards/oson/{osonConfigId}")
    public ResponseEntity<AdminCard> addCard(@PathVariable Long osonConfigId, @RequestBody AdminCard card, HttpServletRequest request) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized access to add card for OsonConfig ID: {}", osonConfigId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        if (!card.getCardNumber().matches("\\d{16}")) {
            logger.warn("Invalid card number format: {}", card.getCardNumber());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        return osonConfigRepository.findById(osonConfigId)
                .map(osonConfig -> {
                    card.setCardNumber(card.getCardNumber().replaceAll("\\s+", ""));
                    card.setOsonConfig(osonConfig);
                    logger.info("Adding new admin card for OsonConfig ID: {}: {}", osonConfigId, card.getCardNumber());
                    AdminCard savedCard = adminCardRepository.save(card);
                    return ResponseEntity.ok(savedCard);
                })
                .orElseGet(() -> {
                    logger.warn("OsonConfig not found for ID: {}", osonConfigId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
                });
    }

    @PutMapping("/cards/{id}")
    public ResponseEntity<AdminCard> updateCard(@PathVariable Long id, @RequestBody AdminCard card, HttpServletRequest request) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized access to update card ID: {}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        if (!card.getCardNumber().matches("\\d{16}")) {
            logger.warn("Invalid card number format for update: {}", card.getCardNumber());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        return adminCardRepository.findById(id)
                .map(existing -> {
                    existing.setCardNumber(card.getCardNumber().replaceAll("\\s+", ""));
                    existing.setOwnerName(card.getOwnerName());
                    existing.setLastUsed(card.getLastUsed());
                    existing.setBalance(card.getBalance());
                    if (card.getOsonConfig() != null && card.getOsonConfig().getId() != null) {
                        osonConfigRepository.findById(card.getOsonConfig().getId())
                                .ifPresent(existing::setOsonConfig);
                    }
                    logger.info("Updating card ID: {}, new card number: {}", id, card.getCardNumber());
                    return ResponseEntity.ok(adminCardRepository.save(existing));
                })
                .orElseGet(() -> {
                    logger.warn("Card not found for update, ID: {}", id);
                    return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
                });
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<? extends Map<String, ? extends Serializable>> deleteCard(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized access to delete card ID: {}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }
        return adminCardRepository.findById(id)
                .map(card -> {
                    if (card.getOsonConfig().isPrimaryConfig()) {
                        long cardCount = adminCardRepository.findByOsonConfig(card.getOsonConfig()).size();
                        if (cardCount <= 1) {
                            logger.error("Cannot delete last card of primary OsonConfig ID: {}", card.getOsonConfig().getId());
                            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                    .body(Map.of("error", "Cannot delete the last card of the primary OsonConfig"));
                        }
                    }
                    adminCardRepository.deleteById(id);
                    logger.info("Deleted card ID: {}", id);
                    return ResponseEntity.ok(Map.of("success", true, "message", "Card deleted"));
                })
                .orElseGet(() -> {
                    logger.warn("Card not found for deletion, ID: {}", id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Card not found"));
                });
    }

    private String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) return "INVALID CARD";
        return cardNumber.substring(0, 4) + "****" + cardNumber.substring(cardNumber.length() - 4);
    }
}