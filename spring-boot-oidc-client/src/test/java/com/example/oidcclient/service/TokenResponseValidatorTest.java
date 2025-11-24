package com.example.oidcclient.service;

import com.example.oidcclient.service.IdTokenValidator.ValidatedTokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TokenResponseValidatorTest {

    @Mock
    private IdTokenValidator idTokenValidator;

    private TokenResponseValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TokenResponseValidator(new ObjectMapper(), idTokenValidator);
    }

    @Test
    void validateInvokesIdTokenAndAccessTokenWhenPresent() throws Exception {
        String response = "{\"id_token\":\"id-token\",\"access_token\":\"access-token\"}";

        validator.validate(response, "nonce-value");

        Mockito.verify(idTokenValidator).validate("id-token", "nonce-value", "access-token", ValidatedTokenType.ID_TOKEN);
        Mockito.verify(idTokenValidator).validate("access-token", null, null, ValidatedTokenType.ACCESS_TOKEN);
    }

    @Test
    void validateFallsBackToAccessTokenWhenIdTokenMissing() throws Exception {
        String response = "{\"access_token\":\"opaque\"}";

        validator.validate(response, "nonce-value");

        Mockito.verify(idTokenValidator).validate("opaque", null, null, ValidatedTokenType.ACCESS_TOKEN);
        Mockito.verifyNoMoreInteractions(idTokenValidator);
    }

    @Test
    void validateSkipsWhenResponseMissingTokens() throws Exception {
        String response = "{\"token_type\":\"Bearer\"}";

        validator.validate(response, "nonce-value");

        Mockito.verifyNoInteractions(idTokenValidator);
    }
}
