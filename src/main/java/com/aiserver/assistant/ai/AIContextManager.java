package com.aiserver.assistant.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AIContextManager {
    private final Map<UUID, Conversation> conversations;
    private final int maxHistorySize;
    private final Path dataPath;
    private final Gson gson;
    private static final long CONVERSATION_TIMEOUT = 30 * 60 * 1000;

    public AIContextManager(Path dataPath, int maxHistorySize) {
        this.conversations = new ConcurrentHashMap<>();
        this.maxHistorySize = maxHistorySize;
        this.dataPath = dataPath;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void addMessage(UUID playerId, String role, String content) {
        Conversation conv = conversations.computeIfAbsent(playerId, k -> new Conversation());
        conv.addMessage(role, content);
        conv.setLastActivity(System.currentTimeMillis());
    }

    public List<Map<String, String>> getConversationHistory(UUID playerId) {
        Conversation conv = conversations.get(playerId);
        if (conv == null) {
            return new ArrayList<>();
        }
        
        if (System.currentTimeMillis() - conv.getLastActivity() > CONVERSATION_TIMEOUT) {
            conv.clear();
            return new ArrayList<>();
        }
        
        return conv.getMessages();
    }

    public void clearConversation(UUID playerId) {
        conversations.remove(playerId);
    }

    public void saveToFile() {
        try {
            if (!Files.exists(dataPath)) {
                Files.createDirectories(dataPath);
            }

            JsonObject root = new JsonObject();
            
            for (Map.Entry<UUID, Conversation> entry : conversations.entrySet()) {
                JsonArray convArray = new JsonArray();
                for (Map<String, String> msg : entry.getValue().getMessages()) {
                    JsonObject msgObj = new JsonObject();
                    msgObj.addProperty("role", msg.get("role"));
                    msgObj.addProperty("content", msg.get("content"));
                    convArray.add(msgObj);
                }
                root.add(entry.getKey().toString(), convArray);
            }

            Path filePath = dataPath.resolve("conversation_history.json");
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                gson.toJson(root, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadFromFile() {
        Path filePath = dataPath.resolve("conversation_history.json");
        if (!Files.exists(filePath)) {
            return;
        }

        try {
            String content = Files.readString(filePath);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();

            for (String key : root.keySet()) {
                UUID playerId = UUID.fromString(key);
                JsonArray convArray = root.getAsJsonArray(key);
                
                Conversation conv = new Conversation();
                for (var element : convArray) {
                    JsonObject msgObj = element.getAsJsonObject();
                    conv.addMessage(
                        msgObj.get("role").getAsString(),
                        msgObj.get("content").getAsString()
                    );
                }
                
                conversations.put(playerId, conv);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cleanupOldConversations() {
        long now = System.currentTimeMillis();
        conversations.entrySet().removeIf(entry -> 
            now - entry.getValue().getLastActivity() > CONVERSATION_TIMEOUT
        );
    }

    private static class Conversation {
        private final Queue<Map<String, String>> messages;
        private long lastActivity;

        public Conversation() {
            this.messages = new ConcurrentLinkedQueue<>();
            this.lastActivity = System.currentTimeMillis();
        }

        public void addMessage(String role, String content) {
            Map<String, String> msg = new HashMap<>();
            msg.put("role", role);
            msg.put("content", content);
            messages.add(msg);

            while (messages.size() > 20) {
                messages.poll();
            }
        }

        public List<Map<String, String>> getMessages() {
            return new ArrayList<>(messages);
        }

        public void clear() {
            messages.clear();
        }

        public long getLastActivity() {
            return lastActivity;
        }

        public void setLastActivity(long lastActivity) {
            this.lastActivity = lastActivity;
        }
    }
}
