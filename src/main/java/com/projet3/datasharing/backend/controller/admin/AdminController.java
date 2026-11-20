package com.projet3.datasharing.backend.controller.admin;

import com.projet3.datasharing.backend.service.storage.ExpiredFileCleanupCronService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/cleanup")
@RequiredArgsConstructor
public class AdminController {

  private final ExpiredFileCleanupCronService cleanupService;

  @PutMapping("/cron")
  public ResponseEntity<Void> updateCron(@RequestParam String cron) {

    if (!CronExpression.isValidExpression(cron)) {
      return ResponseEntity.badRequest().build();
    }
    cleanupService.updateCron(cron);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/activate")
  public ResponseEntity<Void> activate() {
    cleanupService.activate();
    return ResponseEntity.noContent().build();
  }
}
