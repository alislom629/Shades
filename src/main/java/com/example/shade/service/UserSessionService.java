

// PACKAGE: com.example.shade.util

package com.example.shade.util;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserSessionService {
    private final Map<Long, Map<String, String>> userSession = new HashMap<>();

    public void save(Long chatId, String key, String value) {
        userSession.computeIfAbsent(chatId, k -> new HashMap<>()).put(key, value);
    }

    public String get(Long chatId, String key) {
        Map<String, String> session = userSession.get(chatId);
        return session != null ? session.get(key) : null;
    }

    public void clear(Long chatId) {
        userSession.remove(chatId);
    }
}
