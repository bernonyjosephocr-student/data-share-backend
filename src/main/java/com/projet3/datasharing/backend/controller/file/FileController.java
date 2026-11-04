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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.projet3.datasharing.backend.dto.file.DownloadRequestDTO;
import com.projet3.datasharing.backend.dto.file.DownloadResponseDTO;
import com.projet3.datasharing.backend.dto.file.FileMetadataDTO;
import com.projet3.datasharing.backend.service.file.FileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FileController {
  private final FileService fileService;

  @PostMapping(path = "/api/auth/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, String>> uploadFile(
      @RequestPart("file") MultipartFile file,
      @RequestPart("metadata") FileMetadataDTO fileMetadataDTO,
      Principal principal) {

    log.debug("Received file upload request from user: {}", principal.getName());
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file must not be empty.");
    }

    String filename = StringUtils.cleanPath(file.getOriginalFilename() == null
        ? ""
        : file.getOriginalFilename());
    if (!StringUtils.hasText(filename)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file must have a filename.");
    }
    log.debug("Uploading file: {} with metadata: {}", filename, fileMetadataDTO);
    if (file.getSize() > Integer.MAX_VALUE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is too large.");
    }
    String token = fileService.upload(file, fileMetadataDTO, principal.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("downloadToken", token));
  }

  /**
   * Uploads a file without requiring user authentication. This endpoint is
   * intended for public uploads.
   * 
   * @param file
   * @param fileMetadataDTO
   * @return
   */
  @PostMapping(path = "/api/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, String>> uploadFile(
      @RequestPart("file") MultipartFile file,
      @RequestPart("metadata") FileMetadataDTO fileMetadataDTO) {

    log.debug("Received file upload request without authentication.");
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file must not be empty.");
    }

    String filename = StringUtils.cleanPath(file.getOriginalFilename() == null
        ? ""
        : file.getOriginalFilename());
    if (!StringUtils.hasText(filename)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file must have a filename.");
    }

    if (file.getSize() > Integer.MAX_VALUE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is too large.");
    }
    log.debug("Uploading file: {} with metadata: {}", file.getOriginalFilename(), fileMetadataDTO);
    String token = fileService.upload(file, fileMetadataDTO, null);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("downloadToken", token));
  }

  /**
   * Fetches a list of files associated with the authenticated user.
   * 
   * @param principal
   * @return
   */
  @GetMapping("/api/auth/files")
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
  @DeleteMapping("api/auth/files/{fileId}")
  public ResponseEntity<Void> deleteFile(Principal principal, @PathVariable String fileId) {
    log.debug("Deleting file for authenticated user: {}", principal.getName());
    fileService.deleteFile(principal.getName(), fileId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(path = "/api/files/download", consumes = MediaType.APPLICATION_JSON_VALUE)
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

}