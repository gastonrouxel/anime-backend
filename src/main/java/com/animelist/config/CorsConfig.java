package com.animelist.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(   // ← allowedOriginPatterns au lieu de allowedOrigins
                    "http://localhost:*",
                    "http://192.168.*.*:*",   // réseau local IPv4
                    "http://10.*.*.*:*"       // autre plage réseau local
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}