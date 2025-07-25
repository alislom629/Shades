package com.example.shade.controller;

import com.example.shade.model.LotteryPrize;
import com.example.shade.model.UserBalance;
import com.example.shade.repository.LotteryPrizeRepository;
import com.example.shade.service.LotteryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LotteryController {
    private final LotteryService lotteryService;
    private final LotteryPrizeRepository lotteryPrizeRepository;

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000".equals(parts[1]);
        }
        return false;
    }

    @PostMapping("/lottery/prizes")
    public ResponseEntity<LotteryPrize> addPrize(
            @RequestBody LotteryPrize prize,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        LotteryPrize savedPrize = lotteryPrizeRepository.save(prize);
        return ResponseEntity.ok(savedPrize);
    }

    @GetMapping("/lottery/prizes")
    public ResponseEntity<List<LotteryPrize>> getPrizes(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(lotteryPrizeRepository.findAll());
    }

    @DeleteMapping("/lottery/prizes/{id}")
    public ResponseEntity<Void> deletePrize(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        if (lotteryPrizeRepository.existsById(id)) {
            lotteryPrizeRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/lottery/balance/{chatId}")
    public ResponseEntity<UserBalance> getBalance(@PathVariable Long chatId) {
        try {
            UserBalance balance = lotteryService.getBalance(chatId);
            return ResponseEntity.ok(balance);
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/lottery/tickets/{chatId}")
    public ResponseEntity<Void> deleteTickets(@PathVariable Long chatId, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            lotteryService.deleteTickets(chatId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/lottery/balance/{chatId}")
    public ResponseEntity<Void> deleteBalance(@PathVariable Long chatId, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            lotteryService.deleteBalance(chatId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/lottery/tickets/{chatId}")
    public ResponseEntity<UserBalance> addTickets(@PathVariable Long chatId, @RequestParam Long amount, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        try {
            lotteryService.awardTickets(chatId, amount);
            UserBalance balance = lotteryService.getBalance(chatId);
            return ResponseEntity.ok(balance);
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/lottery/award-random-users")
    public ResponseEntity<Void> awardRandomUsers(
            @RequestParam Long totalUsers,
            @RequestParam Long randomUsers,
            @RequestParam Long amount,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            lotteryService.awardRandomUsers(totalUsers, randomUsers, amount);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}