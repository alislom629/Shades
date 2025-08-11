package com.example.shade.repository;

import com.example.shade.model.FeatureSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * Date-8/11/2025
 * By Sardor Tokhirov
 * Time-4:44 AM (GMT+5)
 */
public interface FeatureSettingsRepository extends JpaRepository<FeatureSettings, Long> {
    @Query("SELECT fs FROM FeatureSettings fs ORDER BY fs.createdAt DESC LIMIT 1")
    Optional<FeatureSettings> findLatest();
}
