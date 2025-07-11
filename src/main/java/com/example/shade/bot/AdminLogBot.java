package com.example.shade.bot;

import com.example.shade.service.AdminLogBotService;
import com.example.shade.service.WithdrawService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminLogBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(AdminLogBot.class);
    private final AdminLogBotService adminLogBotService;
    private final AdminTelegramMessageSender adminTelegramMessageSender;
    private final WithdrawService withdrawService;

    @Value("${telegram.admin.bot.token}")
    private String botToken;

    @Value("${telegram.admin.bot.username}")
    private String botUsername;

    @PostConstruct
    public void init() {
        adminTelegramMessageSender.setBot(this);
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
        if (botToken == null || botToken.isEmpty()) {
            logger.error("Bot token not set in application.properties");
            throw new IllegalStateException("Bot token is missing");
        }
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update == null) {
                logger.warn("Received null update");
                return;
            }
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update.getMessage().getText(), update.getMessage().getChatId(),  update.getMessage().getMessageId());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery().getData(), update.getCallbackQuery().getMessage().getChatId(), update.getCallbackQuery().getMessage().getMessageId());
            }
        } catch (Exception e) {
            logger.error("Error processing update: {}", update, e);
        }
    }

    private void handleTextMessage(String messageText, Long chatId, Integer messageId) {
        logger.info("Processing message from chatId {}: {}", chatId, messageText);
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        switch (messageText) {
            case "/start" -> {
                adminLogBotService.registerAdmin(chatId,messageId);
                message.setText("✅ Siz admin sifatida ro‘yxatdan o‘tdingiz. Loglar ushbu chatga yuboriladi.");
                message.setReplyMarkup(createAdminMenuKeyboard());
            }
            case "/view_logs" -> {
                message.setText("Loglar bazada saqlanmaydi. Faqat ushbu chatda ko‘rishingiz mumkin.");
                message.setReplyMarkup(createAdminMenuKeyboard());
            }
            case "/unregister" -> {
                boolean deleted = adminLogBotService.deleteAdminChat(chatId);
                message.setText(deleted ? "✅ Admin ro‘yxatdan o‘chirildi." : "❌ Xatolik: Siz ro‘yxatdan o‘tmagansiz.");
            }
            default -> {
                adminTelegramMessageSender.clearBotData(chatId, messageId);
            }
        }
        adminTelegramMessageSender.sendMessage(message, chatId);
        logger.info("Sent message to admin chatId {}: {}", chatId, message.getText());
    }

    private void handleCallbackQuery(String callbackData, Long chatId, Integer messageId) {
        logger.info("Processing callback from admin chatId {}: {}", chatId, callbackData);
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        if ("/view_logs".equals(callbackData)) {
            message.setText("Loglar bazada saqlanmaydi. Faqat ushbu chatda ko‘rishingiz mumkin.");
            message.setReplyMarkup(createAdminMenuKeyboard());
            adminTelegramMessageSender.sendMessage(message, chatId);
        } else if (callbackData.startsWith("APPROVE_WITHDRAW:") || callbackData.startsWith("REJECT_WITHDRAW:")) {
            Long requestId = Long.parseLong(callbackData.split(":")[1]);
            boolean approve = callbackData.startsWith("APPROVE_WITHDRAW:");
            // Remove buttons from original message
            EditMessageReplyMarkup editMessage = new EditMessageReplyMarkup();
            editMessage.setChatId(chatId.toString());
            editMessage.setMessageId(messageId);
            editMessage.setReplyMarkup(null); // Remove buttons
            try {
                execute(editMessage);
                logger.info("Removed buttons from messageId {} in admin chatId {}", messageId, chatId);
            } catch (Exception e) {
                logger.error("Failed to remove buttons from messageId {} in admin chatId {}: {}", messageId, chatId, e.getMessage());
            }
            withdrawService.processAdminApproval(chatId, requestId, approve);
        } else if ("/unregister".equals(callbackData)) {
            boolean deleted = adminLogBotService.deleteAdminChat(chatId);
            message.setText(deleted ? "✅ Admin ro‘yxatdan o‘chirildi." : "❌ Xatolik: Siz ro‘yxatdan o‘tmagansiz.");
            adminTelegramMessageSender.sendMessage(message, chatId);
        } else {
            message.setText("Noto‘g‘ri buyruq.");
            adminTelegramMessageSender.sendMessage(message, chatId);
        }
    }

    private InlineKeyboardMarkup createAdminMenuKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("📜 Loglarni ko‘rish", "/view_logs"),
                createButton("❌ Ro‘yxatdan o‘chirish", "/unregister")
        ));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardButton createButton(String text, String callback) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callback);
        return button;
    }
}