package com.example.shade.dto;

import com.example.shade.model.RequestStatus;

import java.util.List;
import java.util.Map;

public class DashboardStats {
    private long totalRequests;
    private long approvedRequests;
    private long pendingRequests;
    private long pendingAdminRequests;
    private long canceledRequests;
    private long failedRequests;
    private double totalApprovedWithdrawalAmount;
    private double totalApprovedTopUpAmount;
    private Map<RequestStatus, Long> statusDistribution;
    private Map<String, Long> requestsByPlatform;
    private Map<String, Long> requestsByDate;
    private Map<String, Double> amountByPlatform;
    private double averageApprovedAmount;
    private Map<Long, Long> topUsers;
    private List<Map<String, Object>> recentRequests;
    private Map<String, Map<String, Double>> platformGraphData;

    public DashboardStats(long totalRequests, long approvedRequests, long pendingRequests,
                          long pendingAdminRequests, long canceledRequests, long failedRequests,
                          double totalApprovedWithdrawalAmount, Map<RequestStatus, Long> statusDistribution,
                          Map<String, Long> requestsByPlatform, Map<String, Long> requestsByDate,
                          Map<String, Double> amountByPlatform, double averageApprovedAmount,
                          Map<Long, Long> topUsers, List<Map<String, Object>> recentRequests,
                          double totalApprovedTopUpAmount, Map<String, Map<String, Double>> platformGraphData) {
        this.totalRequests = totalRequests;
        this.approvedRequests = approvedRequests;
        this.pendingRequests = pendingRequests;
        this.pendingAdminRequests = pendingAdminRequests;
        this.canceledRequests = canceledRequests;
        this.failedRequests = failedRequests;
        this.totalApprovedWithdrawalAmount = totalApprovedWithdrawalAmount;
        this.totalApprovedTopUpAmount = totalApprovedTopUpAmount;
        this.statusDistribution = statusDistribution;
        this.requestsByPlatform = requestsByPlatform;
        this.requestsByDate = requestsByDate;
        this.amountByPlatform = amountByPlatform;
        this.averageApprovedAmount = averageApprovedAmount;
        this.topUsers = topUsers;
        this.recentRequests = recentRequests;
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

    public double getTotalApprovedTopUpAmount() {
        return totalApprovedTopUpAmount;
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

    public Map<String, Map<String, Double>> getPlatformGraphData() {
        return platformGraphData;
    }
}