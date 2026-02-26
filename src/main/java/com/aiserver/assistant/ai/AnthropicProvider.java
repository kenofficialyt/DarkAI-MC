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

public class AnthropicProvider implements AIProvider {
    private final HttpClient httpClient;
    private final Gson gson;
    private String apiKey;
    private String model;
    private int maxTokens;
    private double temperature;

    private static final String DEFAULT_MODEL = "claude-3-sonnet-20240229";

    public AnthropicProvider() {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.model = DEFAULT_MODEL;
        this.maxTokens = 2000;
        this.temperature = 0.7;
    }

    @Override
    public String getName() {
        return "Anthropic";
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

                StringBuilder contentBuilder = new StringBuilder();
                
                if (conversationHistory != null) {
                    for (Map<String, String> msg : conversationHistory) {
                        contentBuilder.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n\n");
                    }
                }
                
                contentBuilder.append("user: ").append(prompt);
                requestBody.addProperty("messages", contentBuilder.toString());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.anthropic.com/v1/messages"))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonArray content = jsonResponse.getAsJsonArray("content");
                    if (content != null && content.size() > 0) {
                        return content.get(0).getAsJsonObject()
                                .get("text")
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
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("YOUR_ANTHROPIC_KEY_HERE");
    }

    @Override
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void setModel(String model) {
        this.model = model != null && !model.isEmpty() ? model : DEFAULT_MODEL;
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
