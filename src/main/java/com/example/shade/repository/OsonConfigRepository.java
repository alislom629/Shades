package com.example.shade.repository;

import com.example.shade.model.OsonConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OsonConfigRepository extends JpaRepository<OsonConfig, Long> {
    Optional<OsonConfig> findByPrimaryConfigTrue();
}