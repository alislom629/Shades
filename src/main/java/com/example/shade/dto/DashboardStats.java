package com.example.shade.dto;

import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class DashboardStats {
    private final long totalRequests;
    private final long approvedRequests;
    private final long pendingRequests;
    private final long pendingAdminRequests;
    private final long canceledRequests;
    private final long failedRequests;
    private final double totalApprovedWithdrawalAmount;
    private final Map<RequestStatus, Long> statusDistribution;
    private final Map<String, Long> requestsByPlatform;
    private final Map<String, Long> requestsByDate;
    private final Map<String, Double> amountByPlatform;
    private final double averageApprovedAmount;
    private final Map<Long, Long> topUsers;
    private final List<Map<String, Object>> recentRequests;
    private final double totalApprovedTopUpAmount;
    private final double totalApprovedBonusAmount;
    private final Map<String, Map<String, Double>> platformGraphData;

    public DashboardStats(long totalRequests, long approvedRequests, long pendingRequests, long pendingAdminRequests,
                          long canceledRequests, long failedRequests, double totalApprovedWithdrawalAmount,
                          Map<RequestStatus, Long> statusDistribution, Map<String, Long> requestsByPlatform,
                          Map<String, Long> requestsByDate, Map<String, Double> amountByPlatform,
                          double averageApprovedAmount, Map<Long, Long> topUsers, List<Map<String, Object>> recentRequests,
                          double totalApprovedTopUpAmount, double totalApprovedBonusAmount,
                          Map<String, Map<String, Double>> platformGraphData) {
        this.totalRequests = totalRequests;
        this.approvedRequests = approvedRequests;
        this.pendingRequests = pendingRequests;
        this.pendingAdminRequests = pendingAdminRequests;
        this.canceledRequests = canceledRequests;
        this.failedRequests = failedRequests;
        this.totalApprovedWithdrawalAmount = totalApprovedWithdrawalAmount;
        this.statusDistribution = statusDistribution;
        this.requestsByPlatform = requestsByPlatform;
        this.requestsByDate = requestsByDate;
        this.amountByPlatform = amountByPlatform;
        this.averageApprovedAmount = averageApprovedAmount;
        this.topUsers = topUsers;
        this.recentRequests = recentRequests;
        this.totalApprovedTopUpAmount = totalApprovedTopUpAmount;
        this.totalApprovedBonusAmount = totalApprovedBonusAmount;
        this.platformGraphData = platformGraphData;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public long getApprovedRequests() {
        return approvedRequests;
    }

    public long getPendingRequests() {
        return pendingRequests;
    }

    public long getPendingAdminRequests() {
        return pendingAdminRequests;
    }

    public long getCanceledRequests() {
        return canceledRequests;
    }

    public long getFailedRequests() {
        return failedRequests;
    }

    public double getTotalApprovedWithdrawalAmount() {
        return totalApprovedWithdrawalAmount;
    }

    public Map<RequestStatus, Long> getStatusDistribution() {
        return statusDistribution;
    }

    public Map<String, Long> getRequestsByPlatform() {
        return requestsByPlatform;
    }

    public Map<String, Long> getRequestsByDate() {
        return requestsByDate;
    }

    public Map<String, Double> getAmountByPlatform() {
        return amountByPlatform;
    }

    public double getAverageApprovedAmount() {
        return averageApprovedAmount;
    }

    public Map<Long, Long> getTopUsers() {
        return topUsers;
    }

    public List<Map<String, Object>> getRecentRequests() {
        return recentRequests;
    }

    public double getTotalApprovedTopUpAmount() {
        return totalApprovedTopUpAmount;
    }

    public double getTotalApprovedBonusAmount() {
        return totalApprovedBonusAmount;
    }

    public Map<String, Map<String, Double>> getPlatformGraphData() {
        return platformGraphData;
    }
}