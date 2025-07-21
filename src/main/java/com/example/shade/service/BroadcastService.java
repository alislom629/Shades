package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import com.example.shade.bot.ShadePaymentBot;
import com.example.shade.model.BlockedUser;
import com.example.shade.repository.BlockedUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class BroadcastService {
    private static final Logger logger = LoggerFactory.getLogger(BroadcastService.class);
    private final BlockedUserRepository blockedUserRepository;
    private final TaskScheduler taskScheduler;
    private final MessageSender messageSender;

    public void sendBroadcast(String messageText, String parseMode, String buttonText, String buttonUrl, LocalDateTime scheduledTime) {
        // Validate parseMode
        String effectiveParseMode = parseMode != null && parseMode.equalsIgnoreCase("HTML") ? "HTML" : null;
        if (effectiveParseMode != null && !isValidHtml(messageText)) {
            logger.warn("Invalid HTML in messageText: {}", messageText);
            throw new IllegalArgumentException("Invalid HTML content in message text.");
        }

        // Prepare the message
        InlineKeyboardMarkup markup;
        if (buttonText != null && !buttonText.trim().isEmpty() && buttonUrl != null && !buttonUrl.trim().isEmpty()) {
            markup = createButtonMarkup(buttonText, buttonUrl);
        } else {
            markup = null;
        }

        // Get all non-blocked users
        List<BlockedUser> users = blockedUserRepository.findAllByPhoneNumberNot("BLOCKED");
        logger.info("Found {} non-blocked users for broadcast", users.size());

        // Track success and failure counts
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Create broadcast task
        Runnable broadcastTask = () -> {
            for (BlockedUser user : users) {
                try {
                    SendMessage message = new SendMessage();
                    message.setChatId(user.getChatId());
                    message.setText(messageText);
                    if (effectiveParseMode != null) {
                        message.setParseMode(effectiveParseMode);
                    }
                    if (markup != null) {
                        message.setReplyMarkup(markup);
                    }
                    messageSender.sendMessage(message, user.getChatId());
                    successCount.incrementAndGet();
                    logger.info("Broadcast sent to user {}", user.getChatId());
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    logger.error("Failed to send broadcast to user {}: {}", user.getChatId(), e.getMessage());
                }
            }
            logger.info("Broadcast completed: {} successful, {} failed", successCount.get(), failureCount.get());
        };

        // Schedule or execute immediately
        if (scheduledTime != null && scheduledTime.isAfter(LocalDateTime.now())) {
            taskScheduler.schedule(broadcastTask, scheduledTime.atZone(ZoneId.systemDefault()).toInstant());
            logger.info("Broadcast scheduled for {}", scheduledTime);
        } else {
            broadcastTask.run();
        }
    }

    private InlineKeyboardMarkup createButtonMarkup(String buttonText, String buttonUrl) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(buttonText);
        button.setUrl(buttonUrl);
        rows.add(List.of(button));
        markup.setKeyboard(rows);
        return markup;
    }

    private boolean isValidHtml(String messageText) {
        // Basic validation for Telegram-supported HTML tags
        // Telegram supports: <b>, <i>, <a>, <code>, <pre>, <s>, <u>, <em>, <strong>, <tg-spoiler>, <tg-emoji>
        String[] allowedTags = {"b", "i", "a", "code", "pre", "s", "u", "em", "strong", "tg-spoiler", "tg-emoji"};
        for (String tag : allowedTags) {
            messageText = messageText.replaceAll("<" + tag + "[^>]*>", "").replaceAll("</" + tag + ">", "");
        }
        // Check if any HTML tags remain
        return !messageText.matches(".*<[a-zA-Z]+[^>]*>.*");
    }
}