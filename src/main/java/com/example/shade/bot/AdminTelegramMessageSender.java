package com.example.shade.bot;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminTelegramMessageSender {
    private static final Logger logger = LoggerFactory.getLogger(AdminTelegramMessageSender.class);
    private AbsSender bot;

    public void setBot(AbsSender bot) {
        this.bot = bot;
    }

    public void sendMessage(Long chatId, String text) {
        if (bot == null) {
            logger.error("Bot not set for AdminTelegramMessageSender for chatId: {}", chatId);
            return;
        }
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            bot.execute(message);
            logger.info("Sent message to admin chatId {}: {}", chatId, text);
        } catch (TelegramApiException e) {
            logger.error("Failed to send message to admin chatId {}: {}", chatId, e.getMessage());
        }
    }

    public void sendMessage(SendMessage sendMessage, Long chatId) {
        if (bot == null) {
            logger.error("Bot not set for AdminTelegramMessageSender for chatId: {}", chatId);
            return;
        }
        try {
            bot.execute(sendMessage);
            logger.info("Sent message with keyboard to admin chatId {}: {}", chatId, sendMessage.getText());
        } catch (TelegramApiException e) {
            logger.error("Failed to send message with keyboard to admin chatId {}: {}", chatId, e.getMessage());
        }
    }

    public void sendPhoto(SendPhoto sendPhoto, Long chatId) {
        if (bot == null) {
            logger.error("Bot not set for AdminTelegramMessageSender for chatId: {}", chatId);
            return;
        }
        try {
            bot.execute(sendPhoto);
            logger.info("Sent photo to admin chatId {} with caption: {}", chatId, sendPhoto.getCaption());
        } catch (TelegramApiException e) {
            logger.error("Failed to send photo to admin chatId {}: {}", chatId, e.getMessage());
        }
    }
    public void deleteMessages(Long chatId, List<Integer> messageIds) {
        if (bot == null) {
            logger.error("Bot not set for AdminTelegramMessageSender for chatId: {}", chatId);
            return;
        }
        if (messageIds == null || messageIds.isEmpty()) {
            logger.info("No messages to delete for chatId {}", chatId);
            return;
        }
        for (Integer messageId : messageIds) {
            try {
                DeleteMessage deleteMessage = new DeleteMessage();
                deleteMessage.setChatId(chatId.toString());
                deleteMessage.setMessageId(messageId);
                bot.execute(deleteMessage);
                logger.info("Deleted messageId {} for chatId {}", messageId, chatId);
            } catch (TelegramApiException e) {
                logger.error("Failed to delete messageId {} for chatId {}: {}", messageId, chatId, e.getMessage());
            }
        }
    }
    public void clearBotData(Long chatId, Integer messageId) {
        if (bot == null) {
            logger.error("Bot not set for AdminTelegramMessageSender for chatId: {}", chatId);
            return;
        }
        // Delete the specific message
        if (messageId != null) {
            try {
                DeleteMessage deleteMessage = new DeleteMessage();
                deleteMessage.setChatId(chatId.toString());
                deleteMessage.setMessageId(messageId);
                bot.execute(deleteMessage);
                logger.info("Deleted messageId {} for chatId {}", messageId, chatId);
            } catch (TelegramApiException e) {
                logger.error("Failed to delete messageId {} for chatId {}: {}", messageId, chatId, e.getMessage());
            }
        }
    }
}