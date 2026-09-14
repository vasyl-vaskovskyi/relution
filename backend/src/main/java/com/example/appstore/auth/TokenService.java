package com.example.appstore.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Checks client credentials and issues short-lived access tokens (ADR-0004, ADR-0034). */
@Service
public class TokenService {

    /** The only scope; required on every {@code /api/v1/**} request. */
    public static final String SCOPE = "apps:read";

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    @Autowired
    public TokenService(JwtEncoder encoder, AuthProperties properties) {
        this(encoder, properties, Clock.systemUTC());
    }

    TokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** Compares both values in constant time; both comparisons always run. */
    public boolean credentialsMatch(String clientId, String clientSecret) {
        boolean idMatches = constantTimeEquals(clientId, properties.client().id());
        boolean secretMatches =
                constantTimeEquals(clientSecret, properties.client().secret());
        return idMatches & secretMatches;
    }

    public IssuedToken issue(String clientId) {
        Instant now = clock.instant();
        Duration ttl = properties.jwt().ttl();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .audience(List.of(properties.jwt().audience()))
                .subject(clientId)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("scope", SCOPE)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, ttl.toSeconds());
    }

    /** The encoded token; {@code toString} never prints it. */
    public record IssuedToken(String value, long expiresInSeconds) {

        @Override
        public String toString() {
            return "IssuedToken[expiresInSeconds=" + expiresInSeconds + ", value=***]";
        }
    }

    /** Hashing first gives equal lengths, so the comparison doesn't leak the configured length. */
    static boolean constantTimeEquals(String given, String expected) {
        if (given == null) {
            given = "";
        }
        return MessageDigest.isEqual(sha256(given), sha256(expected));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
