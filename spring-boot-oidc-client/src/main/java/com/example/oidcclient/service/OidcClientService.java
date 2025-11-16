package com.example.oidcclient.service;

import com.example.oidcclient.TokenClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralizes OIDC endpoint resolution and HTTP requests so controllers stay thin.
 */
@Service
public class OidcClientService {

    private static final Logger logger = LoggerFactory.getLogger(OidcClientService.class);

    private final TokenClientService tokenClientService;

    @Value("${keycloak.host:https://localhost:8443}")
    private String keycloakHost;

    @Value("${keycloak.context-path:/realms/myrealm/protocol/openid-connect}")
    private String keycloakContextPath;

    public OidcClientService(TokenClientService tokenClientService) {
        this.tokenClientService = tokenClientService;
    }

    /**
     * Builds the full authorization endpoint while allowing manual overrides from the UI.
     */
    public String resolveAuthorizationEndpoint(String overrideEndpoint) {
        if (overrideEndpoint != null && !overrideEndpoint.isBlank()) {
            return overrideEndpoint;
        }
        return buildEndpoint("/auth");
    }

    /**
     * Builds the full token endpoint and passes requests down to the mTLS client.
     */
    public String requestToken(String overrideEndpoint, Map<String, String> formParams) throws Exception {
        String endpoint = resolveTokenEndpoint(overrideEndpoint);
        return tokenClientService.requestToken(endpoint, formParams);
    }

    public String buildAuthorizationRequestUri(String authorizationEndpoint, Map<String, String> params) {
        String query = params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
        String result = authorizationEndpoint + (authorizationEndpoint.contains("?") ? "&" : "?") + query;
        logger.debug("buildAuthorizationRequestUri: {}", result);
        return result;
    }

    private String resolveTokenEndpoint(String overrideEndpoint) {
        if (overrideEndpoint != null && !overrideEndpoint.isBlank()) {
            return overrideEndpoint;
        }
        return buildEndpoint("/token");
    }

    private String buildEndpoint(String suffix) {
        String host = keycloakHost == null ? "" : keycloakHost.trim();
        String ctx = keycloakContextPath == null ? "" : keycloakContextPath.trim();

        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        if (!ctx.startsWith("/")) {
            ctx = "/" + ctx;
        }
        if (ctx.endsWith("/")) {
            ctx = ctx.substring(0, ctx.length() - 1);
        }

        return host + ctx + suffix;
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
