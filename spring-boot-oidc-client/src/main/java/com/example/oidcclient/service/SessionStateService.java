package com.example.oidcclient.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/**
 * Encapsulates HttpSession usage for PKCE bundles to keep controllers stateless.
 */
@Service
public class SessionStateService {

    private static final String CODE_VERIFIER_PREFIX = "code_verifier";
    private static final String STATE_ATTR = "state";
    private static final String NONCE_ATTR = "nonce";
    private static final String CODE_CHALLENGE_METHOD_ATTR = "code_challenge_method";

    public void storePkceBundle(HttpSession session, String state, String nonce, String codeVerifier) {
        session.setAttribute(composeVerifierKey(state), codeVerifier);
        session.setAttribute(CODE_VERIFIER_PREFIX, codeVerifier);
        session.setAttribute(STATE_ATTR, state);
        session.setAttribute(NONCE_ATTR, nonce);
    }

    public String consumeCodeVerifier(HttpSession session, String state) {
        String key = composeVerifierKey(state);
        Object value = session.getAttribute(key);
        if (value != null) {
            session.removeAttribute(key);
            return value.toString();
        }
        Object fallback = session.getAttribute(CODE_VERIFIER_PREFIX);
        if (fallback != null) {
            session.removeAttribute(CODE_VERIFIER_PREFIX);
            return fallback.toString();
        }
        return null;
    }

    public void rememberCodeChallengeMethod(HttpSession session, String method) {
        session.setAttribute(CODE_CHALLENGE_METHOD_ATTR, method);
    }

    public boolean hasPkceContext(HttpSession session) {
        return session.getAttribute(CODE_CHALLENGE_METHOD_ATTR) != null;
    }

    public void clearPkceContext(HttpSession session) {
        session.removeAttribute(CODE_CHALLENGE_METHOD_ATTR);
        session.removeAttribute(CODE_VERIFIER_PREFIX);
    }

    private String composeVerifierKey(String state) {
        if (state != null && !state.isBlank()) {
            return CODE_VERIFIER_PREFIX + ":" + state;
        }
        return CODE_VERIFIER_PREFIX;
    }
}
