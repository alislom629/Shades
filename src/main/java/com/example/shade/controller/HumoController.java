package com.example.shade.controller;

import com.example.shade.service.HumoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;

/**
 * Date-8/10/2025
 * By Sardor Tokhirov
 * Time-11:31 PM (GMT+5)
 */

@RestController
@RequestMapping("/api/humo")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class HumoController {

    private final HumoService humoService;


    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000".equals(parts[1]);
        }
        return false;
    }

    @RequestMapping("/**")
    public ResponseEntity<Object> receiver(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        String path = request.getRequestURI().substring(request.getContextPath().length() + "/api/humo".length());
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            path += "?" + queryString;
        }
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        return humoService.forwardRequest(path, method, request);
    }
}
