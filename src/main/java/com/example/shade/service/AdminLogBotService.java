package com.example.shade.service;

import com.example.shade.bot.AdminTelegramMessageSender;
import com.example.shade.model.*;
import com.example.shade.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminLogBotService {
    private static final Logger logger = LoggerFactory.getLogger(AdminLogBotService.class);
    private final AdminTelegramMessageSender adminTelegramMessageSender;
    private final AdminChatRepository adminChatRepository;
    private final HizmatRequestRepository requestRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final AdminCardRepository adminCardRepository;
    private final BlockedUserRepository blockedUserRepository;

    public void sendScreenshotRequest(SendPhoto sendPhoto, Long userChatId) {
        if (sendPhoto == null || sendPhoto.getPhoto() == null) {
            logger.error("Invalid SendPhoto object or file for userChatId {}", userChatId);
            return;
        }
        var adminChats = adminChatRepository.findByReceiveNotificationsTrue();
        if (adminChats.isEmpty()) {
            logger.warn("No admin chat IDs with notifications enabled to send screenshot for userChatId {}", userChatId);
            return;
        }

        // Fetch the latest pending screenshot request for the user
        HizmatRequest request = requestRepository.findByChatIdAndStatus(userChatId, RequestStatus.PENDING_SCREENSHOT)
                .orElse(null);
        if (request == null) {
            logger.error("No pending screenshot request found for userChatId {}", userChatId);
            sendToAdmins("❌ No pending screenshot request found for userChatId: " + userChatId);
            return;
        }

        // Calculate RUB amount if needed
        ExchangeRate latest = exchangeRateRepository.findLatest()
                .orElseThrow(() -> new RuntimeException("No exchange rate found in the database"));
        long rubAmount =
                BigDecimal.valueOf(request.getUniqueAmount())
                        .multiply(latest.getUzsToRub())
                        .longValue() / 1000;
        // Format log message as photo caption
        String number = blockedUserRepository.findByChatId(userChatId).get().getPhoneNumber();

        String logMessage = String.format(
                "📋 Hizmat requestId: %d To‘lov skrinshoti keldi 📷\n" +
                        "👤 User ID [%s] %s\n" +
                        "🌐 %s: " + "%s\n"+
                        "💸 Miqdor: %,d UZS\n" +
                        "💸 Miqdor: %,d RUB\n" +
                        "💳 Karta: `%s`\n" +
                        "🔐 Admin kartasi: `%s`\n" +
                        "📅 [%s]",
                request.getId(),
                userChatId, number,  request.getPlatform(), request.getPlatformUserId(),
                request.getUniqueAmount(), rubAmount, request.getCardNumber(),
                request.getAdminCardId() != null ? adminCardRepository.findById(request.getAdminCardId()).map(AdminCard::getCardNumber).orElse("N/A") : "N/A",
                LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // Set log message as photo caption
        sendPhoto.setCaption(logMessage);

        // Send screenshot with log caption to admins
        for (AdminChat adminChat : adminChats) {
            try {
                sendPhoto.setChatId(adminChat.getChatId().toString());
                adminTelegramMessageSender.sendScreenshotRequest(sendPhoto, adminChat.getChatId());
                logger.info("Sent screenshot with log caption to admin chatId {} for userChatId {}", adminChat.getChatId(), userChatId);
            } catch (Exception e) {
                logger.error("Failed to send screenshot with log caption to admin chatId {} for userChatId {}: {}",
                        adminChat.getChatId(), userChatId, e.getMessage());
            }
        }
    }

    public void registerAdmin(Long chatId, Integer messageId) {
        AdminChat adminChat = adminChatRepository.findById(chatId)
                .orElse(null);
        String message = "";
        if (adminChat == null) {
            adminTelegramMessageSender.clearBotData(chatId, messageId);
        }
        if (adminChat != null && !adminChat.isReceiveNotifications()) {
            adminChatRepository.delete(adminChat);
            adminTelegramMessageSender.clearBotData(chatId, messageId);
            logger.info("Deleted AdminChat for chatId {} due to disabled notifications or unauthorized access", chatId);
        } else if (adminChat != null) {
            logger.info("ChatId {} already registered as admin", chatId);
            message = "✅ Foydalanuvchi qaytdi";
        }
        sendToAdmins(message);
        logger.info("Sent register admin message to admins for chatId: {}", chatId);
    }

    public boolean createAdminChat(Long chatId, boolean receiveNotifications) {
        if (adminChatRepository.findById(chatId).isPresent()) {
            logger.warn("Admin chatId {} already exists", chatId);
            sendToAdmins("❌ Admin chatId " + chatId + " already exists.");
            return false;
        }
        AdminChat adminChat = AdminChat.builder()
                .chatId(chatId)
                .receiveNotifications(receiveNotifications)
                .build();
        adminChatRepository.save(adminChat);
        logger.info("Created new admin chatId: {} with notifications: {}", chatId, receiveNotifications);
        sendToAdmins("✅ New admin chatId " + chatId + " created with notifications: " + receiveNotifications);
        return true;
    }

    public List<AdminChat> getAllAdminChats() {
        List<AdminChat> adminChats = adminChatRepository.findAll();
        logger.info("Retrieved {} admin chats", adminChats.size());
        return adminChats;
    }

    public void toggleNotifications(Long chatId, boolean enable) {
        AdminChat adminChat = adminChatRepository.findById(chatId).orElse(null);
        if (adminChat == null) {
            logger.warn("Admin chatId {} not found for toggling notifications", chatId);
            sendToAdmins("❌ Admin chatId " + chatId + " not found for toggling notifications.");
            return;
        }
        adminChat.setReceiveNotifications(enable);
        adminChatRepository.save(adminChat);
        String message = enable ? "✅ Bildirishnomalar yoqildi for chatId: " + chatId :
                "🔔 Bildirishnomalar o‘chirildi for chatId: " + chatId;
        logger.info("{} notifications for admin chatId: {}", enable ? "Enabled" : "Disabled", chatId);
        sendToAdmins(message);
    }

    public boolean updateNotifications(Long chatId, boolean enable) {
        AdminChat adminChat = adminChatRepository.findById(chatId).orElse(null);
        if (adminChat == null) {
            logger.warn("Admin chatId {} not found for updating notifications", chatId);
            sendToAdmins("❌ Admin chatId " + chatId + " not found for updating notifications.");
            return false;
        }
        adminChat.setReceiveNotifications(enable);
        adminChatRepository.save(adminChat);
        logger.info("Updated notifications to {} for admin chatId: {}", enable, chatId);
        sendToAdmins("✅ Updated notifications to " + enable + " for admin chatId: " + chatId);
        return true;
    }

    public boolean deleteAdminChat(Long chatId) {
        AdminChat adminChat = adminChatRepository.findById(chatId).orElse(null);
        if (adminChat == null) {
            logger.warn("Admin chatId {} not found for deletion", chatId);
            sendToAdmins("❌ Admin chatId " + chatId + " not found for deletion.");
            return false;
        }
        adminChatRepository.delete(adminChat);
        logger.info("Deleted admin chatId: {}", chatId);
        sendToAdmins("✅ Admin chatId o'chirildi: " + chatId);
        return true;
    }

    public void sendLog(String message) {
        var adminChats = adminChatRepository.findByReceiveNotificationsTrue();
        if (adminChats.isEmpty()) {
            logger.warn("No admin chat IDs with notifications enabled to send log: {}", message);
            return;
        }
        for (AdminChat adminChat : adminChats) {
            adminTelegramMessageSender.sendMessage(adminChat.getChatId(), message);
            logger.info("Sent log to admin chatId: {}", adminChat.getChatId());
        }
    }

    public void sendWithdrawRequestToAdmins(Long userChatId, String message, Long requestId) {
        sendWithdrawRequestToAdmins(userChatId, message, requestId, createApprovalKeyboard(requestId));
    }

    public void sendWithdrawRequestToAdmins(Long userChatId, String message, Long requestId, InlineKeyboardMarkup keyboard) {
        var adminChats = adminChatRepository.findByReceiveNotificationsTrue();
        if (adminChats.isEmpty()) {
            logger.warn("No admin chat IDs with notifications enabled to send withdraw request: {}", message);
            return;
        }
        for (AdminChat adminChat : adminChats) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(adminChat.getChatId().toString());
            sendMessage.setText(message);
            sendMessage.enableMarkdown(true);
            sendMessage.setReplyMarkup(keyboard);
            adminTelegramMessageSender.sendMessage(sendMessage, adminChat.getChatId());
            logger.info("Sent withdraw request to admin chatId {} for userChatId {}, requestId {}",
                    adminChat.getChatId(), userChatId, requestId);
        }
    }

    public void sendToAdmins(String message) {
        var adminChats = adminChatRepository.findByReceiveNotificationsTrue();
        if (adminChats.isEmpty()) {
            logger.warn("No admin chat IDs with notifications enabled to send message: {}", message);
            return;
        }
        for (AdminChat adminChat : adminChats) {
            adminTelegramMessageSender.sendMessage(adminChat.getChatId(), message);
            logger.info("Sent message to admin chatId: {}", adminChat.getChatId());
        }
    }

    public void sendToAdmins(String message, InlineKeyboardMarkup keyboard) {
        var adminChats = adminChatRepository.findByReceiveNotificationsTrue();
        if (adminChats.isEmpty()) {
            logger.warn("No admin chat IDs with notifications enabled to send message: {}", message);
            return;
        }
        for (AdminChat adminChat : adminChats) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(adminChat.getChatId().toString());
            sendMessage.setText(message);
            sendMessage.setReplyMarkup(keyboard);
            adminTelegramMessageSender.sendMessage(sendMessage, adminChat.getChatId());
            logger.info("Sent message with keyboard to admin chatId: {}", adminChat.getChatId());
        }
    }

    private InlineKeyboardMarkup createApprovalKeyboard(Long requestId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("✅ Tasdiqlash", "APPROVE_WITHDRAW:" + requestId),
                createButton("❌ Rad etish", "REJECT_WITHDRAW:" + requestId)
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