package com.example.shade.controller;

import com.example.shade.model.OsonConfig;
import com.example.shade.repository.OsonConfigRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;

@RestController
@RequestMapping("/api/oson/config")
@RequiredArgsConstructor
public class OsonConfigController {
    private static final Logger logger = LoggerFactory.getLogger(OsonConfigController.class);
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

    @PostMapping
    public ResponseEntity<String> saveOsonConfig(HttpServletRequest request, @RequestBody OsonConfig config) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized attempt to save Oson config");
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        if (config.getId() == null) {
            config.setId(1L); // Fixed ID for single config
        }

        try {
            osonConfigRepository.save(config);
            logger.info("Oson config saved successfully: {}", config);
            return ResponseEntity.ok("Oson config saved successfully");
        } catch (Exception e) {
            logger.error("Error saving Oson config: {}", e.getMessage());
            return ResponseEntity.status(500).body("Error saving Oson config: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getOsonConfig(HttpServletRequest request) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized attempt to retrieve Oson config");
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        try {
            OsonConfig config = osonConfigRepository.findById(1L)
                    .orElseThrow(() -> new IllegalStateException("Oson config not found"));
            logger.info("Oson config retrieved successfully: {}", config);
            return ResponseEntity.ok(config);
        } catch (IllegalStateException e) {
            logger.error("Oson config not found: {}", e.getMessage());
            return ResponseEntity.status(404).body("Oson config not found");
        } catch (Exception e) {
            logger.error("Error retrieving Oson config: {}", e.getMessage());
            return ResponseEntity.status(500).body("Error retrieving Oson config: " + e.getMessage());
        }
    }

    @PutMapping
    public ResponseEntity<String> updateOsonConfig(HttpServletRequest request, @RequestBody OsonConfig config) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized attempt to update Oson config");
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        if (config.getId() == null || config.getId() != 1L) {
            logger.warn("Invalid ID for Oson config update: {}", config.getId());
            return ResponseEntity.status(400).body("Invalid or missing ID, must be 1");
        }

        try {
            if (!osonConfigRepository.existsById(1L)) {
                logger.error("Oson config not found for update");
                return ResponseEntity.status(404).body("Oson config not found");
            }
            osonConfigRepository.save(config);
            logger.info("Oson config updated successfully: {}", config);
            return ResponseEntity.ok("Oson config updated successfully");
        } catch (Exception e) {
            logger.error("Error updating Oson config: {}", e.getMessage());
            return ResponseEntity.status(500).body("Error updating Oson config: " + e.getMessage());
        }
    }

    @DeleteMapping
    public ResponseEntity<String> deleteOsonConfig(HttpServletRequest request) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized attempt to delete Oson config");
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        try {
            if (!osonConfigRepository.existsById(1L)) {
                logger.error("Oson config not found for deletion");
                return ResponseEntity.status(404).body("Oson config not found");
            }
            osonConfigRepository.deleteById(1L);
            logger.info("Oson config deleted successfully");
            return ResponseEntity.ok("Oson config deleted successfully");
        } catch (Exception e) {
            logger.error("Error deleting Oson config: {}", e.getMessage());
            return ResponseEntity.status(500).body("Error deleting Oson config: " + e.getMessage());
        }
    }
}