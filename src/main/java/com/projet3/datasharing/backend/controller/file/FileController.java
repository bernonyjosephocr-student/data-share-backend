package com.projet3.datasharing.backend.controller.file;

import com.projet3.datasharing.backend.config.logging.LogKeys;
import com.projet3.datasharing.backend.dto.file.DownloadRequestDTO;
import com.projet3.datasharing.backend.dto.file.DownloadResponseDTO;
import com.projet3.datasharing.backend.dto.file.FileMetadataDTO;
import com.projet3.datasharing.backend.dto.file.UploadRequestDTO;
import com.projet3.datasharing.backend.service.file.FileService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api")
public class FileController {
  private final FileService fileService;

  private final List<String> fileForbiddenExtensions;

  private final DataSize maxFileSize;

  public FileController(
      FileService fileService,
      @Value("${file.forbidden-extensions}") List<String> fileForbiddenExtensions,
      @Value("${file.max-file-size}") DataSize maxFileSize) {
    this.fileService = fileService;
    this.fileForbiddenExtensions = fileForbiddenExtensions;
    this.maxFileSize = maxFileSize;
  }

  /**
   * Uploads a file with user authenticated.
   *
   * @param file
   * @param uploadRequestDTO
   * @param principal
   * @return
   */
  @SecurityRequirement(name = "bearerAuth") // for swagger
  @PostMapping(path = "/auth/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, String>> uploadFile(
      @RequestPart("file") MultipartFile file,
      @Valid @RequestPart("metadata") UploadRequestDTO uploadRequestDTO,
      Principal principal) {

    long startTime = System.nanoTime();
    String result = LogKeys.RESULT_ERROR;

    try {
      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "file_protected_upload_request_received")
          .addKeyValue(LogKeys.USER_ID, principal.getName())
          .log("File Upload request with authentication received");

      ResponseEntity<Map<String, String>> response =
          processUpload(file, uploadRequestDTO, principal.getName());

      result = LogKeys.RESULT_SUCCESS;

      return response;
    } finally {
      long durationMs = getDuration(startTime);

      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "file_protected_upload_request_finished")
          .addKeyValue(LogKeys.RESPONSE_TYPE, result)
          .addKeyValue(LogKeys.DURATION_MS, durationMs)
          .log("File Upload request with authentication processing finished");
    }
  }

  /**
   * Uploads a file without requiring user authentication.
   *
   * @param file
   * @param uploadRequestDTO
   * @return
   */
  @PostMapping(path = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, String>> uploadFile(
      @RequestPart("file") MultipartFile file,
      @Valid @RequestPart("metadata") UploadRequestDTO uploadRequestDTO) {

    long startTime = System.nanoTime();
    String result = LogKeys.RESULT_ERROR;

    try {
      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "file_upload_request_received")
          .addKeyValue(LogKeys.USER_ID, "no_user_required")
          .log("File Upload request without authentication received");

      ResponseEntity<Map<String, String>> response = processUpload(file, uploadRequestDTO, null);

      result = LogKeys.RESULT_SUCCESS;

      return response;
    } finally {
      long durationMs = getDuration(startTime);

      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "file_upload_request_finished")
          .addKeyValue(LogKeys.RESPONSE_TYPE, result)
          .addKeyValue(LogKeys.DURATION_MS, durationMs)
          .log("File Upload request without autentication processing finished");
    }
  }

  /**
   * Fetches a list of files associated with the authenticated user.
   *
   * @param principal
   * @return
   */
  @SecurityRequirement(name = "bearerAuth") // for swagger
  @GetMapping("/auth/files")
  public ResponseEntity<List<FileMetadataDTO>> getFiles(Principal principal) {

    long startTime = System.nanoTime();
    String result = LogKeys.RESULT_ERROR;

    try {
      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "file_fetch_request_received")
          .addKeyValue(LogKeys.USER_ID, principal.getName())
          .log("Fetch user files request received");

      List<FileMetadataDTO> files = fileService.getFiles(principal.getName());

      result = LogKeys.RESULT_SUCCESS;

      return ResponseEntity.ok(files);
    } finally {
      long durationMs = getDuration(startTime);

      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "files_fetch_request_finished")
          .addKeyValue(LogKeys.RESPONSE_TYPE, result)
          .addKeyValue(LogKeys.DURATION_MS, durationMs)
          .log("Fetch user files request finished");
    }
  }

  /**
   * Deletes a file associated with the authenticated user.
   *
   * @param principal
   * @param fileId
   * @return
   */
  @SecurityRequirement(name = "bearerAuth") // for swagger
  @DeleteMapping("/auth/files/{fileId}")
  public ResponseEntity<Void> deleteFile(Principal principal, @Valid @PathVariable String fileId) {

    long startTime = System.nanoTime();
    String result = LogKeys.RESULT_ERROR;

    try {
      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "file_Delete_request_received")
          .addKeyValue(LogKeys.USER_ID, principal.getName())
          .addKeyValue(LogKeys.FILE_ID, fileId)
          .log("Delete user file request received");

      fileService.deleteFile(principal.getName(), fileId);

      result = LogKeys.RESULT_SUCCESS;

      return ResponseEntity.noContent().build();
    } finally {
      long durationMs = getDuration(startTime);

      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "file_Delete_request_finished")
          .addKeyValue(LogKeys.RESPONSE_TYPE, result)
          .addKeyValue(LogKeys.DURATION_MS, durationMs)
          .log("Delete user file request finished");
    }
  }

  /**
   * Downloads a file using a valid download token.
   *
   * @param downloadRequestDTO
   * @return
   */
  @PostMapping(path = "/files/download", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Resource> downloadFile(
      @Valid @RequestBody DownloadRequestDTO downloadRequestDTO) {

    long startTime = System.nanoTime();
    String result = LogKeys.RESULT_ERROR;

    try {
      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "file_download_request_received")
          .addKeyValue(LogKeys.USER_ID, "no_user_required")
          .log("File Downpload request received");

      if (downloadRequestDTO == null || downloadRequestDTO.token().isBlank()) {
        throw new IllegalArgumentException("Download token must not be blank.");
      }
      DownloadResponseDTO response = fileService.downloadFile(downloadRequestDTO);

      result = LogKeys.RESULT_SUCCESS;

      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              ContentDisposition.attachment()
                  .filename(response.fileName(), StandardCharsets.UTF_8)
                  .build()
                  .toString())
          .body(response.resource());
    } finally {
      long durationMs = getDuration(startTime);

      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "file_download_request_finished")
          .addKeyValue(LogKeys.RESPONSE_TYPE, result)
          .addKeyValue(LogKeys.DURATION_MS, durationMs)
          .log("File Downpload request finished");
    }
  }

  /**
   * @param file
   * @param uploadRequestDTO
   * @param username
   * @return
   */
  private ResponseEntity<Map<String, String>> processUpload(
      MultipartFile file, UploadRequestDTO uploadRequestDTO, String username) {

    validateAndExtractFilename(file);

    String token = fileService.upload(file, uploadRequestDTO, username);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("downloadToken", token));
  }

  private void validateAndExtractFilename(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Le fichier téléversé ne peut etre vide.");
    }
    long maxSize = this.maxFileSize.toBytes();
    if (file.getSize() > maxSize) {

      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "La taille du fichier téléversé est trop grande.");
    }

    String filename =
        StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
    if (!StringUtils.hasText(filename) || !filename.contains(".") || filename.endsWith(".")) {

      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Le fichier téléversé n'et pas valide.");
    }

    // get the free file extension
    String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    if (this.fileForbiddenExtensions.contains(extension)) {

      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "L'extension %s est interdite.".formatted(extension));
    }
  }

  private long getDuration(long startTime) {
    return (System.nanoTime() - startTime) / 1_000_000;
  }
}
