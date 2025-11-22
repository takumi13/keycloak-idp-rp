package com.example.oidcclient;

import com.example.oidcclient.http.KeycloakHttpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TokenClientService {

    private static final Logger logger = LoggerFactory.getLogger(TokenClientService.class);

    private final HttpClient httpClient;

    public TokenClientService(KeycloakHttpClientFactory httpClientFactory) {
        this.httpClient = httpClientFactory.getHttpClient();
    }

    /**
     * token エンドポイントに対する application/x-www-form-urlencoded POST を行い、レスポンス文字列を返す。
     * ここで Keycloak に対して mTLS 通信を行う。
     */
    public String requestToken(String tokenEndpoint, Map<String, String> formParams) throws Exception {
        String form = formParams.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));

        logger.debug("Requesting token from: {}", tokenEndpoint);
        formParams.forEach((k, v) -> logger.debug("Param: {} = {}", k, v));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        logger.debug("Token response status: {}", response.statusCode());
        logger.debug("Token response body: {}", response.body());

        return response.body();
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
