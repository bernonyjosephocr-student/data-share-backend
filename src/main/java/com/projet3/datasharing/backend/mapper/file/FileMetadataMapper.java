package com.projet3.datasharing.backend.mapper.file;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.projet3.datasharing.backend.dto.file.FileMetadataDTO;
import com.projet3.datasharing.backend.entity.file.FileMetadata;

@Mapper(componentModel = "spring")
public interface FileMetadataMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userAccount", ignore = true)
    @Mapping(target = "fileSize", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "expiryDate", ignore = true)
    @Mapping(target = "downloadToken", ignore = true)
    FileMetadata toEntity(FileMetadataDTO fileMetadataDTO);

    @Mapping(target = "isPasswordProtected", expression = "java(fileMetadata.getFilePassword() != null && !fileMetadata.getFilePassword().isBlank())")
    @Mapping(target = "filePassword", ignore = true)
    FileMetadataDTO toDto(FileMetadata fileMetadata);

    List<FileMetadataDTO> toDtoList(List<FileMetadata> fileMetadataList);
}