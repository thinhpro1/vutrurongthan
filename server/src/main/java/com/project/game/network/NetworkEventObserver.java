package com.project.game.network;

/** Optional, protocol-neutral observer for integration checks and operational metrics. */
@FunctionalInterface
public interface NetworkEventObserver {
    NetworkEventObserver NO_OP = (session, type) -> { };

    void onUpdateData(Session session, int type);
}
