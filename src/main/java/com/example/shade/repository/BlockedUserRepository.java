package com.example.shade.repository;

import com.example.shade.model.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {
    boolean existsByChatId(Long chatId);
}