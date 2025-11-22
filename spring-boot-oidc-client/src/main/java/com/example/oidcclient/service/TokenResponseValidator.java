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
        JsonNode accessTokenNode = root.path("access_token");
        String accessToken = accessTokenNode.isString() ? accessTokenNode.stringValue() : null;

        if ((idToken == null || idToken.isBlank()) && (accessToken == null || accessToken.isBlank())) {
            logger.debug("Token response does not include id_token nor access_token; skipping validation");
            return;
        }

        String jwtToValidate;
        String accessTokenForAtHash;
        ValidatedTokenType tokenType;
        if (idToken != null && !idToken.isBlank()) {
            jwtToValidate = idToken;
            accessTokenForAtHash = accessToken;
            tokenType = ValidatedTokenType.ID_TOKEN;
        } else {
            jwtToValidate = accessToken;
            accessTokenForAtHash = null;
            tokenType = ValidatedTokenType.ACCESS_TOKEN;
            logger.debug("Token response omitted id_token. Validating access_token as ID token payload");
        }

        idTokenValidator.validate(jwtToValidate, expectedNonce, accessTokenForAtHash, tokenType);
    }
}
