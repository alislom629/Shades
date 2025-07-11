package com.example.shade.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
    private Long id;
    private String apiUrl;
    private String apiKey;
    private String phone;
    private String password;
    private String deviceId;
    private String deviceName;
}