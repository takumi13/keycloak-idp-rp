package com.example.oidcclient.service;

import com.example.oidcclient.config.properties.OidcClientProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Performs the mandatory verification steps from OIDC Core 1.0 section 3.1.3.7 for ID Tokens.
 */
@Service
public class IdTokenValidator {

    private static final Logger logger = LoggerFactory.getLogger(IdTokenValidator.class);

    private final OidcClientProperties oidcClientProperties;
    private final OidcJwksClient jwksClient;
    private final Clock clock;

    public IdTokenValidator(OidcClientProperties oidcClientProperties,
                            OidcJwksClient jwksClient,
                            Clock clock) {
        this.oidcClientProperties = oidcClientProperties;
        this.jwksClient = jwksClient;
        this.clock = clock;
    }

    public void validate(String idToken, String expectedNonce, String accessToken) throws Exception {
        validate(idToken, expectedNonce, accessToken, ValidatedTokenType.ID_TOKEN);
    }

    public void validate(String token, String expectedNonce, String accessToken, ValidatedTokenType tokenType) throws Exception {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(tokenType + " cannot be blank");
        }

        SignedJWT signedJWT = SignedJWT.parse(token);
        JWSHeader header = signedJWT.getHeader();
        RSAKey rsaKey = jwksClient.getKey(header.getKeyID());
        RSASSAVerifier verifier = new RSASSAVerifier(rsaKey);

        if (!signedJWT.verify(verifier)) {
            logValidation("signature", header.getKeyID(), false, "signature verification failed");
            throw new IllegalStateException("ID Token signature verification failed");
        }
        logValidation("signature", header.getKeyID(), true, null);

        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        validateIssuer(claims);
        validateAudience(claims, tokenType);
        validateAzp(claims, tokenType);
        validateSubject(claims);
        validateExpiry(claims);
        validateIssuedAt(claims);
        validateNonce(claims, expectedNonce, tokenType);
        validateAtHash(claims, accessToken, header);

