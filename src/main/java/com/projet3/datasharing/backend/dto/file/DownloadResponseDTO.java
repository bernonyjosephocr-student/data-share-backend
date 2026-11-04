package com.projet3.datasharing.backend.dto.file;

import org.springframework.core.io.Resource;

public record DownloadResponseDTO(
    String fileName,
    Resource resource) {

}
