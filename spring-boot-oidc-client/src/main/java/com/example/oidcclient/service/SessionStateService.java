package com.example.oidcclient.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates HttpSession usage for PKCE bundles to keep controllers stateless.
 */
@Service
@SuppressWarnings("unchecked")
public class SessionStateService {

    private static final String CONTEXTS_ATTR = SessionStateService.class.getName() + ".PKCE_CONTEXTS";
    private static final String ACTIVE_STATE_ATTR = SessionStateService.class.getName() + ".ACTIVE_STATE";

    public void storePkceBundle(HttpSession session, String state, String nonce, String codeVerifier, String codeChallengeMethod) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state is required");
        }
        Map<String, PkceContext> contexts = getContextMap(session, true);
        contexts.put(state, new PkceContext(state, nonce, codeVerifier, codeChallengeMethod));
        session.setAttribute(ACTIVE_STATE_ATTR, state);
    }

    public void rememberCodeChallengeMethod(HttpSession session, String state, String method) {
        if (state == null || state.isBlank()) {
            return;
        }
        Map<String, PkceContext> contexts = getContextMap(session, false);
        if (contexts == null) {
            return;
        }
        PkceContext current = contexts.get(state);
        if (current == null) {
            return;
        }
        contexts.put(state, current.withCodeChallengeMethod(method));
    }

    public String consumeCodeVerifier(HttpSession session, String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        Map<String, PkceContext> contexts = getContextMap(session, false);
        if (contexts == null) {
            return null;
        }
        PkceContext context = contexts.get(state);
        if (context == null || context.codeVerifier() == null) {
            return null;
        }
        contexts.put(state, context.withCodeVerifier(null));
        return context.codeVerifier();
    }

    public boolean hasPkceContext(HttpSession session) {
        Map<String, PkceContext> contexts = getContextMap(session, false);
        return contexts != null && !contexts.isEmpty();
    }

    public PkceContext loadPkceContext(HttpSession session) {
        String state = attributeToString(session.getAttribute(ACTIVE_STATE_ATTR));
        return loadPkceContext(session, state);
    }

    public PkceContext loadPkceContext(HttpSession session, String state) {
        if (state == null || state.isBlank()) {
            return PkceContext.empty();
        }
        Map<String, PkceContext> contexts = getContextMap(session, false);
        if (contexts == null) {
            return PkceContext.empty();
        }
        return contexts.getOrDefault(state, PkceContext.empty());
    }

    public void clearPkceContext(HttpSession session) {
        Map<String, PkceContext> contexts = getContextMap(session, false);
        if (contexts != null) {
            contexts.clear();
            session.removeAttribute(CONTEXTS_ATTR);
        }
        session.removeAttribute(ACTIVE_STATE_ATTR);
    }

    public void clearPkceContext(HttpSession session, String state) {
        if (state == null || state.isBlank()) {
            return;
        }
        Map<String, PkceContext> contexts = getContextMap(session, false);
        if (contexts == null) {
            return;
        }
        contexts.remove(state);
        String active = attributeToString(session.getAttribute(ACTIVE_STATE_ATTR));
        if (state.equals(active)) {
            session.removeAttribute(ACTIVE_STATE_ATTR);
        }
        if (contexts.isEmpty()) {
            session.removeAttribute(CONTEXTS_ATTR);
        }
    }

    private Map<String, PkceContext> getContextMap(HttpSession session, boolean create) {
        Map<String, PkceContext> contexts = (Map<String, PkceContext>) session.getAttribute(CONTEXTS_ATTR);
        if (contexts == null && create) {
            contexts = new HashMap<>();
            session.setAttribute(CONTEXTS_ATTR, contexts);
        }
        return contexts;
    }

    private String attributeToString(Object value) {
        return value == null ? null : value.toString();
    }

    public record PkceContext(String state, String nonce, String codeVerifier, String codeChallengeMethod) {
        private static final PkceContext EMPTY = new PkceContext(null, null, null, null);

        public static PkceContext empty() {
            return EMPTY;
        }

        public boolean isEmpty() {
            return this == EMPTY || (state == null && nonce == null && codeVerifier == null && codeChallengeMethod == null);
        }

        public PkceContext withCodeVerifier(String newVerifier) {
            if (this == EMPTY) {
                return this;
            }
            return new PkceContext(state, nonce, newVerifier, codeChallengeMethod);
        }

        public PkceContext withCodeChallengeMethod(String method) {
            if (this == EMPTY) {
                return this;
            }
            return new PkceContext(state, nonce, codeVerifier, method);
        }
    }
}
