package com.projet3.datasharing.backend.service.file;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

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

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Transactional(readOnly = true)
@Service
public class FileService {

  private static final Logger cronLog = LoggerFactory.getLogger("CRON");

  private static final String AUTHENTICATED_USER_NOT_FOUND = "Ce compte utilisateur n'existe pas.";

  private final Integer defaultRetentionDay;

  private final FileMetadataRepository fileMetadataRepository;
  private final UserAccountRepository userAccountRepository;
  private final FileMetadataMapper fileMetadataMapper;
  private final JWTService jwtService;
  private final StorageService storageService;
  private final PasswordEncoder passwordEncoder;

  // 2. Explicit constructor combining Spring Beans and Configuration Property
  public FileService(
      @Value("${file.retention-day}") Integer defaultRetentionDay,
      FileMetadataRepository fileMetadataRepository,
      UserAccountRepository userAccountRepository,
      FileMetadataMapper fileMetadataMapper,
      JWTService jwtService,
      StorageService storageService,
      PasswordEncoder passwordEncoder) {

    this.defaultRetentionDay = defaultRetentionDay;
    this.fileMetadataRepository = fileMetadataRepository;
    this.userAccountRepository = userAccountRepository;
    this.fileMetadataMapper = fileMetadataMapper;
    this.jwtService = jwtService;
    this.storageService = storageService;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public String upload(
      MultipartFile file,
      UploadRequestDTO uploadRequestDTO,
      String userEmail) {
    log.info("Starting file upload process for file: {}, {}, {}", file.getOriginalFilename(), uploadRequestDTO,
        userEmail);

    UserAccount userAccount = null;
    if (userEmail != null && !userEmail.isBlank()) {
      userAccount = userAccountRepository.findByEmail(userEmail)
          .orElseThrow(() -> new UsernameNotFoundException(AUTHENTICATED_USER_NOT_FOUND));
    }

    LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
    int retentionDays = uploadRequestDTO.retentionDays() == null
        ? defaultRetentionDay
        : uploadRequestDTO.retentionDays();

    LocalDateTime expiryDate = createdAt.plusDays(retentionDays);

    String uploadId = UUID.randomUUID().toString();
    // Adding some extra claims for the download token, such as the original file
    // name and size. usefulle for the frontend to display information about the
    // file being downloaded without needing to query the database.
    Map<String, Object> extraClaims = new HashMap<>();
    extraClaims.put("fileName", file.getOriginalFilename());
    extraClaims.put("fileSize", file.getSize());
    extraClaims.put("isPassReq", uploadRequestDTO.filePassword() != null && !uploadRequestDTO.filePassword().isBlank());

    // Reuse the token subject as the MinIO prefix for this upload.
    String downloadToken = jwtService.generateDownloadToken(
        uploadId,
        userAccount != null ? userAccount.getId() : null,
        expiryDate.toInstant(ZoneOffset.UTC), extraClaims);

    String filename = StringUtils.cleanPath(file.getOriginalFilename() == null
        ? ""
        : file.getOriginalFilename());

    // Initialize the entity
    FileMetadata metadata = new FileMetadata();
    metadata.setUserAccount(userAccount != null ? userAccount : null);
    metadata.setName(filename);
    metadata.setFileSize(Math.toIntExact(file.getSize()));
    metadata.setCreatedAt(createdAt);
    metadata.setExpiryDate(expiryDate);
    metadata.setDownloadToken(downloadToken);
    log.debug("File password provided: {}", uploadRequestDTO.filePassword());
    if (StringUtils.hasText(uploadRequestDTO.filePassword())) {
      // Persist only the encoded password, never the submitted value.
      metadata.setFilePassword(passwordEncoder.encode(uploadRequestDTO.filePassword()));
      log.debug("Encoding file password for storage.{}", metadata.getFilePassword());
    }
    metadata.setTags(uploadRequestDTO.tags());
    metadata.setRetentionDays(uploadRequestDTO.retentionDays());

    log.debug("Persisting file metadata: {}", metadata.getName());
    fileMetadataRepository.save(metadata);

    log.debug("Saving file {} content into S3", metadata.getName());
    storageService.store(List.of(file), uploadId);

    log.info("File uploaded successfully: {}", file.getOriginalFilename());
    return downloadToken;
  }

  public List<FileMetadataDTO> getFiles(String eMail) {
    UserAccount userAccount = userAccountRepository.findByEmail(eMail)
        .orElseThrow(() -> new UsernameNotFoundException(AUTHENTICATED_USER_NOT_FOUND));
    log.info("Fetching files for user: {}", userAccount.getEmail());
    // Considering only non expired files
    List<FileMetadata> files = fileMetadataRepository.findByUserAccountAndExpiryDateAfter(userAccount,
        LocalDateTime.now());

    return fileMetadataMapper.toDtoList(files);
  }

  @Transactional
  public void deleteFile(String name, String fileId) {
    UserAccount userAccount = userAccountRepository.findByEmail(name)
        .orElseThrow(() -> new UsernameNotFoundException(AUTHENTICATED_USER_NOT_FOUND));

    log.info("Deleting file with ID: {} for user: {}", fileId, userAccount.getEmail());

    FileMetadata metadata = fileMetadataRepository.findById(Long.parseLong(fileId))
        .orElseThrow(() -> new IllegalArgumentException("Aucun fichier ne correspond à cet ID: " + fileId));

    if (!metadata.getUserAccount().getId().equals(userAccount.getId())) {
      throw new SecurityException("Le fichier n'est pas associé au compte utilisateur.");
    }

    // Delete the metadata from the database
    fileMetadataRepository.delete(metadata);

    // Extract the uploadId from the download token and delete the file from storage
    String uploadId = jwtService.validateAndExtractDownloadSubject(metadata.getDownloadToken());
    storageService.delete(uploadId);

    log.info("File with ID: {} and Folder: {}deleted successfully for user: {}", fileId, uploadId,
        userAccount.getEmail());
  }

  /**
   * Downloads a file based on its download token and optional password.
   * 
   * @param token
   * @param filePassword
   * @return
   */
  public DownloadResponseDTO downloadFile(DownloadRequestDTO downloadRequestDTO) {

    String filePassword = downloadRequestDTO.filePassword();

    log.debug("File password provided: {}",
        filePassword != null && !filePassword.isBlank() ? "Yes" : "No");

    String token = downloadRequestDTO.token();

    FileMetadata fileMetadata = fileMetadataRepository.findByDownloadToken(token)
        .orElseThrow(() -> new IllegalArgumentException(
            "Aucun fichier ne correspond au token fourni."));

    String encodedPassword = fileMetadata.getFilePassword();

    // Check password only if the file is password protected
    if (encodedPassword != null && !encodedPassword.isBlank()) {

      if (filePassword == null || filePassword.isBlank()) {
        throw new SecurityException(
            "Ce fichier est protégé. Veuiller fournir le mot de passe associé.");
      }

      if (!passwordEncoder.matches(filePassword, encodedPassword)) {
        throw new SecurityException("Mot de passe founi incorrect.");
      }
    }

    // Retrieve the UUID from the token and find the file in the file store
    String uploadId = jwtService.validateAndExtractDownloadSubject(token);

    List<Resource> resourceFiles = storageService.loadAsResources(uploadId);

    if (resourceFiles.isEmpty()) {
      throw new IllegalArgumentException("File not found for the provided token.");
    }

    return new DownloadResponseDTO(
        resourceFiles.get(0).getFilename(),
        resourceFiles.get(0));
  }

  @Transactional
  public void deleteExpiredFiles() {

    List<FileMetadata> expiredFiles = fileMetadataRepository.findByExpiryDateLessThanEqual(LocalDateTime.now());

    cronLog.info("{} expired file(s) found.", expiredFiles.size());

    for (FileMetadata file : expiredFiles) {
      try {

        String uploadId = jwtService.validateAndExtractExpiredTokenSubject(file.getDownloadToken());
        // Let's keep metadata and minio consistent. If there is an issue, none of them
        // is deleted.
        if (uploadId != null && !uploadId.isBlank()) {
          // Delete metadata from PostgreSQL
          fileMetadataRepository.delete(file);
          // Delete associated objects from MinIO
          storageService.delete(uploadId);
        }
        cronLog.info("Expired file {} successfully deleted.", file.getId());

      } catch (Exception e) {
        cronLog.error("Failed to delete expired file {}. Skipping to next file.", file.getId(), e);
      }
    }
  }

}
