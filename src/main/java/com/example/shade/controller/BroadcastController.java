package com.example.shade.controller;

import com.example.shade.service.BroadcastService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Base64;

@RestController
@RequestMapping("/api/broadcast")
@RequiredArgsConstructor
public class BroadcastController {
    private final BroadcastService broadcastService;

    public static class BroadcastRequest {
        private String messageText;
        private String parseMode; // New field for parse mode (e.g., "HTML")
        private String buttonText;
        private String buttonUrl;
        private LocalDateTime scheduledTime;

        // Getters and setters
        public String getMessageText() {
            return messageText;
        }

        public void setMessageText(String messageText) {
            this.messageText = messageText;
        }

        public String getParseMode() {
            return parseMode;
        }

        public void setParseMode(String parseMode) {
            this.parseMode = parseMode;
        }

        public String getButtonText() {
            return buttonText;
        }

        public void setButtonText(String buttonText) {
            this.buttonText = buttonText;
        }

        public String getButtonUrl() {
            return buttonUrl;
        }

        public void setButtonUrl(String buttonUrl) {
            this.buttonUrl = buttonUrl;
        }

        public LocalDateTime getScheduledTime() {
            return scheduledTime;
        }

        public void setScheduledTime(LocalDateTime scheduledTime) {
            this.scheduledTime = scheduledTime;
        }
    }

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000".equals(parts[1]);
        }
        return false;
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendBroadcast(HttpServletRequest request, @RequestBody BroadcastRequest broadcastRequest) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ Autentifikatsiya xatosi: Noto‘g‘ri foydalanuvchi yoki parol");
        }
        if (broadcastRequest.getMessageText() == null || broadcastRequest.getMessageText().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Message text is required.");
        }
        try {
            broadcastService.sendBroadcast(
                    broadcastRequest.getMessageText(),
                    broadcastRequest.getParseMode(),
                    broadcastRequest.getButtonText(),
                    broadcastRequest.getButtonUrl(),
                    broadcastRequest.getScheduledTime()
            );
            return ResponseEntity.ok("Broadcast initiated successfully.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to initiate broadcast: " + e.getMessage());
        }
    }
}