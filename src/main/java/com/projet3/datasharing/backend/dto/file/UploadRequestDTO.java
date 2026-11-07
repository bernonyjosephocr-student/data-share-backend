package com.projet3.datasharing.backend.dto.file;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UploadRequestDTO(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String filePassword,
        String tags,
        Integer retentionDays) {

}
