package com.example.shade.repository;

import com.example.shade.model.AdminChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminChatRepository extends JpaRepository<AdminChat, Long> {
    List<AdminChat> findByReceiveNotificationsTrue();
}