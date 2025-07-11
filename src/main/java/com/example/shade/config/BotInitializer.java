package com.example.shade;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Date-6/11/2025
 * By Sardor Tokhirov
 * Time-10:09 AM (GMT+5)
 */
@Configuration
public class BotInitializer {

    @Autowired
    private ShadePaymentBot shadePaymentBot;

    @PostConstruct
    public void init() {
        TelegramBotsApi botsApi;
        try {
            botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(shadePaymentBot);
            System.out.println("✅ Bot started and registered successfully!");
        } catch (TelegramApiException e) {
            System.out.println("❌ Failed to register bot: " + e.getMessage());
        }
    }
}
