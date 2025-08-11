package com.example.shade.model;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Date-8/11/2025
 * By Sardor Tokhirov
 * Time-4:28 AM (GMT+5)
 */

@Entity
@Table(name = "feature_settings")
@Data
public class FeatureSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "top_up_enabled", nullable = false)
    private Boolean topUpEnabled = true;

    @Column(name = "withdraw_enabled", nullable = false)
    private Boolean withdrawEnabled = true;

    @Column(name = "bonus_enabled", nullable = false)
    private Boolean bonusEnabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}