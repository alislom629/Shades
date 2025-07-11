package com.example.shade.repository;

import com.example.shade.model.LoginEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginEventRepository extends JpaRepository<LoginEvent, Long> {
}