        logger.debug("{} validation succeeded for jti={}", tokenType, claims.getJWTID());
    }

    private void validateIssuer(JWTClaimsSet claims) {
        String issuer = claims.getIssuer();
        if (!Objects.equals(issuer, oidcClientProperties.getIssuer())) {
            logValidation("iss", issuer, false, "expected " + oidcClientProperties.getIssuer());
            throw new IllegalStateException("Invalid iss claim: " + issuer);
        }
        logValidation("iss", issuer, true, null);
    }

    private void validateAudience(JWTClaimsSet claims, ValidatedTokenType tokenType) {
        List<String> aud = claims.getAudience();
        if (aud == null || aud.isEmpty()) {
            if (tokenType == ValidatedTokenType.ACCESS_TOKEN) {
                logValidation("aud", aud, true, "surrogate access_token");
                return;
            }
            logValidation("aud", aud, false, "claim missing");
            throw new IllegalStateException("ID Token audience does not include client_id");
        }
        if (!aud.contains(oidcClientProperties.getClientId())) {
            logValidation("aud", aud, false, "client_id missing");
            throw new IllegalStateException("ID Token audience does not include client_id");
        }
        logValidation("aud", aud, true, null);
    }

    private void validateAzp(JWTClaimsSet claims, ValidatedTokenType tokenType) {
        List<String> aud = claims.getAudience();
        String azp = (String) claims.getClaim("azp");
        if (tokenType == ValidatedTokenType.ACCESS_TOKEN) {
            if (!Objects.equals(azp, oidcClientProperties.getClientId())) {
                logValidation("azp", azp, false, "expected " + oidcClientProperties.getClientId());
                throw new IllegalStateException("access_token azp mismatch");
            }
            logValidation("azp", azp, true, "surrogate access_token");
            return;
        }
        if (aud != null && aud.size() > 1) {
            if (azp == null || !azp.equals(oidcClientProperties.getClientId())) {
                logValidation("azp", azp, false, "expected " + oidcClientProperties.getClientId());
                throw new IllegalStateException("azp claim missing or mismatched");
            }
            logValidation("azp", azp, true, null);
        } else {
            logValidation("azp", azp != null ? azp : "n/a", true, "single audience");
        }
    }

    private void validateSubject(JWTClaimsSet claims) {
        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            logValidation("sub", sub, false, "subject missing");
            throw new IllegalStateException("sub claim is required");
        }
        logValidation("sub", sub, true, null);
    }

    private void validateExpiry(JWTClaimsSet claims) {
        Instant now = Instant.now(clock);
        if (claims.getExpirationTime() == null) {
            logValidation("exp", null, false, "exp missing");
            throw new IllegalStateException("exp claim is required");
        }
        Instant exp = claims.getExpirationTime().toInstant();
        if (now.isAfter(exp.plus(oidcClientProperties.getClockSkew()))) {
            logValidation("exp", exp, false, "expired at " + exp);
            throw new IllegalStateException("ID Token expired at " + exp);
        }
        logValidation("exp", exp, true, null);
    }

    private void validateIssuedAt(JWTClaimsSet claims) {
        if (claims.getIssueTime() == null) {
            logValidation("iat", null, false, "iat missing");
            throw new IllegalStateException("iat claim is required");
        }
        Instant iat = claims.getIssueTime().toInstant();
        Instant now = Instant.now(clock);
        if (iat.isAfter(now.plus(oidcClientProperties.getClockSkew()))) {
            logValidation("iat", iat, false, "future timestamp");
            throw new IllegalStateException("iat is in the future");
        }
        logValidation("iat", iat, true, null);
    }

    private void validateNonce(JWTClaimsSet claims, String expectedNonce, ValidatedTokenType tokenType) {
        if (tokenType == ValidatedTokenType.ACCESS_TOKEN) {
            logValidation("nonce", "n/a", true, "not supplied in access_token");
            return;
        }
        if (expectedNonce == null || expectedNonce.isBlank()) {
            logValidation("nonce", "n/a", true, "nonce not requested");
            return;
        }
        String nonce = (String) claims.getClaim("nonce");
        if (!Objects.equals(expectedNonce, nonce)) {
            logValidation("nonce", nonce, false, "expected " + expectedNonce);
            throw new IllegalStateException("nonce mismatch");
        }
        logValidation("nonce", nonce, true, null);
    }

    private void validateAtHash(JWTClaimsSet claims, String accessToken, JWSHeader header) throws Exception {
        if (accessToken == null || accessToken.isBlank()) {
            logValidation("at_hash", "n/a", true, "access_token absent");
            return;
        }
        String atHash = (String) claims.getClaim("at_hash");
        if (atHash == null || atHash.isBlank()) {
            logValidation("at_hash", null, false, "claim missing");
            logger.debug("ID Token omitted at_hash; skipping access_token integrity check");
            return;
        }
        String computed = computeAtHash(accessToken, header.getAlgorithm());
        if (!atHash.equals(computed)) {
            logValidation("at_hash", atHash, false, "computed=" + computed);
            throw new IllegalStateException("at_hash claim does not match access_token");
        }
        logValidation("at_hash", atHash, true, null);
    }

    private void logValidation(String field, Object value, boolean success, String detail) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        String result = success ? "OK" : "NG";
        if (detail == null || detail.isBlank()) {
            logger.debug("validate {} [{}] -> {}", field, value, result);
        } else {
            logger.debug("validate {} [{}] -> {} ({})", field, value, result, detail);
        }
    }

    public enum ValidatedTokenType {
        ID_TOKEN,
        ACCESS_TOKEN
    }

    private String computeAtHash(String accessToken, JWSAlgorithm algorithm) throws Exception {
        String jwsAlg = algorithm != null ? algorithm.getName() : "";
        String hashAlg;
        if (jwsAlg.endsWith("256")) {
            hashAlg = "SHA-256";
        } else if (jwsAlg.endsWith("384")) {
            hashAlg = "SHA-384";
        } else if (jwsAlg.endsWith("512")) {
            hashAlg = "SHA-512";
        } else {
            throw new IllegalStateException("Unsupported JWS alg for at_hash verification: " + jwsAlg);
        }
        MessageDigest digest = MessageDigest.getInstance(hashAlg);
        byte[] hash = digest.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
        byte[] leftHalf = new byte[hash.length / 2];
        System.arraycopy(hash, 0, leftHalf, 0, leftHalf.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(leftHalf);
    }
}
