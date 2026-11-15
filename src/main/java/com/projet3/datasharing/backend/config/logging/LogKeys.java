package com.projet3.datasharing.backend.config.logging;

public final class LogKeys {

  private LogKeys() {
    // Utility class
  }

  public static final String EVENT = "event";
  public static final String RESULT = "result";
  public static final String DURATION_MS = "duration_ms";
  public static final String MESSAGE = "message";
  public static final String FAILED_COUNT = "failed_count";
  public static final String DELETED_COUNT = "deleted_count";
  public static final String SKIPPED_COUNT = "skipped_count";
  public static final String FOUND_COUNT = "found_count";

  public static final String USER_ID = "user_id";
  public static final String FILE_ID = "file_id";
  public static final String FILE_NAME = "file_name";
  public static final String FILE_PATH = "file_path";
  public static final String FILE_RETRIEVED = "file_retrieved";

  public static final String ERROR_TYPE = "error_type";

  public static final String UPLOAD_EVENT_KEY = "file_upload_Processing";
  public static final String FETCH_FILE_EVENT_KEY = "file_fetching_Processing";
  public static final String DELETION_FILE_EVENT_KEY = "file_deletion_Processing";
  public static final String DOWNLOAD_FILE_EVENT_KEY = "file_downloading_Processing";
  public static final String FILE_PASS = "file_creds";

  public static final String RESPONSE_TYPE = "response_type";
  public static final String RESULT_SUCCESS = "success";
  public static final String RESULT_ERROR = "error";
}
