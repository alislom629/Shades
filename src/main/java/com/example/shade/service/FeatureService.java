package com.example.shade.service;

import com.example.shade.model.FeatureSettings;
import com.example.shade.repository.FeatureSettingsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Date-8/11/2025
 * By Sardor Tokhirov
 * Time-4:45 AM (GMT+5)
 */



@Service
@RequiredArgsConstructor
public class FeatureService {
    private static final Logger logger = LoggerFactory.getLogger(FeatureService.class);
    private final FeatureSettingsRepository featureSettingsRepository;

    @Transactional
    public FeatureSettings getGlobalSettings() {
        return featureSettingsRepository.findLatest()
                .orElseGet(() -> {
                    FeatureSettings settings = new FeatureSettings();
                    settings.setTopUpEnabled(true);
                    settings.setWithdrawEnabled(true);
                    settings.setBonusEnabled(true);
                    settings.setCreatedAt(LocalDateTime.now());
                    return featureSettingsRepository.save(settings);
                });
    }

    @Transactional
    public void toggleTopUp(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = new FeatureSettings();
        settings.setTopUpEnabled(enabled);
        settings.setWithdrawEnabled(current.getWithdrawEnabled());
        settings.setBonusEnabled(current.getBonusEnabled());
        settings.setCreatedAt(LocalDateTime.now());
        featureSettingsRepository.save(settings);
        logger.info("Top-up {} globally", enabled ? "enabled" : "disabled");
    }

    @Transactional
    public void toggleWithdraw(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = new FeatureSettings();
        settings.setTopUpEnabled(current.getTopUpEnabled());
        settings.setWithdrawEnabled(enabled);
        settings.setBonusEnabled(current.getBonusEnabled());
        settings.setCreatedAt(LocalDateTime.now());
        featureSettingsRepository.save(settings);
        logger.info("Withdraw {} globally", enabled ? "enabled" : "disabled");
    }

    @Transactional
    public void toggleBonus(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = new FeatureSettings();
        settings.setTopUpEnabled(current.getTopUpEnabled());
        settings.setWithdrawEnabled(current.getWithdrawEnabled());
        settings.setBonusEnabled(enabled);
        settings.setCreatedAt(LocalDateTime.now());
        featureSettingsRepository.save(settings);
        logger.info("Bonus {} globally", enabled ? "enabled" : "disabled");
    }

    public boolean canPerformTopUp() {
        return getGlobalSettings().getTopUpEnabled();
    }

    public boolean canPerformWithdraw() {
        return getGlobalSettings().getWithdrawEnabled();
    }

    public boolean canPerformBonus() {
        return getGlobalSettings().getBonusEnabled();
    }
}