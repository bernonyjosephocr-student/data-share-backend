package com.projet3.datasharing.backend.service.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.projet3.datasharing.backend.service.file.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExpiredFileCleanupCronServiceTest {

  @Mock private FileService fileService;

  @Mock private TaskScheduler taskScheduler;

  @InjectMocks private ExpiredFileCleanupCronService cronService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(cronService, "cronExpression", "0 */5 * * * *");
  }

  /** Tests that activating the cleanup schedules a task. */
  @Test
  void shouldScheduleCleanupTask() {
    cronService.activate();

    verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
  }

  /** Tests that the scheduled task delegates expired-file cleanup to FileService. */
  @Test
  void shouldTriggerExpiredFileCleanup() {
    ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

    cronService.activate();

    verify(taskScheduler).schedule(taskCaptor.capture(), any(CronTrigger.class));

    taskCaptor.getValue().run();

    verify(fileService).deleteExpiredFiles();
  }
}
