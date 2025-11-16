package com.example.oidcclient.config;

import com.example.oidcclient.config.properties.AppPathProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final AppPathProperties appPathProperties;

    public SecurityConfig(AppPathProperties appPathProperties) {
        this.appPathProperties = appPathProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // ここで許可するパスをプロパティから組み立て
        String[] permit = new String[] {
                appPathProperties.getRoot(),
                appPathProperties.getHome(),
                appPathProperties.getAuthorizationFlow(),
                appPathProperties.getAuthorize(),
                appPathProperties.getCallback(),
                appPathProperties.getTokenRequest(),
                appPathProperties.getToken(),
                "/css/**",
                "/js/**",
                "/favicon.ico"
        };

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(permit).permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.disable()); // 開発用

        return http.build();
    }
}