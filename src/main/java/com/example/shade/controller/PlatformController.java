package com.example.shade.controller;

import com.example.shade.model.Currency;
import com.example.shade.model.Platform;
import com.example.shade.service.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PlatformController {
    private final PlatformService platformService;

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000".equals(parts[1]);
        }
        return false;
    }


    @GetMapping("/platforms")
    public ResponseEntity<List<Platform>> getPlatforms(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(platformService.getAllPlatforms());
    }

    @GetMapping("/platforms/{id}")
    public ResponseEntity<Platform> getPlatform(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(platformService.getPlatformById(id));
    }

    @PostMapping("/platforms")
    public ResponseEntity<Platform> createPlatform(@RequestBody PlatformRequest request, HttpServletRequest httpRequest) {
        if (!authenticate(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        Platform platform = platformService.createPlatform(
                request.getName(),
                request.getCurrency(),
                request.getApiKey(),
                request.getLogin(),
                request.getPassword(),
                request.getWorkplaceId()
        );
        return ResponseEntity.ok(platform);
    }

    @PutMapping("/platforms/{id}")
    public ResponseEntity<Platform> updatePlatform(@PathVariable Long id, @RequestBody PlatformRequest request, HttpServletRequest httpRequest) {
        if (!authenticate(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        Platform platform = platformService.updatePlatform(
                id,
                request.getName(),
                request.getCurrency(),
                request.getApiKey(),
                request.getLogin(),
                request.getPassword(),
                request.getWorkplaceId()
        );
        return ResponseEntity.ok(platform);
    }

    @DeleteMapping("/platforms/{id}")
    public ResponseEntity<Void> deletePlatform(@PathVariable Long id, HttpServletRequest httpRequest) {
        if (!authenticate(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        platformService.deletePlatform(id);
        return ResponseEntity.noContent().build();
    }

    static class PlatformRequest {
        private String name;
        private Currency currency;
        private String apiKey;
        private String login;
        private String password;
        private String workplaceId;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Currency getCurrency() { return currency; }
        public void setCurrency(Currency currency) { this.currency = currency; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getLogin() { return login; }
        public void setLogin(String login) { this.login = login; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getWorkplaceId() { return workplaceId; }
        public void setWorkplaceId(String workplaceId) { this.workplaceId = workplaceId; }
    }
}