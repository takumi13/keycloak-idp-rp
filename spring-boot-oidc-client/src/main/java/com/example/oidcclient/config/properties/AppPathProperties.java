package com.example.oidcclient.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "application.path")
public class AppPathProperties {

    @NotBlank
    private String root = "/";

    @NotBlank
    private String authorizationFlow = "/authorization-flow";

    @NotBlank
    private String authorize = "/authorize";

    @NotBlank
    private String home = "/home";

    @NotBlank
    private String callback = "/callback";

    @NotBlank
    private String tokenRequest = "/token-request";

    @NotBlank
    private String token = "/token";

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getAuthorizationFlow() {
        return authorizationFlow;
    }

    public void setAuthorizationFlow(String authorizationFlow) {
        this.authorizationFlow = authorizationFlow;
    }

    public String getAuthorize() {
        return authorize;
    }

    public void setAuthorize(String authorize) {
        this.authorize = authorize;
    }

    public String getHome() {
        return home;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public String getCallback() {
        return callback;
    }

    public void setCallback(String callback) {
        this.callback = callback;
    }

    public String getTokenRequest() {
        return tokenRequest;
    }

    public void setTokenRequest(String tokenRequest) {
        this.tokenRequest = tokenRequest;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
