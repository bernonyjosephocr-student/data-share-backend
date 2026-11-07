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
import org.springframework.mock.web.MockMultipartFile;

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

        @Test
        void uploadPersistsMetadataThenStoresFileWithTokenSubjectAsUploadId() {
                UserAccount user = new UserAccount();
                user.setId(42L);
                when(userAccountRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));

                MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf",
                                new byte[] { 1, 2 });
                Map<String, Object> extraClaims = new HashMap<>();
                extraClaims.put("fileName", file.getOriginalFilename());
                extraClaims.put("fileSize", file.getSize());
                extraClaims.put("isPassReq", false);
                when(jwtService.generateDownloadToken(anyString(), eq(42L), any(Instant.class), eq(extraClaims)))
                                .thenReturn("download-token");

                when(fileMetadataRepository.save(any(FileMetadata.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // FileMetadataDTO request = new FileMetadataDTO(null, null, "reports", null,
                // null, false, 3, null, null);
                UploadRequestDTO request = new UploadRequestDTO(null, "reports", 3);
                String token = fileService.upload(file, request, USER_EMAIL);

                assertEquals("download-token", token);
                ArgumentCaptor<FileMetadata> metadataCaptor = ArgumentCaptor.forClass(FileMetadata.class);
                verify(fileMetadataRepository).save(metadataCaptor.capture());
                FileMetadata metadata = metadataCaptor.getValue();
                assertEquals(user, metadata.getUserAccount());
                assertEquals("report.pdf", metadata.getName());
                assertEquals(2, metadata.getFileSize());
                assertEquals("reports", metadata.getTags());
                assertEquals(3, metadata.getRetentionDays());
                assertEquals(metadata.getCreatedAt().plusDays(3), metadata.getExpiryDate());
                assertEquals("download-token", metadata.getDownloadToken());
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

        @Test
        void uploadDefaultsRetentionToSevenDays() {
                UserAccount user = new UserAccount();
                user.setId(42L);
                when(userAccountRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
                MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf",
                                new byte[] { 1 });
                Map<String, Object> extraClaims = new HashMap<>();
                extraClaims.put("fileName", file.getOriginalFilename());
                extraClaims.put("fileSize", file.getSize());
                extraClaims.put("isPassReq", false);
                when(jwtService.generateDownloadToken(anyString(), eq(42L), any(Instant.class), eq(extraClaims)))
                                .thenReturn("download-token");
                when(fileMetadataRepository.save(any(FileMetadata.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                UploadRequestDTO request = new UploadRequestDTO(null, null, null);
                fileService.upload(file, request, USER_EMAIL);

                ArgumentCaptor<FileMetadata> metadataCaptor = ArgumentCaptor.forClass(FileMetadata.class);
                verify(fileMetadataRepository).save(metadataCaptor.capture());
                assertEquals(metadataCaptor.getValue().getCreatedAt().plusDays(7),
                                metadataCaptor.getValue().getExpiryDate());
        }

}
