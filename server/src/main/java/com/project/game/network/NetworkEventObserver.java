package com.project.game.network;

/**
 * Optional, protocol-neutral observer for integration checks and operational metrics.
 * Callbacks execute synchronously on the session reader thread and must not block.
 */
@FunctionalInterface
public interface NetworkEventObserver {
    NetworkEventObserver NO_OP = (session, type) -> { };

    void onUpdateData(Session session, int type);
}
