package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import com.example.shade.model.Currency;
import com.example.shade.model.HizmatRequest;
import com.example.shade.model.Platform;
import com.example.shade.model.UserProfile;
import com.example.shade.repository.HizmatRequestRepository;
import com.example.shade.repository.PlatformRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommonService {
    private static final Logger logger = LoggerFactory.getLogger(CommonService.class);
    private final UserSessionService sessionService;
    private final HizmatRequestRepository requestRepository;
    private final PlatformRepository platformRepository;
    private final MessageSender messageSender;
    private final RestTemplate restTemplate = new RestTemplate() ;

    public void sendPlatformSelection(Long chatId, String prefix) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Kontorani tanlang:");
        message.setReplyMarkup(createPlatformKeyboard(prefix));
        messageSender.sendMessage(message, chatId);
    }

//    public void validateUserId(Long chatId, String userId, String platform, String prefix) {
//        if (!isValidUserId(userId)) {
//            logger.warn("Invalid user ID format for chatId {}: {}", chatId, userId);
//            sendMessageWithNavigation(chatId, "Noto‘g‘ri ID formati. Iltimos, faqat raqamlardan iborat ID kiriting:");
//            return;
//        }
//        String apiUrl = String.format("https://api.%s.com/users/%s", platform.toLowerCase().replace("_", ""), userId);
//        logger.info("Validating user ID {} for platform {} (chatId: {})", userId, platform, chatId);
//        try {
//            ResponseEntity<UserProfile> response = restTemplate.getForEntity(apiUrl, UserProfile.class);
//            UserProfile profile = response.getBody();
//            if (profile != null && profile.get() != null) {
//                String fullName = formatFullName(profile);
//                sessionService.setUserData(chatId, "platformUserId", userId);
//                sessionService.setUserData(chatId, "fullName", fullName);
//                sessionService.setUserState(chatId, prefix + "_APPROVE_USER");
//                sendUserApproval(chatId, fullName, userId, prefix);
//            } else {
//                sendNoUserFound(chatId);
//            }
//        } catch (HttpClientErrorException.NotFound e) {
//            logger.warn("User not found for ID {} on platform {}: {}", userId, platform, e.getMessage());
//            sendNoUserFound(chatId);
//        } catch (Exception e) {
//            logger.error("Error calling API for user ID {} on platform {}: {}", userId, platform, e.getMessage());
//            sendMessageWithNavigation(chatId, "API xatosi yuz berdi. Iltimos, qayta urinib ko‘ring.");
//        }
//    }

    public void sendUserIdInput(Long chatId, String platform, String prefix) {
        logger.info("Sending user ID input for chatId {}, platform: {}", chatId, platform);
        List<HizmatRequest> recentRequests = requestRepository.findTop3ByChatIdAndPlatformOrderByCreatedAtDesc(chatId, platform);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        if (!recentRequests.isEmpty()) {
            HizmatRequest latestRequest = recentRequests.get(0);
            sessionService.setUserData(chatId, "platformUserId", latestRequest.getPlatformUserId());
            message.setText("Sizning so‘nggi ID: " + latestRequest.getPlatformUserId() + "\nShu IDni ishlatasizmi yoki yangi ID kiriting:");
            message.setReplyMarkup(createSavedIdKeyboard(recentRequests, prefix));
        } else {
            message.setText("Iltimos, " + platform + " uchun ID kiriting:");
            message.setReplyMarkup(createNavigationKeyboard());
        }
        messageSender.sendMessage(message, chatId);
    }

    public void sendCardInput(Long chatId, String fullName, String prefix) {
        logger.info("Sending card input for chatId {}, fullName: {}", chatId, fullName);
        List<HizmatRequest> recentRequests = requestRepository.findLatestUniqueCardNumbersByChatId(chatId);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        if (!recentRequests.isEmpty() && recentRequests.get(0).getCardNumber() != null) {
            HizmatRequest latestRequest = recentRequests.get(0);
            sessionService.setUserData(chatId, "cardNumber", latestRequest.getCardNumber());
            message.setText("F.I.O: " + fullName + "\nSo‘nggi karta: " + latestRequest.getCardNumber() + "\nShu kartani ishlatasizmi yoki yangi karta raqamini kiriting:");
            message.setReplyMarkup(createSavedCardKeyboard(recentRequests, prefix));
        } else {
            message.setText("F.I.O: " + fullName + "\nKarta raqamini kiriting (8600xxxxxxxxxxxx):");
            message.setReplyMarkup(createNavigationKeyboard());
        }
        messageSender.sendMessage(message, chatId);
    }

    public boolean isValidCard(String card) {
        return card.replaceAll("\\s+", "").matches("\\d{16}");
    }

    public boolean isValidAmount(String amountText, long minAmount, long maxAmount) {
        try {
            long amount = Long.parseLong(amountText.replaceAll("[^\\d]", ""));
            return amount >= minAmount && amount <= maxAmount;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void sendUserApproval(Long chatId, String fullName, String userId, String prefix) {
        logger.info("Sending user approval prompt for chatId {}, fullName: {}, userId: {}", chatId, fullName, userId);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format("Foydalanuvchi ma'lumotlari:\n\n👤 F.I.O: %s\n🆔 ID: %s\n\nBu ma'lumotlar to‘g‘rimi?", fullName, userId));
        message.setReplyMarkup(createApprovalKeyboard(prefix));
        messageSender.sendMessage(message, chatId);
    }

    private void sendNoUserFound(Long chatId) {
        logger.info("No user found for chatId {}", chatId);
        sendMessageWithNavigation(chatId, "Bu ID bo‘yicha foydalanuvchi topilmadi. Iltimos, boshqa ID kiriting:");
    }

    public void sendMessageWithNavigation(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(createNavigationKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private InlineKeyboardMarkup createPlatformKeyboard(String prefix) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<Platform> uzsPlatforms = platformRepository.findByCurrency(Currency.UZS);
        List<Platform> rubPlatforms = platformRepository.findByCurrency(Currency.RUB);
        int maxRows = Math.max(uzsPlatforms.size(), rubPlatforms.size());
        for (int i = 0; i < maxRows; i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            if (i < uzsPlatforms.size()) {
                Platform uzsPlatform = uzsPlatforms.get(i);
                row.add(createButton("🇺🇿 " + uzsPlatform.getName(), prefix + "_PLATFORM:" + uzsPlatform.getName()));
            }
            if (i < rubPlatforms.size()) {
                Platform rubPlatform = rubPlatforms.get(i);
                row.add(createButton("🇷🇺 " + rubPlatform.getName(), prefix + "_PLATFORM:" + rubPlatform.getName()));
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createSavedIdKeyboard(List<HizmatRequest> recentRequests, String prefix) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton("✅ Ha, ishlataman", prefix + "_USE_SAVED_ID")));
        if (recentRequests.size() > 1) {
            List<InlineKeyboardButton> pastIdButtons = recentRequests.stream()
                    .skip(1)
                    .map(HizmatRequest::getPlatformUserId)
                    .distinct()
                    .limit(2)
                    .map(id -> createButton("ID: " + id, prefix + "_PAST_ID:" + id))
                    .collect(Collectors.toList());
            if (!pastIdButtons.isEmpty()) {
                rows.add(pastIdButtons);
            }
        }
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createSavedCardKeyboard(List<HizmatRequest> recentRequests, String prefix) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton("✅ Ha, ishlataman", prefix + "_USE_SAVED_ID")));
        if (recentRequests.size() > 1) {
            List<InlineKeyboardButton> pastCardButtons = recentRequests.stream()
                    .skip(1)
                    .map(HizmatRequest::getCardNumber)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .limit(2)
                    .map(card -> createButton( card, prefix + "_PAST_CARD:" + card))
                    .collect(Collectors.toList());
            if (!pastCardButtons.isEmpty()) {
                rows.add(pastCardButtons);
            }
        }
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createApprovalKeyboard(String prefix) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("✅ To‘g‘ri", prefix + "_APPROVE_USER"),
                createButton("❌ Noto‘g‘ri", prefix + "_REJECT_USER")
        ));
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createNavigationKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
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

    private InlineKeyboardButton createButton(String text, String callback) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callback);
        return button;
    }


    private boolean isValidUserId(String userId) {
        return userId.matches("\\d+");
    }

    private String maskCard(String card) {
        String cleanCard = card.replaceAll("\\s+", "");
        if (cleanCard.length() != 16) return "INVALID CARD";
        return cleanCard.substring(0, 4) + " **** **** " + cleanCard.substring(12);
    }
}