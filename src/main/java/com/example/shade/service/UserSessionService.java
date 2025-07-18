package com.example.shade.service;

import com.example.shade.model.UserSession;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserSessionService {
    private final Map<Long, UserSession> sessionStore = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> sessionDataStore = new ConcurrentHashMap<>();

    public void setUserState(Long chatId, String state) {
        UserSession session = sessionStore.computeIfAbsent(chatId, k -> new UserSession());
        session.setChatId(chatId);
        session.setState(state);
        sessionStore.put(chatId, session);
    }

    public String getUserState(Long chatId) {
        return Optional.ofNullable(sessionStore.get(chatId))
                .map(UserSession::getState)
                .orElse(null);
    }

    public void setUserData(Long chatId, String key, String value) {
        Map<String, String> data = sessionDataStore.computeIfAbsent(chatId, k -> new HashMap<>());
        data.put(key, value);
        sessionDataStore.put(chatId, data);
    }
    public String getUserData(Long chatId, String key, String defaultValue) {
        return Optional.ofNullable(sessionDataStore.get(chatId))
                .map(data -> data.getOrDefault(key, defaultValue))
                .orElse(defaultValue);
    }

    public String getUserData(Long chatId, String key) {
        return Optional.ofNullable(sessionDataStore.get(chatId))
                .map(data -> data.get(key))
                .orElse(null);
    }

    public void addNavigationState(Long chatId, String state) {
        UserSession session = sessionStore.computeIfAbsent(chatId, k -> new UserSession());
        session.setChatId(chatId);
        List<String> navigationStates = session.getNavigationStates();
        navigationStates.add(state);
        session.setNavigationStates(navigationStates);
        sessionStore.put(chatId, session);
    }

    public String popNavigationState(Long chatId) {
        UserSession session = sessionStore.computeIfAbsent(chatId, k -> new UserSession());
        session.setChatId(chatId);
        List<String> navigationStates = session.getNavigationStates();
        if (navigationStates.isEmpty()) {
            return null;
        }
        String lastState = navigationStates.remove(navigationStates.size() - 1);
        session.setNavigationStates(navigationStates);
        sessionStore.put(chatId, session);
        return lastState;
    }

    public void clearSession(Long chatId) {
        sessionStore.remove(chatId);
        sessionDataStore.remove(chatId);
    }

    public List<Integer> getMessageIds(Long chatId) {
        return Optional.ofNullable(sessionStore.get(chatId))
                .map(UserSession::getMessageIds)
                .orElse(new ArrayList<>());
    }

    public void clearMessageIds(Long chatId) {
        UserSession session = sessionStore.computeIfAbsent(chatId, k -> new UserSession());
        session.setChatId(chatId);
        session.setMessageIds(new ArrayList<>());
        sessionStore.put(chatId, session);
    }

    public Optional<UserSession> getUserSession(Long chatId) {
        return Optional.ofNullable(sessionStore.get(chatId));
    }

    public void saveUserSession(UserSession session) {
        if (session.getChatId() != null) {
            sessionStore.put(session.getChatId(), session);
        }
    }
}