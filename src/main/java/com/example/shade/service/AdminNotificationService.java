package com.example.shade.service;

import java.util.List;

public interface AdminNotificationService {
    void sendWithdrawRequestToAdmins(Long userChatId, String message, Long requestId);
    void sendLog(String message);
}