package com.projet3.datasharing.backend.dto.user;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserAccountDTO(
        @Schema(hidden = true) // hidden in Swagger
        Long id,
        @NotBlank(message = "L'email est requis.") @Email String email,

        @Size(min = 8, message = "8 caractères minimum pour le mot de passe") @NotBlank(message = "Le password est requis.") @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
        @Schema(hidden = true) LocalDateTime createdAt) {
}
