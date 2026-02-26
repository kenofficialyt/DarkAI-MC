package com.aiserver.assistant.features;

import com.aiserver.assistant.AIPlugin;
import com.aiserver.assistant.ai.AIProvider;
import com.aiserver.assistant.utils.TranslationUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ItemCreator {
    private final AIPlugin plugin;
    private final AIProvider aiProvider;
    private final TranslationUtil translations;
    private final Path dataPath;
    private final Gson gson;

    public ItemCreator(AIPlugin plugin) {
        this.plugin = plugin;
        this.aiProvider = plugin.getAIProvider();
        this.translations = plugin.getTranslations();
        this.dataPath = plugin.getDataFolder().toPath().resolve("data");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        if (!Files.exists(dataPath)) {
            try {
                Files.createDirectories(dataPath);
            } catch (Exception ignored) {}
        }
    }

    public CompletableFuture<String> createItem(String spec) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = String.format(
                    "Create a custom Minecraft item. Generate a JSON with:\n" +
                    "{\n" +
                    "  \"name\": \"Display Name\",\n" +
                    "  \"material\": \"MATERIAL_NAME\",\n" +
                    "  \"amount\": 1,\n" +
                    "  \"lore\": [\"line1\", \"line2\"],\n" +
                    "  \"enchantments\": [{\"name\": \"EFFECT\", \"level\": 1}],\n" +
                    "  \"attributes\": [{\"name\": \"ATTRIBUTE\", \"operation\": 0, \"amount\": 1.0, \"slot\": \"mainhand\"}],\n" +
                    "  \"flags\": [\"HIDE_ENCHANTS\", \"HIDE_ATTRIBUTES\"],\n" +
                    "  \"custom-model-data\": 1,\n" +
                    "  \"durability\": 0,\n" +
                    "  \"unbreakable\": false\n" +
                    "}\n\n" +
                    "Item specification: %s\n\n" +
                    "Use valid Material names (DIAMOND_SWORD, GOLDEN_APPLE, etc.)\n" +
                    "Use valid Enchantment names (EFFICIENCY, UNBREAKING, etc.)\n" +
                    "Use valid Attribute names (GENERIC_ATTACK_SPEED, GENERIC_MAX_HEALTH, etc.)\n" +
                    "Use valid ItemFlag names (HIDE_ENCHANTS, HIDE_ATTRIBUTES, etc.)\n" +
                    "Output ONLY valid JSON, no explanation.",
                    spec
                );

                String aiResponse = aiProvider.chat(prompt).join();
                String jsonResponse = extractJson(aiResponse);
                
                if (jsonResponse == null) {
                    return translations.get("item.error").replace("{error}", "Could not parse item data");
                }

                JsonObject itemData = JsonParser.parseString(jsonResponse).getAsJsonObject();

                ItemStack item = createItemStack(itemData);
                
                saveItemToFile(itemData);

                return translations.get("item.created")
                    .replace("{name}", itemData.has("name") ? itemData.get("name").getAsString() : "Custom Item");

            } catch (Exception e) {
                return translations.get("item.error").replace("{error}", e.getMessage());
            }
        });
    }

    public CompletableFuture<String> createTrade(String spec) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = String.format(
                    "Create a Minecraft villager trade. Generate a JSON with:\n" +
                    "{\n" +
                    "  \"result\": {\"material\": \"DIAMOND\", \"amount\": 1},\n" +
                    "  \"ingredients\": [{\"material\": \"EMERALD\", \"amount\": 10}],\n" +
                    "  \"uses\": 10,\n" +
                    "  \"maxUses\": 20,\n" +
                    "  \"exp\": 10,\n" +
                    "  \"priceMultiplier\": 0.05,\n" +
                    "  \"rewardExp\": true\n" +
                    "}\n\n" +
                    "Trade specification: %s\n\n" +
                    "Output ONLY valid JSON, no explanation.",
                    spec
                );

                String aiResponse = aiProvider.chat(prompt).join();
                String jsonResponse = extractJson(aiResponse);
                
                if (jsonResponse == null) {
                    return translations.get("trade.error").replace("{error}", "Could not parse trade data");
                }

                JsonObject tradeData = JsonParser.parseString(jsonResponse).getAsJsonObject();

                saveTradeToFile(tradeData);

                String resultDesc = formatTradeDescription(tradeData);

                return translations.get("trade.created")
                    .replace("{trade}", resultDesc);

            } catch (Exception e) {
                return translations.get("trade.error").replace("{error}", e.getMessage());
            }
        });
    }

    private ItemStack createItemStack(JsonObject data) {
        String materialName = data.has("material") ? data.get("material").getAsString() : "STONE";
        Material material = Material.getMaterial(materialName);
        
        if (material == null) {
            material = Material.STONE;
        }

        int amount = data.has("amount") ? data.get("amount").getAsInt() : 1;
        ItemStack item = new ItemStack(material, amount);

        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            if (data.has("name")) {
                meta.setDisplayName(data.get("name").getAsString());
            }

            if (data.has("lore")) {
                JsonArray loreArray = data.getAsJsonArray("lore");
                List<String> lore = new ArrayList<>();
                for (var line : loreArray) {
                    lore.add(line.getAsString());
                }
                meta.setLore(lore);
            }

            if (data.has("custom-model-data") && data.get("custom-model-data").isJsonPrimitive()) {
                meta.setCustomModelData(data.get("custom-model-data").getAsInt());
            }

            if (data.has("unbreakable") && data.get("unbreakable").getAsBoolean()) {
                meta.setUnbreakable(true);
            }

            if (data.has("durability")) {
                short durability = (short) data.get("durability").getAsInt();
                item.setDurability(durability);
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    private void saveItemToFile(JsonObject itemData) {
        try {
            Path itemsFile = dataPath.resolve("custom-items.json");
            JsonObject root;
            
            if (Files.exists(itemsFile)) {
                root = JsonParser.parseString(Files.readString(itemsFile)).getAsJsonObject();
            } else {
                root = new JsonObject();
            }

            JsonArray items;
            if (root.has("items")) {
                items = root.getAsJsonArray("items");
            } else {
                items = new JsonArray();
                root.add("items", items);
            }

            items.add(itemData);

            try (FileWriter writer = new FileWriter(itemsFile.toFile())) {
                writer.write(gson.toJson(root));
            }
        } catch (Exception ignored) {}
    }

    private void saveTradeToFile(JsonObject tradeData) {
        try {
            Path tradesFile = dataPath.resolve("custom-trades.json");
            JsonObject root;
            
            if (Files.exists(tradesFile)) {
                root = JsonParser.parseString(Files.readString(tradesFile)).getAsJsonObject();
            } else {
                root = new JsonObject();
            }

            JsonArray trades;
            if (root.has("trades")) {
                trades = root.getAsJsonArray("trades");
            } else {
                trades = new JsonArray();
                root.add("trades", trades);
            }

            tradeData.addProperty("created_at", System.currentTimeMillis());
            trades.add(tradeData);

            try (FileWriter writer = new FileWriter(tradesFile.toFile())) {
                writer.write(gson.toJson(root));
            }
        } catch (Exception ignored) {}
    }

    private String formatTradeDescription(JsonObject trade) {
        StringBuilder desc = new StringBuilder();
        
        if (trade.has("result")) {
            JsonObject result = trade.getAsJsonObject("result");
            desc.append(result.get("amount").getAsInt())
                .append("x ")
                .append(result.get("material").getAsString());
        }
        
        desc.append(" <- ");
        
        if (trade.has("ingredients")) {
            JsonArray ingredients = trade.getAsJsonArray("ingredients");
            List<String> ingList = new ArrayList<>();
            for (var ing : ingredients) {
                JsonObject i = ing.getAsJsonObject();
                ingList.add(i.get("amount").getAsInt() + "x " + i.get("material").getAsString());
            }
            desc.append(String.join(", ", ingList));
        }

        return desc.toString();
    }

    private String extractJson(String response) {
        int startIndex = response.indexOf('{');
        int endIndex = response.lastIndexOf('}');
        
        if (startIndex >= 0 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        
        if (response.contains("```json")) {
            startIndex = response.indexOf("```json") + 7;
            int end = response.indexOf("```", startIndex);
            if (end > startIndex) {
                return response.substring(startIndex, end).trim();
            }
        }
        
        return null;
    }

    public void giveItemToPlayer(Player player, JsonObject itemData) {
        ItemStack item = createItemStack(itemData);
        player.getInventory().addItem(item);
    }

    public List<JsonObject> getCustomItems() {
        List<JsonObject> items = new ArrayList<>();
        
        try {
            Path itemsFile = dataPath.resolve("custom-items.json");
            if (Files.exists(itemsFile)) {
                JsonObject root = JsonParser.parseString(Files.readString(itemsFile)).getAsJsonObject();
                if (root.has("items")) {
                    for (var item : root.getAsJsonArray("items")) {
                        items.add(item.getAsJsonObject());
                    }
                }
            }
        } catch (Exception ignored) {}
        
        return items;
    }

    public List<JsonObject> getCustomTrades() {
        List<JsonObject> trades = new ArrayList<>();
        
        try {
            Path tradesFile = dataPath.resolve("custom-trades.json");
            if (Files.exists(tradesFile)) {
                JsonObject root = JsonParser.parseString(Files.readString(tradesFile)).getAsJsonObject();
                if (root.has("trades")) {
                    for (var trade : root.getAsJsonArray("trades")) {
                        trades.add(trade.getAsJsonObject());
                    }
                }
            }
        } catch (Exception ignored) {}
        
        return trades;
    }
}
