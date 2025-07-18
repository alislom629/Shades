package com.example.shade.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsonConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String apiUrl;
    private String apiKey;
    @Column(nullable = false, unique = true)
    private String phone;
    private String password;
    private String deviceId;
    private String deviceName;
    @Column(nullable = false)
    private boolean primaryConfig;
}