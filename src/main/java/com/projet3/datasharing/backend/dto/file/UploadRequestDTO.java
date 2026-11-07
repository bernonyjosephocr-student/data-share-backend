package com.projet3.datasharing.backend.dto.file;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UploadRequestDTO(
        @Size(min = 6, message = "6 caractères minimum pour le mot de passe") @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String filePassword,
        String tags,
        @NotNull(message = "La durée de validité du fichié est requis.") @Min(value = 1, message = "La duré de validité minimum est de 1 jour") Integer retentionDays) {

}
