package com.projet3.datasharing.backend.service.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import com.projet3.datasharing.backend.dto.file.DownloadRequestDTO;
import com.projet3.datasharing.backend.dto.file.DownloadResponseDTO;
import com.projet3.datasharing.backend.dto.file.FileMetadataDTO;
import com.projet3.datasharing.backend.dto.file.UploadRequestDTO;
import com.projet3.datasharing.backend.entity.UserAccount;
import com.projet3.datasharing.backend.entity.file.FileMetadata;
import com.projet3.datasharing.backend.mapper.file.FileMetadataMapper;
import com.projet3.datasharing.backend.repository.UserAccountRepository;
import com.projet3.datasharing.backend.repository.file.FileMetadataRepository;
import com.projet3.datasharing.backend.service.JWTService;
import com.projet3.datasharing.backend.service.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

        private static final String USER_EMAIL = "user@example.com";
        private static final String DOWNLOAD_TOKEN = "download-token";
        private static final String UPLOAD_ID = "upload-id";

        @Mock
        private FileMetadataRepository fileMetadataRepository;

        @Mock
        private UserAccountRepository userAccountRepository;

        @Mock
        private JWTService jwtService;

        @Mock
        private StorageService storageService;

        @Mock
        private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

        private FileService fileService;

        private final FileMetadataMapper fileMetadataMapper = Mappers.getMapper(FileMetadataMapper.class);

        private final int defaultRetentionDays = 7;

        @BeforeEach
        void setUp() {
                fileService = new FileService(
                                defaultRetentionDays,
                                fileMetadataRepository,
                                userAccountRepository,
                                fileMetadataMapper,
                                jwtService,
                                storageService,
                                passwordEncoder);
        }

        /**
         * Tests that a file is uploaded successfully, its metadata is persisted,
         * and its content is stored using the upload ID extracted from the download
         * token.
         */
        @Test
        void shouldUploadFileAndPersistMetadataBeforeStoringContent() {

                UserAccount user = new UserAccount();
                user.setId(42L);

                when(userAccountRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));

                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "report.pdf",
                                "application/pdf",
                                new byte[] { 1, 2 });

                Map<String, Object> extraClaims = new HashMap<>();
                extraClaims.put("fileName", file.getOriginalFilename());
                extraClaims.put("fileSize", file.getSize());
                extraClaims.put("isPassReq", false);

                when(jwtService.generateDownloadToken(
                                anyString(),
                                eq(42L),
                                any(Instant.class),
                                eq(extraClaims))).thenReturn(DOWNLOAD_TOKEN);

                when(fileMetadataRepository.save(any(FileMetadata.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                UploadRequestDTO request = new UploadRequestDTO(null, "reports", 3);

                String token = fileService.upload(file, request, USER_EMAIL);

                assertEquals(DOWNLOAD_TOKEN, token);

                ArgumentCaptor<FileMetadata> metadataCaptor = ArgumentCaptor.forClass(FileMetadata.class);
                verify(fileMetadataRepository).save(metadataCaptor.capture());

                FileMetadata metadata = metadataCaptor.getValue();

                assertEquals(user, metadata.getUserAccount());
                assertEquals("report.pdf", metadata.getName());
                assertEquals(2, metadata.getFileSize());
                assertEquals("reports", metadata.getTags());
                assertEquals(3, metadata.getRetentionDays());
                assertEquals(metadata.getCreatedAt().plusDays(3), metadata.getExpiryDate());
                assertEquals(DOWNLOAD_TOKEN, metadata.getDownloadToken());
                assertNotNull(metadata.getCreatedAt());

                ArgumentCaptor<String> tokenSubjectCaptor = ArgumentCaptor.forClass(String.class);

                verify(jwtService).generateDownloadToken(
                                tokenSubjectCaptor.capture(),
                                eq(42L),
                                eq(metadata.getExpiryDate().toInstant(java.time.ZoneOffset.UTC)),
                                eq(extraClaims));

                ArgumentCaptor<String> uploadIdCaptor = ArgumentCaptor.forClass(String.class);

                InOrder callOrder = inOrder(fileMetadataRepository, storageService);

                callOrder.verify(fileMetadataRepository).save(metadata);
                callOrder.verify(storageService).store(any(), uploadIdCaptor.capture());

                assertEquals(tokenSubjectCaptor.getValue(), uploadIdCaptor.getValue());
        }

        /**
         * Tests that the service returns all files associated with the requested user.
         */
        @Test
        void shouldReturnFilesAssociatedWithUser() {

                UserAccount user = new UserAccount();
                user.setId(42L);
                user.setEmail(USER_EMAIL);

                FileMetadata metadata = new FileMetadata();
                metadata.setName("report.pdf");
                metadata.setUserAccount(user);

                when(userAccountRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
                when(fileMetadataRepository.findByUserAccount(user)).thenReturn(List.of(metadata));

                List<FileMetadataDTO> result = fileService.getFiles(USER_EMAIL);

                assertNotNull(result);
                assertEquals(1, result.size());
                assertEquals("report.pdf", result.get(0).name());
        }

        /**
         * Tests that a user's file is deleted from the database
         * and its associated content is removed from storage.
         */
        @Test
        void shouldDeleteFileAndAssociatedStorageContent() {

                UserAccount user = new UserAccount();
                user.setId(42L);
                user.setEmail(USER_EMAIL);

                FileMetadata metadata = new FileMetadata();
                metadata.setUserAccount(user);
                metadata.setDownloadToken(DOWNLOAD_TOKEN);

                when(userAccountRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
                when(fileMetadataRepository.findById(1L)).thenReturn(Optional.of(metadata));
                when(jwtService.validateAndExtractExpiredOrNotTokenSubject(DOWNLOAD_TOKEN))
                                .thenReturn(UPLOAD_ID);

                fileService.deleteFile(USER_EMAIL, "1");

                verify(fileMetadataRepository).delete(metadata);
                verify(jwtService).validateAndExtractExpiredOrNotTokenSubject(DOWNLOAD_TOKEN);
                verify(storageService).delete(UPLOAD_ID);
        }

        /**
         * Tests that a file can be downloaded successfully without requiring a
         * password.
         */
        @Test
        void shouldDownloadFileWithoutPassword() {

                FileMetadata metadata = new FileMetadata();

                Resource resource = org.mockito.Mockito.mock(Resource.class);

                when(fileMetadataRepository.findByDownloadToken(DOWNLOAD_TOKEN))
                                .thenReturn(Optional.of(metadata));

                when(jwtService.validateAndExtractDownloadSubject(DOWNLOAD_TOKEN))
                                .thenReturn(UPLOAD_ID);

                when(storageService.loadAsResources(UPLOAD_ID))
                                .thenReturn(List.of(resource));

                when(resource.getFilename()).thenReturn("report.pdf");

                DownloadRequestDTO request = new DownloadRequestDTO(DOWNLOAD_TOKEN, null);

                DownloadResponseDTO response = fileService.downloadFile(request);

                assertNotNull(response);
                verify(storageService).loadAsResources(UPLOAD_ID);
        }

        /**
         * Tests that a password-protected file can be downloaded when the provided
         * password is valid.
         */
        @Test
        void shouldDownloadPasswordProtectedFileWithValidPassword() {

                FileMetadata metadata = new FileMetadata();
                metadata.setFilePassword("encoded-password");

                Resource resource = org.mockito.Mockito.mock(Resource.class);

                when(fileMetadataRepository.findByDownloadToken(DOWNLOAD_TOKEN))
                                .thenReturn(Optional.of(metadata));

                when(passwordEncoder.matches("secret", "encoded-password"))
                                .thenReturn(true);

                when(jwtService.validateAndExtractDownloadSubject(DOWNLOAD_TOKEN))
                                .thenReturn(UPLOAD_ID);

                when(storageService.loadAsResources(UPLOAD_ID))
                                .thenReturn(List.of(resource));

                when(resource.getFilename()).thenReturn("report.pdf");

                DownloadRequestDTO request = new DownloadRequestDTO(DOWNLOAD_TOKEN, "secret");

                DownloadResponseDTO response = fileService.downloadFile(request);

                assertNotNull(response);

                verify(passwordEncoder).matches("secret", "encoded-password");
                verify(storageService).loadAsResources(UPLOAD_ID);
        }

        /**
         * Tests that expired files are automatically deleted from the database and
         * their associated content is removed from storage.
         */
        @Test
        void shouldDeleteExpiredFilesAndAssociatedStorageContent() {

                FileMetadata metadata = new FileMetadata();
                metadata.setDownloadToken(DOWNLOAD_TOKEN);

                when(fileMetadataRepository.findByExpiryDateLessThanEqual(any()))
                                .thenReturn(List.of(metadata));

                when(jwtService.validateAndExtractExpiredOrNotTokenSubject(DOWNLOAD_TOKEN))
                                .thenReturn(UPLOAD_ID);

                fileService.deleteExpiredFiles();

                verify(fileMetadataRepository).delete(metadata);
                verify(jwtService).validateAndExtractExpiredOrNotTokenSubject(DOWNLOAD_TOKEN);
                verify(storageService).delete(UPLOAD_ID);
        }
}