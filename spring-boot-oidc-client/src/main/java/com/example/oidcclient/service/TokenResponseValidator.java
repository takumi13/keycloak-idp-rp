package com.example.oidcclient.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.oidcclient.service.IdTokenValidator.ValidatedTokenType;

/**
 * Parses token responses and delegates ID Token verification.
 */
@Service
public class TokenResponseValidator {

    private static final Logger logger = LoggerFactory.getLogger(TokenResponseValidator.class);

    private final ObjectMapper objectMapper;
    private final IdTokenValidator idTokenValidator;

    public TokenResponseValidator(ObjectMapper objectMapper, IdTokenValidator idTokenValidator) {
        this.objectMapper = objectMapper;
        this.idTokenValidator = idTokenValidator;
    }

    public void validate(String rawResponse, String expectedNonce) throws Exception {
        if (rawResponse == null || rawResponse.isBlank()) {
            logger.debug("Token response is empty; nothing to validate");
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawResponse);
        } catch (Exception e) {
            logger.debug("Token response is not JSON; skipping ID Token validation");
            return;
        }
        JsonNode idTokenNode = root.get("id_token");
        String idToken = idTokenNode != null && idTokenNode.isString() ? idTokenNode.stringValue() : null;
        JsonNode accessTokenNode = root.get("access_token");
        String accessToken = accessTokenNode != null && accessTokenNode.isString() ? accessTokenNode.stringValue() : null;

        boolean hasIdToken = idToken != null && !idToken.isBlank();
        boolean hasAccessToken = accessToken != null && !accessToken.isBlank();
        if (!hasIdToken && !hasAccessToken) {
            logger.debug("Token response does not include id_token nor access_token; skipping validation");
            return;
        }

        if (hasIdToken) {
            idTokenValidator.validate(idToken, expectedNonce, accessToken, ValidatedTokenType.ID_TOKEN);
        } else {
            logger.debug("Token response omitted id_token; skipping ID Token validation step");
        }

        if (hasAccessToken) {
            idTokenValidator.validate(accessToken, null, null, ValidatedTokenType.ACCESS_TOKEN);
        } else {
            logger.debug("Token response omitted access_token; skipping access_token JWT validation");
        }
    }
}
