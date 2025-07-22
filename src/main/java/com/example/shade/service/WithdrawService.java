package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import com.example.shade.model.*;
import com.example.shade.model.Currency;
import com.example.shade.repository.BlockedUserRepository;
import com.example.shade.repository.ExchangeRateRepository;
import com.example.shade.repository.HizmatRequestRepository;
import com.example.shade.repository.PlatformRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

@Service
@RequiredArgsConstructor
public class WithdrawService {
    private static final Logger logger = LoggerFactory.getLogger(WithdrawService.class);
    private final UserSessionService sessionService;
    private final HizmatRequestRepository requestRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final PlatformRepository platformRepository;
    private final MessageSender messageSender;
    private final AdminLogBotService adminLogBotService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final BlockedUserRepository blockedUserRepository;

    public void startWithdrawal(Long chatId) {
        logger.info("Starting withdrawal for chatId: {}", chatId);
        sessionService.setUserState(chatId, "WITHDRAW_PLATFORM_SELECTION");
        sessionService.addNavigationState(chatId, "MAIN_MENU");
        sendPlatformSelection(chatId);
    }

    public void handleTextInput(Long chatId, String text) {
        String state = sessionService.getUserState(chatId);
        logger.info("Text input for chatId {}, state: {}, text: {}", chatId, state, text);
        if (state == null) {
            sendMainMenu(chatId);
            return;
        }
        switch (state) {
            case "WITHDRAW_USER_ID_INPUT" -> handleUserIdInput(chatId, text);
            case "WITHDRAW_CARD_INPUT" -> handleCardInput(chatId, text);
            case "WITHDRAW_CODE_INPUT" -> handleCodeInput(chatId, text);
            default -> messageSender.sendMessage(chatId, "Iltimos, menyudan operatsiyani tanlang.");
        }
    }

    public void handleCallback(Long chatId, String callback) {
        logger.info("Callback received for chatId {}: {}", chatId, callback);
        messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
        sessionService.clearMessageIds(chatId);

        if (callback.startsWith("APPROVE_WITHDRAW:") || callback.startsWith("REJECT_WITHDRAW:")) {
            Long requestId = Long.parseLong(callback.split(":")[1]);
            boolean approve = callback.startsWith("APPROVE_WITHDRAW:");
            processAdminApproval(chatId, requestId, approve);
            return;
        }

        switch (callback) {
            case "WITHDRAW_USE_SAVED_ID" -> validateUserId(chatId, sessionService.getUserData(chatId, "platformUserId"));
            case "WITHDRAW_APPROVE_USER" -> handleApproveUser(chatId);
            case "WITHDRAW_REJECT_USER" -> {
                sessionService.setUserState(chatId, "WITHDRAW_USER_ID_INPUT");
                sendUserIdInput(chatId, sessionService.getUserData(chatId, "platform"));
            }
            case "WITHDRAW_USE_SAVED_CARD" -> {
                sessionService.setUserState(chatId, "WITHDRAW_CODE_INPUT");
                sessionService.addNavigationState(chatId, "WITHDRAW_CARD_INPUT");
                sendCodeInput(chatId);
            }
            default -> {
                if (callback.startsWith("WITHDRAW_PLATFORM:")) {
                    String platformName = callback.split(":")[1];
                    logger.info("Platform selected for chatId {}: {}", chatId, platformName);
                    sessionService.setUserData(chatId, "platform", platformName);
                    sessionService.setUserState(chatId, "WITHDRAW_USER_ID_INPUT");
                    sessionService.addNavigationState(chatId, "WITHDRAW_PLATFORM_SELECTION");
                    sendUserIdInput(chatId, platformName);
                } else if (callback.startsWith("WITHDRAW_PAST_ID:")) {
                    validateUserId(chatId, callback.split(":")[1]);
                } else if (callback.startsWith("WITHDRAW_PAST_CARD:")) {
                    sessionService.setUserData(chatId, "cardNumber", callback.split(":")[1]);
                    sessionService.setUserState(chatId, "WITHDRAW_CODE_INPUT");
                    sessionService.addNavigationState(chatId, "WITHDRAW_CARD_INPUT");
                    sendCodeInput(chatId);
                    handleCardInput(chatId,  callback.split(":")[1]);
                } else {
                    logger.warn("Unknown callback for chatId {}: {}", chatId, callback);
                    messageSender.sendMessage(chatId, "Noto‘g‘ri buyruq. Iltimos, qayta urinib ko‘ring.");
                }
            }
        }
    }

