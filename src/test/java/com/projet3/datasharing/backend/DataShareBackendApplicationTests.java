package com.projet3.datasharing.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projet3.datasharing.backend.entity.UserAccount;
import com.projet3.datasharing.backend.repository.UserAccountRepository;
import com.projet3.datasharing.backend.service.JWTService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {
      "level.com.projet3.datasharing.backend=DEBUG",
      "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
    })
class DataShareBackendApplicationTests {

  @Autowired private UserAccountRepository userAccountRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JWTService jwtService;

  @Autowired private MockMvc mockMvc;

  @Test
  void contextLoads() {}

  // This test regroups FILTER-INT-01, -02 et 03
  @Test
  void jwtFilterProtectsRoutesAndAcceptsValidUserTokens() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    userAccountRepository.save(createUser(email, "securePassword123"));

    mockMvc.perform(get("/api/private")).andExpect(status().isUnauthorized());

    String token = jwtService.generateToken(email);
    mockMvc
        .perform(get("/api/private").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/private").header("Authorization", "Bearer invalid.token.value"))
        .andExpect(status().isUnauthorized());
  }

  private UserAccount createUser(String email, String password) {
    UserAccount user = new UserAccount();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    return user;
  }
}
