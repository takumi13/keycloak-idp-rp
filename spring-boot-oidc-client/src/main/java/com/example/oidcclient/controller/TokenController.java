package com.example.oidcclient.controller;

import com.example.oidcclient.service.OidcClientService;
import com.example.oidcclient.service.SessionStateService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class TokenController {

    private static final Logger logger = LoggerFactory.getLogger(TokenController.class);

    private final OidcClientService oidcClientService;
    private final SessionStateService sessionStateService;

    // コンストラクタインジェクション
    public TokenController(OidcClientService oidcClientService, SessionStateService sessionStateService) {
        this.oidcClientService = oidcClientService;
        this.sessionStateService = sessionStateService;
    }

    /**
     * token request を行うエンドポイント。
     * セッションから PKCE の code_verifier を取り出して form に含める（存在する場合）。
     */
    @PostMapping("${app.path.token-request:/token-request}")
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
        String codeVerifier = null;
        if (sessionStateService.hasPkceContext(session)) {
            codeVerifier = (codeVerifierParam != null && !codeVerifierParam.isBlank()) ? codeVerifierParam : null;
            if (codeVerifier == null) {
                codeVerifier = sessionStateService.consumeCodeVerifier(session, state);
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
        return oidcClientService.requestToken(tokenEndpoint, form);
    }
}
