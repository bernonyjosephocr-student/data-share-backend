package com.projet3.datasharing.backend.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface StorageService {

    void store(List<MultipartFile> files, String uploadId);
    
    List<Resource> loadAsResources(String uploadId);
    
    void delete(String uploadId);
}
