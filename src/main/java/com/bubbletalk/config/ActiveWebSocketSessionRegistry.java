package com.bubbletalk.config;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ActiveWebSocketSessionRegistry {

    private final Set<String> activeSessionIds = ConcurrentHashMap.newKeySet();

    public void register(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            activeSessionIds.add(sessionId);
        }
    }

    public void unregister(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            activeSessionIds.remove(sessionId);
        }
    }

    public boolean isActive(String sessionId) {
        return sessionId != null && activeSessionIds.contains(sessionId);
    }

    public int size() {
        return activeSessionIds.size();
    }
}
