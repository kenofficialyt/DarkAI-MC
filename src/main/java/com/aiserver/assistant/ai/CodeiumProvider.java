package com.aiserver.assistant.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CodeiumProvider implements AIProvider {
    private final HttpClient httpClient;
    private final Gson gson;
    private String apiKey;
    private String model;
    private int maxTokens;
    private double temperature;

    public CodeiumProvider() {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.model = "deepseek-coder-v2";
        this.maxTokens = 2000;
        this.temperature = 0.7;
    }

    @Override
    public String getName() {
        return "Codeium";
    }

    @Override
    public CompletableFuture<String> chat(String prompt) {
        return chatWithContext(prompt, null);
    }

    @Override
    public CompletableFuture<String> chatWithContext(String prompt, List<Map<String, String>> conversationHistory) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", model);
                requestBody.addProperty("max_tokens", maxTokens);
                requestBody.addProperty("temperature", temperature);

                JsonArray messages = new JsonArray();
                
                if (conversationHistory != null) {
                    for (Map<String, String> msg : conversationHistory) {
                        JsonObject messageObj = new JsonObject();
                        messageObj.addProperty("role", msg.get("role"));
                        messageObj.addProperty("content", msg.get("content"));
                        messages.add(messageObj);
                    }
                }

                JsonObject userMessage = new JsonObject();
                userMessage.addProperty("role", "user");
                userMessage.addProperty("content", prompt);
                messages.add(userMessage);

                requestBody.add("messages", messages);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.codeium.com/v1/chat/completions"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonArray choices = jsonResponse.getAsJsonArray("choices");
                    if (choices != null && choices.size() > 0) {
                        return choices.get(0).getAsJsonObject()
                                .getAsJsonObject("message")
                                .get("content")
                                .getAsString();
                    }
                }

                return "Error: API request failed with status " + response.statusCode();

            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        });
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("YOUR_CODEIUM_KEY_HERE");
    }

    @Override
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void setModel(String model) {
        this.model = model;
    }

    @Override
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
}
