package com.example.shade;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Component
public class ShadePaymentBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

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
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            if (messageText.equals("/start")) {
                sendWelcomeMessage(chatId);
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            Long chatId = update.getCallbackQuery().getMessage().getChatId();

            handleCallback(chatId, callbackData);
        }
    }

    private void sendWelcomeMessage(Long chatId) {
        String messageText = "Assalomu alaykum! Quyidagi tugmalar orqali kerakli bo‘limni tanlang 👇";

        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setResizeKeyboard(true); // Makes it look good on mobile
        replyKeyboardMarkup.setOneTimeKeyboard(false); // Keep it always shown

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("🏦 Hisob To'ldirish");
        row1.add("💸 Pul chiqarish");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🎁 Bonus");
        row2.add("ℹ️ Aloqa");

        keyboard.add(row1);
        keyboard.add(row2);

        replyKeyboardMarkup.setKeyboard(keyboard);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(messageText);
        message.setReplyMarkup(replyKeyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private void handleCallback(Long chatId, String data) {
        String response;

        switch (data) {
            case "TOP_UP":
                response = "💳 Hisobni to'ldirish uchun summani kiriting.";
                break;
            case "WITHDRAW":
                response = "💸 Pul chiqarish uchun kartangiz va summani kiriting.";
                break;
            case "BONUS":
                response = "🎁 Sizning bonuslaringiz: 0 UZS (hajmi keyinchalik o'zgaradi)";
                break;
            case "CONTACT":
                response = "📞 Aloqa uchun: @admin yoki tel: +998 90 123 45 67";
                break;
            default:
                response = "Noma'lum buyruq!";
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(response);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
