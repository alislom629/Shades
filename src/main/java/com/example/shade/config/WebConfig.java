package com.example.shade.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
/**
 * Date-7/6/2025
 * By Sardor Tokhirov
 * Time-5:10 PM (GMT+5)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("https://xonpey.uz") // Explicitly allow your frontend origin
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Explicitly list methods
                .allowedHeaders("*")
                .allowCredentials(true) // Allow credentials if needed (e.g., for auth headers)
                .maxAge(3600); // Cache preflight response for 1 hour
    }

}