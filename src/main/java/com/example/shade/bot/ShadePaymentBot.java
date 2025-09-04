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
            if (!languageSessionService.checkUserUserSession(chatId)) {
                User userLanguage = userRepository.findByChatId(chatId).orElse(null);
                if (userLanguage == null) {
                    if (update.hasMessage() && update.getMessage().hasText() && update.getMessage().getText().equals("/start")) {
                        sessionService.setUserState(chatId, "AWAITING_LANGUAGE");
                        sendLanguageSelection(chatId);
                        return;
                    }
                    if (update.hasCallbackQuery() && sessionService.getUserState(chatId).equals("AWAITING_LANGUAGE")) {
                        handleLanguageSelection(update.getCallbackQuery().getData(), chatId);
                        return;
                    }
                    return;
                }
                languageSessionService.addUserLanguageSession(chatId, userLanguage.getLanguage());
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
                removeKeyboardMessage.setText(languageSessionService.getTranslation(chatId, "message.phone_number_recieved"));
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
                    messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.please_confirm_payment_transaction"));
                    return;
                }
                if (!featureService.canPerformTopUp()) {
                    messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.feature_unavailable"));
                    return;
                }
                PhotoSize photo = update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1);
                if (photo.getFileId() == null || photo.getFileId().isEmpty()) {
                    logger.error("Invalid photo file ID for chatId {}", chatId);
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setText(languageSessionService.getTranslation(chatId, "message.invalid_photo_file"));
                    message.setReplyMarkup(createBonusMenuKeyboard(chatId));
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
                    sendPhoto.setCaption("Screenshot from user: " + chatId); // Admin message, not translated
                    sendPhoto.setReplyMarkup(createScreenshotMarkup(chatId));
                    adminLogBotService.sendScreenshotRequest(sendPhoto, chatId);
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setText(languageSessionService.getTranslation(chatId, "message.photo_sent_confirmation"));
                    message.setReplyMarkup(createBonusMenuKeyboard(chatId));
                    messageSender.sendMessage(message, chatId);
                } catch (TelegramApiException e) {
                    logger.error("Failed to process photo for chatId {}: {}", chatId, e.getMessage());
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setText(languageSessionService.getTranslation(chatId, "message.photo_processing_error"));
                    message.setReplyMarkup(createBonusMenuKeyboard(chatId));
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
        message.setText(languageSessionService.getTranslation(chatId, "message.phone_number_request"));
        message.setReplyMarkup(createPhoneNumberKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    private ReplyKeyboardMarkup createPhoneNumberKeyboard(Long chatId) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        KeyboardButton contactButton = new KeyboardButton();
        contactButton.setText(languageSessionService.getTranslation(chatId, "button.phone_number_submit"));
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
        if ("AWAITING_PHONE_NUMBER".equals(state)) {
            if (messageText.equals("🏠 Asosiy menyu")) {
                BlockedUser user = blockedUserRepository.findById(chatId).orElse(null);
                if (user != null && user.getPhoneNumber() != null && !"BLOCKED".equals(user.getPhoneNumber())) {
                    sessionService.clearSession(chatId);
                    sendMainMenu(chatId, true);
                } else {
                    messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.awaiting_phone_number"));
                    sendPhoneNumberRequest(chatId);
                }
                return;
            }
            messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.phone_number_prompt"));
            sendPhoneNumberRequest(chatId);
            return;
        }
        if (messageText.equals("/start")) {
            sendMainMenu(chatId, true);
        } else if (messageText.equals("/topup")) {
            if (!featureService.canPerformTopUp()) {
                messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.feature_unavailable"));
                return;
            }
            messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
            topUpService.startTopUp(chatId);
        } else if (messageText.equals("/withdraw")) {
            if (!featureService.canPerformWithdraw()) {
                messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.feature_unavailable"));
                return;
            }
            messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
            withdrawService.startWithdrawal(chatId);
        } else if (messageText.equals("/bonus")) {
            if (!featureService.canPerformBonus()) {
                messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.feature_unavailable"));
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
            messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.invalid_command"));
            sendMainMenu(chatId, true);
        }
    }

    private void handleCallbackQuery(String callback, Long chatId) {
        logger.info("Processing callback from chatId {}: {}", chatId, callback);
        try {
            switch (callback) {
                case "TOPUP" -> {
                    if (!featureService.canPerformTopUp()) {
                        messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.feature_unavailable"));
                        return;
                    }
                    messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
                    topUpService.startTopUp(chatId);
                }
                case "WITHDRAW" -> {
                    if (!featureService.canPerformWithdraw()) {
                        messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.feature_unavailable"));
                        return;
                    }
                    messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
                    withdrawService.startWithdrawal(chatId);
                }
                case "BONUS" -> {
                    if (!featureService.canPerformBonus()) {
                        messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.feature_unavailable"));
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
                            messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.feature_unavailable"));
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
                            messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.feature_unavailable"));
                            return;
                        }
                        messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
                        withdrawService.handleCallback(chatId, callback);
                    } else if (callback.startsWith("BONUS_")) {
                        if (!featureService.canPerformBonus()) {
                            messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.feature_unavailable"));
                            return;
                        }
                        messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "OPEN");
                        bonusService.handleCallback(chatId, callback);
                    } else {
                        logger.warn("Unknown callback for chatId {}: {}", chatId, callback);
                        messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.unknown_callback"));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error handling callback {} for chatId {}: {}", callback, chatId, e.getMessage());
            messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.callback_error"));
        }
    }

    public void sendMainMenu(Long chatId, boolean clearSession) {
        if (clearSession) {
            messageSender.animateAndDeleteMessages(chatId, sessionService.getMessageIds(chatId), "HOME");
            sessionService.clearSession(chatId);
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(languageSessionService.getTranslation(chatId, "message.main_menu_welcome"));
        message.setReplyMarkup(createMainMenuKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    private InlineKeyboardMarkup createMainMenuKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "button.topup"), "TOPUP")));
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "button.withdraw"), "WITHDRAW")));
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "button.bonus"), "BONUS")));
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "button.contact"), "CONTACT")));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createBonusMenuKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private List<InlineKeyboardButton> createNavigationButtons(Long chatId) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        buttons.add(createButton(languageSessionService.getTranslation(chatId, "button.back"), "BACK"));
        buttons.add(createButton(languageSessionService.getTranslation(chatId, "button.home"), "HOME"));
        return buttons;
    }

    private void sendLanguageSelection(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Выберите язык / Tilni tanlang:");
        message.setReplyMarkup(createLanguageKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    private InlineKeyboardMarkup createLanguageKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton(languageSessionService.getTranslation(chatId, "button.language_ru"), "LANG_RU"),
                createButton(languageSessionService.getTranslation(chatId, "button.language_uz"), "LANG_UZ")
        ));
        markup.setKeyboard(rows);
        return markup;
    }

    private void handleLanguageSelection(String callback, Long chatId) {
        User user = userRepository.findByChatId(chatId).orElse(new User());
        user.setChatId(chatId);
        if ("LANG_RU".equals(callback)) {
            user.setLanguage(Language.RU);
        } else if ("LANG_UZ".equals(callback)) {
            user.setLanguage(Language.UZ);
        } else {
            logger.warn("Invalid language callback for chatId {}: {}", chatId, callback);
            return;
        }
        userRepository.save(user);
        logger.info("Language set for chatId {}: {}", chatId, user.getLanguage());
        sessionService.clearSession(chatId);
        languageSessionService.addUserLanguageSession(chatId, user.getLanguage());

        BlockedUser blockedUser = blockedUserRepository.findById(chatId).orElse(null);
        if (blockedUser == null || blockedUser.getPhoneNumber() == null) {
            sendPhoneNumberRequest(chatId);
        } else {
            sendMainMenu(chatId, true);
        }
    }

    private InlineKeyboardButton createButton(String text, String callback) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callback);
        return button;
    }
}