package com.example.shade.config;

import com.example.shade.bot.AdminLogBot;
import com.example.shade.bot.LottoLogBot;
import com.example.shade.bot.ShadePaymentBot;
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


    @Autowired
    private AdminLogBot adminLogBot;

    @Autowired
    private LottoLogBot lottoLogBot;


    @PostConstruct
    public void init() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(adminLogBot); // ✅ Register second bot here
            botsApi.registerBot(shadePaymentBot);
            botsApi.registerBot(lottoLogBot);
            System.out.println("✅ Both bots started and registered successfully!");
        } catch (TelegramApiException e) {
            System.out.println("❌ Failed to register bots: " + e.getMessage());
        }
    }
}