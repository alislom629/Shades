package com.example.shade.repository;

import com.example.shade.model.LotteryPrize;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotteryPrizeRepository extends JpaRepository<LotteryPrize, Long> {
}