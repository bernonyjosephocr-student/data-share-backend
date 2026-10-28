package com.projet3.datasharing.backend.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserAccountDTO(
		Long id,
		@NotBlank @Email String email,
		@NotBlank @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
		LocalDateTime createdAt) {
}
