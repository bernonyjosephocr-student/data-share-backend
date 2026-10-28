package com.projet3.datasharing.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.projet3.datasharing.backend.dto.UserAccountDTO;
import com.projet3.datasharing.backend.entity.UserAccount;

@Mapper(componentModel = "spring")
public interface UserAccountMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "passwordHash", source = "password")
	@Mapping(target = "createdAt", ignore = true)
	UserAccount toEntity(UserAccountDTO userAccountDTO);

	@Mapping(target = "password", ignore = true)
	UserAccountDTO toDto(UserAccount userAccount);
}
