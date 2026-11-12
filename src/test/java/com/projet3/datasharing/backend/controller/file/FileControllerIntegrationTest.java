package com.projet3.datasharing.backend.controller.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.projet3.datasharing.backend.TestcontainersConfiguration;
import com.projet3.datasharing.backend.dto.file.DownloadRequestDTO;
import com.projet3.datasharing.backend.dto.file.UploadRequestDTO;
import com.projet3.datasharing.backend.entity.UserAccount;
import com.projet3.datasharing.backend.entity.file.FileMetadata;
import com.projet3.datasharing.backend.exception.FileNotFoundException;
import com.projet3.datasharing.backend.repository.UserAccountRepository;
import com.projet3.datasharing.backend.repository.file.FileMetadataRepository;
import com.projet3.datasharing.backend.service.JWTService;
import com.projet3.datasharing.backend.service.storage.StorageService;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.expiration-ms=3600000",
        "file.forbidden-extensions=exe,bat,cmd,sh",
        "file.max-file-size=1GB"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private StorageService storageService;

    /**
     * Tests that an authenticated upload persists metadata in PostgreSQL
     * and stores the file content in MinIO.
     */
    @Test
    void shouldUploadFileWithAuthenticatedUser() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        UserAccount user = userAccountRepository.save(createUser(email));
        String authToken = jwtService.generateToken(email);

        byte[] fileContent = { 1, 2, 3, 4 };
        MockMultipartFile file = createFile("report.pdf", fileContent);

        UploadRequestDTO metadataRequest = new UploadRequestDTO(
                "file-password",
                "reports,work",
                2);

        MockMultipartFile metadataPart = createMetadataPart(metadataRequest);

        var result = mockMvc.perform(multipart("/api/auth/files")
                .file(file)
                .file(metadataPart)
                .header("Authorization", "Bearer " + authToken)
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.downloadToken").isNotEmpty())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andReturn();

        String downloadToken = extractDownloadToken(result.getResponse().getContentAsString());
        String uploadId = jwtService.validateAndExtractDownloadSubject(downloadToken);

        FileMetadata savedMetadata = findMetadataByToken(downloadToken);

        assertEquals("report.pdf", savedMetadata.getName());
        assertEquals(user.getId(), savedMetadata.getUserAccount().getId());
        assertEquals(2, savedMetadata.getRetentionDays());
        assertEquals(fileContent.length, savedMetadata.getFileSize());
        assertTrue(passwordEncoder.matches("file-password", savedMetadata.getFilePassword()));
        assertEquals(downloadToken, savedMetadata.getDownloadToken());

        assertArrayEquals(fileContent, storageService.loadAsResources(uploadId)
                .getFirst()
                .getInputStream()
                .readAllBytes());
    }

    /**
     * Tests that an anonymous upload persists metadata without an associated user
     * and stores the file content in MinIO.
     */
    @Test
    void shouldUploadFileWithoutAuthenticatedUser() throws Exception {
        byte[] fileContent = { 5, 6, 7 };
        MockMultipartFile file = createFile("anonymous.pdf", fileContent);

        UploadRequestDTO metadataRequest = new UploadRequestDTO(null, "anonymous", 3);
        MockMultipartFile metadataPart = createMetadataPart(metadataRequest);

        var result = mockMvc.perform(multipart("/api/files")
                .file(file)
                .file(metadataPart)
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.downloadToken").isNotEmpty())
                .andReturn();

        String downloadToken = extractDownloadToken(result.getResponse().getContentAsString());
        String uploadId = jwtService.validateAndExtractDownloadSubject(downloadToken);

        FileMetadata savedMetadata = findMetadataByToken(downloadToken);

        assertEquals("anonymous.pdf", savedMetadata.getName());
        assertNull(savedMetadata.getUserAccount());
        assertEquals(3, savedMetadata.getRetentionDays());

        assertArrayEquals(fileContent, storageService.loadAsResources(uploadId)
                .getFirst()
                .getInputStream()
                .readAllBytes());
    }

    /**
     * Tests that an authenticated user can retrieve the files associated with their
     * account.
     */
    @Test
    void shouldReturnFilesForAuthenticatedUser() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String downloadToken = uploadAuthenticatedFile(email, "user-report.pdf");
        String authToken = jwtService.generateToken(email);

        mockMvc.perform(get("/api/auth/files")
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("user-report.pdf"))
                .andExpect(jsonPath("$[0].downloadToken").value(downloadToken))
                .andExpect(jsonPath("$[0].filePassword").doesNotExist());
    }

    /**
     * Tests that deleting a user's file removes its metadata from PostgreSQL
     * and its associated content from MinIO.
     */
    @Test
    void shouldDeleteFileAndItsStorageContent() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String downloadToken = uploadAuthenticatedFile(email, "to-delete.pdf");
        String authToken = jwtService.generateToken(email);

        FileMetadata metadata = findMetadataByToken(downloadToken);
        String uploadId = jwtService.validateAndExtractDownloadSubject(downloadToken);

        mockMvc.perform(delete("/api/auth/files/{fileId}", metadata.getId())
                .header("Authorization", "Bearer " + authToken)
                .with(csrf()))
                .andExpect(status().isNoContent());

        assertFalse(fileMetadataRepository.findAll().stream()
                .anyMatch(saved -> saved.getId().equals(metadata.getId())));

        assertThrows(FileNotFoundException.class,
                () -> storageService.loadAsResources(uploadId));
    }

    /**
     * Tests that a file can be downloaded through the API using a valid download
     * token.
     */
    @Test
    void shouldDownloadFileWithValidToken() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        byte[] fileContent = { 10, 20, 30, 40 };

        String downloadToken = uploadAuthenticatedFile(email, "download-report.pdf", fileContent);

        DownloadRequestDTO request = new DownloadRequestDTO(downloadToken, null);

        mockMvc.perform(post("/api/files/download")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(fileContent))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        Matchers.containsString("download-report.pdf")));

    }

    /**
     * Tests that uploading a file with a forbidden extension is rejected by the
     * controller.
     */
    @Test
    void shouldRejectUploadWithForbiddenExtension() throws Exception {
        MockMultipartFile file = createFile("malware.exe", new byte[] { 1, 2, 3 });

        UploadRequestDTO metadataRequest = new UploadRequestDTO(null, "test", 2);
        MockMultipartFile metadataPart = createMetadataPart(metadataRequest);

        mockMvc.perform(multipart("/api/files")
                .file(file)
                .file(metadataPart)
                .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    private String uploadAuthenticatedFile(String email, String filename) throws Exception {
        return uploadAuthenticatedFile(email, filename, new byte[] { 1, 2, 3 });
    }

    private String uploadAuthenticatedFile(String email, String filename, byte[] fileContent) throws Exception {
        userAccountRepository.save(createUser(email));

        String authToken = jwtService.generateToken(email);

        MockMultipartFile file = createFile(filename, fileContent);
        MockMultipartFile metadataPart = createMetadataPart(
                new UploadRequestDTO(null, "integration-test", 2));

        var result = mockMvc.perform(multipart("/api/auth/files")
                .file(file)
                .file(metadataPart)
                .header("Authorization", "Bearer " + authToken)
                .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return extractDownloadToken(result.getResponse().getContentAsString());
    }

    private UserAccount createUser(String email) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("securePassword123"));
        return user;
    }

    private MockMultipartFile createFile(String filename, byte[] content) {
        return new MockMultipartFile(
                "file",
                filename,
                MediaType.APPLICATION_PDF_VALUE,
                content);
    }

    private MockMultipartFile createMetadataPart(UploadRequestDTO metadata) throws Exception {
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("filePassword", metadata.filePassword());
        metadataMap.put("tags", metadata.tags());
        metadataMap.put("retentionDays", metadata.retentionDays());

        return new MockMultipartFile(
                "metadata",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(metadataMap));
    }

    private String extractDownloadToken(String response) throws Exception {
        return objectMapper.readTree(response)
                .path("downloadToken")
                .asString();
    }

    private FileMetadata findMetadataByToken(String downloadToken) {
        return fileMetadataRepository.findAll().stream()
                .filter(metadata -> downloadToken.equals(metadata.getDownloadToken()))
                .findFirst()
                .orElseThrow();
    }
}