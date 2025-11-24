package com.example.oidcclient.controller;

import com.example.oidcclient.service.OidcClientService;
import com.example.oidcclient.service.PkceService;
import com.example.oidcclient.service.SessionStateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class AuthorizationFlowController {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationFlowController.class);

    private final OidcClientService oidcClientService;
    private final PkceService pkceService;
    private final SessionStateService sessionStateService;

    public AuthorizationFlowController(
            OidcClientService oidcClientService,
            PkceService pkceService,
            SessionStateService sessionStateService) {
        this.oidcClientService = oidcClientService;
        this.pkceService = pkceService;
        this.sessionStateService = sessionStateService;
    }

    // showForm: PKCE 値（code_verifier, state, nonce）を生成してセッション保存、Thymeleaf に渡す
    @GetMapping("${application.path.authorization-flow:/authorization-flow}")
    public String showForm(HttpSession session, Model model) {
        // state, nonceを生成
        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();

        // PKCEパラメータを生成
        String codeVerifier = pkceService.generateVerifier();
        String codeChallenge = pkceService.generateChallenge(codeVerifier);
        String codeChallengeMethod = "S256";

        logger.debug("[PKCE]code_verifier: " + codeVerifier);
        logger.debug("[PKCE]code_challenge: " + codeChallenge);
        logger.debug("state: " + state);
        logger.debug("nonce: " + nonce);

        // セッションに保存（state に紐付け）
        sessionStateService.storePkceBundle(session, state, nonce, codeVerifier, codeChallengeMethod);

        // Thymeleaf に渡す
        model.addAttribute("code_verifier", codeVerifier);
        model.addAttribute("state", state);
        model.addAttribute("nonce", nonce);
        model.addAttribute("code_challenge", codeChallenge);
        model.addAttribute("code_challenge_method", codeChallengeMethod);

        return "authorization_flow";
    }

    @PostMapping("${application.path.authorize:/authorize}")
    public RedirectView authorize(
            @RequestParam(name = "authorization_endpoint", required = false) String authorizationEndpoint,
            @RequestParam(name = "response_type", required = false) String responseType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "nonce", required = false) String nonce,
            @RequestParam(name = "code_challenge", required = false) String codeChallenge,
            @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
            HttpSession session
    ) {
        String endpoint = oidcClientService.resolveAuthorizationEndpoint(authorizationEndpoint);

        Map<String, String> params = new LinkedHashMap<>();
        if (responseType != null && !responseType.isBlank()) params.put("response_type", responseType);
        if (clientId != null && !clientId.isBlank()) params.put("client_id", clientId);
        if (redirectUri != null && !redirectUri.isBlank()) params.put("redirect_uri", redirectUri);
        String resolvedScope = (scope != null && !scope.isBlank()) ? scope : "openid";
        params.put("scope", resolvedScope);
        if (state != null && !state.isBlank()) params.put("state", state);
        if (nonce != null && !nonce.isBlank()) params.put("nonce", nonce);


        // code_challenge_methodが指定されていない場合はPKCEを利用しない
        String method = (codeChallengeMethod != null && !codeChallengeMethod.isBlank()) ? codeChallengeMethod : "";
        if (method.isEmpty()) {
            codeChallenge = null;
        }
        // PKCE: クライアントが送る code_challenge と method をそのまま渡す（生成しない）
        if (codeChallenge != null && !codeChallenge.isBlank()) {
            params.put("code_challenge", codeChallenge);
            params.put("code_challenge_method", method);
            sessionStateService.rememberCodeChallengeMethod(session, state, method);
        }

        String authUrl = oidcClientService.buildAuthorizationRequestUri(endpoint, params);
        logger.debug("Redirecting to Authorization Endpoint: " + authUrl);
        return new RedirectView(authUrl);
    }
}