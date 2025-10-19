package com.example.oidcclient.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class AuthorizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${keycloak.host:http://localhost:8080}")
    private String keycloakHost;

    @Value("${keycloak.context-path:/realms/myrealm/protocol/openid-connect}")
    private String keycloakContextPath;

    @Value("${app.path.authorization-flow:/authorization_flow}")
    private String authorizationFlowPath;

    @Value("${app.path.authorize:/authorize}")
    private String authorizePath;

    private String buildAuthEndpoint() {
        String host = keycloakHost == null ? "" : keycloakHost.trim();
        String ctx = keycloakContextPath == null ? "" : keycloakContextPath.trim();

        if (host.endsWith("/")) host = host.substring(0, host.length() - 1);
        if (!ctx.startsWith("/")) ctx = "/" + ctx;
        if (ctx.endsWith("/")) ctx = ctx.substring(0, ctx.length() - 1);

        return host + ctx + "/auth";
    }

    @Test
    public void showForm_returnsOk() throws Exception {
        mockMvc.perform(get(authorizationFlowPath))
                .andExpect(status().isOk());
    }

    @Test
    public void postAuthorize_buildsAuthUrlAndRedirects() throws Exception {
        String endpoint = "http://localhost:8080/realms/myrealm/protocol/openid-connect/auth";
        String responseType = "code";
        String clientId = "semi_client";
        String redirectUri = "http://localhost:8081/callback";
        String scope = "openid profile";
        String state = "xyz";

        MvcResult result = mockMvc.perform(post("/authorize")
                        .param("authorization_endpoint", endpoint)
                        .param("response_type", responseType)
                        .param("client_id", clientId)
                        .param("redirect_uri", redirectUri)
                        .param("scope", scope)
                        .param("state", state))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        assertThat(location).startsWith(endpoint);

        // パラメータが含まれていること（redirect_uri はエンコードされる）
        assertThat(location).contains("response_type=" + responseType);
        assertThat(location).contains("client_id=" + clientId);
        assertThat(location).contains("scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8));
        assertThat(location).contains("state=" + state);
        assertThat(location).contains("redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));
    }

    @Test
    public void postAuthorize_buildsAuthUrlAndRedirects_withPKCE() throws Exception {
        String endpoint = buildAuthEndpoint();
        String responseType = "code";
        String clientId = "semi_client";
        String redirectUri = "http://localhost:8081/callback";
        String scope = "openid profile";
        String state = "xyz";
        String codeVerifier = "hogefuga";

        MvcResult result = mockMvc.perform(post(authorizePath)
                        .param("authorization_endpoint", endpoint)
                        .param("response_type", responseType)
                        .param("client_id", clientId)
                        .param("redirect_uri", redirectUri)
                        .param("scope", scope)
                        .param("state", state)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        assertThat(location).startsWith(endpoint);

        // 基本パラメータ
        assertThat(location).contains("response_type=" + responseType);
        assertThat(location).contains("client_id=" + clientId);
        assertThat(location).contains("scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8));
        assertThat(location).contains("state=" + state);
        assertThat(location).contains("redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));

        // PKCE: code_challenge と method を検証
        String expectedChallenge = computeS256CodeChallenge(codeVerifier);
        assertThat(location).contains("code_challenge=" + expectedChallenge);
        assertThat(location).contains("code_challenge_method=S256");
    }

    private static String computeS256CodeChallenge(String verifier) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}