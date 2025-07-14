package com.example.shade.service;

import com.example.shade.model.AdminCard;
import com.example.shade.model.OsonConfig;
import com.example.shade.repository.AdminCardRepository;
import com.example.shade.repository.OsonConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OsonService {
    private static final Logger logger = LoggerFactory.getLogger(OsonService.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final OsonConfigRepository osonConfigRepository;
    private final AdminCardRepository adminCardRepository;
    private static final DateTimeFormatter OSON_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");
    private String authToken;

    private OsonConfig getConfig() {
        return osonConfigRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Oson configuration not found"));
    }

    private synchronized String login() {
        OsonConfig config = getConfig();
        String url = config.getApiUrl() + "/api/user/login";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("token", config.getApiKey());
        headers.set("User-Agent", "Oson/11.4.9 (uz.oson; build:2; iOS 18.5.0) Alamofire/4.9.1");
        headers.set("Accept-Language", "en-UZ;q=1.0, ru-UZ;q=0.9");
        headers.set("Accept-Encoding", "gzip;q=1.0, compress;q=0.5");
        headers.set("Connection", "keep-alive");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("app_version", "11.4.9");
        body.add("dev_id", config.getDeviceId());
        body.add("device_name", config.getDeviceName());
        body.add("lang", "1");
        body.add("password", config.getPassword());
        body.add("phone", config.getPhone());
        body.add("platform", "ios");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && responseBody != null && "0".equals(String.valueOf(responseBody.get("errno")))) {
                authToken = (String) responseBody.get("token");
                logger.info("Oson login successful, token: {}", authToken);
                return authToken;
            } else {
                logger.error("Oson login failed: {}", responseBody != null ? responseBody.get("errstr") : "No response body");
                throw new RuntimeException("Oson login failed: " + (responseBody != null ? responseBody.get("errstr") : "Unknown error"));
            }
        } catch (HttpClientErrorException e) {
            logger.error("Oson login HTTP error: {}", e.getMessage());
            throw new RuntimeException("Oson login failed: HTTP " + e.getStatusCode());
        } catch (Exception e) {
            logger.error("Unexpected error during Oson login: {}", e.getMessage());
            throw new RuntimeException("Oson login failed: " + e.getMessage());
        }
    }

    private String getAuthToken() {
        if (authToken == null) {
            return login();
        }
        return authToken;
    }

    private Long getCardIdByNumber(String cardNumber) {
        OsonConfig config = getConfig();
        String url = config.getApiUrl() + "/api/user/card_v2";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("token", getAuthToken());
        headers.set("User-Agent", "Oson/11.4.9 (uz.oson; build:2; iOS 18.5.0) Alamofire/4.9.1");
        headers.set("Accept-Language", "en-UZ;q=1.0, ru-UZ;q=0.9");
        headers.set("Accept-Encoding", "gzip;q=1.0, compress;q=0.5");
        headers.set("Connection", "keep-alive");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && responseBody != null && "0".equals(String.valueOf(responseBody.get("errno")))) {
                List<Map<String, Object>> cards = (List<Map<String, Object>>) responseBody.get("array");
                for (Map<String, Object> card : cards) {
                    String number = (String) card.get("number");
                    if (number != null && number.endsWith(cardNumber.substring(cardNumber.length() - 4))) {
                        return Long.valueOf(String.valueOf(card.get("id")));
                    }
                }
                logger.warn("No card found with last 4 digits: {}", cardNumber.substring(cardNumber.length() - 4));
                return null;
            } else {
                logger.error("Failed to fetch cards: {}", responseBody != null ? responseBody.get("errstr") : "No response body");
                throw new RuntimeException("Failed to fetch cards: " + (responseBody != null ? responseBody.get("errstr") : "Unknown error"));
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401) {
                authToken = null; // Invalidate token and retry
                return getCardIdByNumber(cardNumber);
            }
            logger.error("HTTP error fetching cards: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch cards: HTTP " + e.getStatusCode());
        } catch (Exception e) {
            logger.error("Unexpected error fetching cards: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch cards: " + e.getMessage());
        }
    }


    public Map<String, Object> verifyPaymentByAmountAndCard(Long chatId, String platform, String platformUserId, long amount, String userCardNumber, String adminCardId, long uniqueAmount) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        response.put("error", "Payment verification failed");
        uniqueAmount = uniqueAmount * 100; // Adjust for Oson API (assuming amount in tiyin)

        Long cardId = getCardIdByNumber(adminCardId);
        if (cardId == null) {
            response.put("error", "Admin card not found");
            return response;
        }

        OsonConfig config = getConfig();
        String url = String.format("%s/api/user/card_history?card_id=%d&count=20&manufacturer=1&offset=0&version=2", config.getApiUrl(), cardId);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("token", getAuthToken());
        headers.set("User-Agent", "Oson/11.4.9 (uz.oson; build:2; iOS 18.5.0) Alamofire/4.9.1");
        headers.set("Accept-Language", "en-UZ;q=1.0, ru-UZ;q=0.9");
        headers.set("Accept-Encoding", "gzip;q=1.0, compress;q=0.5");
        headers.set("Connection", "keep-alive");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> apiResponse = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> responseBody = apiResponse.getBody();
            if (apiResponse.getStatusCode().is2xxSuccessful() && responseBody != null && "0".equals(String.valueOf(responseBody.get("errno")))) {
                List<Map<String, Object>> transactions = (List<Map<String, Object>>) responseBody.get("array");
                String userCardLastDigits = userCardNumber.substring(userCardNumber.length() - 4);
                OffsetDateTime now = OffsetDateTime.now();

                for (Map<String, Object> transaction : transactions) {
                    long txAmount = Long.parseLong(String.valueOf(transaction.get("amount")));
                    String ts = (String) transaction.get("ts");
                    int status = Integer.parseInt(String.valueOf(transaction.get("status")));

                    if (txAmount == uniqueAmount && status == 1) {
                        try {
                            OffsetDateTime txTime = OffsetDateTime.parse(ts, OSON_TIMESTAMP_FORMATTER);
                            if (txTime.isAfter(now.minusMinutes(15))) { // Check transactions within last 15 minutes
                                response.put("status", "SUCCESS");
                                response.put("transactionId", String.valueOf(transaction.get("id")));
                                response.put("billId", transaction.get("refnum"));
                                response.put("payUrl", ""); // No payment URL needed as per new flow
                                return response;
                            }
                        } catch (DateTimeParseException e) {
                            logger.error("Failed to parse timestamp '{}': {}", ts, e.getMessage());
                            response.put("error", "Invalid timestamp format in transaction: " + ts);
                            return response;
                        }
                    }
                }
                response.put("error", "No matching payment found");
            } else {
                response.put("error", responseBody != null ? responseBody.get("errstr") : "No response body");
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401) {
                authToken = null; // Invalidate token and retry
                return verifyPaymentByAmountAndCard(chatId, platform, platformUserId, amount, userCardNumber, adminCardId, uniqueAmount);
            }
            logger.error("HTTP error fetching card history: {}", e.getMessage());
            response.put("error", "HTTP error: " + e.getStatusCode());
        } catch (Exception e) {
             logger.error("Unexpected error fetching card history: {}", e.getMessage());
            response.put("error", "Unexpected error: " + e.getMessage());
        }
        return response;
    }
    public Map<String, Object> getCardsAndWalletBalance() {
        OsonConfig config = getConfig();
        String cardUrl = config.getApiUrl() + "/api/user/card_v2";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("token", getAuthToken());
        headers.set("User-Agent", "Oson/11.4.9 (uz.oson; build:2; iOS 18.5.0) Alamofire/4.9.1");
        headers.set("Accept-Language", "en-UZ;q=1.0, ru-UZ;q=0.9");
        headers.set("Accept-Encoding", "gzip;q=1.0, compress;q=0.5");
        headers.set("Connection", "keep-alive");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        Map<String, Object> result = new HashMap<>();

        // Fetch cards
        try {
            ResponseEntity<Map> cardResponse = restTemplate.exchange(cardUrl, HttpMethod.GET, entity, Map.class);
            Map<String, Object> cardResponseBody = cardResponse.getBody();
            if (cardResponse.getStatusCode().is2xxSuccessful() && cardResponseBody != null && "0".equals(String.valueOf(cardResponseBody.get("errno")))) {
                List<Map<String, Object>> cards = (List<Map<String, Object>>) cardResponseBody.get("array");
                List<Map<String, Object>> cardDetails = new ArrayList<>();
                for (Map<String, Object> card : cards) {
                    String number = (String) card.get("number");
                    String ownerName = (String) card.get("owner");
                    Long balance = Long.parseLong(String.valueOf(card.get("balance")));
                    AdminCard adminCard = adminCardRepository.findByCardNumber(number).orElse(new AdminCard());
                    adminCard.setCardNumber(number);
                    adminCard.setBalance(balance);
                    adminCard.setOwnerName(ownerName);
                    adminCard.setLastUsed(OffsetDateTime.now().toLocalDateTime());
                    adminCardRepository.save(adminCard);
                    cardDetails.add(Map.of(
                            "cardNumber", number,
                            "balance", balance / 100.0, // Convert tiyin to UZS
                            "ownerName", adminCard.getOwnerName() != null ? adminCard.getOwnerName() : "Unknown"
                    ));
                }
                result.put("cards", cardDetails);
                result.put("status", "SUCCESS");
            } else {
                logger.error("Failed to fetch cards: {}", cardResponseBody != null ? cardResponseBody.get("errstr") : "No response body");
                result.put("status", "ERROR");
                result.put("error", "Failed to fetch cards: " + (cardResponseBody != null ? cardResponseBody.get("errstr") : "Unknown error"));
                return result;
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401) {
                authToken = null;
                return getCardsAndWalletBalance();
            }
            logger.error("HTTP error fetching cards: {}", e.getMessage());
            result.put("status", "ERROR");
            result.put("error", "HTTP error fetching cards: " + e.getStatusCode());
            return result;
        } catch (Exception e) {
            logger.error("Unexpected error fetching cards: {}", e.getMessage());
            result.put("status", "ERROR");
            result.put("error", "Unexpected error fetching cards: " + e.getMessage());
            return result;
        }

        return result;
    }
}