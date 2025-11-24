package com.example.oidcclient.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "application.oidc")
public class OidcClientProperties {

    /** Base host for the Keycloak server (e.g., https://localhost:8443). */
    @NotBlank
    private String host = "https://localhost:8443";

    /** Realm context path (e.g., /realms/myrealm/protocol/openid-connect). */
    @NotBlank
    private String contextPath = "/realms/myrealm/protocol/openid-connect";

    /** Optional override for the issuer (defaults to host + realm path). */
    private String issuer;

    /** Client identifier expected in ID tokens. */
    @NotBlank
    private String clientId = "semi_client";

    /** Optional override for JWKS endpoint. Defaults to host/context + /certs. */
    private String jwksUri;

    /** Client secret used for confidential clients. */
    private String clientSecret;

    /** Cache duration for JWKS fetches. */
    private Duration jwksCacheTtl = Duration.ofMinutes(5);

    /** Acceptable clock skew when validating exp/iat. */
    private Duration clockSkew = Duration.ofSeconds(60);

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getIssuer() {
        if (issuer != null && !issuer.isBlank()) {
            return issuer;
        }
        String ctx = normalizeContextPath();
        String suffix = "/protocol/openid-connect";
        if (ctx.endsWith(suffix)) {
            ctx = ctx.substring(0, ctx.length() - suffix.length());
        }
        return normalizeHost() + ctx;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getJwksUri() {
        if (jwksUri != null && !jwksUri.isBlank()) {
            return jwksUri;
        }
        return buildEndpoint("/certs");
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public Duration getJwksCacheTtl() {
        return jwksCacheTtl;
    }

    public void setJwksCacheTtl(Duration jwksCacheTtl) {
        this.jwksCacheTtl = jwksCacheTtl;
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Duration clockSkew) {
        this.clockSkew = clockSkew;
    }

    public String buildEndpoint(String suffix) {
        return normalizeHost() + normalizeContextPath() + suffix;
    }

    private String normalizeHost() {
        String value = host == null ? "" : host.trim();
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeContextPath() {
        String ctx = contextPath == null ? "" : contextPath.trim();
        if (!ctx.startsWith("/")) {
            ctx = "/" + ctx;
        }
        if (ctx.endsWith("/")) {
            ctx = ctx.substring(0, ctx.length() - 1);
        }
        return ctx;
    }
}
