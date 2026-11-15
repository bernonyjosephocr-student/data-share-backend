package com.projet3.datasharing.backend.controller.user;

import com.projet3.datasharing.backend.config.logging.LogKeys;
import com.projet3.datasharing.backend.dto.user.UserAccountDTO;
import com.projet3.datasharing.backend.service.user.UserAccountService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class UserAccountController {

  private final UserAccountService userAccountService;

  /**
   * Register a user
   *
   * @param userAccountDTO
   * @return HttpStatus.CREATED
   */
  @PostMapping("/register")
  public ResponseEntity<Void> register(@Valid @RequestBody UserAccountDTO userAccountDTO) {
    long startTime = System.nanoTime();
    String result = LogKeys.RESULT_ERROR;

    try {
      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "user_register_request_received")
          .addKeyValue(LogKeys.USER_ID, userAccountDTO.email())
          .log("User Registration request received");

      userAccountService.register(userAccountDTO);

      result = LogKeys.RESULT_SUCCESS;

      return ResponseEntity.status(HttpStatus.CREATED).build();
    } finally {
      long durationMs = getDuration(startTime);

      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "user_register_request_received")
          .addKeyValue(LogKeys.RESPONSE_TYPE, result)
          .addKeyValue(LogKeys.DURATION_MS, durationMs)
          .log(" User Registration processing finished");
    }
  }

  /**
   * Authenticates a user.
   *
   * @param userAccountDTO
   * @return a valid session TOKEN
   */
  @PostMapping("/login")
  public ResponseEntity<Map<String, String>> login(
      @Valid @RequestBody UserAccountDTO userAccountDTO) {

    long startTime = System.nanoTime();
    String result = LogKeys.RESULT_ERROR;

    try {
      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "user_login_request_received")
          .addKeyValue(LogKeys.USER_ID, userAccountDTO.email())
          .log("User Login request received");

      String token = userAccountService.login(userAccountDTO);

      result = LogKeys.RESULT_SUCCESS;

      return ResponseEntity.ok(Map.of("token", token));
    } finally {
      long durationMs = getDuration(startTime);

      log.atInfo()
          .addKeyValue(LogKeys.EVENT, "user_login_request_received")
          .addKeyValue(LogKeys.RESPONSE_TYPE, result)
          .addKeyValue(LogKeys.DURATION_MS, durationMs)
          .log(" User Login processing finished");
    }
  }

  private long getDuration(long startTime) {
    return (System.nanoTime() - startTime) / 1_000_000;
  }
}
