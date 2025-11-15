package com.example.oidcclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class OidcClientApplication {

    private static final Logger logger = LoggerFactory.getLogger(OidcClientApplication.class);

    /**
     * Spring の ApplicationContext を static に保持して、
     * static メソッドから TokenClientService を呼べるようにする。
     */
    private static ApplicationContext applicationContext;

    public OidcClientApplication(ApplicationContext context) {
        // Spring Boot 起動時にコンテキストが渡される
        OidcClientApplication.applicationContext = context;
    }

    public static void main(String[] args) {
        SpringApplication.run(OidcClientApplication.class, args);
    }

    /**
     * Authorization Request URI を構築するユーティリティ。
     */
    public static String buildAuthorizationRequestUri(String authorizationEndpoint, Map<String, String> params) {
        String query = params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
        String result = authorizationEndpoint + (authorizationEndpoint.contains("?") ? "&" : "?") + query;
        logger.debug("buildAuthorizationRequestUri: {}", result);
        return result;
    }

    /**
     * ★ Token エンドポイントに対する mTLS 付きリクエストを行う static メソッド。
     * 実際の処理は TokenClientService に委譲し、証明書情報は application.properties から取得する。
     */
    public static String requestToken(String tokenEndpoint, Map<String, String> formParams) throws Exception {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext is not initialized yet.");
        }
        TokenClientService service = applicationContext.getBean(TokenClientService.class);
        return service.requestToken(tokenEndpoint, formParams);
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 は存在する前提なのでここには来ない
            throw new RuntimeException(e);
        }
    }
}
