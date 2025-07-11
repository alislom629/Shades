package com.example.shade.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Date-6/21/2025
 * By Sardor Tokhirov
 * Time-9:25 AM (GMT+5)
 */
@Data
public class UserProfile {
    @JsonProperty("UserId")
    private Long userId;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("CurrencyId")
    private Integer currencyId;
}