package com.example.shade.bot;

import com.example.shade.model.User;
import com.example.shade.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;

import jakarta.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class LottoLogBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(LottoLogBot.class);
    private final LottoMessageSender messageSender;
    private final UserRepository userRepository;

    @Value("${telegram.logbot.token}")
    private String botToken;

    @Value("${telegram.logbot.username}")
    private String botUsername;

    @PostConstruct
    public void init() {
        messageSender.setBot(this);
        clearWebhook();
    }

    @PostConstruct
    public void clearWebhook() {
        try {
            execute(new DeleteWebhook());
            logger.info("Webhook cleared for {}", botUsername);
        } catch (Exception e) {
            logger.error("Error clearing webhook for {}: {}", botUsername, e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update == null || !update.hasMessage() || !update.getMessage().hasText()) {
                logger.warn("Received invalid update: {}", update);
                return;
            }
            Long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            if ("/start".equals(messageText)) {
                handleStartCommand(chatId);
            } else {
                messageSender.sendMessage(chatId, "Iltimos, faqat /start buyrug‘ini ishlating.");
            }
        } catch (Exception e) {
            logger.error("Error processing update: {}", update, e);
        }
    }

    private void handleStartCommand(Long chatId) {
        User user = userRepository.findByChatId(chatId).orElse(null);
        if (user == null) {
            new User();
            user.setChatId(chatId);
            userRepository.save(user);
        }
        messageSender.sendMessage(chatId, "✅ Siz muvaffaqiyatli ro‘yxatdan o‘tdingiz!");
        logger.info("User {} registered in LottoLogBot", chatId);
    }
}