package com.example.shade.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * Date-8/10/2025
 * By Sardor Tokhirov
 * Time-11:40 PM (GMT+5)
 */
@Service
@RequiredArgsConstructor
public class HumoService {
    private final RestTemplate restTemplate  = new RestTemplate();

    public ResponseEntity<Object> forwardRequest(String path, HttpMethod method, HttpServletRequest request) {
        String targetUrl = "http://localhost:2805" + path;
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!headerName.equalsIgnoreCase("Authorization")) {
                headers.add(headerName, request.getHeader(headerName));
            }
        }

        String body = null;
        try {
            BufferedReader reader = request.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            body = sb.toString();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to read request body");
        }

        HttpEntity<String> entity = new HttpEntity<>(body.isEmpty() ? null : body, headers);
        try {
            ResponseEntity<Object> response = restTemplate.exchange(targetUrl, method, entity, Object.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to forward request");
        }
    }

    public boolean verifyPaymentAmount(Long uniqueAmount) {
        String targetUrl = "http://localhost:2805/last_transactions?amount=" + uniqueAmount;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(targetUrl, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("transactions")) {
                List<?> transactions = (List<?>) body.get("transactions");
                return !transactions.isEmpty();
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}