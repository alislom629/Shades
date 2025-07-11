package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "lottery_prizes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LotteryPrize {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "number_of_prize", nullable = false)
    private Integer numberOfPrize;
}