package com.project.game.persistence.player;

public final class DuplicatePlayerException extends PlayerRepositoryException {
    public DuplicatePlayerException(String message, Throwable cause) {
        super(message, cause);
    }
}
