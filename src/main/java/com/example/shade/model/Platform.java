package com.example.shade.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Date-6/21/2025
 * By Sardor Tokhirov
 * Time-10:48 AM (GMT+5)
 */
@Data
@Entity
@Table(name = "platforms")
public class Platform {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false, unique = true)
    private String apiKey;

    @Column(nullable = false)
    private String login;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String workplaceId;
}