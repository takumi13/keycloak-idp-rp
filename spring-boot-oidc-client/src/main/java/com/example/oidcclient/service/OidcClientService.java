package com.example.oidcclient.service;

import com.example.oidcclient.TokenClientService;
import com.example.oidcclient.config.properties.OidcClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final OidcClientProperties oidcClientProperties;

    public OidcClientService(TokenClientService tokenClientService, OidcClientProperties oidcClientProperties) {
        this.tokenClientService = tokenClientService;
        this.oidcClientProperties = oidcClientProperties;
    }

    /**
     * Builds the full authorization endpoint while allowing manual overrides from the UI.
     */
    public String resolveAuthorizationEndpoint(String overrideEndpoint) {
        if (overrideEndpoint != null && !overrideEndpoint.isBlank()) {
            return overrideEndpoint;
        }
        return oidcClientProperties.buildEndpoint("/auth");
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
        return oidcClientProperties.buildEndpoint("/token");
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
