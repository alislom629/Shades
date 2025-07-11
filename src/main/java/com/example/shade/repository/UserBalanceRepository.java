package com.example.shade.repository;

import com.example.shade.model.UserBalance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBalanceRepository extends JpaRepository<UserBalance, Long> {
}