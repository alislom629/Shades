package com.example.shade.service;

import com.example.shade.bot.LottoMessageSender;
import com.example.shade.model.User;
import com.example.shade.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

@Service
public class LottoBotService {
    private static final Logger logger = LoggerFactory.getLogger(LottoBotService.class);
    private final UserRepository userRepository;
    private final LottoMessageSender messageSender;

    private static final List<String> CONGRATULATIONS_MESSAGES = List.of(
            "Tabriklar! Siz katta yutuqqa erishdingiz! 🎉",
            "Ajoyib! Sizning yutug'ingiz ajoyib! 🏆",
            "Wow, katta yutuq! Tabriklar! 🎊",
            "Siz yutdingiz! Ajoyib natija! ✨"
    );

    private static final Random RANDOM = new Random();

    public LottoBotService(UserRepository userRepository, LottoMessageSender messageSender) {
        this.userRepository = userRepository;
        this.messageSender = messageSender;
    }

    public void logWin(long numberOfTickets, Long userId, Long amount) {
        if (amount <= 20000) {
            logger.info("Win amount {} for userId {} is not greater than 20,000; no log sent", amount, userId);
            return;
        }

        String logMessage = String.format(
                "Tanlangan %s ta bilet va ularning qiymati:  \n\n \uD83C\uDF81 Bonus Miqdori: %s\uD83D\uDCB0 \n\uD83D\uDC64 User Id:  `%s` \n\uD83D\uDCC5 Date:  %s \n\uD83D\uDCB0  %s",
                numberOfTickets,
                amount,
                userId.toString().substring(0,3).concat("***").concat(userId.toString().substring(6)),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                getRandomCongratulations()+"\n\n" +
                        "Avtobot: @xonpeybot\n" +
                        "Admin: @Boss9w\n" +
                        "Chat: @Abadiy_kassa"
        );

        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            logger.warn("No registered users to send log for userId {} and amount {}", userId, amount);
            return;
        }

        for (User user : users) {
            messageSender.sendMessage(user.getChatId(), logMessage);
            logger.info("Sent win log to chatId {}: {}", user.getChatId(), logMessage);
        }
    }

    private String getRandomCongratulations() {
        return CONGRATULATIONS_MESSAGES.get(RANDOM.nextInt(CONGRATULATIONS_MESSAGES.size()));
    }
}