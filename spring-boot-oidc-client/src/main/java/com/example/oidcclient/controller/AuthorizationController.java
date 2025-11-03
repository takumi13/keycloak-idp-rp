package com.example.oidcclient.controller;

import com.example.oidcclient.OidcClientApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class AuthorizationController {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationController.class);

    // pkce code verifier size をプロパティから注入（デフォルト 64）
    @Value("${pkce.code-verifier.size:64}")
    private int pkceCodeVerifierSize;

    // Keycloak のホストとコンテキストを properties から注入
    @Value("${keycloak.host:http://localhost:8080}")
    private String keycloakHost;

    @Value("${keycloak.context-path:/realms/myrealm/protocol/openid-connect}")
    private String keycloakContextPath;

    // showForm: PKCE 値（code_verifier, state, nonce）を生成してセッション保存、Thymeleaf に渡す
    @GetMapping("${app.path.authorization-flow:/authorization_flow}")
    public String showForm(
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "client_id", required = false) String clientId,
            HttpServletRequest request,
            Model model) {
        // PKCE code_verifier をプロパティで指定された長さで生成（規定値: pkce.code-verifier.size）
        String codeVerifier = generateCodeVerifier(pkceCodeVerifierSize);
        String state = java.util.UUID.randomUUID().toString();
        String nonce = java.util.UUID.randomUUID().toString();

        // S256 code_challenge を生成
        String codeChallenge = generateS256CodeChallenge(codeVerifier);
        String codeChallengeMethod = "S256";

        logger.debug("[PKCE]code_verifier: " + codeVerifier);
        logger.debug("[PKCE]code_challenge: " + codeChallenge);
        logger.debug("state: " + state);
        logger.debug("nonce: " + nonce);

        // セッションに保存（state に紐付け）
        String sessionKey = "code_verifier:" + state;
        request.getSession(true).setAttribute(sessionKey, codeVerifier);
        request.getSession(true).setAttribute("code_verifier", codeVerifier);
        request.getSession(true).setAttribute("state", state);
        request.getSession(true).setAttribute("nonce", nonce);

        // Thymeleaf に渡す
        model.addAttribute("code_verifier", codeVerifier);
        model.addAttribute("state", state);
        model.addAttribute("nonce", nonce);
        model.addAttribute("code_challenge", codeChallenge);
        model.addAttribute("code_challenge_method", codeChallengeMethod);
        // redirect_uri と client_id をテンプレート初期値として渡す（リクエストで渡されていればそれを優先）
        model.addAttribute("redirect_uri", redirectUri != null ? redirectUri : "");
        model.addAttribute("client_id", clientId != null ? clientId : "");

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
            @RequestParam(name = "code_challenge", required = false) String codeChallenge,
            @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
            @RequestParam(name = "additional_params", required = false) String additionalParams,
            HttpServletRequest request
    ) {
        String endpoint;
        if (authorizationEndpoint == null || authorizationEndpoint.isBlank()) {
            endpoint = keycloakHost + keycloakContextPath + "/auth";
        } else {
            endpoint = authorizationEndpoint;
        }

        Map<String, String> params = new LinkedHashMap<>();
        if (responseType != null && !responseType.isBlank()) params.put("response_type", responseType);
        if (clientId != null && !clientId.isBlank()) params.put("client_id", clientId);
        if (redirectUri != null && !redirectUri.isBlank()) params.put("redirect_uri", redirectUri);
        if (scope != null && !scope.isBlank()) params.put("scope", scope);
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
            request.getSession(true).setAttribute("code_challenge_method", method);
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
        logger.debug("Redirecting to Authorization Endpoint: " + authUrl);
        return new RedirectView(authUrl);
    }

    public static String generateS256CodeChallenge(String codeVerifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PKCE code_challenge", e);
        }
    }

    // 指定サイズのcode_verifierを生成。size<43 の場合は WARN を出し DEFAULT_SIZE を使用。
    private String generateCodeVerifier(int size) {
        final int MIN = 43;
        final int MAX = 128;
        final int DEFAULT_SIZE = 64;
        int actualSize = size;
        if (size < MIN) {
            logger.warn("Requested code_verifier size {} is below minimum {}; using {} instead", size, MIN, DEFAULT_SIZE);
            actualSize = DEFAULT_SIZE;
        } else if (size > MAX) {
            logger.warn("Requested code_verifier size {} is above maximum {}; using {} instead", size, MAX, MAX);
            actualSize = MAX;
        }

        final String PKCE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        java.security.SecureRandom sr = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(actualSize);
        for (int i = 0; i < actualSize; i++) {
            sb.append(PKCE_CHARS.charAt(sr.nextInt(PKCE_CHARS.length())));
        }
        return sb.toString();
    }
}