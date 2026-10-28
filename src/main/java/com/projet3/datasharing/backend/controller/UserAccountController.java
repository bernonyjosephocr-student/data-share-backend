package com.projet3.datasharing.backend.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projet3.datasharing.backend.dto.UserAccountDTO;
import com.projet3.datasharing.backend.service.UserAccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserAccountController {

	private final UserAccountService userAccountService;

	/**
	 * Register a user
	 * @param userAccountDTO
	 * @return HttpStatus.CREATED
	 */
	@PostMapping("/register")
	public ResponseEntity<Void> register(@Valid @RequestBody UserAccountDTO userAccountDTO) {
		userAccountService.register(userAccountDTO);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	/**
	 * Authenticates a user.
	 * @param userAccountDTO
	 * @return a valid session TOKEN
	 */
	@PostMapping("/login")
	public ResponseEntity<Map<String, String>> login(@Valid @RequestBody UserAccountDTO userAccountDTO) {
		String token = userAccountService.login(userAccountDTO);
		return ResponseEntity.ok(Map.of("token", token));
	}
}
