package com.example.shade.controller;

import com.example.shade.model.AdminCard;
import com.example.shade.repository.AdminCardRepository;
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
public class AdminCardController {
    private static final Logger logger = LoggerFactory.getLogger(AdminCardController.class);
    private final AdminCardRepository adminCardRepository;

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

    @PostMapping("/cards")
    public ResponseEntity<AdminCard> addCard(@RequestBody AdminCard card, HttpServletRequest request) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized access to add card");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        if (!card.getCardNumber().matches("\\d{16}")) {
            logger.warn("Invalid card number format: {}", card.getCardNumber());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        card.setCardNumber(card.getCardNumber().replaceAll("\\s+", ""));
        card.setMain(false); // New cards are not main by default
        logger.info("Adding new admin card: {}", maskCard(card.getCardNumber()));
        AdminCard savedCard = adminCardRepository.save(card);
        return ResponseEntity.ok(savedCard);
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
                    // Preserve existing main status unless explicitly changed via set-main
                    logger.info("Updating card ID: {}, new card number: {}", id, maskCard(card.getCardNumber()));
                    return ResponseEntity.ok(adminCardRepository.save(existing));
                })
                .orElseGet(() -> {
                    logger.warn("Card not found for update, ID: {}", id);
                    return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
                });
    }

    @PutMapping("/cards/{id}/set-main")
    public ResponseEntity<? extends Map<String,? extends Object>> setMainCard(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized access to set main card ID: {}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }
        return adminCardRepository.findById(id)
                .map(card -> {
                    // Unset previous main card
                    adminCardRepository.findByMainTrue().ifPresent(mainCard -> {
                        if (!mainCard.getId().equals(id)) {
                            mainCard.setMain(false);
                            adminCardRepository.save(mainCard);
                            logger.info("Unset previous main card: {}", maskCard(mainCard.getCardNumber()));
                        }
                    });
                    // Set new main card
                    card.setMain(true);
                    adminCardRepository.save(card);
                    logger.info("Card set as main: {}", maskCard(card.getCardNumber()));
                    return ResponseEntity.ok(Map.of("success", true, "message", "Card set as main"));
                })
                .orElseGet(() -> {
                    logger.warn("Card not found to set as main, ID: {}", id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Card not found"));
                });
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<? extends Map<String,? extends Serializable>> deleteCard(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized access to delete card ID: {}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }
        return adminCardRepository.findById(id)
                .map(card -> {
                    if (card.isMain()) {
                        logger.error("Cannot delete main card: {}", maskCard(card.getCardNumber()));
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("error", "Cannot delete the main card"));
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