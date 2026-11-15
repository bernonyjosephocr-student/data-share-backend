package com.projet3.datasharing.backend.service.storage;

import com.projet3.datasharing.backend.config.logging.LogKeys;
import com.projet3.datasharing.backend.service.file.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExpiredFileCleanupCronService {

  private final FileService fileService;

  @Scheduled(cron = "${app.cleanup.cron:0 */5 * * * *}")
  public void cleanupExpiredFiles() {

    long startTime = System.nanoTime();

    log.atInfo()
        .addKeyValue(LogKeys.EVENT, "expired_files_cleanup_started")
        .log("Starting expired files cleanup");

    try {
      fileService.deleteExpiredFiles();
      long duration = (System.nanoTime() - startTime) / 1_000_000;
      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "expired_files_cleanup_completed")
          .addKeyValue(LogKeys.DURATION_MS, duration)
          .log("Expired files cleanup completed");

    } catch (RuntimeException ex) {

      long duration = (System.nanoTime() - startTime) / 1_000_000;

      log.atError()
          .addKeyValue(LogKeys.EVENT, "expired_files_cleanup_failed")
          .addKeyValue(LogKeys.DURATION_MS, duration)
          .setCause(ex)
          .log("Expired files cleanup failed");

      throw ex;
    }
  }
}
