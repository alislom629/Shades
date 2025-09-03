package com.example.shade.bot;

import com.example.shade.model.*;
import com.example.shade.repository.BlockedUserRepository;
import com.example.shade.repository.ReferralRepository;
import com.example.shade.repository.UserBalanceRepository;
import com.example.shade.repository.UserRepository;
import com.example.shade.service.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ShadePaymentBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(ShadePaymentBot.class);
    private final TopUpService topUpService;
    private final WithdrawService withdrawService;
    private final BonusService bonusService;
    private final ContactService contactService;
    private final ReferralRepository referralRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final MessageSender messageSender;
    private final UserSessionService sessionService;
    private final AdminLogBotService adminLogBotService;
    private final UserBalanceRepository userBalanceRepository;
    private final FeatureService featureService;
    private final LanguageSessionService languageSessionService;
    private final UserRepository userRepository;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
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
        } catch (TelegramApiException e) {
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
            Long chatId = null;
            if (update.hasMessage()) {
                chatId = update.getMessage().getChatId();
            } else if (update.hasCallbackQuery()) {
                chatId = update.getCallbackQuery().getMessage().getChatId();
            } else if (update.hasMyChatMember()) {
                chatId = update.getMyChatMember().getChat().getId();
            }
            if (chatId == null) {
                logger.warn("No chatId found in update: {}", update);
                return;
            }

            // Handle referral for /start ref_
            if (update.hasMessage() && update.getMessage().hasText() && update.getMessage().getText().startsWith("/start ref_")) {
                String messageText = update.getMessage().getText();
                String referrerIdStr = messageText.substring("/start ref_".length());
                try {
                    Long referrerChatId = Long.parseLong(referrerIdStr);
                    if (!referrerChatId.equals(chatId)) {
                        if (referralRepository.findByReferredChatId(chatId).isEmpty()) {
                            Referral referral = new Referral();
                            referral.setReferrerChatId(referrerChatId);
                            referral.setReferredChatId(chatId);
                            referralRepository.save(referral);
                            logger.info("Referral created: referrerChatId={}, referredChatId={}", referrerChatId, chatId);
                        } else {
                            logger.info("Referral not created: user {} already has a referral", chatId);
                        }
                    } else {
                        logger.warn("Self-referral attempt by chatId: {}", chatId);
                    }
                } catch (NumberFormatException e) {
                    logger.error("Invalid referrer ID format: {}", referrerIdStr);
                }
            }

            // Check if user is blocked or needs to share phone number
            BlockedUser user = blockedUserRepository.findById(chatId).orElse(null);
            if (user != null && "BLOCKED".equals(user.getPhoneNumber())) {
                logger.info("Blocked user {} attempted to interact", chatId);
                return;
            }

            // Handle phone number submission
            if (update.hasMessage() && update.getMessage().hasContact()) {
                String receivedPhoneNumber = update.getMessage().getContact().getPhoneNumber();
                if (user == null) {
                    user = BlockedUser.builder().chatId(chatId).build();
                }
                if (!receivedPhoneNumber.startsWith("+")) {
                    receivedPhoneNumber = "+" + receivedPhoneNumber;
                }
                user.setPhoneNumber(receivedPhoneNumber);
                blockedUserRepository.save(user);
                userBalanceRepository.save(UserBalance.builder().chatId(chatId).tickets(0L).balance(BigDecimal.ZERO).build());

                logger.info("Phone number saved for chatId {}: {}", chatId, receivedPhoneNumber);
                sessionService.clearSession(chatId);

                SendMessage removeKeyboardMessage = new SendMessage();
                removeKeyboardMessage.setChatId(chatId);
                removeKeyboardMessage.setText("Telefon raqamingiz qabul qilindi.");
                removeKeyboardMessage.setReplyMarkup(new ReplyKeyboardRemove(true));
                messageSender.sendMessage(removeKeyboardMessage, chatId);

                sendMainMenu(chatId, true);
                return;
            }

            // If user hasn't shared phone number, show menu link
            if (user == null || user.getPhoneNumber() == null) {
                if (user == null) {
                    user = BlockedUser.builder().chatId(chatId).build();
                    blockedUserRepository.save(user);
                }
                sessionService.setUserState(chatId, "AWAITING_PHONE_NUMBER");
                sendPhoneNumberRequest(chatId);
                return;
            }

            // Handle other messages and callbacks
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update.getMessage().getText(), chatId);
            } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
                String state = sessionService.getUserState(chatId);
                if (!"TOPUP_AWAITING_SCREENSHOT".equals(state)) {
                    logger.warn("Photo received in wrong state for chatId {}: {}", chatId, state);
                    messageSender.sendMessage(chatId, "Iltimos, avval to‘lov so‘rovini yakunlang.");
                    return;
                }
                if (!featureService.canPerformTopUp()) {
                    messageSender.sendMessage(chatId, "Iltimos, 5 daqiqadan so‘ng urinib kuring");
                    return;
                }
                PhotoSize photo = update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1);
                if (photo.getFileId() == null || photo.getFileId().isEmpty()) {
                    logger.error("Invalid photo file ID for chatId {}", chatId);
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setText("Xatolik: Yuklangan rasm fayli noto‘g‘ri. Iltimos, qayta urinib ko‘ring.");
                    message.setReplyMarkup(createBonusMenuKeyboard());
                    messageSender.sendMessage(message, chatId);
                    return;
                }
                GetFile getFile = new GetFile();
                getFile.setFileId(photo.getFileId());
                File downloadedFile = null;
                try {
                    org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);
                    downloadedFile = downloadFile(file);
                    SendPhoto sendPhoto = new SendPhoto();
                    sendPhoto.setPhoto(new InputFile(downloadedFile));
                    sendPhoto.setCaption("Screenshot from user: " + chatId);
                    sendPhoto.setReplyMarkup(createScreenshotMarkup(chatId));
                    adminLogBotService.sendScreenshotRequest(sendPhoto, chatId);
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setText("Rasm yuborildi. Admin tasdiqlashini kuting.");
                    message.setReplyMarkup(createBonusMenuKeyboard());
                    messageSender.sendMessage(message, chatId);
                } catch (TelegramApiException e) {
                    logger.error("Failed to process photo for chatId {}: {}", chatId, e.getMessage());
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setText("Xatolik: Rasmni yuborishda xato yuz berdi. Iltimos, qayta urinib ko‘ring.");
                    message.setReplyMarkup(createBonusMenuKeyboard());
                    messageSender.sendMessage(message, chatId);
                } finally {
                    if (downloadedFile != null && downloadedFile.exists()) {
                        if (downloadedFile.delete()) {
                            logger.info("Deleted temporary file for chatId {}", chatId);
                        } else {
                            logger.warn("Failed to delete temporary file for chatId {}", chatId);
                        }
                    }
                }
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery().getData(), chatId);
            }
        } catch (Exception e) {
            logger.error("Error processing update: {}", update, e);
        }
    }

    private void sendPhoneNumberRequest(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Iltimos, telefon raqamingizni yuboring yoki menyuga o‘tish uchun quyidagi tugmani bosing:");
        message.setReplyMarkup(createPhoneNumberKeyboard());
        messageSender.sendMessage(message, chatId);
    }

    private ReplyKeyboardMarkup createPhoneNumberKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        KeyboardButton contactButton = new KeyboardButton();
        contactButton.setText("📞 Telefon raqamni yuborish");
        contactButton.setRequestContact(true);
        row1.add(contactButton);
        rows.add(row1);
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createScreenshotMarkup(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("✅ Approve", "SCREENSHOT_APPROVE_CHAT:" + chatId),
                createButton("❌ Reject", "SCREENSHOT_REJECT_CHAT:" + chatId)
        ));
        markup.setKeyboard(rows);
        return markup;
    }

    private void handleTextMessage(String messageText, Long chatId) {
        logger.info("Processing message from chatId {}: {}", chatId, messageText);
        String state = sessionService.getUserState(chatId);
        if (!languageSessionService.checkUserUserSession(chatId)) {
            Language userLanguage = userRepository.findByChatId(chatId)
                    .map(User::getLanguage)
                    .orElse(Language.UZ);
            languageSessionService.addUserLanguageSession(chatId, userLanguage);
        }
        if ("AWAITING_PHONE_NUMBER".equals(state)) {
            if (messageText.equals("🏠 Asosiy menyu")) {
                BlockedUser user = blockedUserRepository.findById(chatId).orElse(null);
                if (user != null && user.getPhoneNumber() != null && !"BLOCKED".equals(user.getPhoneNumber())) {
                    sessionService.clearSession(chatId);
                    sendMainMenu(chatId, true);
                } else {
                    messageSender.sendMessage(chatId, "Iltimos, avval telefon raqamingizni yuboring.");
                    sendPhoneNumberRequest(chatId);
                }
                return;
            }
            messageSender.sendMessage(chatId, "Iltimos, telefon raqamingizni yuborish uchun tugmani bosing yoki menyuga o‘tish uchun 'Asosiy menyu' ni tanlang.");
            sendPhoneNumberRequest(chatId);
            return;
        }
        if (messageText.equals("/start")) {
            sendMainMenu(chatId, true);
        } else if (messageText.equals("/topup")) {
            if (!featureService.canPerformTopUp()) {
                messageSender.sendMessage(chatId, "Iltimos, 5 daqiqadan so‘ng urinib kuring");
                return;
            }
            messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
            topUpService.startTopUp(chatId);
        } else if (messageText.equals("/withdraw")) {
            if (!featureService.canPerformWithdraw()) {
                messageSender.sendMessage(chatId, "Iltimos, 5 daqiqadan so‘ng urinib kuring");
                return;
            }
            messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
            withdrawService.startWithdrawal(chatId);
        } else if (messageText.equals("/bonus")) {
            if (!featureService.canPerformBonus()) {
                messageSender.sendMessage(chatId, "Iltimos, 5 daqiqadan so‘ng urinib kuring");
                return;
            }
            messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
            bonusService.startBonus(chatId);
        } else if (state != null && state.startsWith("TOPUP_")) {
            topUpService.handleTextInput(chatId, messageText);
        } else if (state != null && state.startsWith("WITHDRAW_")) {
            withdrawService.handleTextInput(chatId, messageText);
        } else if (state != null && state.startsWith("BONUS_")) {
            bonusService.handleTextInput(chatId, messageText);
        } else {
            messageSender.sendMessage(chatId, "Iltimos, menyudan operatsiyani tanlang.");
            sendMainMenu(chatId, true);
        }
    }

    private void handleCallbackQuery(String callback, Long chatId) {
        logger.info("Processing callback from chatId {}: {}", chatId, callback);
        try {
            switch (callback) {
                case "TOPUP" -> {
                    if (!featureService.canPerformTopUp()) {
                        messageSender.sendMessage(chatId, "Iltimos, 5 daqiqadan so‘ng urinib kuring");
                        return;
                    }
                    messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
                    topUpService.startTopUp(chatId);
                }
                case "WITHDRAW" -> {
                    if (!featureService.canPerformWithdraw()) {
                        messageSender.sendMessage(chatId, "Iltimos, 5 daqiqadan so‘ng urinib kuring");
                        return;
                    }
                    messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
                    withdrawService.startWithdrawal(chatId);
                }
                case "BONUS" -> {
                    if (!featureService.canPerformBonus()) {
                        messageSender.sendMessage(chatId, "Iltimos, 5 daqiqadan so‘ng urinib kuring");
                        return;
                    }
                    messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
                    bonusService.startBonus(chatId);
                }
                case "CONTACT" -> {
                    messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
                    contactService.handleContact(chatId);
                }
                case "HOME" -> sendMainMenu(chatId, true);
                case "BACK" -> {
                    messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "BACK");
                    String state = sessionService.getUserState(chatId);
                    if (state != null && state.startsWith("TOPUP_")) {
                        topUpService.handleBack(chatId);
                    } else if (state != null && state.startsWith("WITHDRAW_")) {
                        withdrawService.handleBack(chatId);
                    } else if (state != null && state.startsWith("BONUS_")) {
                        bonusService.handleBack(chatId);
                    } else {
                        sendMainMenu(chatId, true);
                    }
                }
                default -> {
                    if (callback.startsWith("TOPUP_")) {
                        if (!featureService.canPerformTopUp()) {
                            messageSender.sendMessage(chatId, "Iltimos, 5 daqiqadan so‘ng urinib kuring");
                            return;
                        }
                        if (callback.equals("TOPUP_PAYMENT_CONFIRM")) {
                            List<Integer> messageIds = sessionService.getMessageIds(chatId);
                            if (!messageIds.isEmpty()) {
                                messageSender.editMessageToRemoveButtons(chatId, messageIds.get(messageIds.size() - 1));
                            }
                        } else {
                            messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
                        }
                        topUpService.handleCallback(chatId, callback);
                    } else if (callback.startsWith("WITHDRAW_")) {
                        if (!featureService.canPerformWithdraw()) {
                            messageSender.sendMessage(chatId, "Iltimos, 5 daqiqadan so‘ng urinib kuring");
                            return;
                        }
                        messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
                        withdrawService.handleCallback(chatId, callback);
                    } else if (callback.startsWith("BONUS_")) {
                        if (!featureService.canPerformBonus()) {
                            messageSender.sendMessage(chatId, "Iltimos, 5 daqiqadan so‘ng urinib kuring");
                            return;
                        }
                        messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
                        bonusService.handleCallback(chatId, callback);
                    } else {
                        logger.warn("Unknown callback for chatId {}: {}", chatId, callback);
                        messageSender.sendMessage(chatId, "Noto‘g‘ri buyruq. Iltimos, qayta urinib ko‘ring.");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error handling callback {} for chatId {}: {}", callback, chatId, e.getMessage());
            messageSender.sendMessage(chatId, "Xatolik yuz berdi. Iltimos, qayta urinib ko‘ring.");
        }
    }

    public void sendMainMenu(Long chatId, boolean clearSession) {
        if (clearSession) {
            messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "HOME");
            sessionService.clearSession(chatId);
        }

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
}