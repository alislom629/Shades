package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "hizmat_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HizmatRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "currency", nullable = false)
    private Currency currency;

    @Column(name = "platform_user_id")
    private String platformUserId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "card_number", length = 16)
    private String cardNumber;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "unique_amount")
    private Long uniqueAmount;

    @Column(name = "admin_card_id")
    private Long adminCardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private RequestType type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "transaction_id", length = 36)
    private String transactionId;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "pay_url")
    private String payUrl;


}