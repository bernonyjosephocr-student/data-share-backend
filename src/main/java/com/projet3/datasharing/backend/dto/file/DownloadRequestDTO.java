package com.projet3.datasharing.backend.dto.file;

import jakarta.validation.constraints.NotBlank;

/**
 * DownloadReqDTO
 */
public record DownloadRequestDTO(
        @NotBlank(message = "Le token de téléchargement est requis.") String token,
        String filePassword) {
}