package com.example.oidcclient.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // プロパティ参照
    @Value("${app.path.root:/}")
    private String rootPath;

    @Value("${app.path.authorization-flow:/authorization_flow}")
    private String authorizationFlowPath;

    @Value("${app.path.authorize:/authorize}")
    private String authorizePath;

    @Value("${app.path.callback:/callback}")
    private String callbackPath;

    @Value("${app.path.token-request:/token_request}")
    private String tokenRequestPath;

    @Value("${app.path.token:/token}")
    private String tokenPath;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // ここで許可するパスをプロパティから組み立て
        String[] permit = new String[] {
                rootPath,
                authorizationFlowPath,
                authorizePath,
                callbackPath,
                tokenRequestPath,
                tokenPath,
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