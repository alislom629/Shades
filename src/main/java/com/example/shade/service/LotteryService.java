package com.example.shade.service;

import com.example.shade.bot.AdminTelegramMessageSender;
import com.example.shade.bot.MessageSender;
import com.example.shade.model.HizmatRequest;
import com.example.shade.model.LotteryPrize;
import com.example.shade.model.RequestStatus;
import com.example.shade.model.UserBalance;
import com.example.shade.repository.BlockedUserRepository;
import com.example.shade.repository.HizmatRequestRepository;
import com.example.shade.repository.LotteryPrizeRepository;
import com.example.shade.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LotteryService {
    private static final Logger logger = LoggerFactory.getLogger(LotteryService.class);
    private final UserBalanceRepository userBalanceRepository;
    private final LotteryPrizeRepository lotteryPrizeRepository;
    private final HizmatRequestRepository hizmatRequestRepository;
    private final LottoBotService lottoBotService;
    private final BlockedUserRepository blockedUserRepository;
    private final MessageSender messageSender;
    private final AdminLogBotService adminLogBotService;
    private final Random random = new Random();
    private static final long MINIMUM_TICKETS = 36L;
    private static final long MAXIMUM_TICKETS = 100L;

    public void awardTickets(Long chatId, Long amount) {
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElse(UserBalance.builder()
                        .chatId(chatId)
                        .tickets(0L)
                        .balance(BigDecimal.ZERO)
                        .build());
        Long tickets = amount ;
        balance.setTickets(balance.getTickets() + tickets);
        userBalanceRepository.save(balance);
        logger.info("Awarded {} tickets to chatId {}", tickets, chatId);
    }

    @Transactional
    public void deleteTickets(Long chatId) {
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("User balance not found: " + chatId));
        balance.setTickets(0L);
        userBalanceRepository.save(balance);
        logger.info("Deleted all tickets for chatId {}", chatId);
    }

    @Transactional
    public void deleteBalance(Long chatId) {
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("User balance not found: " + chatId));
        balance.setBalance(BigDecimal.ZERO);
        userBalanceRepository.save(balance);
        logger.info("Reset balance for chatId {}", chatId);
    }

    @Transactional
    public Map<Long, BigDecimal> playLotteryWithDetails(Long chatId, Long numberOfPlays) {
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("User balance not found: " + chatId));
        if (balance.getTickets() < numberOfPlays || numberOfPlays < MINIMUM_TICKETS || numberOfPlays > MAXIMUM_TICKETS) {
            throw new IllegalStateException(String.format("Invalid ticket count: %d. Must be between %d and %d", balance.getTickets(), MINIMUM_TICKETS, MAXIMUM_TICKETS));
        }
        List<LotteryPrize> prizes = lotteryPrizeRepository.findAll();
        if (prizes.isEmpty()) {
            throw new IllegalStateException("No lottery prizes configured");
        }

        // Filter prizes with non-zero amount and available count
        List<LotteryPrize> validPrizes = prizes.stream()
                .filter(prize -> prize.getAmount().compareTo(BigDecimal.ZERO) > 0 && prize.getNumberOfPrize() > 0)
                .collect(Collectors.toList());
        if (validPrizes.isEmpty()) {
            logger.error("No valid prizes with non-zero amount and available count for chatId {}.", chatId);
            throw new IllegalStateException("Yaroqli lotereya sovrinlari mavjud emas");
        }

        Map<Long, BigDecimal> winnings = new HashMap<>();
        // Generate ticket IDs from 1 to numberOfPlays
        List<Long> ticketIds = new ArrayList<>();
        for (long i = 1; i <= numberOfPlays; i++) {
            ticketIds.add(i);
        }

        for (long i = 0; i < numberOfPlays && !ticketIds.isEmpty(); i++) {
            // Check if any prizes are still available
            validPrizes = validPrizes.stream()
                    .filter(prize -> prize.getNumberOfPrize() > 0)
                    .collect(Collectors.toList());
            if (validPrizes.isEmpty()) {
                logger.warn("No prizes left for chatId {}. Breaking loop.", chatId);
                break;
            }

            // Select a random ticket ID
            Long selectedTicket = ticketIds.get(random.nextInt(ticketIds.size()));

            // Determine prize (weighted by numberOfPrize)
            int totalPrizeCount = validPrizes.stream().mapToInt(LotteryPrize::getNumberOfPrize).sum();
            int randomPrizeValue = random.nextInt(totalPrizeCount);
            int currentPrizeCount = 0;
            LotteryPrize selectedPrize = validPrizes.get(0); // Default to first valid prize
            for (LotteryPrize prize : validPrizes) {
                currentPrizeCount += prize.getNumberOfPrize();
                if (randomPrizeValue < currentPrizeCount) {
                    selectedPrize = prize;
                    break;
                }
            }

            BigDecimal winAmount = selectedPrize.getAmount();
            selectedPrize.setNumberOfPrize(selectedPrize.getNumberOfPrize() - 1); // Decrease prize count
            lotteryPrizeRepository.save(selectedPrize); // Persist updated prize count
            winnings.put(selectedTicket, winAmount);
            ticketIds.remove(selectedTicket); // Remove played ticket
        }

        // Update balance
        BigDecimal totalWinnings = winnings.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        balance.setTickets(balance.getTickets() - numberOfPlays);
        balance.setBalance(balance.getBalance().add(totalWinnings));
        userBalanceRepository.save(balance);
        if (totalWinnings.longValue()>=20000){
            lottoBotService.logWin(numberOfPlays,chatId,totalWinnings.longValue());
        }
        logger.info("Played {} tickets for chatId {}, won {} times with total {} UZS", numberOfPlays, chatId, winnings.size(), totalWinnings);
        return winnings;
    }

    @Transactional
    public Map<Long, BigDecimal> playLotteryWithDetailsLottoBot(Long chatId, Long numberOfPlays) {
        List<LotteryPrize> prizes = lotteryPrizeRepository.findAll();
        if (prizes.isEmpty()) {
            throw new IllegalStateException("No lottery prizes configured");
        }

        // Filter prizes with non-zero amount and available count
        List<LotteryPrize> validPrizes = prizes.stream()
                .filter(prize -> prize.getAmount().compareTo(BigDecimal.ZERO) > 0 && prize.getNumberOfPrize() > 0)
                .collect(Collectors.toList());
        if (validPrizes.isEmpty()) {
            logger.error("No valid prizes with non-zero amount and available count for chatId {}.", chatId);
            throw new IllegalStateException("Yaroqli lotereya sovrinlari mavjud emas");
        }

        Map<Long, BigDecimal> winnings = new HashMap<>();
        // Generate ticket IDs from 1 to numberOfPlays
        List<Long> ticketIds = new ArrayList<>();
        for (long i = 1; i <= numberOfPlays; i++) {
            ticketIds.add(i);
        }

        for (long i = 0; i < numberOfPlays && !ticketIds.isEmpty(); i++) {
            // Check if any prizes are still available
            validPrizes = validPrizes.stream()
                    .filter(prize -> prize.getNumberOfPrize() > 0)
                    .collect(Collectors.toList());
            if (validPrizes.isEmpty()) {
                logger.warn("No prizes left for chatId {}. Breaking loop.", chatId);
                break;
            }

            // Select a random ticket ID
            Long selectedTicket = ticketIds.get(random.nextInt(ticketIds.size()));

            // Determine prize (weighted by numberOfPrize)
            int totalPrizeCount = validPrizes.stream().mapToInt(LotteryPrize::getNumberOfPrize).sum();
            int randomPrizeValue = random.nextInt(totalPrizeCount);
            int currentPrizeCount = 0;
            LotteryPrize selectedPrize = validPrizes.get(0); // Default to first valid prize
            for (LotteryPrize prize : validPrizes) {
                currentPrizeCount += prize.getNumberOfPrize();
                if (randomPrizeValue < currentPrizeCount) {
                    selectedPrize = prize;
                    break;
                }
            }

            BigDecimal winAmount = selectedPrize.getAmount();
            selectedPrize.setNumberOfPrize(selectedPrize.getNumberOfPrize() - 1); // Persist updated prize count
            lotteryPrizeRepository.save(selectedPrize);
            winnings.put(selectedTicket, winAmount);
            ticketIds.remove(selectedTicket); // Remove played ticket
        }

        return winnings;
    }

    public UserBalance getBalance(Long chatId) {
        return userBalanceRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("User balance not found: " + chatId));
    }

    @Transactional
    public void awardRandomUsers(Long totalUsers, Long randomUsers, Long amount) {
        if (randomUsers > totalUsers || totalUsers <= 0 || randomUsers <= 0 || amount <= 0) {
            throw new IllegalStateException("Invalid parameters: totalUsers=" + totalUsers + ", randomUsers=" + randomUsers + ", amount=" + amount);
        }

        // Fetch last 'totalUsers' approved requests, ordered by creation time, with limit in query
        Pageable pageable = PageRequest.of(0, totalUsers.intValue(), Sort.by(Sort.Direction.DESC, "createdAt"));
        List<HizmatRequest> requests = hizmatRequestRepository.findByFilters( RequestStatus .APPROVED, pageable);

        if (requests.size() < randomUsers) {
            throw new IllegalStateException("Not enough approved users: requested=" + randomUsers + ", available=" + requests.size());
        }

        // Get unique chat IDs
        List<Long> chatIds = requests.stream()
                .map(HizmatRequest::getChatId)
                .distinct()
                .collect(Collectors.toList());

        if (chatIds.size() < randomUsers) {
            throw new IllegalStateException("Not enough unique approved users: requested=" + randomUsers + ", available=" + chatIds.size());
        }

        // Randomly select 'randomUsers' chat IDs
        Collections.shuffle(chatIds, random);
        List<Long> selectedChatIds = chatIds.subList(0, randomUsers.intValue());

        BigDecimal awardAmount = new BigDecimal(amount);

        // Update balances and send notifications
        for (Long chatId : selectedChatIds) {
            UserBalance balance = userBalanceRepository.findById(chatId)
                    .orElse(UserBalance.builder()
                            .chatId(chatId)
                            .tickets(0L)
                            .balance(BigDecimal.ZERO)
                            .build());
            balance.setBalance(balance.getBalance().add(awardAmount));
            userBalanceRepository.save(balance);
            String messageText = String.format(
                    "\uD83D\uDD25Кунлик бонус\uD83D\uDD25\n" +
                            "\n" +
                            "Омадли уйинчи табриклаймиз. \n" +
                            "Сиз тасодифий танлаш оркали 5,000 сум бонус ютиб  олдингиз. Бонус ботдаги балансингизга кушилди. \n\n"+
                            "💸 Yangi balans: %s so‘m\n"+
                            "📅 [%s]",
                    balance.getBalance().intValue(),  LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));


            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(messageText);
            message.setReplyMarkup(backButtonKeyboard());
            messageSender.sendMessage(message,chatId);
            String number = blockedUserRepository.findByChatId(chatId).get().getPhoneNumber();

            adminLogBotService.sendToAdmins("#Кунлик бонусда голиб болганлар\n" +
                    "\n" +
                    "Balans: " +balance.getBalance().intValue() + "\n" +
                    "User ID: " +chatId + "\n" +
                    "Telefon nomer:" +number+ "\n\n" +
                    "\uD83D\uDCC5 "+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            logger.info("Awarded {} UZS to chatId {}", amount, chatId);
        }
    }
    private InlineKeyboardMarkup backButtonKeyboard() {
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