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
import java.util.List;

@RestController
@RequestMapping("/api/oson/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
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

        if (config.isPrimaryConfig()) {
            osonConfigRepository.findByPrimaryConfigTrue().ifPresent(existing -> {
                existing.setPrimaryConfig(false);
                osonConfigRepository.save(existing);
            });
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

    @GetMapping("/{id}")
    public ResponseEntity<?> getOsonConfig(HttpServletRequest request, @PathVariable Long id) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized attempt to retrieve Oson config ID: {}", id);
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        try {
            OsonConfig config = osonConfigRepository.findById(id)
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

    @GetMapping
    public ResponseEntity<?> getAllOsonConfigs(HttpServletRequest request) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized attempt to retrieve all Oson configs");
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        try {
            List<OsonConfig> configs = osonConfigRepository.findAll();
            logger.info("Retrieved {} Oson configs", configs.size());
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            logger.error("Error retrieving Oson configs: {}", e.getMessage());
            return ResponseEntity.status(500).body("Error retrieving Oson configs: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateOsonConfig(HttpServletRequest request, @PathVariable Long id, @RequestBody OsonConfig config) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized attempt to update Oson config ID: {}", id);
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        if (config.getId() == null || !config.getId().equals(id)) {
            logger.warn("Invalid ID for Oson config update: {}", config.getId());
            return ResponseEntity.status(400).body("Invalid or missing ID");
        }

        try {
            if (!osonConfigRepository.existsById(id)) {
                logger.error("Oson config not found for update ID: {}", id);
                return ResponseEntity.status(404).body("Oson config not found");
            }
            if (config.isPrimaryConfig()) {
                osonConfigRepository.findByPrimaryConfigTrue().ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        existing.setPrimaryConfig(false);
                        osonConfigRepository.save(existing);
                    }
                });
            }
            osonConfigRepository.save(config);
            logger.info("Oson config updated successfully: {}", config);
            return ResponseEntity.ok("Oson config updated successfully");
        } catch (Exception e) {
            logger.error("Error updating Oson config: {}", e.getMessage());
            return ResponseEntity.status(500).body("Error updating Oson config: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/set-primary")
    public ResponseEntity<String> setPrimaryConfig(HttpServletRequest request, @PathVariable Long id) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized attempt to set primary Oson config ID: {}", id);
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        try {
            OsonConfig config = osonConfigRepository.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Oson config not found"));
            osonConfigRepository.findByPrimaryConfigTrue().ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    existing.setPrimaryConfig(false);
                    osonConfigRepository.save(existing);
                }
            });
            config.setPrimaryConfig(true);
            osonConfigRepository.save(config);
            logger.info("Oson config ID: {} set as primary", id);
            return ResponseEntity.ok("Oson config set as primary");
        } catch (IllegalStateException e) {
            logger.error("Oson config not found: {}", e.getMessage());
            return ResponseEntity.status(404).body("Oson config not found");
        } catch (Exception e) {
            logger.error("Error setting primary Oson config: {}", e.getMessage());
            return ResponseEntity.status(500).body("Error setting primary Oson config: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteOsonConfig(HttpServletRequest request, @PathVariable Long id) {
        if (!authenticate(request)) {
            logger.warn("Unauthorized attempt to delete Oson config ID: {}", id);
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        try {
            OsonConfig config = osonConfigRepository.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Oson config not found"));
            if (config.isPrimaryConfig()) {
                logger.error("Cannot delete primary Oson config ID: {}", id);
                return ResponseEntity.status(400).body("Cannot delete primary Oson config");
            }
            osonConfigRepository.deleteById(id);
            logger.info("Oson config deleted successfully ID: {}", id);
            return ResponseEntity.ok("Oson config deleted successfully");
        } catch (IllegalStateException e) {
            logger.error("Oson config not found: {}", e.getMessage());
            return ResponseEntity.status(404).body("Oson config not found");
        } catch (Exception e) {
            logger.error("Error deleting Oson config: {}", e.getMessage());
            return ResponseEntity.status(500).body("Error deleting Oson config: " + e.getMessage());
        }
    }
}