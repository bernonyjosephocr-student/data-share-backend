package com.projet3.datasharing.backend.exception;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Un compte existe déjà pour cet email: " + email);
    }
}
