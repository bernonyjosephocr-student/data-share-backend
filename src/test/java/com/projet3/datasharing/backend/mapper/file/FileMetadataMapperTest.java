package com.projet3.datasharing.backend.mapper.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.projet3.datasharing.backend.dto.file.FileMetadataDTO;
import com.projet3.datasharing.backend.entity.file.FileMetadata;

class FileMetadataMapperTest {

  private final FileMetadataMapper mapper = Mappers.getMapper(FileMetadataMapper.class);

  @Test
  void mapsDtoToEntity() {
    FileMetadataDTO dto = new FileMetadataDTO(
        null,
        "report.pdf",
        "reports",
        null,
        null,
        true,
        3,
        "password",
        null);

    FileMetadata entity = mapper.toEntity(dto);

    assertEquals("report.pdf", entity.getName());
    assertEquals("reports", entity.getTags());
    assertEquals(3, entity.getRetentionDays());
    assertEquals("password", entity.getFilePassword());
  }

  @Test
  void mapsEntityToDtoWithoutExposingPassword() {
    LocalDateTime createdAt = LocalDateTime.now();
    FileMetadata entity = new FileMetadata();
    entity.setId(42L);
    entity.setName("report.pdf");
    entity.setTags("reports");
    entity.setExpiryDate(createdAt.plusDays(3));
    entity.setRetentionDays(3);
    entity.setFilePassword("encoded-password");

    FileMetadataDTO dto = mapper.toDto(entity);

    assertEquals(42L, dto.id());
    assertEquals("report.pdf", dto.name());
    assertEquals(createdAt.plusDays(3), dto.expiryDate());
    assertEquals(3, dto.retentionDays());
    assertTrue(dto.isPasswordProtected());
    assertNull(dto.filePassword());
  }
}
