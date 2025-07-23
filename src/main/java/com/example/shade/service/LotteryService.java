package com.example.shade.service;

import com.example.shade.model.LotteryPrize;
import com.example.shade.model.UserBalance;
import com.example.shade.repository.LotteryPrizeRepository;
import com.example.shade.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    private final LottoBotService lottoBotService;
    private final Random random = new Random();
    private static final long MINIMUM_TICKETS = 36L;
    private static final long MAXIMUM_TICKETS = 400L;

    public void awardTickets(Long chatId, Long amount) {
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElse(UserBalance.builder()
                        .chatId(chatId)
                        .tickets(0L)
                        .balance(BigDecimal.ZERO)
                        .build());
        Long tickets = amount / 30000;
        balance.setTickets(balance.getTickets() + tickets);
        userBalanceRepository.save(balance);
        logger.info("Awarded {} tickets to chatId {}", tickets, chatId);
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
        if (totalWinnings.longValue()>=50000){
            lottoBotService.logWin(numberOfPlays,chatId,totalWinnings.longValue());
        }
        logger.info("Played {} tickets for chatId {}, won {} times with total {} UZS", numberOfPlays, chatId, winnings.size(), totalWinnings);
        return winnings;
    }

    public UserBalance getBalance(Long chatId) {
        return userBalanceRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("User balance not found: " + chatId));
    }
}