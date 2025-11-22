package com.example.oidcclient.http;

import com.example.oidcclient.config.properties.MtlsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * Builds a reusable {@link HttpClient} configured with the application's mTLS keystore/truststore so
 * outbound calls (token endpoint, JWKS fetch) share the same TLS settings.
 */
@Component
public class KeycloakHttpClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakHttpClientFactory.class);

    private final MtlsProperties mtlsProperties;
    private final SSLContext sslContext;
    private final HttpClient httpClient;

    public KeycloakHttpClientFactory(MtlsProperties mtlsProperties) throws Exception {
        this.mtlsProperties = mtlsProperties;
        this.sslContext = buildSslContext();
        this.httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    private SSLContext buildSslContext() throws Exception {
        logger.debug("Initializing Keycloak SSLContext using keystore {} and truststore {}",
                mtlsProperties.getKeyStore(), mtlsProperties.getTrustStore());

        KeyStore keyStore = KeyStore.getInstance(mtlsProperties.getKeyStoreType());
        try (InputStream ksStream = getResource(mtlsProperties.getKeyStore())) {
            if (ksStream == null) {
                throw new IllegalStateException("mTLS key-store not found in classpath: " + mtlsProperties.getKeyStore());
            }
            keyStore.load(ksStream, mtlsProperties.getKeyStorePassword().toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, mtlsProperties.getKeyStorePassword().toCharArray());

        KeyStore trustStore = KeyStore.getInstance(mtlsProperties.getTrustStoreType());
        try (InputStream tsStream = getResource(mtlsProperties.getTrustStore())) {
            if (tsStream == null) {
                throw new IllegalStateException("mTLS trust-store not found in classpath: " + mtlsProperties.getTrustStore());
            }
            trustStore.load(tsStream, mtlsProperties.getTrustStorePassword().toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return context;
    }

    private InputStream getResource(String path) {
        return getClass().getClassLoader().getResourceAsStream(path);
    }
}
