package com.example.oidcclient.service;

import com.example.oidcclient.config.properties.OidcClientProperties;
import com.example.oidcclient.http.KeycloakHttpClientFactory;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fetches and caches Keycloak JWKS documents so ID token signatures can be verified offline.
 */
@Service
public class OidcJwksClient {

    private static final Logger logger = LoggerFactory.getLogger(OidcJwksClient.class);

    private final OidcClientProperties oidcClientProperties;
    private final HttpClient httpClient;
    private final Clock clock;

    private volatile Map<String, RSAKey> cachedKeys = Map.of();
    private volatile Instant cacheExpiry = Instant.MIN;

    public OidcJwksClient(OidcClientProperties oidcClientProperties,
                          KeycloakHttpClientFactory httpClientFactory,
                          Clock clock) {
        this.oidcClientProperties = oidcClientProperties;
        this.httpClient = httpClientFactory.getHttpClient();
        this.clock = clock;
    }

    public RSAKey getKey(String kid) throws Exception {
        Map<String, RSAKey> keys = ensureFreshKeys();
        if (kid == null || kid.isBlank()) {
            if (keys.size() == 1) {
                return keys.values().iterator().next();
            }
            throw new IllegalStateException("ID Token header missing kid and JWKS has multiple entries");
        }
        RSAKey key = keys.get(kid);
        if (key == null) {
            logger.info("JWKS cache miss for kid={}, forcing refresh", kid);
            synchronized (this) {
                refreshKeys();
                key = cachedKeys.get(kid);
            }
        }
        if (key == null) {
            throw new IllegalStateException("Unable to locate signing key for kid=" + kid);
        }
        return key;
    }

    private Map<String, RSAKey> ensureFreshKeys() throws Exception {
        if (Instant.now(clock).isAfter(cacheExpiry)) {
            synchronized (this) {
                if (Instant.now(clock).isAfter(cacheExpiry)) {
                    refreshKeys();
                }
            }
        }
        return cachedKeys;
    }

    private void refreshKeys() throws Exception {
        String jwksUri = oidcClientProperties.getJwksUri();
        logger.debug("Fetching JWKS from {}", jwksUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jwksUri))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("Failed to download JWKS (status " + response.statusCode() + ")");
        }

        JWKSet jwkSet = JWKSet.parse(response.body());
        Map<String, RSAKey> keys = jwkSet.getKeys().stream()
                .map(this::toRsaKey)
                .filter(key -> key != null && key.getKeyID() != null)
                .collect(Collectors.toUnmodifiableMap(RSAKey::getKeyID, Function.identity()));

        cachedKeys = keys;
        cacheExpiry = Instant.now(clock).plus(oidcClientProperties.getJwksCacheTtl());
        logger.debug("Loaded {} signing keys (cache valid until {})", keys.size(), cacheExpiry);
    }

    private RSAKey toRsaKey(JWK jwk) {
        try {
            return jwk == null ? null : jwk.toRSAKey();
        } catch (Exception e) {
            logger.warn("Skipping non-RSA JWKS entry (kid={})", jwk != null ? jwk.getKeyID() : "n/a", e);
            return null;
        }
    }
}
