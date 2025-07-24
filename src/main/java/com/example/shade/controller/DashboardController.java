package com.example.shade.controller;

import com.example.shade.dto.DashboardStats;
import com.example.shade.dto.RequestFilter;
import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;
import com.example.shade.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000".equals(parts[1]);
        }
        return false;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> getDashboardStats(HttpServletRequest request,
                                                            @RequestParam(required = false) Long cardId,
                                                            @RequestParam(required = false) Long platformId,
                                                            @RequestParam(required = false) RequestStatus status,
                                                            @RequestParam(required = false) RequestType type,
                                                            @RequestParam(required = false) LocalDateTime startDate,
                                                            @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(cardId, platformId, status, type, startDate, endDate);
        DashboardStats stats = dashboardService.getDashboardStats(filter);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/requests/count")
    public ResponseEntity<Long> getRequestCount(HttpServletRequest request,
                                                @RequestParam(required = false) Long cardId,
                                                @RequestParam(required = false) Long platformId,
                                                @RequestParam(required = false) RequestStatus status,
                                                @RequestParam(required = false) RequestType type,
                                                @RequestParam(required = false) LocalDateTime startDate,
                                                @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(cardId, platformId, status, type, startDate, endDate);
        long count = dashboardService.getRequestCount(filter);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/requests/approved-count")
    public ResponseEntity<Long> getApprovedRequestCount(HttpServletRequest request,
                                                        @RequestParam(required = false) Long cardId,
                                                        @RequestParam(required = false) Long platformId,
                                                        @RequestParam(required = false) LocalDateTime startDate,
                                                        @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(cardId, platformId, RequestStatus.APPROVED, null, startDate, endDate);
        long count = dashboardService.getRequestCount(filter);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/requests/total-withdrawal-amount")
    public ResponseEntity<Double> getTotalApprovedWithdrawalAmount(HttpServletRequest request,
                                                                   @RequestParam(required = false) Long cardId,
                                                                   @RequestParam(required = false) Long platformId,
                                                                   @RequestParam(required = false) LocalDateTime startDate,
                                                                   @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(cardId, platformId, RequestStatus.APPROVED, RequestType.WITHDRAWAL, startDate, endDate);
        double total = dashboardService.getTotalApprovedWithdrawalAmount(filter);
        return ResponseEntity.ok(total);
    }

    @GetMapping("/requests/total-topup-amount")
    public ResponseEntity<Double> getTotalApprovedTopUpAmount(HttpServletRequest request,
                                                              @RequestParam(required = false) Long cardId,
                                                              @RequestParam(required = false) Long platformId,
                                                              @RequestParam(required = false) LocalDateTime startDate,
                                                              @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(cardId, platformId, RequestStatus.APPROVED, RequestType.TOP_UP, startDate, endDate);
        double total = dashboardService.getTotalApprovedTopUpAmount(filter);
        return ResponseEntity.ok(total);
    }

    @GetMapping("/requests/total-bonus-amount")
    public ResponseEntity<Double> getTotalApprovedBonusAmount(HttpServletRequest request,
                                                              @RequestParam(required = false) Long cardId,
                                                              @RequestParam(required = false) Long platformId,
                                                              @RequestParam(required = false) LocalDateTime startDate,
                                                              @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(cardId, platformId, RequestStatus.BONUS_APPROVED, null, startDate, endDate);
        double total = dashboardService.getTotalApprovedBonusAmount(filter);
        return ResponseEntity.ok(total);
    }

    @GetMapping("/requests/status-distribution")
    public ResponseEntity<Map<RequestStatus, Long>> getStatusDistribution(HttpServletRequest request,
                                                                          @RequestParam(required = false) Long cardId,
                                                                          @RequestParam(required = false) Long platformId,
                                                                          @RequestParam(required = false) LocalDateTime startDate,
                                                                          @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(cardId, platformId, null, null, startDate, endDate);
        Map<RequestStatus, Long> distribution = dashboardService.getStatusDistribution(filter);
        return ResponseEntity.ok(distribution);
    }

    @GetMapping("/requests/by-platform")
    public ResponseEntity<Map<String, Long>> getRequestsByPlatform(HttpServletRequest request,
                                                                   @RequestParam(required = false) LocalDateTime startDate,
                                                                   @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(null, null, null, null, startDate, endDate);
        Map<String, Long> byPlatform = dashboardService.getRequestsByPlatform(filter);
        return ResponseEntity.ok(byPlatform);
    }

    @GetMapping("/requests/by-date")
    public ResponseEntity<Map<String, Long>> getRequestsByDate(HttpServletRequest request,
                                                               @RequestParam(required = false) LocalDateTime startDate,
                                                               @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(null, null, null, null, startDate, endDate);
        Map<String, Long> byDate = dashboardService.getRequestsByDate(filter);
        return ResponseEntity.ok(byDate);
    }

    @GetMapping("/requests/amount-by-platform")
    public ResponseEntity<Map<String, Double>> getAmountByPlatform(HttpServletRequest request,
                                                                   @RequestParam(required = false) LocalDateTime startDate,
                                                                   @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(null, null, RequestStatus.APPROVED, RequestType.WITHDRAWAL, startDate, endDate);
        Map<String, Double> amountByPlatform = dashboardService.getAmountByPlatform(filter);
        return ResponseEntity.ok(amountByPlatform);
    }

    @GetMapping("/requests/average-amount")
    public ResponseEntity<Double> getAverageApprovedAmount(HttpServletRequest request,
                                                           @RequestParam(required = false) Long cardId,
                                                           @RequestParam(required = false) Long platformId,
                                                           @RequestParam(required = false) LocalDateTime startDate,
                                                           @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(cardId, platformId, RequestStatus.APPROVED, RequestType.WITHDRAWAL, startDate, endDate);
        double average = dashboardService.getAverageApprovedAmount(filter);
        return ResponseEntity.ok(average);
    }

    @GetMapping("/requests/top-users")
    public ResponseEntity<Map<Long, Long>> getTopUsersByRequestCount(HttpServletRequest request,
                                                                     @RequestParam(required = false) LocalDateTime startDate,
                                                                     @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(null, null, null, null, startDate, endDate);
        Map<Long, Long> topUsers = dashboardService.getTopUsersByRequestCount(filter);
        return ResponseEntity.ok(topUsers);
    }

    @GetMapping("/requests/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentRequests(HttpServletRequest request,
                                                                       @RequestParam(required = false) LocalDateTime startDate,
                                                                       @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(null, null, null, null, startDate, endDate);
        List<Map<String, Object>> recentRequests = dashboardService.getRecentRequests(filter);
        return ResponseEntity.ok(recentRequests);
    }

    @GetMapping("/requests/platform-graph")
    public ResponseEntity<Map<String, Map<String, Double>>> getPlatformGraphData(HttpServletRequest request,
                                                                                 @RequestParam(required = false) LocalDateTime startDate,
                                                                                 @RequestParam(required = false) LocalDateTime endDate) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestFilter filter = new RequestFilter(null, null, RequestStatus.APPROVED, null, startDate, endDate);
        Map<String, Map<String, Double>> platformGraphData = dashboardService.getPlatformGraphData(filter);
        return ResponseEntity.ok(platformGraphData);
    }
}