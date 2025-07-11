package com.example.shade.service;


import com.example.shade.model.Currency;
import com.example.shade.model.Platform;
import com.example.shade.repository.PlatformRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlatformService {
    private static final Logger logger = LoggerFactory.getLogger(PlatformService.class);
    private final PlatformRepository platformRepository;

    public Platform createPlatform(String name, Currency currency, String apiKey, String login, String password, String workplaceId) {
        logger.info("Creating platform: name={}, currency={}, workplaceId={}", name, currency, workplaceId);
        validateInputs(name, currency, apiKey, login, password, workplaceId);
        Platform platform = new Platform();
        platform.setName(name.trim());
        platform.setCurrency(currency);
        platform.setApiKey(apiKey.trim());
        platform.setLogin(login.trim());
        platform.setPassword(password.trim());
        platform.setWorkplaceId(workplaceId.trim());
        return platformRepository.save(platform);
    }

    public Platform updatePlatform(Long id, String name, Currency currency, String apiKey, String login, String password, String workplaceId) {
        logger.info("Updating platform: id={}, name={}, currency={}, workplaceId={}", id, name, currency, workplaceId);
        Optional<Platform> optionalPlatform = platformRepository.findById(id);
        if (optionalPlatform.isEmpty()) {
            throw new IllegalArgumentException("Platform not found with id: " + id);
        }
        validateInputs(name, currency, apiKey, login, password, workplaceId);
        Platform platform = optionalPlatform.get();
        platform.setName(name.trim());
        platform.setCurrency(currency);
        platform.setApiKey(apiKey.trim());
        platform.setLogin(login.trim());
        platform.setPassword(password.trim());
        platform.setWorkplaceId(workplaceId.trim());
        return platformRepository.save(platform);
    }

    public void deletePlatform(Long id) {
        logger.info("Deleting platform: id={}", id);
        if (!platformRepository.existsById(id)) {
            throw new IllegalArgumentException("Platform not found with id: " + id);
        }
        platformRepository.deleteById(id);
    }

    private void validateInputs(String name, Currency currency, String apiKey, String login, String password, String workplaceId) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Platform name cannot be empty");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be empty");
        }
        if (login == null || login.trim().isEmpty()) {
            throw new IllegalArgumentException("Login cannot be empty");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (workplaceId == null || workplaceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Workplace ID cannot be empty");
        }
    }

    public List<Platform> getAllPlatforms() {
        return platformRepository.findAll();
    }

    public Platform getPlatformById(Long id) {
        logger.info("Deleting platform: id={}", id);
        if (!platformRepository.existsById(id)) {
            throw new IllegalArgumentException("Platform not found with id: " + id);
        }
       return platformRepository.findById(id).get();
    }
}