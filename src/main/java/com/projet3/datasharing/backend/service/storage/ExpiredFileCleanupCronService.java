package com.projet3.datasharing.backend.service.storage;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.projet3.datasharing.backend.service.file.FileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExpiredFileCleanupCronService {

    private final FileService fileService;

    @Scheduled(cron = "${app.cleanup.cron:0 */5 * * * *}")
    public void cleanupExpiredFiles() {
        log.info("Starting expired files cleanup");

        fileService.deleteExpiredFiles();

        log.info("Expired files cleanup completed");
    }
}
