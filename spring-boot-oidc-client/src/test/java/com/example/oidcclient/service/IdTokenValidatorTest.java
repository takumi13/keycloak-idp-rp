package com.example.oidcclient.service;

import com.example.oidcclient.config.properties.OidcClientProperties;
import com.example.oidcclient.service.IdTokenValidator.ValidatedTokenType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

class IdTokenValidatorTest {

    private OidcClientProperties properties;
    private OidcJwksClient jwksClient;
    private Clock clock;
    private IdTokenValidator validator;
    private RSAKey rsaJwk;

    @BeforeEach
    void setUp() throws Exception {
        properties = new OidcClientProperties();
        properties.setHost("https://localhost:8443");
        properties.setContextPath("/realms/myrealm/protocol/openid-connect");
        properties.setClientId("semi_client");
        properties.setClockSkew(Duration.ofSeconds(60));

        jwksClient = Mockito.mock(OidcJwksClient.class);
        clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        validator = new IdTokenValidator(properties, jwksClient, clock);

        rsaJwk = new RSAKeyGenerator(2048)
                .keyID("test-kid")
                .generate();
        Mockito.when(jwksClient.getKey("test-kid")).thenReturn(rsaJwk.toPublicJWK());
    }

    @Test
    void validatePassesForWellFormedIdToken() throws Exception {
        String jwt = buildSignedJwt(builder -> {});

        validator.validate(jwt, "expected-nonce", null, ValidatedTokenType.ID_TOKEN);
    }

    @Test
    void validateRejectsMissingSubject() throws Exception {
        String jwt = buildSignedJwt(builder -> builder.subject(null));

        Assertions.assertThatThrownBy(() -> validator.validate(jwt, "expected-nonce", null, ValidatedTokenType.ID_TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sub");
    }

    private String buildSignedJwt(java.util.function.Consumer<JWTClaimsSet.Builder> claimCustomizer) throws Exception {
        Instant now = Instant.now(clock);
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(properties.getIssuer())
                .audience(properties.getClientId())
                .subject("user-123")
                .expirationTime(Date.from(now.plusSeconds(300)))
                .issueTime(Date.from(now.minusSeconds(30)))
                .claim("nonce", "expected-nonce");
        claimCustomizer.accept(builder);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID()).build(),
                builder.build()
        );
        JWSSigner signer = new RSASSASigner(rsaJwk);
        jwt.sign(signer);
        return jwt.serialize();
    }
}
