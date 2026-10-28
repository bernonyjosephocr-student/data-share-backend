package com.projet3.datasharing.backend.exception;

public class EmailAlreadyUsedException extends RuntimeException {

	public EmailAlreadyUsedException(String email) {
		super("An account already exists for email: " + email);
	}
}
