package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import com.example.shade.dto.BalanceLimit;
import com.example.shade.model.*;
import com.example.shade.model.Currency;
import com.example.shade.repository.*;
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

import javax.xml.bind.DatatypeConverter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BonusService {
    private static final Logger logger = LoggerFactory.getLogger(BonusService.class);
    private final UserSessionService sessionService;
    private final ReferralRepository referralRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final PlatformRepository platformRepository;
    private final HizmatRequestRepository requestRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final AdminChatRepository adminChatRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final LottoBotService lottoBotService;
    private final LotteryService lotteryService;
    private final MessageSender messageSender;
    private final AdminLogBotService adminLogBotService;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final BigDecimal MINIMUM_TOPUP = new BigDecimal("10000");
    private static final BigDecimal MAXIMUM_TOPUP = new BigDecimal("10000000");
    private static final long MINIMUM_TICKETS = 36L;
    private static final long MAXIMUM_TICKETS = 400L;

    public void startBonus(Long chatId) {
        logger.info("Starting bonus section for chatId: {}", chatId);
        sessionService.setUserState(chatId, "BONUS_MENU");
        sessionService.addNavigationState(chatId, "MAIN_MENU");
        sendBonusMenu(chatId);
    }

    public void handleCallback(Long chatId, String callback) {
        logger.info("Bonus callback for chatId {}: {}", chatId, callback);
        messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
        sessionService.clearMessageIds(chatId);

        if (callback.startsWith("BONUS_TOPUP_PLATFORM:")) {
            String platformName = callback.split(":")[1];
            sessionService.setUserData(chatId, "platform", platformName);
            sessionService.setUserState(chatId, "BONUS_TOPUP_USER_ID");
            sessionService.addNavigationState(chatId, "BONUS_TOPUP");
            sendUserIdInput(chatId, platformName);
            return;
        }
        if (callback.startsWith("BONUS_TOPUP_PAST_ID:")) {
            String userId = callback.split(":")[1];
            validateUserId(chatId, userId);
            return;
        }
        if ("BONUS_TOPUP_APPROVE_USER".equals(callback)) {
            handleApproveUser(chatId);
            return;
        }
        if ("BONUS_TOPUP_REJECT_USER".equals(callback)) {
            sessionService.setUserState(chatId, "BONUS_TOPUP_USER_ID");
            sendUserIdInput(chatId, sessionService.getUserData(chatId, "platform"));
            return;
        }
        if ("BONUS_TOPUP_CONFIRM_YES".equals(callback)) {
            initiateTopUpRequest(chatId);
            return;
        }
        if ("BONUS_TOPUP_CONFIRM_NO".equals(callback)) {
            sessionService.setUserState(chatId, "BONUS_TOPUP");
            sendTopUpPlatformMenu(chatId);
            return;
        }
        if ("BONUS_TOPUP_AMOUNT_10000".equals(callback)) {
            handleTopUpInput(chatId, "10000");
            return;
        }
        if ("BONUS_TOPUP_AMOUNT_100000".equals(callback)) {
            handleTopUpInput(chatId, "100000");
            return;
        }
        if (callback.startsWith("ADMIN_APPROVE_TRANSFER:")) {
            Long requestId = Long.valueOf(callback.split(":")[1]);
            handleAdminApproveTransfer(chatId, requestId);
            return;
        }
        if (callback.startsWith("ADMIN_DECLINE_TRANSFER:")) {
            Long requestId = Long.valueOf(callback.split(":")[1]);
            handleAdminDeclineTransfer(chatId, requestId);
            return;
        }
        if (callback.startsWith("ADMIN_REMOVE_TICKETS:")) {
            String userChatId = callback.split(":")[1];
            handleAdminRemoveTickets(chatId, Long.parseLong(userChatId));
            return;
        }
        if (callback.startsWith("ADMIN_REMOVE_BONUS:")) {
            String userChatId = callback.split(":")[1];
            handleAdminRemoveBonus(chatId, Long.parseLong(userChatId));
            return;
        }
        if (callback.startsWith("ADMIN_BLOCK_USER:")) {
            String userChatId = callback.split(":")[1];
            handleAdminBlockUser(chatId, Long.parseLong(userChatId));
            return;
        }

        switch (callback) {
            case "BONUS_LOTTERY" -> {
                sessionService.setUserState(chatId, "BONUS_LOTTERY");
                sessionService.addNavigationState(chatId, "BONUS_MENU");
                sendLotteryMenu(chatId);
            }
            case "BONUS_REFERRAL" -> {
                sessionService.setUserState(chatId, "BONUS_REFERRAL");
                sessionService.addNavigationState(chatId, "BONUS_MENU");
                sendReferralMenu(chatId);
            }
            case "BONUS_LOTTERY_PLAY" -> playLottery(chatId);
            case "BONUS_REFERRAL_LINK" -> sendReferralLink(chatId);
            case "BONUS_TOPUP" -> {
                String savedPlatform = sessionService.getUserData(chatId, "platform");
                if (savedPlatform != null) {
                    sessionService.setUserState(chatId, "BONUS_TOPUP_USER_ID");
                    sessionService.addNavigationState(chatId, "BONUS_MENU");
                    sendUserIdInput(chatId, savedPlatform);
                } else {
                    sessionService.setUserState(chatId, "BONUS_TOPUP");
                    sessionService.addNavigationState(chatId, "BONUS_MENU");
                    sendTopUpPlatformMenu(chatId);
                }
            }
            default -> messageSender.sendMessage(chatId, "Noto‘g‘ri buyruq. Iltimos, qayta urinib ko‘ring.");
        }
    }

    public void handleTextInput(Long chatId, String text) {
        String state = sessionService.getUserState(chatId);
        logger.info("Text input for bonus, chatId: {}, state: {}, text: {}", chatId, state, text);
        if ("BONUS_TOPUP_USER_ID".equals(state)) {
            handleUserIdInput(chatId, text);
        } else if ("BONUS_TOPUP_INPUT".equals(state)) {
            handleTopUpInput(chatId, text);
        } else {
            backMenuMessage(chatId, "Iltimos, menyudan operatsiyani tanlang.");
        }
    }
    public void backMenuMessage(Long chatId,String messageText) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText);
        message.setReplyMarkup(createNavigationKeyboard());
        messageSender.sendMessage(message, chatId);
    }
    public void handleBack(Long chatId) {
        String lastState = sessionService.popNavigationState(chatId);
        logger.info("Handling back for bonus, chatId: {}, lastState: {}", chatId, lastState);
        if (lastState == null) {
            sendMainMenu(chatId);
            return;
        }
        switch (lastState) {
            case "MAIN_MENU" -> sendMainMenu(chatId);
            case "BONUS_MENU" -> {
                sessionService.setUserState(chatId, "BONUS_MENU");
                sendBonusMenu(chatId);
            }
            case "BONUS_LOTTERY" -> {
                sessionService.setUserState(chatId, "BONUS_LOTTERY");
                sendLotteryMenu(chatId);
            }
            case "BONUS_REFERRAL" -> {
                sessionService.setUserState(chatId, "BONUS_REFERRAL");
                sendReferralMenu(chatId);
            }
            case "BONUS_TOPUP" -> {
                sessionService.setUserState(chatId, "BONUS_TOPUP");
                sendTopUpPlatformMenu(chatId);
            }
            case "BONUS_TOPUP_USER_ID", "BONUS_TOPUP_APPROVE_USER" -> {
                sessionService.setUserState(chatId, "BONUS_TOPUP_USER_ID");
                sendUserIdInput(chatId, sessionService.getUserData(chatId, "platform"));
            }
            case "BONUS_TOPUP_INPUT" -> {
                sessionService.setUserState(chatId, "BONUS_TOPUP_INPUT");
                String platform = sessionService.getUserData(chatId, "platform");
                sendTopUpInput(chatId, platform);
            }
            case "BONUS_TOPUP_CONFIRM" -> {
                sessionService.setUserState(chatId, "BONUS_TOPUP_CONFIRM");
                String platform = sessionService.getUserData(chatId, "platform");
                BigDecimal amount = new BigDecimal(sessionService.getUserData(chatId, "amount"));
                sendTopUpConfirmation(chatId, platform, amount);
            }
            default -> sendMainMenu(chatId);
        }
    }

    private void sendBonusMenu(Long chatId) {
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElse(UserBalance.builder().chatId(chatId).tickets(0L).balance(BigDecimal.ZERO).build());
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format("🎁 Bonus bo‘limi:\nChiptalar: %d ta\nBalans: %,d so‘m\nIltimos, birini tanlang:",
                balance.getTickets(), balance.getBalance().longValue()));
        message.setReplyMarkup(createBonusMenuKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private void sendLotteryMenu(Long chatId) {
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElse(UserBalance.builder().chatId(chatId).tickets(0L).balance(BigDecimal.ZERO).build());
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format("🎟 Lotereya bo‘limi:\nSizning chiptalaringiz: %d ta\n" +
                        "O‘ynash uchun kamida %d ta chipta kerak, maksimum %d ta.\nO‘ynashni xohlaysizmi?",
                balance.getTickets(), MINIMUM_TICKETS, MAXIMUM_TICKETS));
        message.setReplyMarkup(createLotteryKeyboard(balance.getTickets()));
        messageSender.sendMessage(message, chatId);
    }

    private void sendReferralMenu(Long chatId) {
        BigDecimal balance = getReferralBalance(chatId);
        Long referralCount = referralRepository.countByReferrerChatId(chatId);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format("🤝 Referal bo‘limi:\nReferal foydalanuvchilar: %d ta\nBalans: %,d so‘m\nTanlang:",
                referralCount, balance.longValue()));
        message.setReplyMarkup(createReferralKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private void sendReferralLink(Long chatId) {
        String referralLink = String.format("https://t.me/xonpeybot?start=ref_%d", chatId);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.enableMarkdown(true); // Enable Markdown formatting

        message.setText(String.format(
                "*Sizning referal havolangiz:*\n\n" +
                        "`%s`\n\n" +
                        "📋 Havolani bosib ushlab turing va nusxalang!",
               referralLink
        ));

        message.setReplyMarkup(createNavigationKeyboard()); // Your existing nav buttons
        messageSender.sendMessage(message, chatId);
    }



    private void sendTopUpPlatformMenu(Long chatId) {
        BigDecimal balance = getReferralBalance(chatId);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format("💰 Pul to‘ldirish:\nBalans: %,d so‘m\nMinimal to‘ldirish summasi: 10,000 so‘m\nKontorani tanlang:",
                balance.longValue()));
        message.setReplyMarkup(createTopUpPlatformKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private void sendUserIdInput(Long chatId, String platform) {
        List<HizmatRequest> recentRequests = requestRepository.findTop3ByChatIdAndPlatformOrderByCreatedAtDesc(chatId, platform);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        if (!recentRequests.isEmpty()) {
            HizmatRequest latestRequest = recentRequests.get(0);
            sessionService.setUserData(chatId, "platformUserId", latestRequest.getPlatformUserId());
            message.setText("Pastagi ID larni birini ishlatishingiz mumkin yoki yangi ID kiriting:");
            message.setReplyMarkup(createSavedIdKeyboard(recentRequests));
        } else {
            message.setText(String.format("Iltimos, %s uchun ID kiriting (faqat raqamlar):", platform));
            message.setReplyMarkup(createNavigationKeyboard());
        }
        messageSender.sendMessage(message, chatId);
    }

    private void sendUserApproval(Long chatId, String fullName, String userId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format("Foydalanuvchi ma'lumotlari:\n\n👤 F.I.O: %s\n🆔 ID: %s\n\nBu ma'lumotlar to‘g‘rimi?", fullName, userId));
        message.setReplyMarkup(createApprovalKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private void sendNoUserFound(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Bu ID bo‘yicha foydalanuvchi topilmadi. Iltimos, boshqa ID kiriting:");
        message.setReplyMarkup(createNavigationKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private void sendTopUpInput(Long chatId, String platform) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format("%s uchun to‘ldirmoqchi bo‘lgan summani kiriting (10,000 - 10,000,000 so‘m):", platform));
        message.setReplyMarkup(createAmountKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private void sendTopUpConfirmation(Long chatId, String platform, BigDecimal amount) {
        String userId = sessionService.getUserData(chatId, "platformUserId");
        String fullName = sessionService.getUserData(chatId, "fullName");
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format("Ma'lumotlarni tekshiring:\n\n 👤 Id Raqam: `%s` \n F.I.O: %s\nKontora: %s\n🆔 ID: %s\n💰 Summa: %,d so‘m\n\nTo‘ldirishni tasdiqlaysizmi?",
                userId,
                fullName, platform, userId, amount.longValue()));
        message.setReplyMarkup(createConfirmKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private void handleUserIdInput(Long chatId, String userId) {
        messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
        sessionService.clearMessageIds(chatId);

        if (!isValidUserId(userId)) {
            logger.warn("Invalid user ID format for chatId {}: {}", chatId, userId);
            messageSender.sendMessage(chatId, "Noto‘g‘ri ID formati. Iltimos, faqat raqamlardan iborat ID kiriting:");
            String platform = sessionService.getUserData(chatId, "platform");
            sendUserIdInput(chatId, platform);
            return;
        }
        validateUserId(chatId, userId);
    }

    private void validateUserId(Long chatId, String userId) {
        String platformName = sessionService.getUserData(chatId, "platform");
        Platform platform = platformRepository.findByName(platformName)
                .orElseThrow(() -> new IllegalStateException("Platform not found: " + platformName));

        String hash = platform.getApiKey();
        String cashierPass = platform.getPassword();
        String cashdeskId = platform.getWorkplaceId();

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
                        .createdAt(LocalDateTime.now(ZoneId.of("GMT+5")))
                        .amount(0L)
                        .currency(currency)
                        .type(RequestType.TOP_UP)
                        .build();
                requestRepository.save(request);

                sessionService.setUserState(chatId, "BONUS_TOPUP_INPUT");
                sessionService.addNavigationState(chatId, "BONUS_TOPUP_APPROVE_USER");
                sendTopUpInput(chatId, platformName);
            } else {
                logger.warn("Invalid user profile for ID {} on platform {}. Response: {}", userId, platformName, profile);
                sendNoUserFound(chatId);
            }
        } catch (HttpClientErrorException.NotFound e) {
            logger.warn("User not found for ID {} on platform {}: {}", userId, platformName, e.getMessage());
            sendNoUserFound(chatId);
        } catch (HttpClientErrorException e) {
            logger.error("API error for user ID {} on platform {}: {}", userId, platformName, e.getMessage());
            String errorMessage = e.getStatusCode().value() == 401 ? "Invalid signature" :
                    e.getStatusCode().value() == 403 ? "Invalid confirm parameter. Please check user ID and platform credentials." :
                            "API error occurred";
            messageSender.sendMessage(chatId, errorMessage + ". Iltimos, qayta urinib ko‘ring.");
            sendUserIdInput(chatId, platformName);
        } catch (Exception e) {
            logger.error("Error calling API for user ID {} on platform {}: {}", userId, platformName, e.getMessage());
            messageSender.sendMessage(chatId, "API xatosi yuz berdi. Iltimos, qayta urinib ko‘ring.");
            sendUserIdInput(chatId, platformName);
        }
    }

    private void handleApproveUser(Long chatId) {
        sessionService.setUserState(chatId, "BONUS_TOPUP_INPUT");
        sessionService.addNavigationState(chatId, "BONUS_TOPUP_APPROVE_USER");
        String platform = sessionService.getUserData(chatId, "platform");
        sendTopUpInput(chatId, platform);
    }

    private void handleTopUpInput(Long chatId, String input) {
        messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
        sessionService.clearMessageIds(chatId);

        String amountStr = input.trim();
        String platform = sessionService.getUserData(chatId, "platform");

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);

            if (amount.compareTo(MINIMUM_TOPUP) < 0 || amount.compareTo(MAXIMUM_TOPUP) > 0) {
                messageSender.sendMessage(chatId, "❗️Summa 10,000 dan 10,000,000 so‘m gacha bo‘lishi kerak!");
                sendTopUpInput(chatId, platform);
                return;
            }

            UserBalance balance = userBalanceRepository.findById(chatId)
                    .orElse(UserBalance.builder().chatId(chatId).tickets(0L).balance(BigDecimal.ZERO).build());

            if (balance.getBalance().compareTo(MINIMUM_TOPUP) < 0) {
                messageSender.sendMessage(chatId, String.format("❗️Hisobingizda kamida 10,000 so‘m bo‘lishi kerak. Joriy balans: %,d so‘m", balance.getBalance().longValue()));
                sendTopUpInput(chatId, platform);
                return;
            }

            if (balance.getBalance().compareTo(amount) < 0) {
                messageSender.sendMessage(chatId, String.format("❗️Balansingizda yetarli mablag‘ yo‘q. Joriy balans: %,d so‘m", balance.getBalance().longValue()));
                sendTopUpInput(chatId, platform);
                return;
            }

        } catch (NumberFormatException e) {
            logger.warn("Invalid amount format for chatId {}: {}", chatId, amountStr);
            messageSender.sendMessage(chatId, "❗️Noto‘g‘ri summa formati. Iltimos, to‘g‘ri raqam kiriting:");
            sendTopUpInput(chatId, platform);
            return;
        }

        sessionService.setUserData(chatId, "amount", amount.toString());
        sessionService.setUserState(chatId, "BONUS_TOPUP_CONFIRM");
        sessionService.addNavigationState(chatId, "BONUS_TOPUP_INPUT");
        sendTopUpConfirmation(chatId, platform, amount);
    }

    private void initiateTopUpRequest(Long chatId) {
        String platform = sessionService.getUserData(chatId, "platform");
        String userId = sessionService.getUserData(chatId, "platformUserId");
        String amountStr = sessionService.getUserData(chatId, "amount");
        String fullName = sessionService.getUserData(chatId, "fullName");

        BigDecimal amount = new BigDecimal(amountStr);
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElse(UserBalance.builder().chatId(chatId).tickets(0L).balance(BigDecimal.ZERO).build());

        if (balance.getBalance().compareTo(amount) < 0) {
            logger.warn("Insufficient balance for chatId {}: requested {}, available {}", chatId, amount, balance.getBalance());
            messageSender.sendMessage(chatId, String.format("Balansingizda yetarli mablag‘ yo‘q! Joriy balans: %,d so‘m", balance.getBalance()));
            sendTopUpInput(chatId, platform);
            return;
        }
        HizmatRequest request = requestRepository.findTopByChatIdAndPlatformAndPlatformUserIdAndStatusOrderByCreatedAtDesc(
                chatId, platform, userId, RequestStatus.PENDING).orElse(null);
        if (request == null) {
            logger.error("No pending request found for chatId {}, platform: {}, userId: {}", chatId, platform, userId);
            messageSender.sendMessage(chatId, "Xatolik: So‘rov topilmadi. Qayta urinib ko‘ring.");
            sendMainMenu(chatId);
            return;
        }
        balance.setBalance(balance.getBalance().subtract(new BigDecimal(amount.longValue())));
        userBalanceRepository.save(balance);
        request.setAmount(amount.longValue());
        request.setUniqueAmount(amount.longValue());
        request.setStatus(RequestStatus.PENDING_ADMIN);
        requestRepository.save(request);
        String userMessage = String.format("⏳Bonus pul yechish so'rovi junatildi: \n\n So'rov ID: %d \n \uD83C\uDF10 %s : %s\n💰 Summa: %,d so‘m  \n",
                request.getId(), request.getPlatform(), request.getPlatformUserId(), request.getAmount());
        messageSender.sendMessage(chatId, userMessage);

        sendAdminApprovalRequest(chatId, request);
        sessionService.setUserState(chatId, "BONUS_MENU");
        sendBonusMenu(chatId);
    }

    private void sendAdminApprovalRequest(Long chatId, HizmatRequest request) {
        String number = blockedUserRepository.findByChatId(request.getChatId()).get().getPhoneNumber();

        String message = String.format(
                "*#Bonus pul yechish so'rovi:*\n\n" +
                        "\uD83C\uDD94: `%d`\n" +
                        "🌐 *%s:* `%s`\n" +
                        "💰 *Summa:* `%,d so‘m`\n" +
                        "👤 *Foydalanuvchi:* `%d`\n" +
                        "📞 *Telefon:* `%s`\n\n" +
                        "*Tasdiqlaysizmi?*",
                request.getId(),
                request.getPlatform(),
                escapeMarkdown(request.getPlatformUserId()),
                request.getAmount(),
                chatId,
                escapeMarkdown(number)
        );

        adminLogBotService.sendWithdrawRequestToAdmins(chatId, message, request.getId(), createAdminApprovalKeyboard(request.getId(), request.getChatId()));
    }
    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("`", "\\`")
                .replace("[", "\\[");
    }

    public BalanceLimit getCashdeskBalance(String hash, String cashierPass, String cashdeskId) {
        RestTemplate restTemplate = new RestTemplate();
        String baseUrl = "https://partners.servcul.com/CashdeskBotAPI";
        String dt = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"));

        // Generate signature
        String sha256Input = String.format("hash=%s&cashierpass=%s&dt=%s", hash, cashierPass, dt);
        String sha256Result = sha256Hex(sha256Input);
        String md5Input = String.format("dt=%s&cashierpass=%s&cashdeskid=%s", dt, cashierPass, cashdeskId);
        String md5Result = DigestUtils.md5DigestAsHex(md5Input.getBytes(StandardCharsets.UTF_8));
        String finalSignature = sha256Hex(sha256Result + md5Result);

        // Generate confirm
        String confirm = DigestUtils.md5DigestAsHex((cashdeskId + ":" + hash).getBytes(StandardCharsets.UTF_8));

        // Build URL
        String url = String.format("%s/Cashdesk/%s/Balance?confirm=%s&dt=%s", baseUrl, cashdeskId, confirm, dt);

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("sign", finalSignature);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Make GET request and extract balance
        Map<String, Object> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();
        Object balanceObj = response != null ? response.get("Balance") : null;
        Object limitObj = response != null ? response.get("Limit") : null;
        return balanceObj != null ? new BalanceLimit(new BigDecimal(balanceObj.toString()),new BigDecimal(limitObj.toString())) : null;
    }

    public void handleAdminApproveTransfer(Long chatId, Long requestId) {
        HizmatRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Request not found: " + requestId));

        creditReferral(request.getChatId(), request.getAmount());

        String platformName = request.getPlatform();
        Platform platformData = platformRepository.findByName(platformName)
                .orElseThrow(() -> new IllegalStateException("Platform not found: " + platformName));

        String hash = platformData.getApiKey();
        String cashierPass = platformData.getPassword();
        String cashdeskId = platformData.getWorkplaceId();
        String lng = "uz";
        String userId = request.getPlatformUserId();
        String cardNumber = request.getCardNumber();
        ExchangeRate latest = exchangeRateRepository.findLatest()
                .orElseThrow(() -> new RuntimeException("No exchange rate found in the database"));
        long amount = request.getCurrency().equals(Currency.RUB) ?
                BigDecimal.valueOf(request.getAmount())
                        .multiply(latest.getUzsToRub())
                        .longValue() / 1000 : request.getAmount();
        if (hash == null || cashierPass == null || cashdeskId == null ||
                hash.isEmpty() || cashierPass.isEmpty() || cashdeskId.isEmpty()) {
            logger.error("Invalid platform credentials for transfer {}", platformName);
            messageSender.sendMessage(request.getChatId(), "Platform sozlamalarida xato. Administrator bilan bog‘laning.");
            sendMainMenu(request.getChatId());
            return;
        }

        String confirm = DigestUtils.md5DigestAsHex((userId + ":" + hash).getBytes(StandardCharsets.UTF_8));
        String sha256Input = "hash=" + hash + "&lng=" + lng + "&userid=" + userId;
        String sha256Part = sha256Hex(sha256Input);
        String md5Input = "summa=" + amount + "&cashierpass=" + cashierPass + "&cashdeskid=" + cashdeskId;
        String md5Part = DigestUtils.md5DigestAsHex(md5Input.getBytes(StandardCharsets.UTF_8));
        String finalSignature = sha256Hex(sha256Part + md5Part);

        String apiUrl = String.format("https://partners.servcul.com/CashdeskBotAPI/Deposit/%s/Add", userId);
        HttpHeaders headers = new HttpHeaders();
        headers.set("sign", finalSignature);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("cashdeskId", Integer.parseInt(cashdeskId));
        body.put("lng", lng);
        body.put("summa", amount);
        body.put("confirm", confirm);
        body.put("cardNumber", cardNumber);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            Object successObj = responseBody != null ? responseBody.get("success") : null;
            if (successObj == null && responseBody != null) successObj = responseBody.get("Success");

            if (Boolean.TRUE.equals(successObj)) {
                request.setStatus(RequestStatus.APPROVED);
                request.setTransactionId(UUID.randomUUID().toString());
                requestRepository.save(request);
                logger.info("✅ Platform transfer completed: chatId={}, userId={}, amount={}", request.getChatId(), userId, amount);
                messageSender.animateAndDeleteMessages(request.getChatId(), sessionService.getMessageIds(request.getChatId()), "OPEN");
                sessionService.clearMessageIds(request.getChatId());
                String number = blockedUserRepository.findByChatId(request.getChatId()).get().getPhoneNumber();


                BalanceLimit cashdeskBalance = getCashdeskBalance(hash, cashierPass, cashdeskId);
                if (cashdeskBalance==null){
                    String message = String.format("🆔: %d #Bonus tasdiqlandi ✅ \n\uD83C\uDF10 %s :  %s\n💰 Bonus: %,d so‘m\n\uD83D\uDC64 Foydalanuvchi: `%d` \n\uD83D\uDCDE %s \n\n 📅 [%s]",
                            request.getId(),  request.getPlatform(), request.getPlatformUserId(), request.getAmount(), request.getChatId(),number, LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));


                    String bonusMessage = String.format("🆔: %d #Bonus tasdiqlandi ✅ \n\uD83C\uDF10 %s :  %s\n💰 Bonus: %,d so‘m \n\n 📅 [%s]",
                            request.getId(),  request.getPlatform(), request.getPlatformUserId(), request.getAmount(), LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

                    messageSender.sendMessage(request.getChatId(), bonusMessage);

                    adminLogBotService.sendToAdmins(message);
                }else {
                    String message = String.format("🆔: %d #Bonus tasdiqlandi ✅\n\uD83C\uDF10 %s :  %s\n💰 Bonus: %,d so‘m\n Foydalanuvchi: `%d` \n \uD83D\uDCDE %s \n\n  🎟 Kontoraada qolgan limit: %,d %s \n\n 📅 [%s]",
                            request.getId(),  request.getPlatform(), request.getPlatformUserId(), request.getAmount(), request.getChatId(),number, cashdeskBalance.getLimit().longValue(),platformData.getCurrency().toString(), LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));


                    String bonusMessage = String.format("🆔: %d #Bonus tasdiqlandi ✅\n\uD83C\uDF10 %s :  %s\n💰 Bonus: %,d so‘m \n\n 📅 [%s]",
                            request.getId(),  request.getPlatform(), request.getPlatformUserId(), request.getAmount(), LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

                    messageSender.sendMessage(request.getChatId(), bonusMessage);

                    adminLogBotService.sendToAdmins(message);
                }

            } else {
                String error = responseBody != null && responseBody.get("Message") != null
                        ? responseBody.get("Message").toString()
                        : "Platform javob bermadi.";
                logger.error("❌ Transfer failed for chatId {}: {}", request.getChatId(), error);
//                messageSender.sendMessage(request.getChatId(), "❌ Platformga to‘lov yuborilmadi: " + error);
                adminLogBotService.sendToAdmins("So‘rov tasdiqlandi, lekin kontorada xatolik yuz berdi: " + error + " (Foydalanuvchi: " + request.getChatId() + ")");
                handleTransferFailure(chatId, request);

            }
        } catch (Exception e) {
            logger.error("❌ Error transferring top-up to platform for chatId {}: {}", request.getChatId(), e.getMessage());
            messageSender.sendMessage(request.getChatId(), "❌ To‘lov yuborishda xatolik yuz berdi. Qayta urinib ko‘ring.");
            adminLogBotService.sendToAdmins("So‘rov tasdiqlandi, lekin kontorada xatolik yuz berdi: " + e.getMessage() + " (Foydalanuvchi: " + request.getChatId() + ")");
        }

        sendMainMenu(request.getChatId());
    }
    private void handleTransferFailure(Long chatId, HizmatRequest request) {
        ExchangeRate latest = exchangeRateRepository.findLatest()
                .orElseThrow(() -> new RuntimeException("No exchange rate found in the database"));
        long amount = request.getCurrency().equals(Currency.RUB) ?
                BigDecimal.valueOf(request.getUniqueAmount())
                        .multiply(latest.getUzsToRub())
                        .longValue() / 1000 : request.getUniqueAmount();
        String number = blockedUserRepository.findByChatId(request.getChatId()).get().getPhoneNumber();
        long rubAmount =
                BigDecimal.valueOf(request.getUniqueAmount())
                        .multiply(latest.getUzsToRub())
                        .longValue() / 1000 ;
        String errorLogMessage = String.format(
                "🆔: %d \n Transfer xatosi ❌\n" +
                        "👤 User ID [%s] %s\n" +
                        "🌐 %s: " + "%s\n"+
                        "💸 Miqdor: %,d UZS\n" +
                        "💸 Miqdor: %,d RUB\n" +
                        "💳 Karta: `%s`\n" +
                        "📅 [%s] ",
                request.getId(),
                request.getChatId(),number, request.getPlatform(), request.getPlatformUserId(),
                request.getUniqueAmount(), rubAmount, request.getCardNumber(),
                LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
               );

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("✅ Qabul qilish", "ADMIN_APPROVE_TRANSFER:" + request.getId()),
                createButton("❌ Rad etish", "ADMIN_DECLINE_TRANSFER:" + request.getId())
        ));
        markup.setKeyboard(rows);

        adminLogBotService.sendToAdmins(errorLogMessage, markup);
        messageSender.sendMessage(request.getChatId(), "❌ Transfer xatosi: Pul o‘tkazishda xato yuz berdi. Admin qayta tekshiradi.");
    }

    public void handleAdminDeclineTransfer(Long chatId, Long requestId) {
        HizmatRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Request not found: " + requestId));
        request.setStatus(RequestStatus.CANCELED);
        requestRepository.save(request);
        String number = blockedUserRepository.findByChatId(request.getChatId()).get().getPhoneNumber();
        UserBalance balance = userBalanceRepository.findById(request.getChatId())
                .orElse(UserBalance.builder().chatId(requestId).tickets(0L).balance(BigDecimal.ZERO).build());
        String errorLogMessage = String.format(
                "🆔: %d \n Bonus rad etildi ❌\n" +
                        "👤 User ID [%s] %s\n" +
                        "🌐 %s: " + "%s\n"+
                        "💸 Bonus: %s \n"+
                        "💰 Balans: %s so‘m\n"+
                        "📅 [%s] ",
                request.getId(),
                request.getChatId(),number, request.getPlatform(), request.getPlatformUserId(),request.getUniqueAmount(),balance.getBalance().longValue(),
                LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        String userErrorLogMessage = String.format(
                "🆔: %d \n Bonus rad etildi ❌\n" +
                        "🌐 %s: " + "%s\n"+
                        "💸 Bonus: %s \n"+
                        "💰 Balans: %s so‘m\n"+
                        "📅 [%s] ",
                request.getId(),
                request.getPlatform(), request.getPlatformUserId(),request.getUniqueAmount(),balance.getBalance().longValue(),
                LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(userErrorLogMessage);
        message.setReplyMarkup(backButtonKeyboard());
        messageSender.sendMessage(message, request.getChatId() );
        adminLogBotService.sendToAdmins(errorLogMessage);
    }

    public void handleAdminRemoveTickets(Long chatId, Long userChatId) {
        AdminChat adminChat = adminChatRepository.findById(chatId).orElse(null);
        if (adminChat == null || !adminChat.isReceiveNotifications()) {
            messageSender.sendMessage(chatId, "Sizda bu amalni bajarish uchun ruxsat yo‘q.");
            return;
        }
        UserBalance balance = userBalanceRepository.findById(userChatId)
                .orElse(UserBalance.builder().chatId(userChatId).tickets(0L).balance(BigDecimal.ZERO).build());
        balance.setTickets(0L);
        userBalanceRepository.save(balance);

        messageSender.sendMessage(userChatId, "Sizning chiptalaringiz o‘chirildi.");
        adminLogBotService.sendToAdmins("Chiptalar o‘chirildi: Foydalanuvchi: " + userChatId);
    }

    public void handleAdminRemoveBonus(Long chatId, Long userChatId) {
        AdminChat adminChat = adminChatRepository.findById(chatId).orElse(null);
        if (adminChat == null || !adminChat.isReceiveNotifications()) {
            messageSender.sendMessage(chatId, "Sizda bu amalni bajarish uchun ruxsat yo‘q.");
            return;
        }
        UserBalance balance = userBalanceRepository.findById(userChatId)
                .orElse(UserBalance.builder().chatId(userChatId).tickets(0L).balance(BigDecimal.ZERO).build());
        balance.setBalance(BigDecimal.ZERO);
        userBalanceRepository.save(balance);

        messageSender.sendMessage(userChatId, "Sizning bonus balansingiz o‘chirildi.");
        adminLogBotService.sendToAdmins("Bonus balansi o‘chirildi: Foydalanuvchi: " + userChatId);
    }

    public void handleAdminBlockUser(Long chatId, Long userChatId) {
        AdminChat adminChat = adminChatRepository.findById(chatId).orElse(null);
        if (adminChat == null || !adminChat.isReceiveNotifications()) {
            messageSender.sendMessage(chatId, "Sizda bu amalni bajarish uchun ruxsat yo‘q.");
            return;
        }
        BlockedUser blockedUser = BlockedUser.builder().chatId(userChatId).phoneNumber("BLOCKED").build();
        blockedUserRepository.save(blockedUser);

        messageSender.sendMessage(userChatId, "Sizning hisobingiz bloklandi.");
        adminLogBotService.sendToAdmins("Foydalanuvchi bloklandi: Foydalanuvchi: " + userChatId);
    }

    private void playLottery(Long chatId) {
        try {
            UserBalance balance = userBalanceRepository.findById(chatId)
                    .orElse(UserBalance.builder().chatId(chatId).tickets(0L).balance(BigDecimal.ZERO).build());
            Long availableTickets = balance.getTickets();
            if (availableTickets < MINIMUM_TICKETS) {
                messageSender.sendMessage(chatId, String.format("O‘ynash uchun kamida %s ta chipta kerak! Sizda %s ta chipta bor.", MINIMUM_TICKETS, availableTickets));
                sendLotteryMenu(chatId);
                return;
            }

            Long numberOfPlays = Math.min(availableTickets, MAXIMUM_TICKETS);
            Map<Long, BigDecimal> ticketWinnings = lotteryService.playLotteryWithDetails(chatId, numberOfPlays);

            balance.setTickets(balance.getTickets() - numberOfPlays);
            BigDecimal totalWinnings = ticketWinnings.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            balance.setBalance(balance.getBalance().add(totalWinnings));
            userBalanceRepository.save(balance);

            StringBuilder winningsLog = new StringBuilder("🎉 Lotereya natijalari:\n");
            ticketWinnings.forEach((ticketNumber, amount) ->
                    winningsLog.append(String.format("%,d so‘m\n",amount.longValue())));
            winningsLog.append(String.format("Jami yutuq: %,d so‘m\nYangi balans: %,d so‘m",
                    totalWinnings.longValue(), balance.getBalance().longValue()));

            messageSender.sendMessage(chatId, winningsLog.toString());
            String number = blockedUserRepository.findByChatId(chatId).get().getPhoneNumber();

            String adminLog = String.format(
                    "Lotereya o‘ynaldi 🎟\n" +
                            "👤 User ID [%s] %s\n" +
                            "🎫 O‘ynalgan chiptalar: %s ta\n" +
                            "💰 Jami yutuq: %s so‘m\n" +
                            "💸 Yangi balans: %s so‘m\n"+
                            "📅 [%s]",
                    chatId,number, numberOfPlays, totalWinnings.longValue(), balance.getBalance().longValue(),  LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            adminLogBotService.sendLog(adminLog);

            sendLotteryMenu(chatId);
        } catch (IllegalStateException e) {
            logger.error("Lottery play failed for chatId {}: {}", chatId, e.getMessage());
            messageSender.sendMessage(chatId, "Xatolik: " + e.getMessage());
            sendLotteryMenu(chatId);
        }
    }

    public BigDecimal getReferralBalance(Long chatId) {
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElse(UserBalance.builder().chatId(chatId).tickets(0L).balance(BigDecimal.ZERO).build());
        return balance.getBalance();
    }

    public void creditReferral(Long referredChatId, long topUpAmount) {
        Referral referral = referralRepository.findByReferredChatId(referredChatId).orElse(null);
        if (referral == null) {
            logger.info("No referral found for referredChatId: {}", referredChatId);
            return;
        }

        Long referrerChatId = referral.getReferrerChatId();
        BigDecimal commission = new BigDecimal(topUpAmount).multiply(new BigDecimal("0.001")).setScale(2, RoundingMode.DOWN);
        UserBalance referrerBalance = userBalanceRepository.findById(referrerChatId)
                .orElse(UserBalance.builder().chatId(referrerChatId).tickets(0L).balance(BigDecimal.ZERO).build());
        referrerBalance.setBalance(referrerBalance.getBalance().add(commission));
        userBalanceRepository.save(referrerBalance);
        logger.info("Credited {} UZS to referrer {} for referredChatId {}", commission, referrerChatId, referredChatId);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            return DatatypeConverter.printHexBinary(hash).toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }

    private void sendMainMenu(Long chatId) {
        sessionService.clearSession(chatId);
        sessionService.setUserState(chatId, "MAIN_MENU");
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Xush kelibsiz! Operatsiyani tanlang:");
        message.setReplyMarkup(createMainMenuKeyboard());
        messageSender.sendMessage(message, chatId);
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

    private InlineKeyboardMarkup createBonusMenuKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton("🎟 Lotereya", "BONUS_LOTTERY")));
        rows.add(List.of(createButton("🤝 Referal", "BONUS_REFERRAL")));
        rows.add(List.of(createButton("💰 Pul to‘ldirish", "BONUS_TOPUP")));
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }
    private InlineKeyboardMarkup backButtonKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createLotteryKeyboard(long ticketCount) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (ticketCount >= MINIMUM_TICKETS) {
            rows.add(List.of(createButton("🎲 O‘ynash", "BONUS_LOTTERY_PLAY")));
        }
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createReferralKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton("🔗 Referal havola", "BONUS_REFERRAL_LINK")));
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createTopUpPlatformKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<Platform> uzsPlatforms = platformRepository.findByCurrency(Currency.UZS);
        List<Platform> rubPlatforms = platformRepository.findByCurrency(Currency.RUB);

        int maxRows = Math.max(uzsPlatforms.size(), rubPlatforms.size());
        for (int i = 0; i < maxRows; i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            if (i < uzsPlatforms.size()) {
                Platform uzsPlatform = uzsPlatforms.get(i);
                row.add(createButton("🇺🇿 " + uzsPlatform.getName(), "BONUS_TOPUP_PLATFORM:" + uzsPlatform.getName()));
            }
            if (i < rubPlatforms.size()) {
                Platform rubPlatform = rubPlatforms.get(i);
                row.add(createButton("🇷🇺 " + rubPlatform.getName(), "BONUS_TOPUP_PLATFORM:" + rubPlatform.getName()));
            }else{
                i++;
                if (i < uzsPlatforms.size() && i<maxRows) {
                    Platform uzsPlatform = uzsPlatforms.get(i);
                    row.add(createButton("🇺🇿 " + uzsPlatform.getName(), "BONUS_TOPUP_PLATFORM:" + uzsPlatform.getName()));
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
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
                    .map(id -> createButton("ID: " + id, "BONUS_TOPUP_PAST_ID:" + id))
                    .collect(Collectors.toList());
            if (!pastIdButtons.isEmpty()) {
                rows.add(pastIdButtons);
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
                createButton("✅ To‘g‘ri", "BONUS_TOPUP_APPROVE_USER"),
                createButton("❌ Noto‘g‘ri", "BONUS_TOPUP_REJECT_USER")
        ));
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createAmountKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("10,000 so‘m", "BONUS_TOPUP_AMOUNT_10000"),
                createButton("100,000 so‘m", "BONUS_TOPUP_AMOUNT_100000")
        ));
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createConfirmKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("✅ Ha", "BONUS_TOPUP_CONFIRM_YES"),
                createButton("❌ Yo‘q", "BONUS_TOPUP_CONFIRM_NO")
        ));
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createAdminApprovalKeyboard(Long requestId, Long userChatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("✅ Tasdiqlash", "ADMIN_APPROVE_TRANSFER:" + requestId),
                createButton("❌ Rad etish", "ADMIN_DECLINE_TRANSFER:" + requestId)
        ));
        rows.add(List.of(
                createButton("🎟 Chiptalarni o‘chirish", "ADMIN_REMOVE_TICKETS:" + userChatId),
                createButton("💰 Bonusni o‘chirish", "ADMIN_REMOVE_BONUS:" + userChatId)
        ));
        rows.add(List.of(createButton("🚫 Foydalanuvchini bloklash", "ADMIN_BLOCK_USER:" + userChatId)));
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
}