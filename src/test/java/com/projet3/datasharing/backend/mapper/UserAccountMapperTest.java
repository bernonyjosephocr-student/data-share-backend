package com.projet3.datasharing.backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.projet3.datasharing.backend.dto.UserAccountDTO;
import com.projet3.datasharing.backend.entity.UserAccount;

class UserAccountMapperTest {

	private final UserAccountMapper userAccountMapper = Mappers.getMapper(UserAccountMapper.class);

	@Test
	void mapsDtoToEntity() {
		UserAccountDTO userAccountDTO = new UserAccountDTO(
				1L, "user@example.com", "plain-password", LocalDateTime.parse("2026-10-28T10:15:30"));

		UserAccount userAccount = userAccountMapper.toEntity(userAccountDTO);

		assertNull(userAccount.getId());
		assertEquals("user@example.com", userAccount.getEmail());
		assertEquals("plain-password", userAccount.getPasswordHash());
		assertNull(userAccount.getCreatedAt());
	}

	@Test
	void mapsEntityToDtoWithoutExposingPasswordHash() {
		LocalDateTime createdAt = LocalDateTime.parse("2026-10-28T10:15:30");
		UserAccount userAccount = new UserAccount();
		userAccount.setId(1L);
		userAccount.setEmail("user@example.com");
		userAccount.setPasswordHash("encoded-password");
		userAccount.setCreatedAt(createdAt);

		UserAccountDTO userAccountDTO = userAccountMapper.toDto(userAccount);

		assertEquals(1L, userAccountDTO.id());
		assertEquals("user@example.com", userAccountDTO.email());
		assertNull(userAccountDTO.password());
		assertEquals(createdAt, userAccountDTO.createdAt());
	}
}
