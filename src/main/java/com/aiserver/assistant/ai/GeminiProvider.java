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

public class GeminiProvider implements AIProvider {
    private final HttpClient httpClient;
    private final Gson gson;
    private String apiKey;
    private String model;
    private int maxTokens;
    private double temperature;

    private static final String DEFAULT_MODEL = "gemini-2.0-flash";

    public GeminiProvider() {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.model = DEFAULT_MODEL;
        this.maxTokens = 2000;
        this.temperature = 0.7;
    }

    @Override
    public String getName() {
        return "Google Gemini";
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
                
                JsonArray contents = new JsonArray();
                JsonObject content = new JsonObject();
                content.addProperty("role", "user");
                
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty("text", prompt);
                parts.add(part);
                content.add("parts", parts);
                contents.add(content);
                
                requestBody.add("contents", contents);

                JsonObject generationConfig = new JsonObject();
                generationConfig.addProperty("maxOutputTokens", maxTokens);
                generationConfig.addProperty("temperature", temperature);
                requestBody.add("generationConfig", generationConfig);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
                    if (candidates != null && candidates.size() > 0) {
                        JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                        if (firstCandidate.has("content")) {
                            JsonObject contentObj = firstCandidate.getAsJsonObject("content");
                            JsonArray partsArr = contentObj.getAsJsonArray("parts");
                            if (partsArr != null && partsArr.size() > 0) {
                                return partsArr.get(0).getAsJsonObject().get("text").getAsString();
                            }
                        }
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
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("YOUR_GEMINI_KEY_HERE");
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
