package com.projet3.datasharing.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class JWTServiceTest {

  private static final String SIGNING_KEY = Base64.getEncoder().encodeToString(
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

  @Test
  void generatedTokenCanBeValidatedAndContainsItsSubject() {
    JWTService jwtService = new JWTService(new ObjectMapper(), SIGNING_KEY, 60_000);

    String token = jwtService.generateToken("user@example.com");

    assertEquals("user@example.com", jwtService.validateAndExtractSubject(token));
  }

  @Test
  void rejectsTokenWithModifiedSignature() {
    JWTService jwtService = new JWTService(new ObjectMapper(), SIGNING_KEY, 60_000);
    String token = jwtService.generateToken("user@example.com");
    int signatureStart = token.lastIndexOf('.') + 1;
    char firstSignatureCharacter = token.charAt(signatureStart);
    char replacement = firstSignatureCharacter == 'a' ? 'b' : 'a';
    String changedToken = token.substring(0, signatureStart) + replacement
        + token.substring(signatureStart + 1);

    assertThrows(IllegalArgumentException.class, () -> jwtService.validateAndExtractSubject(changedToken));
  }

  @Test
  void rejectsExpiredToken() throws InterruptedException {
    JWTService jwtService = new JWTService(new ObjectMapper(), SIGNING_KEY, 1);
    String token = jwtService.generateToken("user@example.com");

    Thread.sleep(Duration.ofMillis(50));

    assertThrows(IllegalArgumentException.class, () -> jwtService.validateAndExtractSubject(token));
  }

  @Test
  void rejectsSigningKeyShorterThan256Bits() {
    String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

    assertThrows(IllegalArgumentException.class,
        () -> new JWTService(new ObjectMapper(), shortKey, 60_000));
  }
}
