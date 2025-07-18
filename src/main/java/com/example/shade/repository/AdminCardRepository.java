package com.example.shade.repository;

import com.example.shade.model.AdminCard;
import com.example.shade.model.OsonConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AdminCardRepository extends JpaRepository<AdminCard, Long> {

    Optional<AdminCard> findByCardNumber(String cardNumber);

    @Query("SELECT a FROM AdminCard a WHERE a.osonConfig.primaryConfig = true AND (a.lastUsed IS NULL OR a.lastUsed = (SELECT MIN(a2.lastUsed) FROM AdminCard a2 WHERE a2.osonConfig.primaryConfig = true)) ORDER BY a.lastUsed DESC LIMIT 1")
    Optional<AdminCard> findLeastRecentlyUsed();

    Optional<AdminCard> findByCardNumberAndOsonConfig(String cardNumber, OsonConfig osonConfig);

    List<AdminCard> findByOsonConfig(OsonConfig osonConfig);
}