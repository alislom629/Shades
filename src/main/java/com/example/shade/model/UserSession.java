package com.example.shade.model;


import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserSession {
    private Long chatId;
    private String state;
    private List<String> navigationStates = new ArrayList<>();
    private List<Integer> messageIds = new ArrayList<>();
}