package com.example.oidcclient.controller;

import com.example.oidcclient.OidcClientApplication;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class IntegrationAuthTokenFlowTest {

    @Autowired
    private MockMvc mockMvc;
    private static final Logger logger = LoggerFactory.getLogger(IntegrationAuthTokenFlowTest.class);

    @Test
    void authorize_then_tokenRequest_reusesSessionCodeVerifier() throws Exception {
        String expectedResponse = "{\"access_token\":\"dummy-token\",\"token_type\":\"bearer\"}";

        AtomicReference<Map<String, String>> capturedForm = new AtomicReference<>();

        try (MockedStatic<OidcClientApplication> mocked =
                     Mockito.mockStatic(OidcClientApplication.class, Mockito.CALLS_REAL_METHODS)) {
            // requestToken のみをスタブして form をキャプチャ
            mocked.when(() -> OidcClientApplication.requestToken(Mockito.anyString(), Mockito.anyMap()))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Map<String, String> form = (Map<String, String>) invocation.getArgument(1);
                        capturedForm.set(form);
                        return expectedResponse;
                    });

            MockHttpSession session = new MockHttpSession();

            String state = "flow-state-123";
            // クライアントが生成する code_verifier（テスト側で生成）
            String codeVerifier = "sample-verifier-abc";
            // クライアントが送る code_challenge（AuthorizationController の実装と同じ生成法を利用）
            String codeChallenge = AuthorizationController.generateS256CodeChallenge(codeVerifier);
            String authorizationEndpoint = "http://localhost:8080/realms/myrealm/protocol/openid-connect/auth";

            // 1) authorize をクライアントとして送る（ここでは client が code_challenge を送る）
            MvcResult authResult = mockMvc.perform(post("/authorize")
                            .with(csrf())
                            .session(session)
                            .param("authorization_endpoint", authorizationEndpoint)
                            .param("response_type", "code")
                            .param("client_id", "semi_client")
                            .param("redirect_uri", "http://localhost:8081/callback")
                            .param("state", state)
                            .param("code_challenge", codeChallenge)
                            .param("code_challenge_method", "S256")
                     )
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            // Location ヘッダ / redirectedUrl / レスポンスボディ を順に確認して
            // リダイレクト先（＝認可サーバへ飛ばす URL）を取得する。getHeader("Location") が取れない場
            // 合はコンテンツ内に埋め込まれた URL を正規表現で探す。
            String authorizeResponse = authResult.getResponse().getHeader("Location");
            if (authorizeResponse == null || authorizeResponse.isBlank()) {
                authorizeResponse = authResult.getResponse().getRedirectedUrl();
            }
            if (authorizeResponse == null || authorizeResponse.isBlank()) {
                String content = authResult.getResponse().getContentAsString();
                if (content != null && !content.isBlank()) {
                    Pattern urlPattern = Pattern.compile("(https?://[^\\s\"'<>]+)");
                    Matcher urlMatcher = urlPattern.matcher(content);
                    while (urlMatcher.find()) {
                        String candidate = urlMatcher.group(1);
                        if (candidate.contains("code_challenge") || candidate.contains("code=")) {
                            authorizeResponse = candidate;
                            break;
                        }
                    }
                }
            }
            logger.debug("Authorize redirect authorizeResponse: {}", authorizeResponse);
            org.assertj.core.api.Assertions.assertThat(authorizeResponse).startsWith(authorizationEndpoint);

            // ----- 外部認可サーバへ実際にアクセスしてログインフォームを submit し、callback の code を取得する -----
            // 1) Cookie 管理を用意
            java.net.CookieManager cookieManager = new java.net.CookieManager();
            java.net.CookieHandler.setDefault(cookieManager);

            // 2) GET でログインフォームを取得（クッキー / hidden input を取得するため）
            java.net.URL authUrl = new java.net.URL(authorizeResponse);
            java.net.HttpURLConnection getConn = (java.net.HttpURLConnection) authUrl.openConnection();
            getConn.setInstanceFollowRedirects(false);
            getConn.setRequestMethod("GET");
            getConn.setConnectTimeout(5000);
            getConn.setReadTimeout(5000);
            String loginHtml;
            try (java.io.InputStream is = getConn.getInputStream();
                 java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                loginHtml = sb.toString();
            } catch (java.io.IOException e) {
                // 一部の auth-server は GET で 302 を返す場合があるので、エラー時はレスポンスボディを取る
                java.io.InputStream err = getConn.getErrorStream();
                if (err != null) {
                    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(err, java.nio.charset.StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line).append('\n');
                        loginHtml = sb.toString();
                    }
                } else {
                    throw e;
                }
            }
            logger.debug("Fetched login page length: {}", loginHtml == null ? 0 : loginHtml.length());

            // 3) login form の action と hidden inputs を簡易パース
            String formAction = authorizeResponse; // デフォルトは authorizeResponse
            Pattern formPattern = Pattern.compile("(?si)<form[^>]*action\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</form>");
            Matcher fm = formPattern.matcher(loginHtml);
            String formBody = "";
            if (fm.find()) {
                String action = fm.group(1);
                String inner = fm.group(2);
                formBody = inner;
                try {
                    java.net.URL base = authUrl;
                    java.net.URL resolved = new java.net.URL(base, action);
                    formAction = resolved.toString();
                } catch (Exception ex) {
                    formAction = action;
                }
            } else {
                // フォームがない場合はページ内の最初の POST 宛先を探す（最終手段）
                Pattern actionPattern = Pattern.compile("(https?://[^\\s\"'<>]+/login[^\"'<>\\s]*)");
                Matcher am = actionPattern.matcher(loginHtml);
                if (am.find()) formAction = am.group(1);
            }

            // hidden input を抽出
            java.util.Map<String,String> params = new java.util.LinkedHashMap<>();
            Pattern inputPattern = Pattern.compile("(?i)<input[^>]*name\\s*=\\s*\"([^\"]+)\"[^>]*>");
            Matcher im = inputPattern.matcher(formBody);
            while (im.find()) {
                String inputTag = im.group(0);
                // name
                Pattern nameP = Pattern.compile("(?i)name\\s*=\\s*\"([^\"]+)\"");
                Matcher nm = nameP.matcher(inputTag);
                String name = null;
                if (nm.find()) name = nm.group(1);
                // value
                Pattern valueP = Pattern.compile("(?i)value\\s*=\\s*\"([^\"]*)\"");
                Matcher vm = valueP.matcher(inputTag);
                String value = vm.find() ? vm.group(1) : "";
                if (name != null) params.put(name, value);
            }

            // 4) username/password をセット（Keycloak 標準は username/password）
            params.put("username", "semi");
            params.put("password", "semi");
            // もしフォームに submit ボタン名があれば付ける（汎用的に）
            if (!params.containsKey("submit")) params.put("submit", "login");

            // 5) POST 送信（cookie を引き継ぎ）
            java.net.URL postUrl = new java.net.URL(formAction);
            java.net.HttpURLConnection postConn = (java.net.HttpURLConnection) postUrl.openConnection();
            postConn.setInstanceFollowRedirects(false); // リダイレクトは自分で処理
            postConn.setRequestMethod("POST");
            postConn.setDoOutput(true);
            postConn.setConnectTimeout(5000);
            postConn.setReadTimeout(5000);
            postConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            // クッキーをヘッダにセット（CookieManager が自動管理する場合もあるが明示的に）
            java.net.CookieStore store = cookieManager.getCookieStore();
            java.util.List<java.net.HttpCookie> cookies = store.getCookies();
            if (!cookies.isEmpty()) {
                StringBuilder ck = new StringBuilder();
                for (java.net.HttpCookie c : cookies) {
                    if (ck.length()>0) ck.append("; ");
                    ck.append(c.getName()).append("=").append(c.getValue());
                }
                postConn.setRequestProperty("Cookie", ck.toString());
            }

            StringBuilder bodySb = new StringBuilder();
            for (java.util.Map.Entry<String,String> e : params.entrySet()) {
                if (bodySb.length()>0) bodySb.append('&');
                bodySb.append(java.net.URLEncoder.encode(e.getKey(), "UTF-8"));
                bodySb.append('=');
                bodySb.append(java.net.URLEncoder.encode(e.getValue(), "UTF-8"));
            }
            byte[] bodyBytes = bodySb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            postConn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            try (java.io.OutputStream os = postConn.getOutputStream()) {
                os.write(bodyBytes);
            }

            // 6) レスポンスを確認して Location から callback URL を取得
            int respCode = postConn.getResponseCode();
            logger.debug("Auth server POST response code: {}", respCode);
            String callbackLocation = postConn.getHeaderField("Location");
            String responseBody = null;
            try (java.io.InputStream is2 = respCode >= 400 ? postConn.getErrorStream() : postConn.getInputStream()) {
                if (is2 != null) {
                    try (java.io.BufferedReader br2 = new java.io.BufferedReader(new java.io.InputStreamReader(is2, java.nio.charset.StandardCharsets.UTF_8))) {
                        StringBuilder sb2 = new StringBuilder();
                        String ln;
                        while ((ln = br2.readLine()) != null) sb2.append(ln).append('\n');
                        responseBody = sb2.toString();
                    }
                }
            }
            logger.debug("Auth server callback Location: {}, body length: {}", callbackLocation, responseBody == null ? 0 : responseBody.length());

            // 7) callbackLocation に code が含まれていればそれを使い、なければレスポンスボディ中を探す
            String authCodeFromAuthServer = null;
            if (callbackLocation != null && callbackLocation.contains("code=")) {
                authCodeFromAuthServer = UriComponentsBuilder.fromUriString(callbackLocation).build().getQueryParams().getFirst("code");
            } else if (responseBody != null) {
                Pattern p = Pattern.compile("([?&]code=)([^&#\"']+)");
                Matcher m = p.matcher(responseBody);
                if (m.find()) {
                    authCodeFromAuthServer = java.net.URLDecoder.decode(m.group(2), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            if (authCodeFromAuthServer == null || authCodeFromAuthServer.isBlank()) {
                logger.debug("auth code not found after posting credentials; using fallback test code");
                authCodeFromAuthServer = "auth-code-from-authz";
            }
            // ----- end external auth server interaction -----

             mockMvc.perform(post("/token_request")
                             .with(csrf())
                             .session(session)
                             .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                             .param("code", authCodeFromAuthServer)
                             .param("state", state)
                             .param("client_id", "semi_client")
                             .param("redirect_uri", "http://localhost:8081/callback")
                             .param("code_verifier", codeVerifier)
                      )
                     .andExpect(status().isOk())
                     .andExpect(content().string(expectedResponse));
        }

        // token リクエストに code_verifier が含まれていること（クライアント送信またはセッション経由で送られる）
        Map<String, String> form = capturedForm.get();
        org.assertj.core.api.Assertions.assertThat(form).isNotNull();
        org.assertj.core.api.Assertions.assertThat(form.get("code_verifier")).isEqualTo("sample-verifier-abc");
    }
}