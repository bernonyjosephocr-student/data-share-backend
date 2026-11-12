package com.projet3.datasharing.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class JWTServiceTest {

  private static final String SIGNING_KEY = Base64.getEncoder().encodeToString(
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

  private final ObjectMapper objectMapper = new ObjectMapper();

  // Verifies that a generated JWT contains the expected subject, expiration and
  // signature.
  @Test
  void shouldGenerateAuthenticationToken() throws Exception {
    JWTService jwtService = new JWTService(objectMapper, SIGNING_KEY, 60_000);

    String token = jwtService.generateToken("user@example.com");

    String[] segments = token.split("\\.");
    assertEquals(3, segments.length);

    JsonNode header = objectMapper.readTree(
        Base64.getUrlDecoder().decode(segments[0]));
    JsonNode payload = objectMapper.readTree(
        Base64.getUrlDecoder().decode(segments[1]));

    assertEquals("HS256", header.path("alg").asText());
    assertEquals("user@example.com", payload.path("sub").asText());
    assertTrue(payload.has("exp"));
    assertNotNull(segments[2]);
  }

  // Verifies that a valid and non-expired authentication token returns its
  // subject.
  @Test
  void shouldValidateAuthenticationTokenAndExtractSubject() {
    JWTService jwtService = new JWTService(objectMapper, SIGNING_KEY, 60_000);

    String token = jwtService.generateToken("user@example.com");

    assertEquals("user@example.com", jwtService.validateAndExtractSubject(token));
  }

  // Verifies that a generated download token contains the expected claims.
  @Test
  void shouldGenerateDownloadToken() throws Exception {
    JWTService jwtService = new JWTService(objectMapper, SIGNING_KEY, 60_000);

    String token = jwtService.generateDownloadToken(
        "upload-uuid",
        42L,
        Instant.now().plusSeconds(60),
        Map.of());

    String[] segments = token.split("\\.");
    JsonNode payload = objectMapper.readTree(
        Base64.getUrlDecoder().decode(segments[1]));

    assertEquals(3, segments.length);
    assertEquals("upload-uuid", payload.path("sub").asText());
    assertEquals("DOWNLOAD", payload.path("action").asText());
    assertEquals(42L, payload.path("userId").asLong());
    assertTrue(payload.has("exp"));
  }

  // Verifies that a valid and non-expired download token returns its upload
  // identifier.
  @Test
  void shouldValidateDownloadTokenAndExtractSubject() {
    JWTService jwtService = new JWTService(objectMapper, SIGNING_KEY, 60_000);

    String token = jwtService.generateDownloadToken(
        "upload-uuid",
        42L,
        Instant.now().plusSeconds(60),
        Map.of());

    assertEquals("upload-uuid", jwtService.validateAndExtractDownloadSubject(token));
  }

  // Verifies that the subject of an expired download token can still be
  // extracted.
  @Test
  void shouldExtractSubjectFromExpiredDownloadToken() {
    JWTService jwtService = new JWTService(objectMapper, SIGNING_KEY, 60_000);

    String token = jwtService.generateDownloadToken(
        "upload-uuid",
        42L,
        Instant.now().minusSeconds(60),
        Map.of());

    assertEquals("upload-uuid",
        jwtService.validateAndExtractExpiredOrNotTokenSubject(token));
  }
}