package com.projet3.datasharing.backend.handler;

import com.projet3.datasharing.backend.config.logging.LogKeys;
import com.projet3.datasharing.backend.exception.EmailAlreadyUsedException;
import java.io.FileNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalRestExceptionHandler {

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<Map<String, String>> handleBadCredentials(
      BadCredentialsException exception) {

    log.atError()
        .addKeyValue(LogKeys.EVENT, "login_processing_failed")
        .addKeyValue(LogKeys.ERROR_TYPE, exception.getClass().getSimpleName())
        .setCause(exception)
        .log("Bad Credentials exception while processing login request");

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of(LogKeys.MESSAGE, exception.getMessage()));
  }

  @ExceptionHandler(EmailAlreadyUsedException.class)
  public ResponseEntity<Map<String, String>> handleEmailAlreadyUsed(
      EmailAlreadyUsedException exception) {

    log.atError()
        .addKeyValue(LogKeys.EVENT, "registration_processing_failed")
        .addKeyValue(LogKeys.ERROR_TYPE, exception.getClass().getSimpleName())
        .setCause(exception)
        .log(" Exception while processing login request");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of(LogKeys.MESSAGE, exception.getMessage()));
  }

  @ExceptionHandler(UsernameNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleUsernameNotFoundException(
      UsernameNotFoundException exception) {

    log.atError()
        .addKeyValue(LogKeys.EVENT, "user_file_operation_processing_failed")
        .addKeyValue(LogKeys.ERROR_TYPE, exception.getClass().getSimpleName())
        .setCause(exception)
        .log(" Exception while processing a user file operation");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of(LogKeys.MESSAGE, exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidationExceptions(
      MethodArgumentNotValidException exception) {

    log.atError()
        .addKeyValue(LogKeys.EVENT, "Validation_processing_failed")
        .addKeyValue(LogKeys.ERROR_TYPE, exception.getClass().getSimpleName())
        .setCause(exception)
        .log(" Exception while processing request arguments validation");

    Map<String, String> errors = new LinkedHashMap<>();

    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

    return ResponseEntity.badRequest().body(errors);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handleResponseStatusException(
      ResponseStatusException exception) {

    return ResponseEntity.status(exception.getStatusCode())
        .body(Map.of(LogKeys.MESSAGE, exception.getReason()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, String>> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException exception) {

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            Map.of(
                LogKeys.MESSAGE,
                "La taille du fichier téléversé dépasse la limite maximale autorisée."));
  }

  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<Map<String, String>> handleSecurityException(SecurityException exception) {

    log.atError()
        .addKeyValue(LogKeys.EVENT, "security_processing_failed")
        .addKeyValue(LogKeys.ERROR_TYPE, exception.getClass().getSimpleName())
        .setCause(exception)
        .log(" Exception while processing security filtering on request");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of(LogKeys.MESSAGE, exception.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleIllegalArgumentException(
      IllegalArgumentException exception) {

    log.atError()
        .addKeyValue(LogKeys.EVENT, "finding_processing_failed")
        .addKeyValue(LogKeys.ERROR_TYPE, exception.getClass().getSimpleName())
        .setCause(exception)
        .log(" Exception while looking for an element");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of(LogKeys.MESSAGE, exception.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {

    log.atError()
        .addKeyValue(LogKeys.EVENT, "request_processing_failed")
        .addKeyValue(LogKeys.ERROR_TYPE, ex.getClass().getSimpleName())
        .setCause(ex)
        .log("Unhandled exception while processing request");

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of(LogKeys.MESSAGE, ex.getMessage()));
  }

  @ExceptionHandler(FileNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleFileNotFoundException(Exception ex) {

    log.atError()
        .addKeyValue(LogKeys.EVENT, "retrieve_file_processing_failed")
        .addKeyValue(LogKeys.ERROR_TYPE, ex.getClass().getSimpleName())
        .setCause(ex)
        .log(ex.getMessage());

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of(LogKeys.MESSAGE, ex.getMessage()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, String>> handleNoResourceFound(
      NoResourceFoundException exception) {

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of(LogKeys.MESSAGE, exception.getMessage()));
  }
}
