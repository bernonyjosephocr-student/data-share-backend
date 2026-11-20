package com.projet3.datasharing.backend.service.storage;

import com.projet3.datasharing.backend.config.logging.LogKeys;
import com.projet3.datasharing.backend.service.file.FileService;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExpiredFileCleanupCronService {

  private final FileService fileService;
  private final TaskScheduler taskScheduler;

  @Value("${storage.cleanup.cron}")
  private String cronExpression;

  private ScheduledFuture<?> scheduledTask;

  /** Updates the cron expression. */
  public synchronized void updateCron(String cron) {
    this.cronExpression = cron;

    if (scheduledTask != null && !scheduledTask.isCancelled()) {
      // do not inturupt the current cron
      scheduledTask.cancel(false);
      // create a new scheduling
      scheduledTask = taskScheduler.schedule(this::cleanupExpiredFiles, new CronTrigger(cron));
    }

    log.atInfo()
        .addKeyValue(LogKeys.EVENT, "expired_files_cleanup_cron_updated")
        .addKeyValue("cron", cron)
        .log("Cleanup cron updated");
  }

  /** Activates the cleanup task. */
  public synchronized void activate() {
    if (scheduledTask != null && !scheduledTask.isCancelled()) {
      return;
    }
    // create the expired files cleanup task usint the cron
    scheduledTask =
        taskScheduler.schedule(this::cleanupExpiredFiles, new CronTrigger(cronExpression));

    log.info("Cleanup activated with cron: {}", cronExpression);
  }

  private void cleanupExpiredFiles() {

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
