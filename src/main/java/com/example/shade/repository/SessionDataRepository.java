package com.example.shade.repository;

import com.example.shade.model.SessionData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionDataRepository extends JpaRepository<SessionData, Long> {
    List<SessionData> findByChatId(Long chatId);
    Optional<SessionData> findByChatIdAndKey(Long chatId, String key);
    void deleteByChatId(Long chatId);
}