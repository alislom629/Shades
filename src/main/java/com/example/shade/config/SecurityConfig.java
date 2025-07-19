package com.example.shade.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Enable CORS and use the CorsConfigurationSource defined in WebConfig
                // This is the key that links Spring Security with your CORS configuration.
                .cors(withDefaults())

                // 2. Disable CSRF for stateless APIs (common for REST APIs)
                .csrf(AbstractHttpConfigurer::disable)

                // 3. Define authorization rules
                .authorizeHttpRequests(authorize -> authorize
                        // Allow public access to the login endpoint
                        .requestMatchers("/api/admin/login").permitAll()
                        // You might have other public endpoints
                        .requestMatchers("/api/public/**").permitAll()
                        // Secure all other endpoints
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}