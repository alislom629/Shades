package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Data
@AllArgsConstructor
@Builder
public class Referral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long referrerChatId; // User who shared the referral link
    private Long referredChatId; // User who joined via the link
    private BigDecimal balance;   // Referral balance for referrer
    private LocalDateTime createdAt;

    public Referral() {
        this.balance = BigDecimal.ZERO;
        this.createdAt = LocalDateTime.now(ZoneId.of("GMT+5"));
    }
}