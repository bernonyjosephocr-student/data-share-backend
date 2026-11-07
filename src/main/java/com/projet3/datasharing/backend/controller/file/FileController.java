package com.projet3.datasharing.backend.controller.file;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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

import com.projet3.datasharing.backend.dto.file.DownloadRequestDTO;
import com.projet3.datasharing.backend.dto.file.DownloadResponseDTO;
import com.projet3.datasharing.backend.dto.file.FileMetadataDTO;
import com.projet3.datasharing.backend.dto.file.UploadRequestDTO;
import com.projet3.datasharing.backend.service.file.FileService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileController {
  private final FileService fileService;

  @SecurityRequirement(name = "bearerAuth") // for swagger
  @PostMapping(path = "/auth/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, String>> uploadFile(
      @RequestPart("file") MultipartFile file,
      @RequestPart("metadata") UploadRequestDTO uploadRequestDTO,
      Principal principal) {

    log.debug("Received authenticated file upload request from user: {}", principal.getName());
    return processUpload(file, uploadRequestDTO, principal.getName());
  }

  /**
   * Uploads a file without requiring user authentication. This endpoint is
   * intended for public uploads.
   * 
   * @param file
   * @param uploadRequestDTO
   * @return
   */
  @PostMapping(path = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, String>> uploadFile(
      @RequestPart("file") MultipartFile file,
      @RequestPart("metadata") UploadRequestDTO uploadRequestDTO) {

    log.debug("Received file upload request without authentication.");
    return processUpload(file, uploadRequestDTO, null);
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
    log.debug("Fetching files for authenticated user: {}", principal.getName());
    List<FileMetadataDTO> files = fileService.getFiles(principal.getName());
    return ResponseEntity.ok(files);
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
  public ResponseEntity<Void> deleteFile(Principal principal, @PathVariable String fileId) {
    log.debug("Deleting file for authenticated user: {}", principal.getName());
    fileService.deleteFile(principal.getName(), fileId);
    return ResponseEntity.noContent().build();
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

    if (downloadRequestDTO == null || downloadRequestDTO.token().isBlank()) {
      throw new IllegalArgumentException("Download token must not be blank.");
    }
    DownloadResponseDTO response = fileService.downloadFile(downloadRequestDTO);

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(response.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(response.resource());
  }

  /**
   * 
   * @param file
   * @param uploadRequestDTO
   * @param username
   * @return
   */

  private ResponseEntity<Map<String, String>> processUpload(
      MultipartFile file,
      UploadRequestDTO uploadRequestDTO,
      String username) {

    String filename = validateAndExtractFilename(file);
    log.debug("Uploading file: {} with metadata: {} for owner: {}", filename, uploadRequestDTO, username);

    String token = fileService.upload(file, uploadRequestDTO, username);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("downloadToken", token));
  }

  private String validateAndExtractFilename(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file must not be empty.");
    }

    if (file.getSize() > Integer.MAX_VALUE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is too large.");
    }

    String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
    if (!StringUtils.hasText(filename)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file must have a filename.");
    }

    return filename;
  }
}