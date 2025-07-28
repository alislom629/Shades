package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

/**
 * Date-7/28/2025
 * By Sardor Tokhirov
 * Time-10:27 AM (GMT+5)
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BalanceLimit {
    private BigDecimal balance;
    private BigDecimal limit;
}
