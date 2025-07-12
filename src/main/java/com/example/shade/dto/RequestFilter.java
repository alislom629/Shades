package com.example.shade.dto;

import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;

import java.time.LocalDateTime;

public class RequestFilter {
    private Long cardId;
    private Long platformId;
    private RequestStatus status;
    private RequestType type;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    public RequestFilter(Long cardId, Long platformId, RequestStatus status, RequestType type,
                         LocalDateTime startDate, LocalDateTime endDate) {
        this.cardId = cardId;
        this.platformId = platformId;
        this.status = status;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long getCardId() {
        return cardId;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public RequestType getType() {
        return type;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }
}