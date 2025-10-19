package com.example.oidcclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootApplication
public class OidcClientApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(OidcClientApplication.class, args);

    }

    /**
     * Authorization Request URI を構築するユーティリティ。
     * authorizationEndpoint には Keycloak の /protocol/openid-connect/auth を指定してください。
     * パラメータは OIDC 3.1.2.1 に準拠したキーを渡してください（例: response_type, client_id, redirect_uri, scope, state, nonce）。
     */
    public static String buildAuthorizationRequestUri(String authorizationEndpoint, Map<String, String> params) {
        String query = params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
        return authorizationEndpoint + (authorizationEndpoint.contains("?") ? "&" : "?") + query;
    }

    /**
     * token エンドポイントに対する application/x-www-form-urlencoded POST を行い、レスポンス文字列を返す。
     * tokenEndpoint に "http://localhost:8080/realms/myrealm/protocol/openid-connect/token" を指定してください。
     * 例の formParams: grant_type=authorization_code, code, redirect_uri, client_id, client_secret
     */
    public static String requestToken(String tokenEndpoint, Map<String, String> formParams) throws Exception {
        String form = formParams.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // ここでは単純にレスポンスボディを返す。必要であればステータスやヘッダも返すように変更してください。
        return response.body();
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 は存在する前提なのでここには来ない
            throw new RuntimeException(e);
        }
    }

    // ...existing code...
}