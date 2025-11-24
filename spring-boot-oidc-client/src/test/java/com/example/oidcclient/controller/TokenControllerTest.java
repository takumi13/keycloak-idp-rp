package com.example.oidcclient.controller;

import com.example.oidcclient.config.properties.AppPathProperties;
import com.example.oidcclient.service.OidcClientService;
import com.example.oidcclient.service.SessionStateService;
import com.example.oidcclient.service.SessionStateService.PkceContext;
import com.example.oidcclient.service.TokenResponseValidator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TokenControllerTest.TestConfig.class)
class TokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionStateService sessionStateService;

    @Autowired
    private AppPathProperties appPathProperties;

    @Autowired
    private OidcClientService oidcClientService;

    @Autowired
    private TokenResponseValidator tokenResponseValidator;

    @Test
    @SuppressWarnings("unchecked")
    void requestToken_consumesPkceFromSession_andClearsContext() throws Exception {
        String tokenEndpoint = "https://localhost:8443/realms/myrealm/protocol/openid-connect/token";
        String expectedResponse = "{\"token\":\"ok\"}";

        ArgumentCaptor<Map<String, String>> formCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.when(oidcClientService.requestToken(eq(tokenEndpoint), anyMap())).thenReturn(expectedResponse);

        MockHttpSession session = new MockHttpSession();
        String state = "state-123";
        String nonce = "nonce-123";
        String codeVerifier = "code-verifier-123";

        sessionStateService.storePkceBundle(session, state, nonce, codeVerifier, "S256");
        sessionStateService.rememberCodeChallengeMethod(session, state, "S256");

        mockMvc.perform(post(appPathProperties.getTokenRequest())
                        .session(session)
                        .param("token_endpoint", tokenEndpoint)
                        .param("code", "authorization-code")
                        .param("client_id", "semi_client")
                        .param("redirect_uri", "https://localhost:8081/callback")
                        .param("state", state))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedResponse));

        Mockito.verify(oidcClientService).requestToken(eq(tokenEndpoint), formCaptor.capture());
        Map<String, String> form = formCaptor.getValue();
        Assertions.assertThat(form.get("code_verifier")).isEqualTo(codeVerifier);
        Assertions.assertThat(form.get("code")).isEqualTo("authorization-code");

        Mockito.verify(tokenResponseValidator).validate(expectedResponse, nonce);

        PkceContext cleared = sessionStateService.loadPkceContext(session);
        Assertions.assertThat(cleared.codeVerifier()).isNull();
        Assertions.assertThat(cleared.state()).isNull();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        OidcClientService oidcClientService() {
            return Mockito.mock(OidcClientService.class);
        }

        @Bean
        @Primary
        TokenResponseValidator tokenResponseValidator() {
            return Mockito.mock(TokenResponseValidator.class);
        }
    }
}
