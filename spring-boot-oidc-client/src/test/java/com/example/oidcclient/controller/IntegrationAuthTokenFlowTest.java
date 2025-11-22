package com.example.oidcclient.controller;

import com.example.oidcclient.TokenClientService;
import com.example.oidcclient.config.properties.AppPathProperties;
import com.example.oidcclient.service.PkceService;
import com.example.oidcclient.service.SessionStateService;
import com.example.oidcclient.service.TokenResponseValidator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(IntegrationAuthTokenFlowTest.TestConfig.class)
public class IntegrationAuthTokenFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PkceService pkceService;

    @Autowired
    private TokenClientService tokenClientService;

    @Autowired
    private SessionStateService sessionStateService;

    @Autowired
    private AppPathProperties appPathProperties;

    @Test
    void authorize_then_tokenRequest_reusesSessionCodeVerifier() throws Exception {
        String expectedResponse = "{\"access_token\":\"dummy-token\",\"token_type\":\"bearer\"}";
        AtomicReference<Map<String, String>> capturedForm = new AtomicReference<>();

        Mockito.when(tokenClientService.requestToken(Mockito.anyString(), Mockito.anyMap()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Map<String, String> form = (Map<String, String>) invocation.getArgument(1);
                    capturedForm.set(form);
                    return expectedResponse;
                });

        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get(appPathProperties.getAuthorizationFlow()).session(session))
            .andExpect(status().isOk());

        SessionStateService.PkceContext pkceContext = sessionStateService.loadPkceContext(session);
        String state = pkceContext.state();
        String codeVerifier = pkceContext.codeVerifier();
        String codeChallenge = pkceService.generateChallenge(codeVerifier);

        mockMvc.perform(post(appPathProperties.getAuthorize())
                        .with(csrf())
                        .session(session)
                        .param("response_type", "code")
                        .param("client_id", "semi_client")
                        .param("redirect_uri", "https://localhost:8081/callback")
                        .param("scope", "openid profile")
                        .param("state", state)
                        .param("code_challenge", codeChallenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post(appPathProperties.getTokenRequest())
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", "auth-code-from-authz")
                        .param("state", state)
                        .param("client_id", "semi_client")
                        .param("redirect_uri", "https://localhost:8081/callback"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedResponse));

        Map<String, String> form = capturedForm.get();
        Assertions.assertThat(form).isNotNull();
        Assertions.assertThat(form.get("code_verifier")).isEqualTo(codeVerifier);

        SessionStateService.PkceContext clearedContext = sessionStateService.loadPkceContext(session);
        Assertions.assertThat(clearedContext.codeVerifier()).isNull();
        Assertions.assertThat(clearedContext.state()).isNull();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        TokenClientService tokenClientService() {
            return Mockito.mock(TokenClientService.class);
        }

        @Bean
        @Primary
        TokenResponseValidator tokenResponseValidator() {
            return Mockito.mock(TokenResponseValidator.class);
        }
    }
}