package com.example.oidcclient.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates PKCE verifiers/challenges with minimal controller logic.
 */
@Service
public class PkceService {

    private static final Logger logger = LoggerFactory.getLogger(PkceService.class);
    private static final String PKCE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
    private static final int MIN = 43;
    private static final int MAX = 128;
    private static final int DEFAULT_SIZE = 64;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${pkce.code-verifier.size:64}")
    private int configuredSize;

    public String generateVerifier() {
        return generateVerifier(configuredSize);
    }

    public String generateVerifier(int requestedSize) {
        int actualSize = normalizeSize(requestedSize);
        StringBuilder sb = new StringBuilder(actualSize);
        for (int i = 0; i < actualSize; i++) {
            sb.append(PKCE_CHARS.charAt(secureRandom.nextInt(PKCE_CHARS.length())));
        }
        return sb.toString();
    }

    public String generateChallenge(String codeVerifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PKCE code_challenge", e);
        }
    }

    private int normalizeSize(int requestedSize) {
        if (requestedSize < MIN) {
            logger.warn("Requested code_verifier size {} is below minimum {}; using {} instead", requestedSize, MIN, DEFAULT_SIZE);
            return DEFAULT_SIZE;
        }
        if (requestedSize > MAX) {
            logger.warn("Requested code_verifier size {} is above maximum {}; using {} instead", requestedSize, MAX, MAX);
            return MAX;
        }
        return requestedSize;
    }
}
