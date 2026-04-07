package com.example.rag.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationMemoryService {

    private final Map<String, List<String>> sessionMemory = new ConcurrentHashMap<>();

    public void addMessage(String sessionId, String role, String message) {
        sessionMemory.computeIfAbsent(sessionId, k -> new ArrayList<>())
                .add(role + ": " + message);
    }

    public List<String> getMemory(String sessionId) {
        return sessionMemory.getOrDefault(sessionId, List.of());
    }
}
