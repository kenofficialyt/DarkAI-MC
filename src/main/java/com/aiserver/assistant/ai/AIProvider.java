package com.aiserver.assistant.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface AIProvider {
    
    String getName();
    
    CompletableFuture<String> chat(String prompt);
    
    CompletableFuture<String> chatWithContext(String prompt, List<Map<String, String>> conversationHistory);
    
    boolean isAvailable();
    
    void setApiKey(String apiKey);
    
    void setModel(String model);
    
    void setMaxTokens(int maxTokens);
    
    void setTemperature(double temperature);
}
