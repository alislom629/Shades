package com.example.shade.bot;

import com.example.shade.model.UserSession;
import com.example.shade.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MessageSender {
    private static final Logger logger = LoggerFactory.getLogger(MessageSender.class);
    private final UserSessionService sessionService;
    private AbsSender bot;

    public void setBot(AbsSender bot) {
        this.bot = bot;
    }

    public void sendMessage(SendMessage message, Long chatId) {
        try {
            message.setChatId(chatId);
            var sentMessage = bot.execute(message);
            UserSession session = sessionService.getUserSession(chatId).orElse(new UserSession());
            session.setChatId(chatId);
            List<Integer> messageIds = sessionService.getMessageIds(chatId);
            messageIds.add(sentMessage.getMessageId());
            session.setMessageIds(messageIds);
            sessionService.saveUserSession(session);
        } catch (TelegramApiException e) {
            logger.error("Error sending message to chatId {}: {}", chatId, e.getMessage());
        }
    }

    public void sendMessage(Long chatId, String text) {
        int maxLength = 4096;

        // Split and send in parts
        for (int start = 0; start < text.length(); start += maxLength) {
            int end = Math.min(start + maxLength, text.length());
            String chunk = text.substring(start, end);

            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(chunk);
            sendMessage(message, chatId); // existing method
        }
    }


    public void sendMessage(Long chatId, String text, ReplyKeyboard replyMarkup) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(replyMarkup);
        sendMessage(message, chatId);
    }

    public void animateAndDeleteMessages(Long chatId, List<Integer> messageIds, String animationType) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        for (Integer messageId : messageIds) {
            try {
                bot.execute(new DeleteMessage(String.valueOf(chatId), messageId));
            } catch (TelegramApiException e) {
                if (!e.getMessage().contains("message to delete not found")) {
                    logger.error("Error deleting message {} for chatId {}: {}", messageId, chatId, e.getMessage());
                }
            }
        }
    }
}