package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import com.example.shade.dto.BalanceLimit;
import com.example.shade.model.*;
import com.example.shade.model.Currency;
import com.example.shade.repository.*;
import jakarta.xml.bind.DatatypeConverter;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TopUpService {
    private static final Logger logger = LoggerFactory.getLogger(TopUpService.class);
    private final UserSessionService sessionService;
    private final HizmatRequestRepository requestRepository;
    private final PlatformRepository platformRepository;
    private final AdminCardRepository adminCardRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final BonusService bonusService;
    private final LotteryService lotteryService;
    private final OsonService osonService;
    private final MessageSender messageSender;
    private final AdminLogBotService adminLogBotService;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final long MIN_AMOUNT = 10_000;
    private static final long MAX_AMOUNT = 10_000_000;
    private static final String PAYMENT_MESSAGE_KEY = "payment_message_id";
    private static final String PAYMENT_ATTEMPTS_KEY = "payment_attempts";
    private final BlockedUserRepository blockedUserRepository;

    public void startTopUp(Long chatId) {
        logger.info("Starting top-up for chatId: {}", chatId);
        sessionService.setUserState(chatId, "TOPUP_PLATFORM_SELECTION");
        sessionService.addNavigationState(chatId, "MAIN_MENU");
        sessionService.setUserData(chatId, PAYMENT_ATTEMPTS_KEY, "0");
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
            case "TOPUP_USER_ID_INPUT" -> handleUserIdInput(chatId, text);
            case "TOPUP_CARD_INPUT" -> handleCardInput(chatId, text);
            case "TOPUP_AMOUNT_INPUT" -> handleAmountInput(chatId, text);
            case "TOPUP_PAYMENT_CONFIRM" -> handlePaymentConfirmation(chatId, text);
            default -> messageSender.sendMessage(chatId, "Iltimos, menyudan operatsiyani tanlang.");
        }
    }

    public void handleCallback(Long chatId, String callback) {
        logger.info("Callback received for chatId {}: {}", chatId, callback);
        messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
        sessionService.clearMessageIds(chatId);

        switch (callback) {
            case "TOPUP_USE_SAVED_ID" -> validateUserId(chatId, sessionService.getUserData(chatId, "platformUserId"));
            case "TOPUP_APPROVE_USER" -> handleApproveUser(chatId);
            case "TOPUP_REJECT_USER" -> {
                sessionService.setUserState(chatId, "TOPUP_USER_ID_INPUT");
                sendUserIdInput(chatId, sessionService.getUserData(chatId, "platform"));
            }
            case "TOPUP_USE_SAVED_CARD" -> {
                sessionService.setUserState(chatId, "TOPUP_AMOUNT_INPUT");
                sessionService.addNavigationState(chatId, "TOPUP_CARD_INPUT");
                sendAmountInput(chatId);
            }
            case "TOPUP_AMOUNT_10000" -> {
                sessionService.setUserData(chatId, "amount", "10000");
                sessionService.setUserState(chatId, "TOPUP_CONFIRMATION");
                sessionService.addNavigationState(chatId, "TOPUP_AMOUNT_INPUT");
                initiateTopUpRequest(chatId);
            }
            case "TOPUP_AMOUNT_10000000" -> {
                sessionService.setUserData(chatId, "amount", "10000000");
                sessionService.setUserState(chatId, "TOPUP_CONFIRMATION");
                sessionService.addNavigationState(chatId, "TOPUP_AMOUNT_INPUT");
                initiateTopUpRequest(chatId);
            }
            case "TOPUP_CONFIRM" -> initiateTopUpRequest(chatId);
            case "TOPUP_PAYMENT_CONFIRM" -> verifyPayment(chatId);
            case "TOPUP_SEND_SCREENSHOT" -> {
                sessionService.setUserState(chatId, "TOPUP_AWAITING_SCREENSHOT");
                messageSender.sendMessage(chatId, "Iltimos, to‘lov chekining skrinshotini yuboring.");
            }
            default -> {
                if (callback.startsWith("TOPUP_PLATFORM:")) {
                    String platformName = callback.split(":")[1];
                    logger.info("Platform selected for chatId {}: {}", chatId, platformName);
                    sessionService.setUserData(chatId, "platform", platformName);
                    sessionService.setUserState(chatId, "TOPUP_USER_ID_INPUT");
                    sessionService.addNavigationState(chatId, "TOPUP_PLATFORM_SELECTION");
                    sendUserIdInput(chatId, platformName);
                } else if (callback.startsWith("TOPUP_PAST_ID:")) {
                    validateUserId(chatId, callback.split(":")[1]);
                } else if (callback.startsWith("TOPUP_PAST_CARD:")) {
                    sessionService.setUserData(chatId, "cardNumber", callback.split(":")[1]);
                    sessionService.setUserState(chatId, "TOPUP_AMOUNT_INPUT");
                    sessionService.addNavigationState(chatId, "TOPUP_CARD_INPUT");
                    sendAmountInput(chatId);
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
            case "TOPUP_PLATFORM_SELECTION" -> {
                sessionService.setUserState(chatId, "TOPUP_PLATFORM_SELECTION");
                sendPlatformSelection(chatId);
            }
            case "TOPUP_USER_ID_INPUT", "TOPUP_APPROVE_USER" -> {
                sessionService.setUserState(chatId, "TOPUP_USER_ID_INPUT");
                sendUserIdInput(chatId, sessionService.getUserData(chatId, "platform"));
            }
            case "TOPUP_CARD_INPUT" -> {
                sessionService.setUserState(chatId, "TOPUP_CARD_INPUT");
                sendCardInput(chatId, sessionService.getUserData(chatId, "fullName"));
            }
            case "TOPUP_AMOUNT_INPUT" -> {
                sessionService.setUserState(chatId, "TOPUP_AMOUNT_INPUT");
                sendAmountInput(chatId);
            }
            case "TOPUP_PAYMENT_CONFIRM" -> {
                sessionService.setUserState(chatId, "TOPUP_PAYMENT_CONFIRM");
                sendPaymentInstruction(chatId);
            }
            case "TOPUP_AWAITING_SCREENSHOT" -> {
                sessionService.setUserState(chatId, "TOPUP_PAYMENT_CONFIRM");
                sendPaymentInstruction(chatId);
            }
            default -> sendMainMenu(chatId);
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

        String confirmInput = userId + ":" + hash;
        String confirm = DigestUtils.md5DigestAsHex(confirmInput.getBytes(StandardCharsets.UTF_8));
        String sha256Input1 = "hash=" + hash + "&userid=" + userId + "&cashdeskid=" + cashdeskId;
        String sha256Result1 = sha256Hex(sha256Input1);
        String md5Input = "userid=" + userId + "&cashierpass=" + cashierPass + "&hash=" + hash;
        String md5Result = DigestUtils.md5DigestAsHex(md5Input.getBytes(StandardCharsets.UTF_8));
        String finalSignature = sha256Hex(sha256Result1 + md5Result);

        logger.debug("Validating user ID {}: confirmInput={}, confirm={}, sha256Input1={}, sha256Result1={}, md5Input={}, md5Result={}, finalSignature={}",
                userId, confirmInput, confirm, sha256Input1, sha256Result1, md5Input, md5Result, finalSignature);

        String apiUrl = String.format("https://partners.servcul.com/CashdeskBotAPI/Users/%s?confirm=%s&cashdeskId=%s",
                userId, confirm, cashdeskId);
        logger.info("Validating user ID {} for platform {} (chatId: {}), URL: {}", userId, platformName, chatId, apiUrl);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("sign", finalSignature);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<UserProfile> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, UserProfile.class);
            UserProfile profile = response.getBody();

            if (response.getStatusCode().is2xxSuccessful() && profile != null && profile.getUserId() != null && profile.getName() != null) {
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
                        .currency(currency)
                        .amount(0L)
                        .type(RequestType.TOP_UP)
                        .build();
                requestRepository.save(request);

                sessionService.setUserState(chatId, "TOPUP_APPROVE_USER");
                sendUserApproval(chatId, fullName, userId);
            } else {
                logger.warn("Invalid user profile for ID {} on platform {}. Response: {}", userId, platformName, profile);
                sendNoUserFound(chatId);
            }
        } catch (HttpClientErrorException.NotFound e) {
            logger.warn("User not found for ID {} on platform {}: {}", userId, platformName, e.getMessage());
            sendNoUserFound(chatId);
        } catch (HttpClientErrorException e) {
            logger.error("API error for user ID {} on platform {}: {}", userId, platformName, e.getMessage());
            String errorMessage = e.getStatusCode().value() == 401 ?
                    "Authentication failed: Invalid credentials or signature" :
                    e.getStatusCode().value() == 403 ?
                            "Invalid confirm parameter. Please check user ID and platform credentials." :
                            "API error occurred";
            sendMessageWithNavigation(chatId, errorMessage + ". Please try again.");
        } catch (Exception e) {
            logger.error("Error calling API for user ID {} on platform {}: {}", userId, platformName, e.getMessage());
            sendMessageWithNavigation(chatId, "API xatosi yuz berdi. Iltimos, qayta urinib ko‘ring.");
        }
    }

    private void handleApproveUser(Long chatId) {
        sessionService.setUserState(chatId, "TOPUP_CARD_INPUT");
        sessionService.addNavigationState(chatId, "TOPUP_APPROVE_USER");
        String fullName = sessionService.getUserData(chatId, "fullName");
        if (fullName == null) {
            logger.error("FullName is null for chatId {}", chatId);
            messageSender.sendMessage(chatId, "Xatolik: Foydalanuvchi ma'lumotlari topilmadi. Qayta urinib ko‘ring.");
            sessionService.setUserState(chatId, "TOPUP_USER_ID_INPUT");
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

        sessionService.setUserState(chatId, "TOPUP_AMOUNT_INPUT");
        sessionService.addNavigationState(chatId, "TOPUP_CARD_INPUT");
        sendAmountInput(chatId);
    }

    private void handleAmountInput(Long chatId, String amountText) {
        messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
        sessionService.clearMessageIds(chatId);
        try {
            long amount = Long.parseLong(amountText.replaceAll("[^\\d]", ""));
            if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
                sendMessageWithNavigation(chatId, "Summa 10 000 dan 10 000 000 gacha bo‘lishi kerak! Qayta kiriting:");
                return;
            }
            sessionService.setUserData(chatId, "amount", String.valueOf(amount));
            sessionService.setUserState(chatId, "TOPUP_CONFIRMATION");
            sessionService.addNavigationState(chatId, "TOPUP_AMOUNT_INPUT");
            initiateTopUpRequest(chatId);
        } catch (NumberFormatException e) {
            logger.warn("Invalid amount format for chatId {}: {}", chatId, amountText);
            sendMessageWithNavigation(chatId, "Iltimos, to‘g‘ri summa kiriting (faqat raqamlar):");
        }
    }

    private void handlePaymentConfirmation(Long chatId, String text) {
        sendMessageWithNavigation(chatId, "Iltimos, to‘lovni tasdiqlash uchun 'Tasdiqlash' tugmasini bosing.");
    }

    private void initiateTopUpRequest(Long chatId) {
        if (sessionService.getUserData(chatId, "platformUserId") == null) {
            logger.error("No validated user ID for chatId {}", chatId);
            messageSender.sendMessage(chatId, "Foydalanuvchi tasdiqlanmagan. Iltimos, ID ni qayta kiriting.");
            sessionService.setUserState(chatId, "TOPUP_USER_ID_INPUT");
            sendUserIdInput(chatId, sessionService.getUserData(chatId, "platform"));
            return;
        }

        String platformName = sessionService.getUserData(chatId, "platform").replace("_", "");

        AdminCard adminCard = adminCardRepository.findLeastRecentlyUsed()
                .orElseThrow(() -> new IllegalStateException("No admin cards available"));

        long amount = Long.parseLong(sessionService.getUserData(chatId, "amount"));
        long uniqueAmount = generateUniqueAmount(amount);

        HizmatRequest request = requestRepository.findTopByChatIdAndPlatformAndPlatformUserIdOrderByCreatedAtDesc(
                chatId, platformName, sessionService.getUserData(chatId, "platformUserId")).orElse(null);
        if (request == null) {
            logger.error("No pending request found for chatId {}, platform: {}, userId: {}", chatId, platformName, sessionService.getUserData(chatId, "platformUserId"));
            messageSender.sendMessage(chatId, "Xatolik: So‘rov topilmadi. Qayta urinib ko‘ring.");
            sendMainMenu(chatId);
            return;
        }

        request.setAmount(amount);
        request.setUniqueAmount(uniqueAmount);
        request.setAdminCardId(adminCard.getId());
        request.setCardNumber(sessionService.getUserData(chatId, "cardNumber"));
        request.setStatus(RequestStatus.PENDING_PAYMENT);
        request.setTransactionId(UUID.randomUUID().toString());
        request.setPaymentAttempts(0);
        requestRepository.save(request);

        adminCard.setLastUsed(LocalDateTime.now());
        adminCardRepository.save(adminCard);

        sessionService.setUserState(chatId, "TOPUP_PAYMENT_CONFIRM");
        sessionService.addNavigationState(chatId, "TOPUP_CONFIRMATION");
        sendPaymentInstruction(chatId);
    }

    private void verifyPayment(Long chatId) {
        HizmatRequest request = requestRepository.findByChatIdAndStatus(chatId, RequestStatus.PENDING_PAYMENT)
                .orElse(null);
        if (request == null) {
            logger.error("No pending payment request found for chatId {}", chatId);
            messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
            sessionService.clearMessageIds(chatId);
            messageSender.sendMessage(chatId, "Xatolik: So‘rov topilmadi. Iltimos, qayta urinib ko‘ring.");
            sendMainMenu(chatId);
            return;
        }

        int attempts = Integer.parseInt(sessionService.getUserData(chatId, PAYMENT_ATTEMPTS_KEY, "0"));
        attempts++;
        sessionService.setUserData(chatId, PAYMENT_ATTEMPTS_KEY, String.valueOf(attempts));
        request.setPaymentAttempts(attempts);
        requestRepository.save(request);

        AdminCard adminCard = adminCardRepository.findById(request.getAdminCardId())
                .orElseThrow(() -> new IllegalStateException("Admin card not found: " + request.getAdminCardId()));

        Map<String, Object> statusResponse = osonService.verifyPaymentByAmountAndCard(
                chatId, request.getPlatform(), request.getPlatformUserId(),
                request.getAmount(), request.getCardNumber(), adminCard.getCardNumber(), request.getUniqueAmount());

        if ("SUCCESS".equals(statusResponse.get("status"))) {
            request.setTransactionId((String) statusResponse.get("transactionId"));
            request.setBillId(Long.parseLong(String.valueOf(statusResponse.get("billId"))));
            request.setPayUrl((String) statusResponse.get("payUrl"));
            request.setStatus(RequestStatus.APPROVED);
            requestRepository.save(request);

            BalanceLimit transferSuccessful = transferToPlatform(request, adminCard);
            ExchangeRate latest = exchangeRateRepository.findLatest()
                    .orElseThrow(() -> new RuntimeException("No exchange rate found in the database"));
            long amount = request.getCurrency().equals(Currency.RUB) ?
                    BigDecimal.valueOf(request.getUniqueAmount())
                            .multiply(latest.getUzsToRub())
                            .longValue() / 1000 : request.getUniqueAmount();
            long rubAmount =
                    BigDecimal.valueOf(request.getUniqueAmount())
                            .multiply(latest.getUzsToRub())
                            .longValue() / 1000;
            if (transferSuccessful!=null) {
                UserBalance balance = userBalanceRepository.findById(chatId)
                        .orElseGet(() -> {
                            UserBalance newBalance = UserBalance.builder()
                                    .chatId(chatId)
                                    .tickets(0L)
                                    .balance(BigDecimal.ZERO)
                                    .build();
                            return userBalanceRepository.save(newBalance);
                        });
                long tickets = request.getAmount() / 30_000;
                if (tickets > 0) {
                    lotteryService.awardTickets(chatId, tickets);
                }

                bonusService.creditReferral(chatId, request.getAmount());
                String number = blockedUserRepository.findByChatId(chatId).get().getPhoneNumber();
                String logMessage = String.format(
                        "📋 So‘rov ID: %d  To‘lov yakunlandi ✅\n" +
                                "👤User ID [%s] %s\n" +  // Clickable number with + sign
                                "🌐 %s: " + "%s\n" +
                                "💸 Miqdor: %,d UZS\n" +
                                "💸 Miqdor: %,d RUB\n" +
                                "💳 Karta raqami: `%s`\n" +
                                "🔐 Admin kartasi: `%s`\n" +
                                "🎟️ Chiptalar: %d\n\n" +
                                "📅 [%s]",
                        request.getId(),
                        chatId,
                        number,
                        request.getPlatform(),
                        request.getPlatformUserId(),
                        request.getUniqueAmount(),
                        rubAmount,
                        request.getCardNumber(),
                        adminCard.getCardNumber(),
                        tickets,
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                );

                String logMessageAdmin = String.format(
                        "📋 So‘rov ID: %d  To‘lov yakunlandi ✅\n" +
                                "👤User ID [%s] %s\n" +  // Clickable number with + sign
                                "🌐 %s: " + "%s\n" +
                                "💸 Miqdor: %,d UZS\n" +
                                "💸 Miqdor: %,d RUB\n" +
                                "💳 Karta raqami: `%s`\n" +
                                "🔐 Admin kartasi: `%s`\n" +
                                "🎟️ Chiptalar: %d\n\n" +
                                "🎟 Platformada qolgan limit: %,d %s\n\n" +
                                "📅 [%s]",
                        request.getId(),
                        chatId,
                        number,
                        request.getPlatform(),
                        request.getPlatformUserId(),
                        request.getUniqueAmount(),
                        rubAmount,
                        request.getCardNumber(),
                        adminCard.getCardNumber(),
                        tickets,
                        transferSuccessful.getLimit().longValue(),
                        request.getCurrency().toString(),
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                );
                adminLogBotService.sendLog(logMessageAdmin);

                messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
                sessionService.clearMessageIds(chatId);
                sessionService.setUserData(chatId, PAYMENT_ATTEMPTS_KEY, "0");
                messageSender.sendMessage(chatId, logMessage +
                        (tickets > 0 ? " Siz " + tickets + " ta lotereya chiptasi oldingiz!" : ""));
                sendMainMenu(chatId);
            } else {
                handleTransferFailure(chatId, request, adminCard);
            }
        } else {
            logger.warn("Payment not received for chatId {}, uniqueAmount: {}, cardNumber: {}",
                    chatId, request.getUniqueAmount(), request.getCardNumber());

            if (attempts >= 2) {
                request.setStatus(RequestStatus.PENDING_SCREENSHOT);
                requestRepository.save(request);
                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                message.setText("To‘lov hali qabul qilinmadi. Iltimos, to‘lov chekining skrinshotini yuboring.");
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                rows.add(createNavigationButtons());
                markup.setKeyboard(rows);
                message.setReplyMarkup(markup);
                messageSender.sendMessage(message, chatId);

                sessionService.setUserState(chatId, "TOPUP_AWAITING_SCREENSHOT");
            } else {
                messageSender.sendMessage(chatId, "To‘lov hali qabul qilinmadi. Iltimos, biroz kuting va yana 'Tasdiqlash' tugmasini bosing.");
                sendPaymentInstruction(chatId);
            }
        }
    }

    private void handleTransferFailure(Long chatId, HizmatRequest request, AdminCard adminCard) {
        ExchangeRate latest = exchangeRateRepository.findLatest()
                .orElseThrow(() -> new RuntimeException("No exchange rate found in the database"));
        long amount = request.getCurrency().equals(Currency.RUB) ?
                BigDecimal.valueOf(request.getUniqueAmount())
                        .multiply(latest.getUzsToRub())
                        .longValue() / 1000 : request.getUniqueAmount();
        long rubAmount =
                BigDecimal.valueOf(request.getUniqueAmount())
                        .multiply(latest.getUzsToRub())
                        .longValue() / 1000;
        String number = blockedUserRepository.findByChatId(chatId).get().getPhoneNumber();
        String errorLogMessage = String.format(
                " 📋 So‘rov ID: %d Transfer xatosi ❌\n" +
                        "👤 User ID [%s] %s\n" +  // Clickable number with + sign
                        "🌐 %s: " + "%s\n" +
                        "💸 Miqdor: %,d UZS\n" +
                        "💸 Miqdor: %,d RUB\n" +
                        "💳 Karta raqami: `%s`\n" +
                        "🔐 Admin kartasi: `%s`\n" +
                        "📅 [%s] ",
                request.getId(),
                chatId, number,  // ✅ chatId as label, phone number as link
                request.getPlatform(),
                request.getPlatformUserId(),
                request.getUniqueAmount(),
                rubAmount,
                request.getCardNumber(),
                adminCard.getCardNumber(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );


        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("✅ Qabul qilish", "ADMIN_APPROVE_TRANSFER:" + request.getId()),
                createButton("❌ Rad etish", "ADMIN_DECLINE_TRANSFER:" + request.getId())
        ));
        markup.setKeyboard(rows);

        adminLogBotService.sendToAdmins(errorLogMessage, markup);
        messageSender.sendMessage(chatId, "❌ Transfer xatosi: Pul o‘tkazishda xato yuz berdi. Admin qayta tekshiradi.");
    }

    public void handleScreenshotApproval(Long chatId, Long requestId, boolean approve) {
        HizmatRequest request = requestRepository.findByChatIdAndStatus(requestId, RequestStatus.PENDING_SCREENSHOT)
                .orElse(null);
        if (request == null) {
            logger.error("No request found for ID {}", requestId);
            adminLogBotService.sendLog("❌ Xatolik: So‘rov topilmadi. ID: " + requestId);
            return;
        }

        AdminCard adminCard = adminCardRepository.findById(request.getAdminCardId())
                .orElseThrow(() -> new IllegalStateException("Admin card not found: " + request.getAdminCardId()));

        ExchangeRate latest = exchangeRateRepository.findLatest()
                .orElseThrow(() -> new RuntimeException("No exchange rate found in the database"));
        long amount = request.getCurrency().equals(Currency.RUB) ?
                BigDecimal.valueOf(request.getUniqueAmount())
                        .multiply(latest.getUzsToRub())
                        .longValue() / 1000 : request.getUniqueAmount();
        long rubAmount =
                BigDecimal.valueOf(request.getUniqueAmount())
                        .multiply(latest.getUzsToRub())
                        .longValue() / 1000;

        if (approve) {
            request.setStatus(RequestStatus.APPROVED);
            requestRepository.save(request);

            BalanceLimit transferSuccessful = transferToPlatform(request, adminCard);
            if (transferSuccessful!=null) {
                UserBalance balance = userBalanceRepository.findById(requestId)
                        .orElseGet(() -> {
                            UserBalance newBalance = UserBalance.builder()
                                    .chatId(requestId)
                                    .tickets(0L)
                                    .balance(BigDecimal.ZERO)
                                    .build();
                            return userBalanceRepository.save(newBalance);
                        });
                long tickets = request.getAmount() / 30_000;
                if (tickets > 0) {
                    lotteryService.awardTickets(requestId,tickets);
                }

                bonusService.creditReferral(requestId, request.getAmount());

                String number = blockedUserRepository.findByChatId(requestId).get().getPhoneNumber();
                String logMessage = String.format(
                        " 📋 So‘rov ID: %d To‘lov skrinshoti tasdiqlandi ✅\n" +
                                "👤ID [%s] %s\n" +  // Clickable number with + sign
                                "🌐 %s: " + "%s\n" +
                                "💸 Miqdor: %,d UZS\n" +
                                "💸 Miqdor: %,d RUB\n" +
                                "💳 Karta raqami: `%s`\n" +
                                "🔐 Admin kartasi: `%s`\n" +
                                "🎟️ Chiptalar: %d\n\n" +
                                "📅 [%s] ",
                        request.getId(),
                        request.getChatId(), number,  // 🟢 Show chatId, link to phone number
                        request.getPlatform(),
                        request.getPlatformUserId(),
                        request.getUniqueAmount(),
                        rubAmount,
                        request.getCardNumber(),
                        adminCard.getCardNumber(),
                        tickets,
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                );
                String adminLogMessage = String.format(
                        " 📋 So‘rov ID: %d To‘lov skrinshoti tasdiqlandi ✅\n" +
                                "👤ID [%s] %s\n" +  // Clickable number with + sign
                                "🌐 %s: " + "%s\n" +
                                "💸 Miqdor: %,d UZS\n" +
                                "💸 Miqdor: %,d RUB\n" +
                                "💳 Karta raqami: `%s`\n" +
                                "🔐 Admin kartasi: `%s`\n" +
                                "🎟️ Chiptalar: %d\n\n" +
                                "🎟 Platformada qolgan limit: %,d %s\n\n" +
                                "📅 [%s] ",
                        request.getId(),
                        request.getChatId(), number,  // 🟢 Show chatId, link to phone number
                        request.getPlatform(),
                        request.getPlatformUserId(),
                        request.getUniqueAmount(),
                        rubAmount,
                        request.getCardNumber(),
                        adminCard.getCardNumber(),
                        tickets,
                        transferSuccessful.getLimit().longValue(),
                        request.getCurrency().toString(),
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                );


                adminLogBotService.sendLog(adminLogMessage);
                messageSender.sendMessage(requestId, logMessage +
                        (tickets > 0 ? " Siz " + tickets + " ta lotereya chiptasi oldingiz!" : ""));
            } else {
                handleTransferFailure(requestId, request, adminCard);
            }
        } else {
            request.setStatus(RequestStatus.CANCELED);
            requestRepository.save(request);

            String number = blockedUserRepository.findByChatId(requestId).get().getPhoneNumber();
            String logMessage = String.format(
                    "📋 So‘rov ID: %d To‘lov skrinshoti rad etildi ❌\n" +
                            "👤ID [%s] %s\n" +
                            "🌐 %s: " + "%s\n" +
                            "💸 Miqdor: %,d UZS\n" +
                            "💸 Miqdor: %,d RUB\n" +
                            "💳 Karta raqami: `%s`\n" +
                            "🔐 Admin kartasi: `%s`\n" +
                            "📅 [%s] ",
                    request.getId(),
                    chatId, number,  // chatId as label, phone as target
                    request.getPlatform(),
                    request.getPlatformUserId(),
                    request.getUniqueAmount(),
                    rubAmount,
                    request.getCardNumber(),
                    adminCard.getCardNumber(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );


            adminLogBotService.sendLog(logMessage);
            messageSender.sendMessage(requestId, logMessage);
        }

        sessionService.clearMessageIds(requestId);
        sessionService.setUserData(requestId, PAYMENT_ATTEMPTS_KEY, "0");
        sendMainMenu(requestId);
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

    private BalanceLimit transferToPlatform(HizmatRequest request, AdminCard adminCard) {
        String platformName = request.getPlatform();
        Platform platform = platformRepository.findByName(platformName)
                .orElseThrow(() -> new IllegalStateException("Platform not found: " + platformName));

        String hash = platform.getApiKey();
        String cashierPass = platform.getPassword();
        String cashdeskId = platform.getWorkplaceId();
        String userId = request.getPlatformUserId();
        long amount = request.getUniqueAmount();
        ExchangeRate latest = exchangeRateRepository.findLatest()
                .orElseThrow(() -> new RuntimeException("No exchange rate found in the database"));
        if (request.getCurrency().equals(Currency.RUB)) {
            amount = BigDecimal.valueOf(request.getUniqueAmount())
                    .multiply(latest.getUzsToRub())
                    .longValue() / 1000;
        }
        String lng = "ru";

        if (hash == null || cashierPass == null || cashdeskId == null ||
                hash.isEmpty() || cashierPass.isEmpty() || cashdeskId.isEmpty()) {
            logger.error("Invalid platform credentials for platform {}: hash={}, cashierPass={}, cashdeskId={}",
                    platformName, hash, cashierPass, cashdeskId);
            messageSender.sendMessage(request.getChatId(), "Platform sozlamalarida xato. Administrator bilan bog‘laning.");
            return null;
        }

        String confirm = DigestUtils.md5DigestAsHex((userId + ":" + hash).getBytes(StandardCharsets.UTF_8));
        String sha256Input1 = "hash=" + hash + "&lng=" + lng + "&userid=" + userId;
        String sha256Result1 = sha256Hex(sha256Input1);
        String md5Input = "summa=" + amount + "&cashierpass=" + cashierPass + "&cashdeskid=" + cashdeskId;
        String md5Result = DigestUtils.md5DigestAsHex(md5Input.getBytes(StandardCharsets.UTF_8));
        String finalSignature = sha256Hex(sha256Result1 + md5Result);

        String apiUrl = String.format("https://partners.servcul.com/CashdeskBotAPI/Deposit/%s/Add", userId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("sign", finalSignature);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("cashdeskId", Integer.parseInt(cashdeskId));
        body.put("lng", lng);
        body.put("summa", amount);
        body.put("confirm", confirm);
        body.put("cardNumber", adminCard.getCardNumber());

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            Object successObj = responseBody != null ? responseBody.get("success") : null;
            if (successObj == null && responseBody != null) {
                successObj = responseBody.get("Success");
            }

            if (response.getStatusCode().is2xxSuccessful() && Boolean.TRUE.equals(successObj)) {
                logger.info("✅ Transfer successful for chatId {}, userId: {}, amount: {}, platform: {}",
                        request.getChatId(), userId, amount, platformName);

                return getCashdeskBalance(hash,cashierPass,cashdeskId);
            }

            String errorMsg = responseBody != null && responseBody.get("Message") != null
                    ? responseBody.get("Message").toString()
                    : "Platformdan noto‘g‘ri javob qaytdi.";

            logger.error("❌ Transfer failed for chatId {}, userId: {}, response: {}", request.getChatId(), userId, responseBody);
            messageSender.sendMessage(request.getChatId(), "❌ Transfer xatosi: " + errorMsg);
            adminLogBotService.sendToAdmins("❌ Transfer xatosi: " + errorMsg);
            return null;

        } catch (HttpClientErrorException e) {
            logger.error("API error for transfer, chatId {}, userId {}: {}", request.getChatId(), userId, e.getMessage());
            messageSender.sendMessage(request.getChatId(), "API xatosi: Auth yoki sign noto‘g‘ri.");
            return null;

        } catch (Exception e) {
            logger.error("Unexpected error during transfer for chatId {}: {}", request.getChatId(), e.getMessage());
            messageSender.sendMessage(request.getChatId(), "Noma’lum xatolik. Qayta urinib ko‘ring.");
            return null;
        }
    }

    private void sendPaymentInstruction(Long chatId) {
        HizmatRequest request = requestRepository.findByChatIdAndStatus(chatId, RequestStatus.PENDING_PAYMENT)
                .orElseThrow(() -> new IllegalStateException("Pending payment request not found for chatId: " + chatId));
        AdminCard adminCard = adminCardRepository.findById(request.getAdminCardId())
                .orElseThrow(() -> new IllegalStateException("Admin card not found: " + request.getAdminCardId()));

        int attempts = Integer.parseInt(sessionService.getUserData(chatId, PAYMENT_ATTEMPTS_KEY, "0"));
        ExchangeRate latest = exchangeRateRepository.findLatest()
                .orElseThrow(() -> new RuntimeException("No exchange rate found in the database"));

        String messageText;
        if (request.getCurrency().equals(Currency.RUB)) {
            long amount = BigDecimal.valueOf(request.getUniqueAmount())
                    .multiply(latest.getUzsToRub())
                    .longValue() / 1000;
            messageText = String.format(
                    "Diqqat! Aniq %,d UZS o‘tkazing, bu sizning summangizdan farq qiladi!\n" +
                            "Karta: `%s`\n" +
                            "BUNI O‘TKAZMANG: %,d UZS ❌\n" +
                            "BUNI O‘TKAZING: %,d UZS ✅\n\n" +
                            "1000 UZS ----> %s RUB kurs narxida\n\n" +
                            "Sizga %s RUB tushadi \n\n" +
                            "✅ To‘lovni amalga oshirganingizdan so‘ng, 15 daqiqa ichida '%s' tugmasini bosing!\n" +
                            "⛔️ Agar xato summa o‘tkazsangiz, pul 15 ish kuni ichida qaytariladi yoki yo‘qoladi!\n\n" +
                            "Agar to‘lov darhol amalga oshmasa, biroz kuting va yana tugmani bosing.\n" +
                            "TG_ID: %d #%d",
                    request.getUniqueAmount(), adminCard.getCardNumber(),
                    request.getAmount(), request.getUniqueAmount(), latest.getUzsToRub(), amount,
                    attempts >= 2 ? "Skrinshot yuborish" : "Tasdiqlash", chatId, request.getId());
        } else {
            messageText = String.format(
                    "Diqqat! Aniq %,d UZS o‘tkazing, bu sizning summangizdan farq qiladi!\n" +
                            "Karta:   `%s`\n" +
                            "BUNI O‘TKAZMANG: %,d UZS ❌\n" +
                            "BUNI O‘TKAZING: %,d UZS ✅\n\n" +
                            "✅ To‘lovni amalga oshirganingizdan so‘ng, 15 daqiqa ichida '%s' tugmasini bosing!\n" +
                            "⛔️ Agar xato summa o‘tkazsangiz, pul 15 ish kuni ichida qaytariladi yoki yo‘qoladi!\n\n" +
                            "Agar to‘lov darhol amalga oshmasa, biroz kuting va yana tugmani bosing.\n" +
                            "TG_ID: %d #%d",
                    request.getUniqueAmount(), adminCard.getCardNumber(),
                    request.getAmount(), request.getUniqueAmount(),
                    attempts >= 2 ? "Skrinshot yuborish" : "Tasdiqlash", chatId, request.getId());
        }
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText);
        message.setReplyMarkup(createPaymentConfirmKeyboard(attempts));
        messageSender.sendMessage(message, chatId);

        List<Integer> messageIds = sessionService.getMessageIds(chatId);
        Integer messageId = messageIds.isEmpty() ? null : messageIds.get(messageIds.size() - 1);
        if (messageId != null) {
            sessionService.setUserData(chatId, PAYMENT_MESSAGE_KEY, String.valueOf(messageId));
        } else {
            logger.error("Failed to retrieve messageId for chatId {}", chatId);
            messageSender.sendMessage(chatId, "Xatolik: Xabar ID si topilmadi. Iltimos, qayta urinib ko‘ring.");
        }
    }

    private long generateUniqueAmount(long baseAmount) {
        Random random = new Random();
        int randomDigits = random.nextInt(100);
        return baseAmount + randomDigits;
    }

    private void sendPlatformSelection(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Platformani tanlang:");
        message.setReplyMarkup(createPlatformKeyboard());
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
            message.setText("Iltimos, " + platform + " uchun ID kiriting:");
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
        sendMessageWithNavigation(chatId, "Bu ID bo‘yicha foydalanuvchi topilmadi. Iltimos, boshqa ID kiriting:");
    }

    private void sendCardInput(Long chatId, String fullName) {
        List<HizmatRequest> recentRequests = requestRepository.findLatestUniqueCardNumbersByChatId(chatId);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
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

    private void sendAmountInput(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Summani kiriting (10 000 - 10 000 000 so‘m):");
        message.setReplyMarkup(createAmountKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private void sendMainMenu(Long chatId) {
        sessionService.clearSession(chatId);
        sessionService.setUserState(chatId, "MAIN_MENU");
        sessionService.setUserData(chatId, PAYMENT_ATTEMPTS_KEY, "0");
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Xush kelibsiz! Operatsiyani tanlang:");
        message.setReplyMarkup(createMainMenuKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private void sendMessageWithNavigation(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(createNavigationKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private InlineKeyboardMarkup createPlatformKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<Platform> uzsPlatforms = platformRepository.findByCurrency(Currency.UZS);
        List<Platform> rubPlatforms = platformRepository.findByCurrency(Currency.RUB);

        int maxRows = Math.max(uzsPlatforms.size(), rubPlatforms.size());
        for (int i = 0; i < maxRows; i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            if (i < uzsPlatforms.size()) {
                Platform uzsPlatform = uzsPlatforms.get(i);
                row.add(createButton("🇺🇿 " + uzsPlatform.getName(), "TOPUP_PLATFORM:" + uzsPlatform.getName()));
            }
            if (i < rubPlatforms.size()) {
                Platform rubPlatform = rubPlatforms.get(i);
                row.add(createButton("🇷🇺 " + rubPlatform.getName(), "TOPUP_PLATFORM:" + rubPlatform.getName()));
            } else {
                i++;
                if (i < uzsPlatforms.size() && i < maxRows) {
                    Platform uzsPlatform = uzsPlatforms.get(i);
                    row.add(createButton("🇺🇿 " + uzsPlatform.getName(), "TOPUP_PLATFORM:" + uzsPlatform.getName()));
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
                    .map(id -> createButton("ID: " + id, "TOPUP_PAST_ID:" + id))
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
            List<String> distinctCards = recentRequests.stream()
                    .map(HizmatRequest::getCardNumber)
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(2)
                    .collect(Collectors.toList());

            for (String card : distinctCards) {
                InlineKeyboardButton button = createButton(maskCard(card), "TOPUP_PAST_CARD:" + card);
                rows.add(Collections.singletonList(button));
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
                createButton("✅ To‘g‘ri", "TOPUP_APPROVE_USER"),
                createButton("❌ Noto‘g‘ri", "TOPUP_REJECT_USER")
        ));
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createAmountKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("10,000 so‘m", "TOPUP_AMOUNT_10000"),
                createButton("10,000,000 so‘m", "TOPUP_AMOUNT_10000000")
        ));
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createConfirmKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton("✅ To‘ldirish", "TOPUP_CONFIRM")));
        rows.add(createNavigationButtons());
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createPaymentConfirmKeyboard(int attempts) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        String buttonText = attempts >= 2 ? "📷 Skrinshot yuborish" : "✅ Tasdiqlash";
        String callbackData = attempts >= 2 ? "TOPUP_SEND_SCREENSHOT" : "TOPUP_PAYMENT_CONFIRM";
        rows.add(List.of(createButton(buttonText, callbackData)));
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

    String maskCard(String card) {
        String cleanCard = card.replaceAll("\\s+", "");
        if (cleanCard.length() != 16) return "INVALID CARD";
        return cleanCard.substring(0, 4) + " **** **** " + cleanCard.substring(12);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return DatatypeConverter.printHexBinary(hash).toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }
}