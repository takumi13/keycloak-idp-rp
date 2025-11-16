package com.example.oidcclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TokenClientService {

    private static final Logger logger = LoggerFactory.getLogger(TokenClientService.class);

    // --- Keycloak mTLS 用プロパティを注入 ---
    @Value("${keycloak.mtls.key-store}")
    private String mtlsKeyStorePath;          // 例: "ssl/oidc-mtls-client-keystore.p12"

    @Value("${keycloak.mtls.key-store-password}")
    private String mtlsKeyStorePassword;      // 例: "changeit"

    @Value("${keycloak.mtls.key-store-type:PKCS12}")
    private String mtlsKeyStoreType;          // 例: "PKCS12"

    @Value("${keycloak.mtls.trust-store}")
    private String mtlsTrustStorePath;        // 例: "ssl/keycloak-server-truststore.p12"

    @Value("${keycloak.mtls.trust-store-password}")
    private String mtlsTrustStorePassword;    // 例: "changeit"

    @Value("${keycloak.mtls.trust-store-type:PKCS12}")
    private String mtlsTrustStoreType;        // 例: "PKCS12"

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

        // --- mTLS 用の SSLContext を構築 ---
        SSLContext sslContext = buildMtlsSslContext();

        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        logger.debug("Token response status: {}", response.statusCode());
        logger.debug("Token response body: {}", response.body());

        return response.body();
    }

    /**
     * keycloak.mtls.* の設定値を使って mTLS 用の SSLContext を構築する。
     */
    private SSLContext buildMtlsSslContext() throws Exception {
        logger.debug("Building mTLS SSLContext");
        logger.debug("  key-store        = {}", mtlsKeyStorePath);
        logger.debug("  trust-store      = {}", mtlsTrustStorePath);
        logger.debug("  key-store-type   = {}", mtlsKeyStoreType);
        logger.debug("  trust-store-type = {}", mtlsTrustStoreType);

        // --- クライアント側 keystore (クライアント証明書＋秘密鍵) ---
        KeyStore keyStore = KeyStore.getInstance(mtlsKeyStoreType);
        try (InputStream ksStream = getClass().getClassLoader().getResourceAsStream(mtlsKeyStorePath)) {
            if (ksStream == null) {
                throw new IllegalStateException("mTLS key-store not found in classpath: " + mtlsKeyStorePath);
            }
            keyStore.load(ksStream, mtlsKeyStorePassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, mtlsKeyStorePassword.toCharArray());

        // --- TrustStore (Keycloakのサーバ証明書 or CA) ---
        KeyStore trustStore = KeyStore.getInstance(mtlsTrustStoreType);
        try (InputStream tsStream = getClass().getClassLoader().getResourceAsStream(mtlsTrustStorePath)) {
            if (tsStream == null) {
                throw new IllegalStateException("mTLS trust-store not found in classpath: " + mtlsTrustStorePath);
            }
            trustStore.load(tsStream, mtlsTrustStorePassword.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        return sslContext;
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
