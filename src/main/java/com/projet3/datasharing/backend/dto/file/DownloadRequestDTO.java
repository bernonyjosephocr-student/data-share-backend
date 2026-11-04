package com.projet3.datasharing.backend.dto.file;

import jakarta.validation.constraints.NotBlank;

/**
 * DownloadReqDTO
 */
public record DownloadRequestDTO(
    @NotBlank String token,
    String filePassword) {
}