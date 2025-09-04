package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContactService {
    private final MessageSender messageSender;
    private final LanguageSessionService languageSessionService;

    public void handleContact(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(languageSessionService.getTranslation(chatId, "contact.message.contact_prompt"));
        message.setReplyMarkup(createContactKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    private InlineKeyboardMarkup createContactKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // 1 - Admin button
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "contact.button.admin"), "https://t.me/Boss9w")));

        // 2 - Chat button
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "contact.button.chat"), "https://t.me/Abadiy_Kassa")));
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private List<InlineKeyboardButton> createNavigationButtons(Long chatId) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        buttons.add(createButton(languageSessionService.getTranslation(chatId, "contact.button.back"), "BACK"));
        buttons.add(createButton(languageSessionService.getTranslation(chatId, "contact.button.home"), "HOME"));
        return buttons;
    }

    private InlineKeyboardButton createButton(String text, String callbackOrUrl) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        if (callbackOrUrl.startsWith("http")) {
            button.setUrl(callbackOrUrl);
        } else {
            button.setCallbackData(callbackOrUrl);
        }
        return button;
    }
}