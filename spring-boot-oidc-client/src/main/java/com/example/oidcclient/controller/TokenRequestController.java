package com.example.oidcclient.controller;

import com.example.oidcclient.TokenClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class TokenRequestController {

    private static final Logger logger = LoggerFactory.getLogger(TokenRequestController.class);

    @Value("${keycloak.host:https://localhost:8443}")
    private String keycloakHost;

    @Value("${keycloak.context-path:/realms/myrealm/protocol/openid-connect}")
    private String keycloakContextPath;

    private final TokenClientService tokenClientService;

    // コンストラクタインジェクション
    public TokenRequestController(TokenClientService tokenClientService) {
        this.tokenClientService = tokenClientService;
    }

    /**
     * token request を行うエンドポイント。
     * セッションから PKCE の code_verifier を取り出して form に含める（存在する場合）。
     */
    @PostMapping("${app.path.token-request:/token_request}")
    public String requestToken(
            @RequestParam(name = "token_endpoint", required = false) String tokenEndpoint,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "code_verifier", required = false) String codeVerifierParam,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "client_secret", required = false) String clientSecret,
            @RequestParam(name = "grant_type", required = false, defaultValue = "authorization_code") String grantType,
            @RequestParam(name = "state", required = false) String state,
            HttpSession session
    ) throws Exception {
        String endpoint = (tokenEndpoint == null || tokenEndpoint.isBlank()) ? buildDefaultTokenEndpoint() : tokenEndpoint;

        String codeVerifier = null;
        String sessionKey = null;

        if (session.getAttribute("code_challenge_method") != null) {
            codeVerifier = (codeVerifierParam != null && !codeVerifierParam.isBlank()) ? codeVerifierParam : null;
            sessionKey = (state != null && !state.isBlank()) ? "code_verifier:" + state : "code_verifier";
            if (codeVerifier == null) {
                Object cvObj = session.getAttribute(sessionKey);
                if (cvObj != null) {
                    codeVerifier = cvObj.toString();
                    session.removeAttribute(sessionKey);
                }
            }
            logger.debug("code_verifier: {}", codeVerifier);
        }

        Map<String, String> form = new LinkedHashMap<>();
        if (grantType != null && !grantType.isBlank()) form.put("grant_type", grantType);
        if (code != null && !code.isBlank()) form.put("code", code);
        if (redirectUri != null && !redirectUri.isBlank()) form.put("redirect_uri", redirectUri);
        if (clientId != null && !clientId.isBlank()) form.put("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) form.put("client_secret", clientSecret);
        if (codeVerifier != null && !codeVerifier.isBlank()) form.put("code_verifier", codeVerifier);

        // ★ mTLS 付きで token エンドポイントに POST
        return tokenClientService.requestToken(endpoint, form);
    }

    private String buildDefaultTokenEndpoint() {
        String host = keycloakHost == null ? "" : keycloakHost.trim();
        String ctx = keycloakContextPath == null ? "" : keycloakContextPath.trim();

        if (host.endsWith("/")) host = host.substring(0, host.length() - 1);
        if (!ctx.startsWith("/")) ctx = "/" + ctx;
        if (ctx.endsWith("/")) ctx = ctx.substring(0, ctx.length() - 1);

        return host + ctx + "/token";
    }
}
