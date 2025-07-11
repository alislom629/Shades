package com.example.shade.controller;

import com.example.shade.model.HizmatRequest;
import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;
import com.example.shade.repository.HizmatRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TransactionController {
    private final HizmatRequestRepository hizmatRequestRepository;

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000".equals(parts[1]);
        }
        return false;
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<HizmatRequest>> getTransactions(
            @RequestParam(required = false) Long cardId,
            @RequestParam(required = false) Long platformId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        RequestStatus requestStatus = status != null ? RequestStatus.valueOf(status) : null;
        RequestType requestType = type != null ? RequestType.valueOf(type) : null;

        Pageable pageable = PageRequest.of(0, 100); // Fetch last 100 records
        List<HizmatRequest> transactions = hizmatRequestRepository.findByFilters(cardId, platformId, requestStatus, requestType)
                .stream()
                .limit(100)
                .toList();

        return ResponseEntity.ok(transactions);
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        if (hizmatRequestRepository.existsById(id)) {
            hizmatRequestRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/transactions/bulk")
    public ResponseEntity<Void> deleteTransactions(@RequestBody List<Long> ids, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        long existingIds = ids.stream().filter(hizmatRequestRepository::existsById).count();
        if (existingIds == 0) {
            return ResponseEntity.notFound().build();
        }
        hizmatRequestRepository.deleteAllById(ids);
        return ResponseEntity.noContent().build();
    }
}