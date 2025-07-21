package com.example.shade.repository;

import com.example.shade.model.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {
    boolean existsByChatId(Long chatId);

    Optional<BlockedUser> findByChatId(Long chatId);
}