package com.example.shade.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uzs_to_rub", nullable = false, precision = 10, scale = 4)
    private BigDecimal uzsToRub;

    @Column(name = "rub_to_uzs", nullable = false, precision = 10, scale = 4)
    private BigDecimal rubToUzs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}