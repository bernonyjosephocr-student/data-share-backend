package com.projet3.datasharing.backend.mapper.user;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.projet3.datasharing.backend.dto.user.UserAccountDTO;
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
