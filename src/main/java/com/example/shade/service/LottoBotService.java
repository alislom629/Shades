package com.example.shade.service;

import com.example.shade.bot.LottoMessageSender;
import com.example.shade.model.AdminChat;
import com.example.shade.repository.AdminChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

@Service
public class LottoBotService {
    private static final Logger logger = LoggerFactory.getLogger(LottoBotService.class);
    private final AdminChatRepository adminChatRepository;
    private final LottoMessageSender messageSender;
    private final LanguageSessionService languageSessionService;
    private static final Random RANDOM = new Random();

    public LottoBotService(AdminChatRepository adminChatRepository, LottoMessageSender messageSender, LanguageSessionService languageSessionService) {
        this.adminChatRepository = adminChatRepository;
        this.messageSender = messageSender;
        this.languageSessionService = languageSessionService;
    }

    public void logWin(long numberOfTickets, Long userId, Long amount) {
        if (amount <= 20000) {
            logger.info("Win amount {} for userId {} is not greater than 20,000; no log sent", amount, userId);
            return;
        }

        String maskedUserId = userId.toString().length() >= 7
                ? userId.toString().substring(0, 3).concat("***").concat(userId.toString().substring(6))
                : userId.toString();
        String date = LocalDateTime.now(ZoneId.of("GMT+5"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logMessage = String.format(
                languageSessionService.getTranslation(userId, "lotto.message.win_log"),
                numberOfTickets, amount, maskedUserId, date, getRandomCongratulations(userId)
        );

        List<AdminChat> adminChats = adminChatRepository.findByReceiveNotificationsTrue();
        if (adminChats.isEmpty()) {
            logger.warn("No admin channels with notifications enabled for userId {} and amount {}", userId, amount);
            return;
        }

        for (AdminChat adminChat : adminChats) {
            messageSender.sendMessage(adminChat.getChatId(), logMessage);
            logger.info("Sent win log to channel {}: {}", adminChat.getChatId(), logMessage);
        }
    }

    private String getRandomCongratulations(Long chatId) {
        int index = RANDOM.nextInt(4) + 1; // Random index from 1 to 4
        String translationKey = "lotto.congratulations." + index;
        return languageSessionService.getTranslation(chatId, translationKey);
    }
}