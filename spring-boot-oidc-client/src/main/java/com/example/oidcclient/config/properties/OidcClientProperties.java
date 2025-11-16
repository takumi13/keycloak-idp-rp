package com.example.oidcclient.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "application.oidc")
public class OidcClientProperties {

    /** Base host for the Keycloak server (e.g., https://localhost:8443). */
    @NotBlank
    private String host = "https://localhost:8443";

    /** Realm context path (e.g., /realms/myrealm/protocol/openid-connect). */
    @NotBlank
    private String contextPath = "/realms/myrealm/protocol/openid-connect";

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }
}
