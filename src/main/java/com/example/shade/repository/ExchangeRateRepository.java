package com.example.shade.repository;

import com.example.shade.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    @Query("SELECT er FROM ExchangeRate er ORDER BY er.createdAt DESC LIMIT 1")
    Optional<ExchangeRate> findLatest();
}