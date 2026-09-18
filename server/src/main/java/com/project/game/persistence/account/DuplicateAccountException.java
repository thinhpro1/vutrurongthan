package com.project.game.persistence.account;

public final class DuplicateAccountException extends AccountRepositoryException {
    public DuplicateAccountException(String message, Throwable cause) {
        super(message, cause);
    }
}
