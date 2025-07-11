package com.example.shade.repository;

import com.example.shade.model.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReferralRepository extends JpaRepository<Referral, Long> {
    @Query("SELECT r FROM Referral r WHERE r.referredChatId = :referredChatId")
    Optional<Referral> findByReferredChatId(Long referredChatId);

    @Query("SELECT r FROM Referral r WHERE r.referrerChatId = :referrerChatId")
    List<Referral> findByReferrerChatId(Long referrerChatId);

    @Query("SELECT COUNT(r) FROM Referral r WHERE r.referrerChatId = :referrerChatId")
    Long countByReferrerChatId(Long referrerChatId);
}