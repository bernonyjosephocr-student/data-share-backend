package com.projet3.datasharing.backend.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserAccountDTO(
                @Schema(hidden = true) // hidden in Swagger
                Long id,
                @NotBlank @Email String email,
                @NotBlank @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
                @Schema(hidden = true) LocalDateTime createdAt) {
}
