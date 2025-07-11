package com.example.shade.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "session_data")
public class SessionData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(nullable = false)
    private String key;

    @Column
    private String value;
}