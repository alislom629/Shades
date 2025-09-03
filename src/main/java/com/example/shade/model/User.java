package com.example.shade.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
    @Id
    private Long chatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language language;
}