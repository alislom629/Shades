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
        registry.addMapping("/api/**")
                .allowedOrigins("*") // Allow ALL origins
                .allowedMethods("*") // Allow ALL methods
                .allowedHeaders("*")
                .allowCredentials(true).maxAge(3600);; // Must be false when using "*"
    }

}