package com.example.oidcclient.controller;

import com.example.oidcclient.config.properties.OidcClientProperties;
import com.example.oidcclient.service.OidcClientService;
import com.example.oidcclient.service.SessionStateService;
import com.example.oidcclient.service.SessionStateService.PkceContext;
import com.example.oidcclient.service.TokenResponseValidator;
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
    private final TokenResponseValidator tokenResponseValidator;
    private final OidcClientProperties oidcClientProperties;

    // コンストラクタインジェクション
    public TokenController(OidcClientService oidcClientService,
                           SessionStateService sessionStateService,
                           TokenResponseValidator tokenResponseValidator,
                           OidcClientProperties oidcClientProperties) {
        this.oidcClientService = oidcClientService;
        this.sessionStateService = sessionStateService;
        this.tokenResponseValidator = tokenResponseValidator;
        this.oidcClientProperties = oidcClientProperties;
    }

    /**
     * token request を行うエンドポイント。
     * セッションから PKCE の code_verifier を取り出して form に含める（存在する場合）。
     */
    @PostMapping("${application.path.token-request:/token-request}")
    public String requestToken(
            @RequestParam(name = "token_endpoint", required = false) String tokenEndpoint,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "grant_type", required = false, defaultValue = "authorization_code") String grantType,
            @RequestParam(name = "state") String state,
            HttpSession session
    ) throws Exception {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state is required to resolve PKCE context");
        }
        PkceContext pkceContext = sessionStateService.loadPkceContext(session, state);
        if (pkceContext.isEmpty()) {
            throw new IllegalStateException("PKCE context not found for state=" + state);
        }
        boolean pkceActive = pkceContext.codeChallengeMethod() != null && !pkceContext.codeChallengeMethod().isBlank();

        String codeVerifier = pkceActive ? sessionStateService.consumeCodeVerifier(session, state) : null;
        if (pkceActive && (codeVerifier == null || codeVerifier.isBlank())) {
            throw new IllegalStateException("Missing code_verifier for PKCE-enabled authorization state=" + state);
        }
        if (pkceActive) {
            logger.debug("code_verifier (from session)");
        }

        Map<String, String> form = new LinkedHashMap<>();
        if (grantType != null && !grantType.isBlank()) form.put("grant_type", grantType);
        if (code != null && !code.isBlank()) form.put("code", code);
        if (redirectUri != null && !redirectUri.isBlank()) form.put("redirect_uri", redirectUri);
        String resolvedClientId = (clientId != null && !clientId.isBlank()) ? clientId : oidcClientProperties.getClientId();
        if (resolvedClientId != null && !resolvedClientId.isBlank()) {
            form.put("client_id", resolvedClientId);
        }
        String resolvedClientSecret = oidcClientProperties.getClientSecret();
        if (resolvedClientSecret != null && !resolvedClientSecret.isBlank()) {
            form.put("client_secret", resolvedClientSecret);
        }
        if (codeVerifier != null && !codeVerifier.isBlank()) form.put("code_verifier", codeVerifier);

        // ★ mTLS 付きで token エンドポイントに POST
        try {
            String response = oidcClientService.requestToken(tokenEndpoint, form);
            tokenResponseValidator.validate(response, pkceContext.nonce());
            return response;
        } finally {
            sessionStateService.clearPkceContext(session, state);
        }
    }
}
