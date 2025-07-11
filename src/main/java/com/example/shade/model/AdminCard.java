package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_card")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_number", nullable = false, length = 16)
    private String cardNumber;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "last_used")
    private LocalDateTime lastUsed;

    @Column(nullable = false)
    private boolean main;
}