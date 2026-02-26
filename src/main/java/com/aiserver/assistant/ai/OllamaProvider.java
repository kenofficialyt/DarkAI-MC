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

public class OllamaProvider implements AIProvider {
    private final HttpClient httpClient;
    private final Gson gson;
    private String ollamaUrl;
    private String model;
    private int maxTokens;
    private double temperature;

    public OllamaProvider() {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.ollamaUrl = "http://localhost:11434";
        this.model = "llama2";
        this.maxTokens = 2000;
        this.temperature = 0.7;
    }

    @Override
    public String getName() {
        return "Ollama";
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
                requestBody.addProperty("stream", false);
                
                StringBuilder promptBuilder = new StringBuilder();
                
                if (conversationHistory != null) {
                    for (Map<String, String> msg : conversationHistory) {
                        promptBuilder.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n");
                    }
                }
                
                promptBuilder.append("user: ").append(prompt);
                requestBody.addProperty("prompt", promptBuilder.toString());

                JsonObject options = new JsonObject();
                options.addProperty("num_predict", maxTokens);
                options.addProperty("temperature", temperature);
                requestBody.add("options", options);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(ollamaUrl + "/api/generate"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (jsonResponse.has("response")) {
                        return jsonResponse.get("response").getAsString();
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
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/tags"))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void setApiKey(String apiKey) {
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

    public void setOllamaUrl(String ollamaUrl) {
        this.ollamaUrl = ollamaUrl;
    }
}
