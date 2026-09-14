package com.example.appstore.auth;

import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * HS256 signing and validation (ADR-0004, ADR-0034). A secret-key decoder checks only the time claims by default, so
 * issuer and audience validators are added explicitly ({@code docs/architecture/security.md#authentication}).
 */
@Configuration(proxyBeanMethods = false)
class JwtConfiguration {

    static final int MIN_SECRET_BYTES = 32;

    @Bean
    JwtEncoder jwtEncoder(AuthProperties properties) {
        return NimbusJwtEncoder.withSecretKey(signingKey(properties.jwt().secret()))
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(
                        signingKey(properties.jwt().secret()))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        String audience = properties.jwt().audience();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(properties.jwt().issuer()),
                new JwtClaimValidator<List<String>>(
                        JwtClaimNames.AUD, audiences -> audiences != null && audiences.contains(audience))));
        return decoder;
    }

    /** Fails fast with a message that never contains the secret. */
    static SecretKey signingKey(String base64Secret) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Secret.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("appstore.auth.jwt.secret must be Base64");
        }
        if (key.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "appstore.auth.jwt.secret must decode to at least " + MIN_SECRET_BYTES + " bytes");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }
}