    public void handleBack(Long chatId) {
        String lastState = sessionService.popNavigationState(chatId);
        logger.info("Handling back for chatId {}, lastState: {}", chatId, lastState);
        if (lastState == null) {
            sendMainMenu(chatId);
            return;
        }
        switch (lastState) {
            case "MAIN_MENU" -> sendMainMenu(chatId);
            case "WITHDRAW_PLATFORM_SELECTION" -> {
                sessionService.setUserState(chatId, "WITHDRAW_PLATFORM_SELECTION");
                sendPlatformSelection(chatId);
            }
            case "WITHDRAW_USER_ID_INPUT", "WITHDRAW_APPROVE_USER" -> {
                sessionService.setUserState(chatId, "WITHDRAW_USER_ID_INPUT");
                sendUserIdInput(chatId, sessionService.getUserData(chatId, "platform"));
            }
            case "WITHDRAW_CARD_INPUT" -> {
                sessionService.setUserState(chatId, "WITHDRAW_CARD_INPUT");
                sendCardInput(chatId, sessionService.getUserData(chatId, "fullName"));
            }
            case "WITHDRAW_CODE_INPUT" -> {
                sessionService.setUserState(chatId, "WITHDRAW_CODE_INPUT");
                sendCodeInput(chatId);
            }
            default -> sendMainMenu(chatId);
        }
    }

