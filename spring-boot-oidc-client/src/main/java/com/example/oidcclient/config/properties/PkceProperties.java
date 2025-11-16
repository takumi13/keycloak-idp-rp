package com.example.oidcclient.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "application.pkce")
public class PkceProperties {

    @Min(43)
    @Max(128)
    private int codeVerifierSize = 64;

    public int getCodeVerifierSize() {
        return codeVerifierSize;
    }

    public void setCodeVerifierSize(int codeVerifierSize) {
        this.codeVerifierSize = codeVerifierSize;
    }
}
