package com.example.shade.service;

import com.example.shade.dto.DashboardStats;
import com.example.shade.dto.RequestFilter;
import com.example.shade.model.HizmatRequest;
import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;
import com.example.shade.repository.HizmatRequestRepository;
import com.example.shade.repository.PlatformRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    @Autowired
    private HizmatRequestRepository requestRepository;

    @Autowired
    private PlatformRepository platformRepository;

    public DashboardStats getDashboardStats(RequestFilter filter) {
        long totalRequests = getRequestCount(filter);
        long approvedRequests = getRequestCount(new RequestFilter(
                filter.getCardId(), filter.getPlatformId(), RequestStatus.APPROVED, filter.getType(),
                filter.getStartDate(), filter.getEndDate()));
        long pendingRequests = getRequestCount(new RequestFilter(
                filter.getCardId(), filter.getPlatformId(), RequestStatus.PENDING, filter.getType(),
                filter.getStartDate(), filter.getEndDate()));
        long pendingAdminRequests = getRequestCount(new RequestFilter(
                filter.getCardId(), filter.getPlatformId(), RequestStatus.PENDING_ADMIN, filter.getType(),
                filter.getStartDate(), filter.getEndDate()));
        long canceledRequests = getRequestCount(new RequestFilter(
                filter.getCardId(), filter.getPlatformId(), RequestStatus.CANCELED, filter.getType(),
                filter.getStartDate(), filter.getEndDate()));
        long failedRequests = getRequestCount(new RequestFilter(
                filter.getCardId(), filter.getPlatformId(), RequestStatus.FAILED, filter.getType(),
                filter.getStartDate(), filter.getEndDate()));
        double totalApprovedWithdrawalAmount = getTotalApprovedWithdrawalAmount(new RequestFilter(
                filter.getCardId(), filter.getPlatformId(), RequestStatus.APPROVED, RequestType.WITHDRAWAL,
                filter.getStartDate(), filter.getEndDate()));
        double totalApprovedTopUpAmount = getTotalApprovedTopUpAmount(new RequestFilter(
                filter.getCardId(), filter.getPlatformId(), RequestStatus.APPROVED, RequestType.TOP_UP,
                filter.getStartDate(), filter.getEndDate()));
        Map<RequestStatus, Long> statusDistribution = getStatusDistribution(filter);
        Map<String, Long> requestsByPlatform = getRequestsByPlatform(filter);
        Map<String, Long> requestsByDate = getRequestsByDate(filter);
        Map<String, Double> amountByPlatform = getAmountByPlatform(new RequestFilter(
                filter.getCardId(), filter.getPlatformId(), RequestStatus.APPROVED, RequestType.WITHDRAWAL,
                filter.getStartDate(), filter.getEndDate()));
        double averageApprovedAmount = getAverageApprovedAmount(new RequestFilter(
                filter.getCardId(), filter.getPlatformId(), RequestStatus.APPROVED, RequestType.WITHDRAWAL,
                filter.getStartDate(), filter.getEndDate()));
        Map<Long, Long> topUsers = getTopUsersByRequestCount(filter);
        List<Map<String, Object>> recentRequests = getRecentRequests(filter);
        Map<String, Map<String, Double>> platformGraphData = getPlatformGraphData(new RequestFilter(
                filter.getCardId(), filter.getPlatformId(), RequestStatus.APPROVED, null,
                filter.getStartDate(), filter.getEndDate()));

        return new DashboardStats(totalRequests, approvedRequests, pendingRequests, pendingAdminRequests,
                canceledRequests, failedRequests, totalApprovedWithdrawalAmount, statusDistribution, requestsByPlatform,
                requestsByDate, amountByPlatform, averageApprovedAmount, topUsers, recentRequests,
                totalApprovedTopUpAmount, platformGraphData);
    }

    public long getRequestCount(RequestFilter filter) {
        List<HizmatRequest> requests = requestRepository.findByFilters(
                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
        if (filter.getStartDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                    .collect(Collectors.toList());
        }
        if (filter.getEndDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                    .collect(Collectors.toList());
        }
        return requests.size();
    }

    public double getTotalApprovedWithdrawalAmount(RequestFilter filter) {
        List<HizmatRequest> requests = requestRepository.findByFilters(
                filter.getCardId(), filter.getPlatformId(), RequestStatus.APPROVED, RequestType.WITHDRAWAL);
        if (filter.getStartDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                    .collect(Collectors.toList());
        }
        if (filter.getEndDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                    .collect(Collectors.toList());
        }
        return requests.stream()
                .mapToDouble(r -> r.getAmount() != null ? r.getAmount() : 0.0)
                .sum();
    }

    public double getTotalApprovedTopUpAmount(RequestFilter filter) {
        List<HizmatRequest> requests = requestRepository.findByFilters(
                filter.getCardId(), filter.getPlatformId(), RequestStatus.APPROVED, RequestType.TOP_UP);
        if (filter.getStartDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                    .collect(Collectors.toList());
        }
        if (filter.getEndDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                    .collect(Collectors.toList());
        }
        return requests.stream()
                .mapToDouble(r -> r.getAmount() != null ? r.getAmount() : 0.0)
                .sum();
    }

    public Map<RequestStatus, Long> getStatusDistribution(RequestFilter filter) {
        List<HizmatRequest> requests = requestRepository.findByFilters(
                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
        if (filter.getStartDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                    .collect(Collectors.toList());
        }
        if (filter.getEndDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                    .collect(Collectors.toList());
        }
        return requests.stream()
                .collect(Collectors.groupingBy(
                        HizmatRequest::getStatus,
                        Collectors.counting()
                ));
    }

    public Map<String, Long> getRequestsByPlatform(RequestFilter filter) {
        List<HizmatRequest> requests = requestRepository.findByFilters(
                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
        if (filter.getStartDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                    .collect(Collectors.toList());
        }
        if (filter.getEndDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                    .collect(Collectors.toList());
        }
        return requests.stream()
                .collect(Collectors.groupingBy(
                        HizmatRequest::getPlatform,
                        Collectors.counting()
                ));
    }

    public Map<String, Long> getRequestsByDate(RequestFilter filter) {
        List<HizmatRequest> requests = requestRepository.findByFilters(
                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
        if (filter.getStartDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                    .collect(Collectors.toList());
        }
        if (filter.getEndDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                    .collect(Collectors.toList());
        }
        return requests.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getCreatedAt().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                        Collectors.counting()
                ));
    }

    public Map<String, Double> getAmountByPlatform(RequestFilter filter) {
        List<HizmatRequest> requests = requestRepository.findByFilters(
                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
        if (filter.getStartDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                    .collect(Collectors.toList());
        }
        if (filter.getEndDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                    .collect(Collectors.toList());
        }
        return requests.stream()
                .filter(r -> r.getStatus() == RequestStatus.APPROVED && r.getType() == RequestType.WITHDRAWAL)
                .collect(Collectors.groupingBy(
                        HizmatRequest::getPlatform,
                        Collectors.summingDouble(r -> r.getAmount() != null ? r.getAmount() : 0.0)
                ));
    }

    public double getAverageApprovedAmount(RequestFilter filter) {
        List<HizmatRequest> requests = requestRepository.findByFilters(
                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
        if (filter.getStartDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                    .collect(Collectors.toList());
        }
        if (filter.getEndDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                    .collect(Collectors.toList());
        }
        return requests.stream()
                .filter(r -> r.getStatus() == RequestStatus.APPROVED && r.getType() == RequestType.WITHDRAWAL)
                .mapToDouble(r -> r.getAmount() != null ? r.getAmount() : 0.0)
                .average()
                .orElse(0.0);
    }

    public Map<Long, Long> getTopUsersByRequestCount(RequestFilter filter) {
        List<HizmatRequest> requests = requestRepository.findByFilters(
                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
        if (filter.getStartDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                    .collect(Collectors.toList());
        }
        if (filter.getEndDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                    .collect(Collectors.toList());
        }
        return requests.stream()
                .collect(Collectors.groupingBy(
                        HizmatRequest::getChatId,
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        HashMap::new
                ));
    }

    public List<Map<String, Object>> getRecentRequests(RequestFilter filter) {
        List<HizmatRequest> requests = requestRepository.findByFilters(
                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
        if (filter.getStartDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                    .collect(Collectors.toList());
        }
        if (filter.getEndDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                    .collect(Collectors.toList());
        }
        return requests.stream()
                .sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt()))
                .limit(5)
                .map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", r.getId());
                    map.put("chatId", r.getChatId());
                    map.put("platform", r.getPlatform());
                    map.put("platformUserId", r.getPlatformUserId());
                    map.put("fullName", r.getFullName());
                    map.put("cardNumber", r.getCardNumber());
                    map.put("amount", r.getAmount());
                    map.put("status", r.getStatus());
                    map.put("type", r.getType());
                    map.put("createdAt", r.getCreatedAt());
                    return map;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Map<String, Double>> getPlatformGraphData(RequestFilter filter) {
        List<HizmatRequest> requests = requestRepository.findByFilters(
                filter.getCardId(), filter.getPlatformId(), RequestStatus.APPROVED, null);
        if (filter.getStartDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                    .collect(Collectors.toList());
        }
        if (filter.getEndDate() != null) {
            requests = requests.stream()
                    .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                    .collect(Collectors.toList());
        }
        Map<String, Map<String, Double>> result = new HashMap<>();
        requests.stream()
                .filter(r -> r.getStatus() == RequestStatus.APPROVED)
                .collect(Collectors.groupingBy(HizmatRequest::getPlatform))
                .forEach((platform, platformRequests) -> {
                    Map<String, Double> amounts = new HashMap<>();
                    double withdrawalAmount = platformRequests.stream()
                            .filter(r -> r.getType() == RequestType.WITHDRAWAL)
                            .mapToDouble(r -> r.getAmount() != null ? r.getAmount() : 0.0)
                            .sum();
                    double topUpAmount = platformRequests.stream()
                            .filter(r -> r.getType() == RequestType.TOP_UP)
                            .mapToDouble(r -> r.getAmount() != null ? r.getAmount() : 0.0)
                            .sum();
                    amounts.put("withdrawal", withdrawalAmount);
                    amounts.put("top_up", topUpAmount);
                    result.put(platform, amounts);
                });
        return result;
    }
}