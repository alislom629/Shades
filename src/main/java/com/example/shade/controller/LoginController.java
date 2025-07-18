package com.example.shade.controller;

import com.example.shade.bot.MessageSender;
import com.example.shade.model.LoginEvent;
import com.example.shade.repository.LoginEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    private final LoginEventRepository loginEventRepository;

    @PostMapping("/admin/login-info")
    public ResponseEntity<String> recordAdminLogin(@RequestBody AdminLoginRequest loginRequest) {
        String username = loginRequest.getUsername() != null ? loginRequest.getUsername() : "Unknown";
        String userAgent = loginRequest.getUserAgent() != null ? loginRequest.getUserAgent() : "Unknown";
        String ipAddress = loginRequest.getIpAddress() != null ? loginRequest.getIpAddress() : "Unknown";
        String deviceName = loginRequest.getDeviceName() != null ? loginRequest.getDeviceName() : "Unknown";
        String city = loginRequest.getCity() != null ? loginRequest.getCity() : "Unknown";
        String country = loginRequest.getCountry() != null ? loginRequest.getCountry() : "Unknown";

        logger.info("Admin login: username={}, userAgent={}, ipAddress={}, deviceName={}, city={}, country={}",
                username, userAgent, ipAddress, deviceName, city, country);

        LoginEvent loginEvent = LoginEvent.builder()
                .username(username)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .deviceName(deviceName)
                .city(city)
                .country(country)
                .loginTime(LocalDateTime.now())
                .build();
        loginEventRepository.save(loginEvent);


        return ResponseEntity.ok("Admin login recorded");
    }

    @GetMapping("/admin/login")
    public ResponseEntity<List<LoginEvent>> getAllLoginEvents() {
        logger.info("Fetching all admin login events");
        List<LoginEvent> loginEvents = loginEventRepository.findAll();
        return ResponseEntity.ok(loginEvents);
    }
}

class AdminLoginRequest {
    private String username;
    private String userAgent;
    private String ipAddress;
    private String deviceName;
    private String city;
    private String country;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}