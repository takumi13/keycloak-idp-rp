package com.example.oidcclient;

import com.example.oidcclient.config.properties.MtlsProperties;
import com.example.oidcclient.http.KeycloakHttpClientFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.net.ssl.SSLContext;
import java.io.InputStream;

@SpringBootTest
@Tag("mtls")
@EnabledIfEnvironmentVariable(named = "ENABLE_MTLS_TESTS", matches = "true")
class TokenClientServiceMtlsSmokeTest {

    @Autowired
    private KeycloakHttpClientFactory httpClientFactory;

    @Autowired
    private MtlsProperties mtlsProperties;

    @Test
    void buildsSslContextUsingBundledStores() throws Exception {
        SSLContext context = httpClientFactory.getSslContext();
        Assertions.assertThat(context).isNotNull();
        Assertions.assertThat(context.getSupportedSSLParameters().getCipherSuites()).isNotEmpty();
    }

    @Test
    void keystoreAndTruststoreResourcesAreResolvable() throws Exception {
        Assertions.assertThat(resourceExists(mtlsProperties.getKeyStore())).isTrue();
        Assertions.assertThat(resourceExists(mtlsProperties.getTrustStore())).isTrue();
    }

    private boolean resourceExists(String path) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            return stream != null && stream.available() >= 0;
        }
    }
}
