package com.projet3.datasharing.backend.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.projet3.datasharing.backend.exception.EmailAlreadyUsedException;

@RestControllerAdvice
public class GlobalRestExceptionHandler {

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<Void> handleBadCredentials(BadCredentialsException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}

	@ExceptionHandler(EmailAlreadyUsedException.class)
	public ResponseEntity<Void> handleEmailAlreadyUsed(EmailAlreadyUsedException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
	}
}
