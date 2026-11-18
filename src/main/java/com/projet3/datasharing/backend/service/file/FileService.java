package com.projet3.datasharing.backend.service.file;

import com.projet3.datasharing.backend.config.logging.LogKeys;
import com.projet3.datasharing.backend.dto.file.DownloadRequestDTO;
import com.projet3.datasharing.backend.dto.file.DownloadResponseDTO;
import com.projet3.datasharing.backend.dto.file.FileMetadataDTO;
import com.projet3.datasharing.backend.dto.file.UploadRequestDTO;
import com.projet3.datasharing.backend.entity.UserAccount;
import com.projet3.datasharing.backend.entity.file.FileMetadata;
import com.projet3.datasharing.backend.exception.FileNotFoundException;
import com.projet3.datasharing.backend.mapper.file.FileMetadataMapper;
import com.projet3.datasharing.backend.repository.UserAccountRepository;
import com.projet3.datasharing.backend.repository.file.FileMetadataRepository;
import com.projet3.datasharing.backend.service.JWTService;
import com.projet3.datasharing.backend.service.storage.StorageService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
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
  public String upload(MultipartFile file, UploadRequestDTO uploadRequestDTO, String userEmail) {

    log.atDebug()
        .addKeyValue(LogKeys.EVENT, LogKeys.UPLOAD_EVENT_KEY)
        .addKeyValue(LogKeys.FILE_NAME, file.getOriginalFilename())
        .addKeyValue(LogKeys.USER_ID, userEmail != null ? userEmail : null)
        .log("File upload is being processing");

    UserAccount userAccount = null;
    if (userEmail != null && !userEmail.isBlank()) {
      userAccount =
          userAccountRepository
              .findByEmail(userEmail)
              .orElseThrow(() -> new UsernameNotFoundException(AUTHENTICATED_USER_NOT_FOUND));
    }

    LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
    int retentionDays =
        uploadRequestDTO.retentionDays() == null
            ? defaultRetentionDay
            : uploadRequestDTO.retentionDays();

    LocalDateTime expiryDate = createdAt.plusDays(retentionDays);

    String uploadId = UUID.randomUUID().toString();
    // Adding some extra claims for the download token, such as the original file
    // name and size... . usefull for the frontend to display information about the
    // file being downloaded without needing to query the database.
    Map<String, Object> extraClaims = new HashMap<>();
    extraClaims.put("fileName", file.getOriginalFilename());
    extraClaims.put("fileSize", file.getSize());
    extraClaims.put(
        "isPassReq",
        uploadRequestDTO.filePassword() != null && !uploadRequestDTO.filePassword().isBlank());

    // Reuse the token subject as the MinIO prefix for this upload.
    String downloadToken =
        jwtService.generateDownloadToken(
            uploadId,
            userAccount != null ? userAccount.getId() : null,
            expiryDate.toInstant(ZoneOffset.UTC),
            extraClaims);

    String filename =
        StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());

    // Initialize the entity
    FileMetadata metadata = new FileMetadata();
    metadata.setUserAccount(userAccount != null ? userAccount : null);
    metadata.setName(filename);
    metadata.setFileSize(Math.toIntExact(file.getSize()));
    metadata.setCreatedAt(createdAt);
    metadata.setExpiryDate(expiryDate);
    metadata.setDownloadToken(downloadToken);

    if (StringUtils.hasText(uploadRequestDTO.filePassword())) {
      log.atDebug()
          .addKeyValue(LogKeys.EVENT, LogKeys.UPLOAD_EVENT_KEY)
          .log("File password is provided. Encoding it");

      metadata.setFilePassword(passwordEncoder.encode(uploadRequestDTO.filePassword()));
    }
    metadata.setTags(uploadRequestDTO.tags());
    metadata.setRetentionDays(uploadRequestDTO.retentionDays());

    log.atDebug()
        .addKeyValue(LogKeys.EVENT, LogKeys.UPLOAD_EVENT_KEY)
        .log("Persisting file metadata.");
    fileMetadataRepository.save(metadata);

    log.atDebug()
        .addKeyValue(LogKeys.EVENT, LogKeys.UPLOAD_EVENT_KEY)
        .addKeyValue(LogKeys.FILE_PATH, uploadId + "/" + metadata.getName())
        .log("Saving file content into S3");
    storageService.store(List.of(file), uploadId);

    return downloadToken;
  }

  /**
   * Get all the files for a given user.
   *
   * @param userEmail
   * @return
   */
  public List<FileMetadataDTO> getFiles(String userEmail) {
    UserAccount userAccount =
        userAccountRepository
            .findByEmail(userEmail)
            .orElseThrow(() -> new UsernameNotFoundException(AUTHENTICATED_USER_NOT_FOUND));

    log.atDebug()
        .addKeyValue(LogKeys.EVENT, LogKeys.FETCH_FILE_EVENT_KEY)
        .addKeyValue(LogKeys.USER_ID, userEmail)
        .log("User files fetching is being processing");
    // Considering all the user uploaded files.
    List<FileMetadata> files = fileMetadataRepository.findByUserAccount(userAccount);

    return fileMetadataMapper.toDtoList(files);
  }

  @Transactional
  public void deleteFile(String userEmail, String fileId) {

    log.atDebug()
        .addKeyValue(LogKeys.EVENT, LogKeys.DELETION_FILE_EVENT_KEY)
        .addKeyValue(LogKeys.USER_ID, userEmail)
        .addKeyValue(LogKeys.FILE_ID, fileId)
        .log("User file deletion is being processing");

    UserAccount userAccount =
        userAccountRepository
            .findByEmail(userEmail)
            .orElseThrow(() -> new UsernameNotFoundException(AUTHENTICATED_USER_NOT_FOUND));

    FileMetadata metadata =
        fileMetadataRepository
            .findById(Long.parseLong(fileId))
            .orElseThrow(
                () -> new FileNotFoundException("Aucun fichier ne correspond à cet ID: " + fileId));

    if (!metadata.getUserAccount().getId().equals(userAccount.getId())) {
      throw new SecurityException("Le fichier n'est pas associé au compte utilisateur.");
    }

    // Delete the metadata from the database
    log.atDebug()
        .addKeyValue(LogKeys.EVENT, LogKeys.DELETION_FILE_EVENT_KEY)
        .log("Deleting file metadata.");
    fileMetadataRepository.delete(metadata);

    // Extract the uploadId from the download token and delete the file from storage
    String uploadId =
        jwtService.validateAndExtractExpiredOrNotTokenSubject(metadata.getDownloadToken());

    log.atDebug()
        .addKeyValue(LogKeys.EVENT, LogKeys.DELETION_FILE_EVENT_KEY)
        .addKeyValue(LogKeys.FILE_PATH, uploadId + "/" + metadata.getName())
        .log("Deleting file from S3 bucket");
    storageService.delete(uploadId);
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

    log.atDebug()
        .addKeyValue(LogKeys.EVENT, LogKeys.DOWNLOAD_FILE_EVENT_KEY)
        .addKeyValue(
            LogKeys.FILE_PASS, filePassword != null && !filePassword.isBlank() ? "Yes" : "No")
        .log("File downloading is being processing");

    String token = downloadRequestDTO.token();

    FileMetadata fileMetadata =
        fileMetadataRepository
            .findByDownloadToken(token)
            .orElseThrow(
                () -> new IllegalArgumentException("Aucun fichier ne correspond au token fourni."));

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

    log.atDebug()
        .addKeyValue(LogKeys.EVENT, LogKeys.DOWNLOAD_FILE_EVENT_KEY)
        .addKeyValue(LogKeys.FILE_RETRIEVED, resourceFiles.get(0).getDescription())
        .log("File retrieved from s3");

    return new DownloadResponseDTO(resourceFiles.get(0).getDescription(), resourceFiles.get(0));
  }

  /** Deletes rexpired files */
  @Transactional
  public void deleteExpiredFiles() {

    List<FileMetadata> expiredFiles =
        fileMetadataRepository.findByExpiryDateLessThanEqual(LocalDateTime.now());

    int deletedCount = 0;
    int failedCount = 0;
    int skippedCount = 0;

    cronLog
        .atInfo()
        .addKeyValue("event", "expired_files_found")
        .addKeyValue("found_count", expiredFiles.size())
        .log("Expired files retrieved");

    for (FileMetadata file : expiredFiles) {
      try {

        String uploadId =
            jwtService.validateAndExtractExpiredOrNotTokenSubject(file.getDownloadToken());

        if (uploadId == null || uploadId.isBlank()) {
          skippedCount++;

          cronLog
              .atWarn()
              .addKeyValue("event", "expired_file_skipped")
              .addKeyValue("file_id", file.getId())
              .log("Expired file skipped: invalid upload identifier");

          continue;
        }

        // Delete metadata from PostgreSQL
        fileMetadataRepository.delete(file);

        // Delete associated objects from MinIO
        storageService.delete(uploadId);

        deletedCount++;

        cronLog
            .atInfo()
            .addKeyValue(LogKeys.EVENT, "expired_file_deleted")
            .addKeyValue(LogKeys.FILE_ID, file.getId())
            .log("Expired file successfully deleted");

      } catch (Exception exception) {
        failedCount++;

        cronLog
            .atError()
            .addKeyValue(LogKeys.EVENT, "expired_file_deletion_failed")
            .addKeyValue(LogKeys.FILE_ID, file.getId())
            .setCause(exception)
            .log("Failed to delete expired file; skipping to next file");
      }
    }

    cronLog
        .atInfo()
        .addKeyValue(LogKeys.EVENT, "expired_files_cleanup_summary")
        .addKeyValue(LogKeys.FOUND_COUNT, expiredFiles.size())
        .addKeyValue(LogKeys.DELETED_COUNT, deletedCount)
        .addKeyValue(LogKeys.FAILED_COUNT, failedCount)
        .addKeyValue(LogKeys.SKIPPED_COUNT, skippedCount)
        .log("Expired files cleanup finished");
  }
}
