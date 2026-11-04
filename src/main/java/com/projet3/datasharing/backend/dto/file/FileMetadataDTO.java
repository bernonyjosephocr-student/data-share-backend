package com.projet3.datasharing.backend.dto.file;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FileMetadataDTO(
        Long id,
        String name,
        String tags,
        Integer fileSize,
        LocalDateTime expiryDate,
        Boolean isPasswordProtected,
        Integer retentionDays,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String filePassword) {
}
