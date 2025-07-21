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

    public void handleContact(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("📞 Bog‘lanish uchun quyidagi tugmalardan foydalaning:");
        message.setReplyMarkup(createContactKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private InlineKeyboardMarkup createContactKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // 1 - Admin tugmasi
        rows.add(List.of(createButton("👤 Admin", "https://t.me/Boss9w")));

        // 2 - Chat tugmasi
        rows.add(List.of(createButton("💬 Chat", "https://t.me/Abadiy_Kassa"))); // replace with actual group/chat link
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private List<InlineKeyboardButton> createNavigationButtons() {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        buttons.add(createButton("🔙 Orqaga", "BACK"));
        buttons.add(createButton("🏠 Bosh sahifa", "HOME"));
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
