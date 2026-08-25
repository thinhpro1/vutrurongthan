package com.project.game.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SessionManager {
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sessionsByIp = new ConcurrentHashMap<>();

    public int nextId() {
        return nextId.getAndIncrement();
    }

    public boolean tryAdd(Session session, int maxPerIp) {
        AtomicInteger count = sessionsByIp.computeIfAbsent(session.remoteAddress(), ignored -> new AtomicInteger());
        if (count.incrementAndGet() > maxPerIp) {
            count.decrementAndGet();
            if (count.get() == 0) {
                sessionsByIp.remove(session.remoteAddress(), count);
            }
            return false;
        }
        sessions.put(session.id(), session);
        return true;
    }

    public void remove(Session session) {
        if (sessions.remove(session.id(), session)) {
            AtomicInteger count = sessionsByIp.get(session.remoteAddress());
            if (count != null && count.decrementAndGet() <= 0) {
                sessionsByIp.remove(session.remoteAddress(), count);
            }
        }
    }

    public Session find(int id) {
        return sessions.get(id);
    }

    public int onlineCount() {
        return sessions.size();
    }

    public void closeAll() {
        sessions.values().forEach(Session::close);
    }
}
