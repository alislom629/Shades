package com.example.shade.repository;

import com.example.shade.model.Currency;
import com.example.shade.model.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Date-6/21/2025
 * By Sardor Tokhirov
 * Time-10:53 AM (GMT+5)
 */
@Repository
public interface PlatformRepository extends JpaRepository<Platform, Long> {
    List<Platform> findByCurrency(Currency currency);
    Optional<Platform> findByName(String name);

}