    public void processAdminApproval(Long adminChatId, Long requestId, boolean approve) {
        HizmatRequest request = requestRepository.findById(requestId)
                .orElse(null);
        if (request == null) {
            logger.error("No request found for requestId {}", requestId);
            adminLogBotService.sendToAdmins("❌ Xatolik: So‘rov topilmadi for requestId " + requestId);
            return;
        }

        if (!request.getStatus().equals(RequestStatus.PENDING_ADMIN)) {
            logger.warn("Invalid request status for requestId {}: {}", requestId, request.getStatus());
            adminLogBotService.sendToAdmins("❌ Xatolik: So‘rov allaqachon ko‘rib chiqilgan yoki noto‘g‘ri holatda for requestId " + requestId);
            return;
        }

        String platform = request.getPlatform();
        String userId = request.getPlatformUserId();
        String cardNumber = request.getCardNumber();
        String code = request.getTransactionId();
        Long chatId = request.getChatId();
        String number = blockedUserRepository.findByChatId(chatId).get().getPhoneNumber();

        if (approve) {
            request.setStatus(RequestStatus.APPROVED);
            requestRepository.save(request);

            String logMessage = String.format(
                    "#PUL \n\n 📋 Tranzaksiya ID: %s Pul yechib olish tasdiqlandi ✅\n" +
                            "👤 User ID [%s] %s\n" +
                            "🌐 %s: " + "%s\n"+
                            "💳 Karta raqami: `%s`\n" +
                            "🔑 Kod: %s\n" +
                            " 📅 [%s]",
                    request.getId() ,number,
                    chatId, platform, userId,
                    cardNumber, code, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            adminLogBotService.sendLog(logMessage);
            adminLogBotService.sendToAdmins("✅ So‘rov tasdiqlandi: requestId " + requestId);
            String message = String.format(
                    "📋 Tranzaksiya ID: %s Pul yechib olish tasdiqlandi ✅\n" +
                            "👤 User ID [%s] \n" +
                            "🌐 %s: " + "%s\n"+
                            "💳 Karta raqami: `%s`\n" +
                            "🔑 Kod: %s\n" +
                            "📅 [%s]",
                    request.getId(),
                    chatId, platform, userId,
                    cardNumber, code, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            messageSender.sendMessage(chatId, "✅ So‘rovingiz tasdiqlandi! \n" + message);
            sendMainMenu(chatId);
        } else {
            request.setStatus(RequestStatus.CANCELED);
            requestRepository.save(request);

            String logMessage = String.format(
                    "#PUL \n\n 📋 So'rov ID: %s  Pul yechib olish rad etildi ❌\n" +
                            "👤 User ID [%s] %s\n" +  // Clickable number with + sign
                            "🌐 %s: " + "%s\n"+
                            "💳 Karta raqami: `%s`\n" +
                            "🔑 Kod: %s\n" +
                            "📅 [%s]",
                    request.getId(),chatId,number,
                     platform, userId,
                    cardNumber, code,  LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            adminLogBotService.sendLog(logMessage);
            adminLogBotService.sendToAdmins("❌ So‘rov rad etildi: requestId " + requestId);

            messageSender.sendMessage(chatId, "❌ Pul yechib olish so‘rovingiz rad etildi. Iltimos, qayta urinib ko‘ring.");
            sendMainMenu(chatId);
        }
        logger.info("Admin chatId {} {} withdraw requestId {}", adminChatId, approve ? "approved" : "rejected", requestId);
    }

    private BigDecimal processPayout(Long chatId, String platformName, String userId, String code, Long requestId, String cardNumber) {
        Platform platform = platformRepository.findByName(platformName.replace("_", ""))
                .orElseThrow(() -> new IllegalStateException("Platform not found: " + platformName));

        String hash = platform.getApiKey();
        String cashierPass = platform.getPassword();
        String cashdeskId = platform.getWorkplaceId();
        String lng = "uz";

        if (hash == null || cashierPass == null || cashdeskId == null || hash.isEmpty() || cashierPass.isEmpty() || cashdeskId.isEmpty()) {
            logger.error("Invalid platform credentials for platform {}: hash={}, cashierPass={}, cashdeskId={}",
                    platformName, hash, cashierPass, cashdeskId);
            messageSender.sendMessage(chatId, "Platform sozlamalarida xato. Administrator bilan bog‘laning.");
            return null;
        }

        try {
            Integer.parseInt(cashdeskId);
        } catch (NumberFormatException e) {
            logger.error("Invalid cashdeskId format for platform {}: {}", platformName, cashdeskId);
            messageSender.sendMessage(chatId, "Platform sozlamalarida xato. Administrator bilan bog‘laning.");
            return null;
        }

        // Confirm and signature
        String confirm = DigestUtils.md5DigestAsHex((userId + ":" + hash).getBytes(StandardCharsets.UTF_8));
        String sha256Input1 = "hash=" + hash + "&lng=" + lng + "&userid=" + userId;
        String sha256Result1 = sha256Hex(sha256Input1);
        String md5Input = "code=" + code + "&cashierpass=" + cashierPass + "&cashdeskid=" + cashdeskId;
        String md5Result = DigestUtils.md5DigestAsHex(md5Input.getBytes(StandardCharsets.UTF_8));
        String finalSignature = sha256Hex(sha256Result1 + md5Result);

        String apiUrl = String.format("https://partners.servcul.com/CashdeskBotAPI/Deposit/%s/Payout", userId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("sign", finalSignature);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("cashdeskId", Integer.parseInt(cashdeskId));
        body.put("lng", lng);
        body.put("code", code);
        body.put("confirm", confirm);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            Object successObj = responseBody != null ? responseBody.get("success") : null;
            if (successObj == null && responseBody != null) {
                successObj = responseBody.get("Success");
            }
            HizmatRequest request = requestRepository.findById(requestId)
                    .orElse(null);
            String errorMsg = responseBody != null && responseBody.get("Message") != null
                    ? responseBody.get("Message").toString()
                    : "Platformdan noto‘g‘ri javob qaytdi.";

            String cancelLogMessage = String.format(
                    "❌ Arizangiz bekor qilindi!\n\n" +
                            "#%d\n" +
                            "💳 Karta: `%s`\n" +
                            "💸 Valyuta: UZS 🇺🇿\n" +
                            "🆔 %s ID: %s\n" +
                            "#️⃣ 4 ta kod: %s\n\n" +
                            "❌ Xabar: %s \n\n" +
                            "📆 Vaqt: %s",
                    request.getId(),               // e.g., 74224
                    cardNumber,                  // e.g., 5614684905893317
                    platform.getName(),                      // e.g., "1XBET UZS"
                    userId,                        // e.g., 1322429831
                    code,
                    errorMsg,// e.g., "Euej"
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            if (response.getStatusCode().is2xxSuccessful() && Boolean.TRUE.equals(successObj)) {
                Object summaObj = responseBody.get("Summa");
                BigDecimal summa = null;
                if (summaObj != null) {
                    try {
                        summa = new BigDecimal(summaObj.toString());
                    } catch (NumberFormatException e) {
                        logger.warn("Failed to parse summa value: {}", summaObj);
                    }
                }

                logger.info("✅ Payout successful for userId {} on platform {}, summa={}, requestId: {}", userId, platformName, summa, requestId);
                return summa;
            } else {
                logger.warn("❌ Payout failed for userId {} on platform {}, response: {}", userId, platformName, responseBody);
                messageSender.sendMessage(chatId, cancelLogMessage );
                sendMainMenu(chatId);
                return null;
            }
        } catch (HttpClientErrorException e) {
            String errorMsg = e.getStatusCode().value() == 401 ? "Invalid signature" :
                    e.getStatusCode().value() == 403 ? "Invalid confirm" : "API xatosi: " + e.getMessage();
            logger.error("Payout API error for userId {} on platform {}: {}", userId, platformName, e.getMessage());
            messageSender.sendMessage(chatId, "❌ Payout xatosi: " + errorMsg);
            adminLogBotService.sendToAdmins("❌ Payout API error: " + errorMsg + " for requestId " + requestId);
            sendMainMenu(chatId);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error during payout for userId {} on platform {}: {}", userId, platformName, e.getMessage());
            messageSender.sendMessage(chatId, "❌ Noma’lum xatolik. Qayta urinib ko‘ring.");
            adminLogBotService.sendToAdmins("❌ Payout API error: Unexpected error for requestId " + requestId);
            sendMainMenu(chatId);
            return null;
        }
    }

    private void handleUserIdInput(Long chatId, String userId) {
        messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
        sessionService.clearMessageIds(chatId);

        if (!isValidUserId(userId)) {
            logger.warn("Invalid user ID format for chatId {}: {}", chatId, userId);
            sendMessageWithNavigation(chatId, "Noto‘g‘ri ID formati. Iltimos, faqat raqamlardan iborat ID kiriting:");
            return;
        }
        validateUserId(chatId, userId);
    }

    private void validateUserId(Long chatId, String userId) {
        String platformName = sessionService.getUserData(chatId, "platform").replace("_", "");
        Platform platform = platformRepository.findByName(platformName)
                .orElseThrow(() -> new IllegalStateException("Platform not found: " + platformName));

        String hash = platform.getApiKey();
        String cashierPass = platform.getPassword();
        String cashdeskId = platform.getWorkplaceId();

        if (hash == null || cashierPass == null || cashdeskId == null || hash.isEmpty() || cashierPass.isEmpty() || cashdeskId.isEmpty()) {
            logger.error("Invalid platform credentials for platform {}: hash={}, cashierPass={}, cashdeskId={}",
                    platformName, hash, cashierPass, cashdeskId);
            messageSender.sendMessage(chatId, "Platform sozlamalarida xato. Administrator bilan bog‘laning.");
            return;
        }

        // Validate cashdeskId
        try {
            Integer.parseInt(cashdeskId);
        } catch (NumberFormatException e) {
            logger.error("Invalid cashdeskId format for platform {}: {}", platformName, cashdeskId);
            messageSender.sendMessage(chatId, "Platform sozlamalarida xato. Administrator bilan bog‘laning.");
            return;
        }

        String confirmInput = userId + ":" + hash;
        String confirm = DigestUtils.md5DigestAsHex(confirmInput.getBytes(StandardCharsets.UTF_8));
        String sha256Input1 = "hash=" + hash + "&userid=" + userId + "&cashdeskid=" + cashdeskId;
        String sha256Result1 = sha256Hex(sha256Input1);
        String md5Input = "userid=" + userId + "&cashierpass=" + cashierPass + "&hash=" + hash;
        String md5Result = DigestUtils.md5DigestAsHex(md5Input.getBytes(StandardCharsets.UTF_8));
        String finalSignature = sha256Hex(sha256Result1 + md5Result);

        String apiUrl = String.format("https://partners.servcul.com/CashdeskBotAPI/Users/%s?confirm=%s&cashdeskId=%s",
                userId, confirm, cashdeskId);
        logger.info("Validating user ID {} for platform {} (chatId: {}), URL: {}", userId, platformName, chatId, apiUrl);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("sign", finalSignature);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<UserProfile> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, UserProfile.class);
            UserProfile profile = response.getBody();

            if (response.getStatusCode().is2xxSuccessful() && profile != null && profile.getUserId() != null && !profile.getName().isEmpty()) {
                String fullName = profile.getName();
                sessionService.setUserData(chatId, "platformUserId", userId);
                sessionService.setUserData(chatId, "fullName", fullName);
                Currency currency = Currency.UZS;
                if (profile.getCurrencyId() == 1L) {
                    currency = Currency.RUB;
                }
                HizmatRequest request = HizmatRequest.builder()
                        .chatId(chatId)
                        .platform(platformName)
                        .platformUserId(userId)
                        .fullName(fullName)
                        .status(RequestStatus.PENDING)
                        .createdAt(LocalDateTime.now())
                        .type(RequestType.WITHDRAWAL)
                        .currency(currency)
                        .build();
                requestRepository.save(request);

                sessionService.setUserState(chatId, "WITHDRAW_APPROVE_USER");
                sendUserApproval(chatId, fullName, userId);
            } else {
                logger.warn("Invalid user profile for ID {} on platform {}. Response: {}", userId, platformName, profile);
                sendNoUserFound(chatId);
            }
        } catch (HttpClientErrorException e) {
            String errorMsg = e.getStatusCode().value() == 400 ? "Invalid cashdeskId or parameters: " + e.getResponseBodyAsString() :
                    e.getStatusCode().value() == 401 ? "Invalid signature" :
                            e.getStatusCode().value() == 403 ? "Invalid confirm" : "API xatosi: " + e.getMessage();
            logger.error("Error calling API for user ID {} on platform {}: {}", userId, platformName, errorMsg);
            sendMessageWithNavigation(chatId, "❌ API xatosi: " + errorMsg + ". Iltimos, qayta urinib ko‘ring yoki administrator bilan bog‘laning.");
        } catch (Exception e) {
            logger.error("Unexpected error calling API for user ID {} on platform {}: {}", userId, platformName, e.getMessage());
            sendMessageWithNavigation(chatId, "❌ Noma’lum xatolik. Iltimos, qayta urinib ko‘ring.");
        }
    }

    private void handleApproveUser(Long chatId) {
        sessionService.setUserState(chatId, "WITHDRAW_CARD_INPUT");
        sessionService.addNavigationState(chatId, "WITHDRAW_APPROVE_USER");
        String fullName = sessionService.getUserData(chatId, "fullName");
        if (fullName == null) {
            logger.error("FullName is null for chatId {}", chatId);
            messageSender.sendMessage(chatId, "Xatolik: Foydalanuvchi ma'lumotlari topilmadi. Qayta urinib ko‘ring.");
            sessionService.setUserState(chatId, "WITHDRAW_USER_ID_INPUT");
            sendUserIdInput(chatId, sessionService.getUserData(chatId, "platform"));
        } else {
            sendCardInput(chatId, fullName);
        }
    }

    private void handleCardInput(Long chatId, String card) {
        messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
        sessionService.clearMessageIds(chatId);

        if (!isValidCard(card)) {
            logger.warn("Invalid card format for chatId {}: {}", chatId, card);
            sendMessageWithNavigation(chatId, "Noto‘g‘ri karta formati. 16 raqamli UZCARD raqamini kiriting:");
            return;
        }
        String cardNumber = card.replaceAll("\\s+", "");
        sessionService.setUserData(chatId, "cardNumber", cardNumber);

        String platform = sessionService.getUserData(chatId, "platform");
        String userId = sessionService.getUserData(chatId, "platformUserId");
        HizmatRequest request = requestRepository.findTopByChatIdAndPlatformAndPlatformUserIdOrderByCreatedAtDesc(
                chatId, platform, userId).orElse(null);
        if (request != null) {
            request.setCardNumber(cardNumber);
            requestRepository.save(request);
        }

        sessionService.setUserState(chatId, "WITHDRAW_CODE_INPUT");
        sessionService.addNavigationState(chatId, "WITHDRAW_CARD_INPUT");
        sendCodeInput(chatId);
    }

    private void handleCodeInput(Long chatId, String code) {
        messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
        sessionService.clearMessageIds(chatId);

        if (!isValidCode(code)) {
            logger.warn("Invalid code format for chatId {}: {}", chatId, code);
            sendMessageWithNavigation(chatId, "Noto‘g‘ri kod formati. Iltimos, to‘g‘ri kodni kiriting:");
            return;
        }

        String platform = sessionService.getUserData(chatId, "platform");
        String userId = sessionService.getUserData(chatId, "platformUserId");
        String cardNumber = sessionService.getUserData(chatId, "cardNumber");
        HizmatRequest request = requestRepository.findTopByChatIdAndPlatformAndPlatformUserIdOrderByCreatedAtDesc(
                chatId, platform, userId).orElse(null);
        if (request == null) {
            logger.error("No pending request found for chatId {}, platform: {}, userId: {}", chatId, platform, userId);
            messageSender.sendMessage(chatId, "Xatolik: So‘rov topilmadi. Qayta urinib ko‘ring.");
            sendMainMenu(chatId);
            return;
        }

        request.setTransactionId(code);
        request.setStatus(RequestStatus.PENDING_ADMIN);
        requestRepository.save(request);

        // Process payout immediately
        BigDecimal paidAmount = processPayout(chatId, platform, userId, code, request.getId(),cardNumber).multiply(BigDecimal.valueOf(-1)).setScale(2, RoundingMode.DOWN);;

        String number = blockedUserRepository.findByChatId(chatId).get().getPhoneNumber();

        if (paidAmount != null) {
            BigDecimal netAmount=paidAmount;
            if (!request.getCurrency().equals(Currency.RUB)) {
                netAmount=paidAmount.multiply(BigDecimal.valueOf(0.98)).setScale(2, RoundingMode.DOWN);
            }else {
                ExchangeRate latest = exchangeRateRepository.findLatest()
                        .orElseThrow(() -> new RuntimeException("No exchange rate found in the database"));
                netAmount = netAmount.multiply(latest.getRubToUzs()).setScale(2, RoundingMode.DOWN);
            }

            String logMessage = String.format(
                    "#PUL \n\n 📋 So‘rov ID: %d  Pul yechib olish so‘rovi qabul qilindi 💸\n" +
                            "👤 User ID [%s] %s\n" +
                            "🌐 %s: %s\n" +
                            "💳 Karta raqami: `%s`\n" +
                            "🔑 Kod: %s\n" +
                            "💵 Yechilgan: %s\n" +
                            "💵 Foydalanuvchiga tushgan: %s\n" +
                            "📅 [%s]",
                    request.getId(),
                    chatId, number, platform, userId, cardNumber, code,paidAmount.toPlainString(),
                    netAmount.toPlainString(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            messageSender.sendMessage(chatId,
                    "✅ Pul yechib olish so‘rovingiz muvaffaqiyatli qabul qilindi !\n" +
                            "💸 Yechilgan: " + paidAmount.toPlainString() + "\n" +
                            "💵 Sizga tushadi: " + netAmount.toPlainString() + "\n" +
                            "📋 So‘rov ID: " + request.getId() + "\n" +
                            "🕓 Admin tasdiqini kuting.");

            adminLogBotService.sendWithdrawRequestToAdmins(chatId, logMessage, request.getId());
        }



    }

    private void sendPlatformSelection(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Platformani tanlang:");
        InlineKeyboardMarkup keyboard = createPlatformKeyboard();
        message.setReplyMarkup(keyboard);
        messageSender.sendMessage(message, chatId);
        logger.info("Sent platform selection to chatId {} with {} buttons", chatId, keyboard.getKeyboard().size());
    }

    private void sendUserIdInput(Long chatId, String platform) {
        List<HizmatRequest> recentRequests = requestRepository.findTop3ByChatIdAndPlatformOrderByCreatedAtDesc(chatId, platform);
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        if (!recentRequests.isEmpty()) {
            HizmatRequest latestRequest = recentRequests.get(0);
            sessionService.setUserData(chatId, "platformUserId", latestRequest.getPlatformUserId());
            message.setText("Pastagi ID larni birini ishlatishingiz mumkin yoki yangi ID kiriting:");
            message.setReplyMarkup(createSavedIdKeyboard(recentRequests));
        } else {
            message.setText("Iltimos, " + platform + " uchun ID kiriting:");
            message.setReplyMarkup(createNavigationKeyboard());
        }
        messageSender.sendMessage(message, chatId);
    }

    private void sendUserApproval(Long chatId, String fullName, String userId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(String.format("Foydalanuvchi ma'lumotlari:\n\n👤 F.I.O: %s\n🆔 ID: %s\n\nBu ma'lumotlar to‘g‘rimi?", fullName, userId));
        message.setReplyMarkup(createApprovalKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private void sendNoUserFound(Long chatId) {
        sendMessageWithNavigation(chatId, "Bu ID bo‘yicha foydalanuvchi topilmadi. Iltimos, boshqa ID kiriting:");
    }

    private void sendCardInput(Long chatId, String fullName) {
        List<HizmatRequest> recentRequests = requestRepository.findLatestUniqueCardNumbersByChatId(chatId);
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        if (!recentRequests.isEmpty() && recentRequests.get(0).getCardNumber() != null) {
            HizmatRequest latestRequest = recentRequests.get(0);
            sessionService.setUserData(chatId, "cardNumber", latestRequest.getCardNumber());
            message.setText("Pastagi kartalarni birini ishlatishingiz mumkin yoki yangi karta raqamini kiriting:");
            message.setReplyMarkup(createSavedCardKeyboard(recentRequests));
        } else {
            message.setText("F.I.O: " + fullName + "\nKarta raqamini kiriting (8600xxxxxxxxxxxx):");
            message.setReplyMarkup(createNavigationKeyboard());
        }
        messageSender.sendMessage(message, chatId);
    }

    private void sendCodeInput(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Platformadan olingan pul yechib olish kodini kiriting:");
        message.setReplyMarkup(createNavigationKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private void sendMainMenu(Long chatId) {
        sessionService.clearSession(chatId);
        sessionService.setUserState(chatId, "MAIN_MENU");
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Xush kelibsiz! Operatsiyani tanlang:");
        message.setReplyMarkup(createMainMenuKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private void sendMessageWithNavigation(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(createNavigationKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private InlineKeyboardMarkup createPlatformKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<Platform> uzsPlatforms = platformRepository.findByCurrency(Currency.UZS);
        List<Platform> rubPlatforms = platformRepository.findByCurrency(Currency.RUB);

        if ((uzsPlatforms == null || uzsPlatforms.isEmpty()) && (rubPlatforms == null || rubPlatforms.isEmpty())) {
            logger.error("No platforms found in database for keyboard creation");
            messageSender.sendMessage(null, "❌ Xatolik: Platformalar topilmadi. Administrator bilan bog‘laning.");
            rows.add(createNavigationButtons());
        } else {
            int maxRows = Math.max(uzsPlatforms.size(), rubPlatforms.size());
            for (int i = 0; i < maxRows; i++) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                if (i < uzsPlatforms.size()) {
                    Platform uzsPlatform = uzsPlatforms.get(i);
                    if (uzsPlatform != null && uzsPlatform.getName() != null && !uzsPlatform.getName().isEmpty()) {
                        row.add(createButton("🇺🇿 " + uzsPlatform.getName(), "WITHDRAW_PLATFORM:" + uzsPlatform.getName()));
                    } else {
                        logger.warn("Skipping invalid UZS platform: {}", uzsPlatform);
                    }
                }
                if (i < rubPlatforms.size()) {
                    Platform rubPlatform = rubPlatforms.get(i);
                    if (rubPlatform != null && rubPlatform.getName() != null && !rubPlatform.getName().isEmpty()) {
                        row.add(createButton("🇷🇺 " + rubPlatform.getName(), "WITHDRAW_PLATFORM:" + rubPlatform.getName()));
                    } else {
                        logger.warn("Skipping invalid RUB platform: {}", rubPlatform);
                    }
                } else {
                    i++;
                    if (i < uzsPlatforms.size() && i<maxRows) {
                        Platform uzsPlatform = uzsPlatforms.get(i);
                        row.add(createButton("🇺🇿 " + uzsPlatform.getName(), "WITHDRAW_PLATFORM:" + uzsPlatform.getName()));
                    }
                }
                if (!row.isEmpty()) {
                    rows.add(row);
                }
            }
            if (rows.isEmpty()) {
                logger.error("No valid platforms with non-empty names found");
                messageSender.sendMessage(null, "❌ Xatolik: Yaroqli platformalar topilmadi. Administrator bilan bog‘laning.");
                rows.add(createNavigationButtons());
            } else {
                rows.add(createNavigationButtons());
            }
        }
        markup.setKeyboard(rows);
        logger.info("Created platform keyboard with {} platform buttons", rows.size() - 1);
        return markup;
    }

    private InlineKeyboardMarkup createSavedIdKeyboard(List<HizmatRequest> recentRequests) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (!recentRequests.isEmpty()) {
            List<InlineKeyboardButton> pastIdButtons = recentRequests.stream()
                    .map(HizmatRequest::getPlatformUserId)
                    .distinct()
                    .limit(2)
                    .map(id -> createButton("ID: " + id, "WITHDRAW_PAST_ID:" + id))
                    .collect(Collectors.toList());
            if (!pastIdButtons.isEmpty()) {
                rows.add(pastIdButtons);
            }
        }
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createSavedCardKeyboard(List<HizmatRequest> recentRequests) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (!recentRequests.isEmpty()) {
            List<InlineKeyboardButton> pastCardButtons = recentRequests.stream()
                    .map(HizmatRequest::getCardNumber)
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(2)
                    .map(card -> createButton("Karta: " + maskCard(card), "WITHDRAW_PAST_CARD:" + card))
                    .collect(Collectors.toList());
            if (!pastCardButtons.isEmpty()) {
                rows.add(pastCardButtons);
            }
        }
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createApprovalKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("✅ To‘g‘ri", "WITHDRAW_APPROVE_USER"),
                createButton("❌ Noto‘g‘ri", "WITHDRAW_REJECT_USER")
        ));
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createMainMenuKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton("🏦 Hisob To'ldirish", "TOPUP")));
        rows.add(List.of(createButton("💸 Pul Chiqarish", "WITHDRAW")));
        rows.add(List.of(createButton("🎁 Bonus", "BONUS")));
        rows.add(List.of(createButton("ℹ️ Aloqa", "CONTACT")));
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

    private boolean isValidCard(String card) {
        return card.replaceAll("\\s+", "").matches("\\d{16}");
    }

    private boolean isValidCode(String code) {
        return code.matches("[A-Za-z0-9]+");
    }

    private String maskCard(String card) {
        String cleanCard = card.replaceAll("\\s+", "");
        if (cleanCard.length() != 16) return "INVALID CARD";
        return cleanCard.substring(0, 4) + " **** **** " + cleanCard.substring(12);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

}