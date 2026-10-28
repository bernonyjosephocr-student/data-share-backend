package com.projet3.datasharing.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
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
	 */
	public String generateToken(String subject) {
		if (subject == null || subject.isBlank()) {
			throw new IllegalArgumentException("JWT subject must not be blank");
		}

		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plusMillis(expirationMillis);
		try {
			String header = encodeJson(Map.of("alg", JWT_ALGORITHM, "typ", "JWT"));
			String payload = encodeJson(Map.of(
					"sub", subject,
					"iat", issuedAt.getEpochSecond(),
					"exp", expiresAt.toEpochMilli() / 1000.0));
			String content = header + "." + payload;
			return content + "." + URL_ENCODER.encodeToString(sign(content));
		} catch (JacksonException exception) {
			throw new IllegalStateException("Unable to serialize JWT claims", exception);
		}
	}

	/**
	 * Validates the token signature and expiry and return the token subject.
	 */
	public String validateAndExtractSubject(String token) {
		if (token == null || token.isBlank()) {
			throw new IllegalArgumentException("JWT must not be blank");
		}

		String[] segments = token.split("\\.", -1);
		if (segments.length != 3) {
			throw new IllegalArgumentException("JWT must contain three segments");
		}

		try {
			JsonNode header = objectMapper.readTree(URL_DECODER.decode(segments[0]));
			if (header == null || !JWT_ALGORITHM.equals(header.path("alg").asString())) {
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
			String subject = payload.path("sub").asString();
			JsonNode expiration = payload.get("exp");
			if (subject.isBlank() || expiration == null || !expiration.isNumber()
					|| expiration.doubleValue() <= Instant.now().toEpochMilli() / 1000.0) {
				throw new IllegalArgumentException("JWT subject is missing or the token has expired");
			}
			return subject;
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
