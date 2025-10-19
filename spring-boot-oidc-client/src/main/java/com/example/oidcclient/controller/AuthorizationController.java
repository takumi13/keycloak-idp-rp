package com.example.oidcclient.controller;

import com.example.oidcclient.OidcClientApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class AuthorizationController {

    // Keycloak のホストとコンテキストを properties から注入
    @Value("${keycloak.host:http://localhost:8080}")
    private String keycloakHost;

    @Value("${keycloak.context-path:/realms/myrealm/protocol/openid-connect}")
    private String keycloakContextPath;

    @GetMapping("${app.path.authorization-flow:/authorization_flow}")
    public String showForm() {
        return "authorization_flow";
    }

    @PostMapping("${app.path.authorize:/authorize}")
    public RedirectView authorize(
            @RequestParam(name = "authorization_endpoint", required = false) String authorizationEndpoint,
            @RequestParam(name = "response_type", required = false) String responseType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "nonce", required = false) String nonce,
            @RequestParam(name = "code_verifier", required = false) String codeVerifier,
            @RequestParam(name = "additional_params", required = false) String additionalParams
    ) {
        String endpoint = (authorizationEndpoint == null || authorizationEndpoint.isBlank())
                ? buildDefaultAuthorizationEndpoint()
                : authorizationEndpoint;

        Map<String, String> params = new LinkedHashMap<>();
        if (responseType != null && !responseType.isBlank()) params.put("response_type", responseType);
        if (clientId != null && !clientId.isBlank()) params.put("client_id", clientId);
        if (redirectUri != null && !redirectUri.isBlank()) params.put("redirect_uri", redirectUri);
        if (scope != null && !scope.isBlank()) params.put("scope", scope);
        if (state != null && !state.isBlank()) params.put("state", state);
        if (nonce != null && !nonce.isBlank()) params.put("nonce", nonce);

        if (codeVerifier != null && !codeVerifier.isBlank()) {
            String codeChallenge = generateS256CodeChallenge(codeVerifier);
            params.put("code_challenge", codeChallenge);
            params.put("code_challenge_method", "S256");
            // 実運用では code_verifier をセッション/DB 等に保存してください
        }

        if (additionalParams != null && !additionalParams.isBlank()) {
            String[] lines = additionalParams.split("\\r?\\n");
            for (String line : lines) {
                String l = line.trim();
                if (l.isEmpty()) continue;
                int idx = l.indexOf('=');
                if (idx > 0) {
                    String k = l.substring(0, idx).trim();
                    String v = l.substring(idx + 1).trim();
                    if (!k.isEmpty()) params.put(k, v);
                }
            }
        }

        String authUrl = OidcClientApplication.buildAuthorizationRequestUri(endpoint, params);
        return new RedirectView(authUrl);
    }

    private String buildDefaultAuthorizationEndpoint() {
        String host = keycloakHost == null ? "" : keycloakHost.trim();
        String ctx = keycloakContextPath == null ? "" : keycloakContextPath.trim();

        if (host.endsWith("/")) host = host.substring(0, host.length() - 1);
        if (!ctx.startsWith("/")) ctx = "/" + ctx;
        if (ctx.endsWith("/")) ctx = ctx.substring(0, ctx.length() - 1);

        return host + ctx + "/auth";
    }

    private static String generateS256CodeChallenge(String codeVerifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PKCE code_challenge", e);
        }
    }
}