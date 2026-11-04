package com.projet3.datasharing.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Create and verify that JWT token signed in HS256.
 */
@Service
public class JWTService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String JWT_ALGORITHM = "HS256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final SecretKeySpec signingKey;
    private final long expirationMillis;

    public JWTService(
            ObjectMapper objectMapper,
            @Value("${security.jwt.secret-base64}") String secretBase64,
            @Value("${security.jwt.expiration-ms}") long expirationMillis) {
        this.objectMapper = objectMapper;
        this.signingKey = createSigningKey(secretBase64);
        if (expirationMillis <= 0) {
            throw new IllegalArgumentException("JWT expiration must be greater than zero milliseconds");
        }
        this.expirationMillis = expirationMillis;
    }

    /**
     * Signs a JWT token in which the subject is identified by the user account.
     * 
     * @param subject
     * @return
     */
    public String generateToken(String subject) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusMillis(expirationMillis);
        return buildToken(subject, issuedAt, expiresAt, Map.of());
    }

    /**
     * Signs a JWT Download token in which the subject is identified by a UUID.
     * 
     * @param subject
     * @param userId
     * @param expiresAt
     * @return
     */
    public String generateDownloadToken(String uUID, Long userId, Instant expiresAt,
            Map<String, Object> extraClaimsParam) {
        Map<String, Object> extraClaims = new HashMap<>(extraClaimsParam);
        extraClaims.put("action", "DOWNLOAD");
        if (userId != null) {
            extraClaims.put("userId", userId);
        }

        return buildToken(uUID, Instant.now(), expiresAt, extraClaims);
    }

    /**
     * 
     * @param token
     * @return
     */
    public String validateAndExtractSubject(String token) {
        JsonNode payload = parseAndValidateToken(token);
        String subject = payload.path("sub").asText(); // .asText() est plus sûr que .asString() selon la version de
                                                       // Jackson

        JsonNode expiration = payload.get("exp");
        if (subject.isBlank() || expiration == null || !expiration.isNumber()
                || expiration.doubleValue() <= Instant.now().toEpochMilli() / 1000.0) {
            throw new IllegalArgumentException("JWT subject is missing or the token has expired");
        }
        return subject;
    }

    /**
     * 
     * @param token
     * @return
     */
    public String validateAndExtractDownloadSubject(String token) {
        JsonNode payload = parseAndValidateToken(token);

        // it is expected to be DOWNLOAD
        String action = payload.path("action").asString();
        if (!"DOWNLOAD".equals(action)) {
            throw new IllegalArgumentException("Invalid token action context");
        }

        return validateAndExtractSubject(token);
    }

    /**
     * Build a token
     * 
     * @param subject
     * @param issuedAt
     * @param expiresAt
     * @param extraClaims
     * @return the built token of format HEADER.PAYLOAD.SIGNATURE
     */
    private String buildToken(String subject, Instant issuedAt, Instant expiresAt, Map<String, Object> extraClaims) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject must not be blank");
        }

        try {
            String header = encodeJson(Map.of("alg", JWT_ALGORITHM, "typ", "JWT"));

            // adding payload basic claims + optional claims
            Map<String, Object> payloadClaims = new HashMap<>(extraClaims);
            payloadClaims.put("sub", subject);
            payloadClaims.put("iat", issuedAt.getEpochSecond());
            // exp is in seconds since epoch, not milliseconds
            payloadClaims.put("exp", expiresAt.toEpochMilli() / 1000.0);

            String payload = encodeJson(payloadClaims);
            String content = header + "." + payload;

            return content + "." + URL_ENCODER.encodeToString(sign(content));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize JWT claims", exception);
        }
    }

    /**
     * Parse and validate the token
     * 
     * @param token
     * @return the token payload
     */

    private JsonNode parseAndValidateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT must not be blank");
        }

        String[] segments = token.split("\\.", -1);
        if (segments.length != 3) {
            throw new IllegalArgumentException("JWT must contain three segments");
        }

        try {
            JsonNode header = objectMapper.readTree(URL_DECODER.decode(segments[0]));
            if (header == null || !JWT_ALGORITHM.equals(header.path("alg").asText())) {
                throw new IllegalArgumentException("Unsupported JWT signing algorithm");
            }

            byte[] suppliedSignature = URL_DECODER.decode(segments[2]);
            byte[] expectedSignature = sign(segments[0] + "." + segments[1]);
            if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
                throw new IllegalArgumentException("JWT signature is invalid");
            }

            JsonNode payload = objectMapper.readTree(URL_DECODER.decode(segments[1]));
            if (payload == null) {
                throw new IllegalArgumentException("JWT payload is missing");
            }

            return payload;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT is malformed or invalid", exception);
        }
    }

    private String encodeJson(Object value) {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(content.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign JWT", exception);
        }
    }

    private SecretKeySpec createSigningKey(String secretBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(secretBase64);
            if (keyBytes.length < 32) {
                throw new IllegalArgumentException("JWT signing key must contain at least 256 bits");
            }
            return new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT signing key must be valid Base64 with at least 256 bits",
                    exception);
        }
    }
}
