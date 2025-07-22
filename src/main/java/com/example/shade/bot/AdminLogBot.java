package com.example.shade.bot;

import com.example.shade.model.BlockedUser;
import com.example.shade.repository.BlockedUserRepository;
import com.example.shade.service.AdminLogBotService;
import com.example.shade.service.BonusService;
import com.example.shade.service.TopUpService;
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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class AdminLogBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(AdminLogBot.class);
    private final AdminLogBotService adminLogBotService;
    private final AdminTelegramMessageSender adminTelegramMessageSender;
    private final WithdrawService withdrawService;
    private final BonusService bonusService;
    private final TopUpService topUpService;
    private final BlockedUserRepository blockedUserRepository;

    @Value("${telegram.admin.bot.token}")
    private String botToken;

    @Value("${telegram.admin.bot.username}")
    private String botUsername;

    // Static list of motivational texts in Uzbek
    private static final List<String> MOTIVATIONAL_TEXTS = List.of(
            "Sizning har bir harakatingiz foydalanuvchilarga yordam beradi! Ishda davom eting! 💪",
            "Har bir muvaffaqiyat kichik qadamlardan boshlanadi. Ajoyib ish! 🔥",
            "Sizning mehnatingiz tufayli platformamiz yanada yaxshi! Rahmat! 🌟",
            "Qiyinchiliklar faqat sizni kuchliroq qiladi. Oldinga! 🚀",
            "Sizning fidoyiligingiz muhim. Har kuni yangi imkoniyatlar! ✨",
            "Ishingizdagi sadoqat ajoyib natijalar keltiradi! Davom eting! 🏆",
            "Sizning harakatlaringiz jamoamizning muvaffaqiyatidir! Rahmat! 🙌",
            "Bugun qilgan ishlaringiz kelajakni shakllantiradi! 💼"
    );

    // Lorem ipsum text in Uzbek (>200 words)
    private static final String LOREM_IPSUM_UZBEK = "Lorem ipsum og'riqli bo'lishi kerak, lekin ayni paytda u foydalanuvchilar uchun muhim ma'lumotlarni taqdim etadi. Ushbu matn oddiy so'zlar to'plami emas, balki dizayn va tarkibni sinash uchun ishlatiladigan maxsus shakl hisoblanadi. Har bir loyiha muvaffaqiyatga erishish uchun aniq maqsadlarga ega bo'lishi kerak. Sizning harakatlaringiz ushbu maqsadlarga erishishda muhim ahamiyatga ega. Har bir kichik qadam katta natijalarga olib keladi. Ish jarayonida qiyinchiliklar bo'lishi mumkin, lekin bu qiyinchiliklar faqat sizni yanada kuchliroq qiladi. Har kuni yangi imkoniyatlar ochiladi, va sizning fidoyiligingiz ushbu imkoniyatlarni ro'yobga chiqaradi. Platformamizning muvaffaqiyati sizning mehnatingizga bog'liq, shuning uchun har bir vazifa muhimdir. Davom eting, chunki sizning ishlaringiz kelajakni shakllantiradi. Har bir muvaffaqiyatli yakunlangan vazifa jamoamizning umumiy maqsadlariga hissa qo'shadi. Ishonch bilan oldinga intiling, chunki sizning har bir harakatingiz foydalanuvchilar uchun qulaylik yaratadi. Sizning sadoqatingiz va mehnatingiz tufayli bizning platformamiz yanada rivojlanadi va foydalanuvchilar uchun eng yaxshi xizmatni taqdim etadi. Har kuni yangi imkoniyatlar ochiladi, va sizning harakatlaringiz ushbu imkoniyatlarni amalga oshirishga yordam beradi.";

    private static final Random RANDOM = new Random();

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
                handleTextMessage(update.getMessage().getText(), update.getMessage().getChatId(), update.getMessage().getMessageId());
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
        message.setReplyMarkup(createAdminMenuKeyboard());

        if (messageText.startsWith("/block ")) {
            try {
                String[] parts = messageText.split(" ");
                if (parts.length != 2) {
                    message.setText("❌ Xatolik: /block <userId> formatida kiriting.");
                    adminTelegramMessageSender.sendMessage(message, chatId);
                    return;
                }
                Long userId = Long.parseLong(parts[1]);
                BlockedUser user = blockedUserRepository.findById(userId)
                        .orElse(BlockedUser.builder().chatId(userId).build());
                if ("BLOCKED".equals(user.getPhoneNumber())) {
                    message.setText("❌ Bu foydalanuvchi allaqachon bloklangan.");
                } else {
                    user.setPhoneNumber("BLOCKED");
                    blockedUserRepository.save(user);
                    message.setText("✅ Foydalanuvchi (ID: " + userId + ") bloklandi.");
                    logger.info("User {} blocked by admin chatId {}", userId, chatId);
                }
            } catch (NumberFormatException e) {
                message.setText("❌ Xatolik: Foydalanuvchi ID raqam bo‘lishi kerak.");
                logger.warn("Invalid userId format in /block command from chatId {}: {}", chatId, messageText);
            }
        } else if (messageText.startsWith("/unblock ")) {
            try {
                String[] parts = messageText.split(" ");
                if (parts.length != 2) {
                    message.setText("❌ Xatolik: /unblock <userId> formatida kiriting.");
                    adminTelegramMessageSender.sendMessage(message, chatId);
                    return;
                }
                Long userId = Long.parseLong(parts[1]);
                BlockedUser user = blockedUserRepository.findById(userId).orElse(null);
                if (user == null || !"BLOCKED".equals(user.getPhoneNumber())) {
                    message.setText("❌ Bu foydalanuvchi bloklanmagan.");
                } else {
                    user.setPhoneNumber(null);
                    blockedUserRepository.save(user);
                    message.setText("✅ Foydalanuvchi (ID: " + userId + ") blokdan chiqarildi.");
                    logger.info("User {} unblocked by admin chatId {}", userId, chatId);
                }
            } catch (NumberFormatException e) {
                message.setText("❌ Xatolik: Foydalanuvchi ID raqam bo‘lishi kerak.");
                logger.warn("Invalid userId format in /unblock command from chatId {}: {}", chatId, messageText);
            }
        } else if (messageText.equals("💡 Motivatsiya")) {
            message.setText(getRandomMotivationalText() + "\n\n\n" + LOREM_IPSUM_UZBEK);
        } else {
            switch (messageText) {
                case "/start" -> {
                    adminLogBotService.registerAdmin(chatId, messageId);
                    message.setText("✅ Siz admin sifatida ro‘yxatdan o‘tdingiz. Loglar ushbu chatga yuboriladi.");
                }
                case "/view_logs" -> {
                    message.setText("Loglar bazada saqlanmaydi. Faqat ushbu chatda ko‘rishingiz mumkin.");
                }
                case "/unregister" -> {
                    boolean deleted = adminLogBotService.deleteAdminChat(chatId);
                    message.setText(deleted ? "✅ Admin ro‘yxatdan o‘chirildi." : "❌ Xatolik: Siz ro‘yxatdan o‘tmagansiz.");
                }
                default -> {
                    adminTelegramMessageSender.clearBotData(chatId, messageId);
                    return; // No message sent for unknown commands
                }
            }
        }
        adminTelegramMessageSender.sendMessage(message, chatId);
        logger.info("Sent message to admin chatId {}: {}", chatId, message.getText());
    }

    private void handleCallbackQuery(String callbackData, Long chatId, Integer messageId) {
        logger.info("Processing callback from admin chatId {}: {}", chatId, callbackData);

        // Remove buttons from original message
        EditMessageReplyMarkup editMessage = new EditMessageReplyMarkup();
        editMessage.setChatId(chatId.toString());
        editMessage.setMessageId(messageId);
        editMessage.setReplyMarkup(null);
        try {
            execute(editMessage);
            logger.info("Removed buttons from messageId {} in admin chatId {}", messageId, chatId);
        } catch (Exception e) {
            logger.error("Failed to remove buttons from messageId {} in admin chatId {}: {}", messageId, chatId, e.getMessage());
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setReplyMarkup(createAdminMenuKeyboard());

        if (callbackData.startsWith("APPROVE_WITHDRAW:")) {
            Long requestId = Long.parseLong(callbackData.split(":")[1]);
            withdrawService.processAdminApproval(chatId, requestId, true);
            return;
        } else if (callbackData.startsWith("REJECT_WITHDRAW:")) {
            Long requestId = Long.parseLong(callbackData.split(":")[1]);
            withdrawService.processAdminApproval(chatId, requestId, false);
            return;
        } else if (callbackData.startsWith("SCREENSHOT_APPROVE:")) {
            Long requestId = Long.parseLong(callbackData.split(":")[1]);
            topUpService.handleScreenshotApproval(chatId, requestId, true);
            return;
        } else if (callbackData.startsWith("SCREENSHOT_REJECT:")) {
            Long requestId = Long.parseLong(callbackData.split(":")[1]);
            topUpService.handleScreenshotApproval(chatId, requestId, false);
            return;
        } else if (callbackData.startsWith("ADMIN_APPROVE_TRANSFER:")) {
            Long requestId = Long.parseLong(callbackData.split(":")[1]);
            bonusService.handleAdminApproveTransfer(chatId, requestId);
            return;
        } else if (callbackData.startsWith("ADMIN_DECLINE_TRANSFER:")) {
            Long requestId = Long.parseLong(callbackData.split(":")[1]);
            bonusService.handleAdminDeclineTransfer(chatId, requestId);
            return;
        } else if (callbackData.startsWith("ADMIN_REMOVE_TICKETS:")) {
            Long userId = Long.parseLong(callbackData.split(":")[1]);
            bonusService.handleAdminRemoveTickets(chatId, userId);
            return;
        } else if (callbackData.startsWith("ADMIN_REMOVE_BONUS:")) {
            Long userId = Long.parseLong(callbackData.split(":")[1]);
            bonusService.handleAdminRemoveBonus(chatId, userId);
            return;
        } else if (callbackData.startsWith("ADMIN_BLOCK_USER:")) {
            Long userId = Long.parseLong(callbackData.split(":")[1]);
            BlockedUser user = blockedUserRepository.findById(userId)
                    .orElse(BlockedUser.builder().chatId(userId).build());
            if ("BLOCKED".equals(user.getPhoneNumber())) {
                message.setText("❌ Bu foydalanuvchi allaqachon bloklangan.");
            } else {
                user.setPhoneNumber("BLOCKED");
                blockedUserRepository.save(user);
                message.setText("✅ Foydalanuvchi (ID: " + userId + ") bloklandi.");
                logger.info("User {} blocked by admin chatId {}", userId, chatId);
            }
        } else {
            message.setText("Noto‘g‘ri buyruq.");
        }
        adminTelegramMessageSender.sendMessage(message, chatId);
    }

    private String getRandomMotivationalText() {
        return MOTIVATIONAL_TEXTS.get(RANDOM.nextInt(MOTIVATIONAL_TEXTS.size()));
    }

    private ReplyKeyboardMarkup createAdminMenuKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("💡 Motivatsiya")); // Only motivation button
        rows.add(row);
        markup.setKeyboard(rows);
        return markup;
    }
}