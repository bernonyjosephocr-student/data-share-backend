package com.projet3.datasharing.backend.service.storage;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.projet3.datasharing.backend.service.file.FileService;

@ExtendWith(MockitoExtension.class)
class ExpiredFileCleanupCronServiceTest {

    @Mock
    private FileService fileService;

    @InjectMocks
    private ExpiredFileCleanupCronService cronService;

    /**
     * Tests that the scheduled cleanup task delegates expired-file cleanup to
     * FileService.
     */
    @Test
    void shouldTriggerExpiredFileCleanup() {
        cronService.cleanupExpiredFiles();

        verify(fileService).deleteExpiredFiles();
    }
}
