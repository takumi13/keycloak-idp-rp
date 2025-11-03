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
        String codeVerifier = "TKwV36z4a0lK9fIupr2yThIjvy7y1nGDE6VQ6ikM2nU";
        // クライアント側で生成する code_challenge をテスト側でも算出して送信する
        String codeChallenge = AuthorizationController.generateS256CodeChallenge(codeVerifier);

        MvcResult result = mockMvc.perform(post(authorizePath)
                        .param("authorization_endpoint", endpoint)
                        .param("response_type", responseType)
                        .param("client_id", clientId)
                        .param("redirect_uri", redirectUri)
                        .param("scope", scope)
                        .param("state", state)
                        .param("code_challenge", codeChallenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectUrl = result.getResponse().getRedirectedUrl();
        // 基本パラメータの存在を検証
        org.assertj.core.api.Assertions.assertThat(redirectUrl).contains("response_type=code");
        org.assertj.core.api.Assertions.assertThat(redirectUrl).contains("client_id=" + clientId);
        org.assertj.core.api.Assertions.assertThat(redirectUrl).contains("redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));
        org.assertj.core.api.Assertions.assertThat(redirectUrl).contains("scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8));
        org.assertj.core.api.Assertions.assertThat(redirectUrl).contains("state=" + state);
        // PKCE 関連のパラメータを検証
        org.assertj.core.api.Assertions.assertThat(redirectUrl).contains("code_challenge=" + codeChallenge);
        org.assertj.core.api.Assertions.assertThat(redirectUrl).contains("code_challenge_method=S256");
    }
}