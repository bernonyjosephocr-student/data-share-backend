package com.projet3.datasharing.backend.controller.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.projet3.datasharing.backend.TestcontainersConfiguration;
import com.projet3.datasharing.backend.entity.UserAccount;
import com.projet3.datasharing.backend.repository.UserAccountRepository;
import com.projet3.datasharing.backend.service.JWTService;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.expiration-ms=3600000"
})
@AutoConfigureMockMvc // This asks Sprign to configure the MockMvc with automatic Spring security. No
                      // need to build the Mock MVC manually.
@Import(TestcontainersConfiguration.class)
class UserAccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Tests that registration persists a user account with an encoded password.
     */
    @Test
    void shouldRegisterUserWithEncodedPassword() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String password = "securePassword123";

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "%s",
                            "password": "%s"
                        }
                        """.formatted(email, password)))
                .andExpect(status().isCreated());

        UserAccount user = userAccountRepository.findByEmail(email).orElseThrow();

        assertTrue(passwordEncoder.matches(password, user.getPasswordHash()));
        assertNotNull(user.getCreatedAt());
    }

    /**
     * Tests that valid credentials return a JWT containing the user's email.
     */
    @Test
    void shouldReturnJwtForValidCredentials() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String password = "securePassword123";

        userAccountRepository.save(createUser(email, password));

        var result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "%s",
                            "password": "%s"
                        }
                        """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String token = extractToken(result.getResponse().getContentAsString());

        assertEquals(email, jwtService.validateAndExtractSubject(token));
    }

    /**
     * Tests that login rejects credentials containing an incorrect password.
     */
    @Test
    void shouldRejectLoginWithInvalidPassword() throws Exception {
        String email = UUID.randomUUID() + "@example.com";

        userAccountRepository.save(createUser(email, "securePassword123"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "%s",
                            "password": "wrong-password"
                        }
                        """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    private UserAccount createUser(String email, String password) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        return user;
    }

    private String extractToken(String response) throws Exception {
        return objectMapper.readTree(response).path("token").asString();
    }
}