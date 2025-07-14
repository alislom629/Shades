package com.example.shade.repository;

import com.example.shade.model.AdminCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AdminCardRepository extends JpaRepository<AdminCard, Long> {
    @Query("SELECT a FROM AdminCard a WHERE a.lastUsed IS NULL OR a.lastUsed = (SELECT MIN(a2.lastUsed) FROM AdminCard a2) order by a.lastUsed desc limit 1")
    Optional<AdminCard> findLeastRecentlyUsed();

    Optional<AdminCard> findByMainTrue();

    Optional<AdminCard> findByCardNumber(String cardNumber);
}