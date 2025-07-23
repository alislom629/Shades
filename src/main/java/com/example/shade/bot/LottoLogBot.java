package com.example.shade.bot;

import com.example.shade.repository.AdminChatRepository;
import com.example.shade.service.LotteryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LottoLogBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(LottoLogBot.class);
    private final LottoMessageSender messageSender;
    private final AdminChatRepository adminChatRepository;
    private final LotteryService lotteryService;

    private static final Long MINIMUM_TICKETS = 1L;
    private static final Long MAXIMUM_TICKETS = 400L;
    private final Map<Long, String> userState = new HashMap<>();

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
            if (update.hasMessage() && update.getMessage().hasText()) {
                Long chatId = update.getMessage().getChatId();
                String messageText = update.getMessage().getText();

                if ("/start".equals(messageText)) {
                    handleStartCommand(chatId);
                    return;
                }
                if (!adminChatRepository.findById(chatId).isPresent()) {return;
                }
                    if ("🎟 O‘ynash".equals(messageText)) {
                    handlePlayCommand(chatId);
                } else if (userState.getOrDefault(chatId, "").equals("AWAITING_TICKET_COUNT")) {
                    handleTicketCountInput(chatId, messageText);
                } else {
                    messageSender.sendMessage(chatId.toString(), "Iltimos, /start buyrug‘ini ishlating yoki 🎟 O‘ynash tugmasini bosing.", createLotteryMenu());
                }
            } else {
                logger.warn("Received invalid update: {}", update);
            }
        } catch (Exception e) {
            logger.error("Error processing update: {}", update, e);
            messageSender.sendMessage(update.getMessage().getChatId().toString(), "Xatolik: " + e.getMessage(), createLotteryMenu());
        }
    }

    private void handleStartCommand(Long chatId) {
        if (adminChatRepository.findById(chatId).isPresent()) {
            userState.remove(chatId);
            messageSender.sendMessage(chatId.toString(), "✅ Lotereya o‘ynash uchun 🎟 O‘ynash tugmasini bosing.", createLotteryMenu());
            logger.info("User {} started LottoLogBot", chatId);
        }
    }

    private void handlePlayCommand(Long chatId) {
        userState.put(chatId, "AWAITING_TICKET_COUNT");
        messageSender.sendMessage(chatId.toString(), "Nechta chipta o‘ynamoqchisiz? (1-400)", createLotteryMenu());
    }

    private void handleTicketCountInput(Long chatId, String input) {
        try {
            Long numberOfPlays = Long.parseLong(input.trim());
            if (numberOfPlays < MINIMUM_TICKETS || numberOfPlays > MAXIMUM_TICKETS) {
                messageSender.sendMessage(chatId.toString(), String.format("Noto‘g‘ri son! 1 dan %d gacha son kiriting.", MAXIMUM_TICKETS), createLotteryMenu());
                return;
            }

            userState.remove(chatId);
            Map<Long, BigDecimal> ticketWinnings = lotteryService.playLotteryWithDetailsLottoBot(chatId, numberOfPlays);
            BigDecimal totalWinnings = ticketWinnings.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

            StringBuilder winningsLog = new StringBuilder("🎉 Lotereya natijalari:\n");
            ticketWinnings.forEach((ticketNumber, amount) ->
                    winningsLog.append(String.format("%d so‘m\n", amount.intValue())));
            winningsLog.append(String.format("Jami yutuq: %,d so‘m\nYangi balans: %,d so‘m", totalWinnings.intValue(), 0));

            messageSender.sendMessage(chatId.toString(), winningsLog.toString(), createLotteryMenu());
        } catch (NumberFormatException e) {
            messageSender.sendMessage(chatId.toString(), "Iltimos, faqat raqam kiriting (1-400).", createLotteryMenu());
        } catch (IllegalStateException e) {
            messageSender.sendMessage(chatId.toString(), "Xatolik: " + e.getMessage(), createLotteryMenu());
            userState.remove(chatId);
        }
    }

    private ReplyKeyboardMarkup createLotteryMenu() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("🎟 O‘ynash"));
        rows.add(row);
        markup.setKeyboard(rows);
        return markup;
    }
}