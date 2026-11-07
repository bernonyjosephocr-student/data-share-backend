package com.projet3.datasharing.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.projet3.datasharing.backend.entity.UserAccount;
import com.projet3.datasharing.backend.entity.file.FileMetadata;
import com.projet3.datasharing.backend.repository.UserAccountRepository;
import com.projet3.datasharing.backend.repository.file.FileMetadataRepository;
import com.projet3.datasharing.backend.service.JWTService;
import com.projet3.datasharing.backend.service.storage.StorageService;

import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.expiration-ms=3600000",
        "file.forbidden-extensions=exe,bat,cmd,sh",
        "file.max-file-size=1GB",
        "level.com.projet3.datasharing.backend=DEBUG"
})
class DataShareBackendApplicationTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @MockitoBean
    private StorageService storageService;

    @Test
    void contextLoads() {
    }

    @Test
    void registrationPersistsAccountWithEncodedPassword() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        String email = UUID.randomUUID() + "@example.com";
        String password = "securePassword123";

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());

        UserAccount user = userAccountRepository.findByEmail(email).orElseThrow();
        assertTrue(passwordEncoder.matches(password, user.getPasswordHash()));
        assertNotNull(user.getCreatedAt());
    }

    @Test
    void loginReturnsJwtForValidCredentials() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        String email = UUID.randomUUID() + "@example.com";
        String password = "securePassword123";

        userAccountRepository.save(createUser(email, password));

        var result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asString();

        assertEquals(email, jwtService.validateAndExtractSubject(token));
    }

    @Test
    void loginRejectsInvalidCredentials() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        String email = UUID.randomUUID() + "@example.com";

        userAccountRepository.save(createUser(email, "securePassword123"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtFilterProtectsRoutesAndAcceptsValidUserTokens() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        String email = UUID.randomUUID() + "@example.com";
        userAccountRepository.save(createUser(email, "securePassword123"));

        mockMvc.perform(get("/api/private"))
                .andExpect(status().isUnauthorized());

        String token = jwtService.generateToken(email);
        mockMvc.perform(get("/api/private").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/private").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUploadPersistsMetadataAndReturnsOnlyDownloadToken() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        String email = UUID.randomUUID() + "@example.com";
        UserAccount user = userAccountRepository.save(createUser(email, "securePassword123"));
        String authToken = jwtService.generateToken(email);

        // 1. the physical file
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[] { 1, 2 });

        // 2. creation of the JSON part
        String metadataJson = """
                {
                    "filePassword": "file-password",
                    "tags": "reports,work",
                    "retentionDays": 2
                }
                """;
        MockMultipartFile metadataPart = new MockMultipartFile(
                "metadata", // the @RequestPart("metadata") in the contrôleur
                "",
                MediaType.APPLICATION_JSON_VALUE,
                metadataJson.getBytes());

        // 3. Execution of the request with the 2 pieces ot the multipart
        var result = mockMvc.perform(multipart("/api/auth/files")
                .file(file)
                .file(metadataPart) // On injecte la partie metadata JSON ici !
                .header("Authorization", "Bearer " + authToken)
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.downloadToken").isNotEmpty()) // 💡 Attention aux casses (downloadToken vs
                                                                     // downLoadToken)
                .andExpect(jsonPath("$.id").doesNotExist())
                .andReturn();

        String downloadToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("downloadToken").asString();
        assertEquals(1, objectMapper.readTree(result.getResponse().getContentAsString()).size());

        String uploadId = jwtService.validateAndExtractDownloadSubject(downloadToken);
        FileMetadata metadata = fileMetadataRepository.findAll().stream()
                .filter(saved -> saved.getName().equals("report.pdf"))
                .filter(saved -> saved.getUserAccount().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, metadata.getRetentionDays());
        assertEquals(2, metadata.getFileSize());
        assertEquals(metadata.getCreatedAt().plusDays(2), metadata.getExpiryDate());
        assertTrue(passwordEncoder.matches("file-password", metadata.getFilePassword()));
        assertEquals(downloadToken, metadata.getDownloadToken());
        org.mockito.Mockito.verify(storageService).store(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(uploadId));
    }

    private UserAccount createUser(String email, String password) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        return user;
    }
}
