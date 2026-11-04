package com.projet3.datasharing.backend.repository.file;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projet3.datasharing.backend.entity.UserAccount;
import com.projet3.datasharing.backend.entity.file.FileMetadata;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    public List<FileMetadata> findByUserAccount(UserAccount userAccount);

    public Optional<FileMetadata> findByDownloadToken(String token);
}
