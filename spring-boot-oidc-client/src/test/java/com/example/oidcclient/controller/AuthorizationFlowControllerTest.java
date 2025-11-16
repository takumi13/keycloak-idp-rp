package com.example.oidcclient.controller;

import com.example.oidcclient.config.properties.AppPathProperties;
import com.example.oidcclient.config.properties.OidcClientProperties;
import com.example.oidcclient.service.PkceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class AuthorizationFlowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PkceService pkceService;

    @Autowired
    private OidcClientProperties oidcClientProperties;

    @Autowired
    private AppPathProperties appPathProperties;

    private String buildAuthEndpoint() {
        String host = oidcClientProperties.getHost() == null ? "" : oidcClientProperties.getHost().trim();
        String ctx = oidcClientProperties.getContextPath() == null ? "" : oidcClientProperties.getContextPath().trim();

        if (host.endsWith("/")) host = host.substring(0, host.length() - 1);
        if (!ctx.startsWith("/")) ctx = "/" + ctx;
        if (ctx.endsWith("/")) ctx = ctx.substring(0, ctx.length() - 1);

        return host + ctx + "/auth";
    }

    @Test
    public void showForm_returnsOk() throws Exception {
        mockMvc.perform(get(appPathProperties.getAuthorizationFlow()))
                .andExpect(status().isOk());
    }

    @Test
    public void postAuthorize_buildsAuthUrlAndRedirects() throws Exception {
        String endpoint = "https://localhost:8443/realms/myrealm/protocol/openid-connect/auth";
        String responseType = "code";
        String clientId = "semi_client";
        String redirectUri = "https://localhost:8081/callback";
        String scope = "openid profile";
        String state = "xyz";

        MvcResult result = mockMvc.perform(post(appPathProperties.getAuthorize())
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
        String redirectUri = "https://localhost:8081/callback";
        String scope = "openid profile";
        String state = "xyz";
        String codeVerifier = "TKwV36z4a0lK9fIupr2yThIjvy7y1nGDE6VQ6ikM2nU";
        // クライアント側で生成する code_challenge をテスト側でも算出して送信する
        String codeChallenge = pkceService.generateChallenge(codeVerifier);

        MvcResult result = mockMvc.perform(post(appPathProperties.getAuthorize())
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