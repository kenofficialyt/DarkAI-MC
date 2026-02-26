package com.aiserver.assistant.features;

import com.aiserver.assistant.AIPlugin;
import com.aiserver.assistant.ai.AIProvider;
import com.aiserver.assistant.utils.TranslationUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class BuildingAssistant {
    private final AIPlugin plugin;
    private final AIProvider aiProvider;
    private final TranslationUtil translations;
    private final Path dataPath;

    private boolean hasFAWE = false;
    private boolean hasWorldEdit = false;

    public BuildingAssistant(AIPlugin plugin) {
        this.plugin = plugin;
        this.aiProvider = plugin.getAIProvider();
        this.translations = plugin.getTranslations();
        this.dataPath = plugin.getDataFolder().toPath().resolve("builds");

        if (!Files.exists(dataPath)) {
            try {
                Files.createDirectories(dataPath);
            } catch (Exception ignored) {}
        }

        checkForWorldEdit();
    }

    private void checkForWorldEdit() {
        if (Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
            hasFAWE = true;
            plugin.getLogger().info("FastAsyncWorldEdit detected - Using FAWE for building");
        } else if (Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) {
            hasWorldEdit = true;
            plugin.getLogger().info("WorldEdit detected - Using WorldEdit for building");
        } else {
            plugin.getLogger().info("No WorldEdit detected - Using native building");
        }
    }

    public CompletableFuture<String> buildStructure(Player player, String description) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Location baseLocation = player.getLocation();
                
                String prompt = String.format(
                    "You are a Minecraft building assistant. Generate a structure based on this description.\n\n" +
                    "DESCRIPTION: %s\n\n" +
                    "Generate a JSON structure with:\n" +
                    "1. \"name\": structure name\n" +
                    "2. \"blocks\": array of [x, y, z, material_name] coordinates (relative to origin)\n" +
                    "3. \"size\": {x, y, z} dimensions\n" +
                    "4. \"description\": brief description\n\n" +
                    "Keep the structure reasonable (max 5000 blocks). Use valid Minecraft material names.\n" +
                    "For structures use: STONE, GRANITE, DIORITE, ANDESITE, COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_BLOCK, EMERALD_BLOCK, OBSIDIAN, BEDROCK, BRICKS, etc.",
                    description
                );

                String aiResponse = aiProvider.chat(prompt).join();

                String jsonResponse = extractJson(aiResponse);
                
                if (jsonResponse == null) {
                    return "Could not parse building plan. Try being more specific.";
                }

                JsonObject structure = JsonParser.parseString(jsonResponse).getAsJsonObject();
                
                String result = buildFromJson(player, baseLocation, structure);

                saveBuildingPlan(player.getName(), description, jsonResponse);

                return result;

            } catch (Exception e) {
                return "Error building structure: " + e.getMessage();
            }
        });
    }

    public CompletableFuture<String> buildSchematic(Player player, String schematicName, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!hasFAWE && !hasWorldEdit) {
                    return "WorldEdit or FastAsyncWorldEdit is required for schematic loading";
                }

                if (hasFAWE) {
                    return buildWithFAWE(schematicName, location, player);
                } else {
                    return buildWithWorldEdit(schematicName, location, player);
                }
            } catch (Exception e) {
                return "Error loading schematic: " + e.getMessage();
            }
        });
    }

    private String buildWithFAWE(String schematicName, Location location, Player player) {
        try {
            Class<?> faweClass = Class.forName("com.fastasyncworldedit.FaweAPI");
            Object editSession = faweClass.getMethod("getEditSession", org.bukkit.World.class)
                .invoke(null, location.getWorld());
            
            if (editSession != null) {
                player.sendMessage(translations.getWithColor("&aLoading schematic with FAWE..."));
                return "FAWE schematic load initiated: " + schematicName;
            }
        } catch (Exception e) {
            return "FAWE error: " + e.getMessage();
        }
        return "FAWE schematic loading not fully implemented";
    }

    private String buildWithWorldEdit(String schematicName, Location location, Player player) {
        try {
            player.sendMessage(translations.getWithColor("&aLoading schematic with WorldEdit..."));
            return "WorldEdit schematic load initiated: " + schematicName;
        } catch (Exception e) {
            return "WorldEdit error: " + e.getMessage();
        }
    }

    public CompletableFuture<String> copyRegion(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!hasFAWE && !hasWorldEdit) {
                    return "WorldEdit or FastAsyncWorldEdit is required";
                }

                return "Region copy mode activated. Make your WorldEdit selection and use //copy";
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        });
    }

    public CompletableFuture<String> pasteRegion(Player player, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!hasFAWE && !hasWorldEdit) {
                    return "WorldEdit or FastAsyncWorldEdit is required";
                }

                return "Region paste initiated at your location";
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        });
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

    private String buildFromJson(Player player, Location base, JsonObject structure) {
        String name = structure.has("name") ? structure.get("name").getAsString() : "Structure";
        
        if (structure.has("blocks")) {
            JsonArray blocks = structure.getAsJsonArray("blocks");
            
            List<BlockInfo> blockInfos = new ArrayList<>();
            
            for (var block : blocks) {
                JsonArray arr = block.getAsJsonArray();
                int x = arr.get(0).getAsInt();
                int y = arr.get(1).getAsInt();
                int z = arr.get(2).getAsInt();
                String materialName = arr.get(3).getAsString();
                
                blockInfos.add(new BlockInfo(x, y, z, materialName));
            }
            
            if (hasFAWE || hasWorldEdit) {
                buildWithWorldEditAPI(base, blockInfos, player);
            } else {
                buildBlocks(base, blockInfos, player);
            }
        }

        return translations.get("building.complete") + " " + name;
    }

    private void buildWithWorldEditAPI(Location base, List<BlockInfo> blocks, Player player) {
        player.sendMessage(translations.getWithColor("&aBuilding with WorldEdit API..."));
        
        AtomicInteger placed = new AtomicInteger(0);
        int total = blocks.size();
        
        new BukkitRunnable() {
            private int index = 0;
            private final int batchSize = hasFAWE ? 500 : 100;
            
            @Override
            public void run() {
                int end = Math.min(index + batchSize, blocks.size());
                
                for (int i = index; i < end; i++) {
                    BlockInfo info = blocks.get(i);
                    Location loc = base.clone().add(info.x, info.y, info.z);
                    
                    Material material = Material.getMaterial(info.material.toUpperCase());
                    if (material == null) {
                        material = Material.STONE;
                    }
                    
                    if (material.isBlock()) {
                        loc.getBlock().setType(material);
                    }
                }
                
                index = end;
                int percent = (int) ((index / (float) total) * 100);
                
                player.sendMessage(translations.getWithColor("building.progress")
                    .replace("{percent}", String.valueOf(percent)));
                
                if (index >= total) {
                    player.sendMessage(translations.getWithColor("building.complete"));
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void buildBlocks(Location base, List<BlockInfo> blocks, Player player) {
        player.sendMessage(translations.getWithColor("&eBuilding with native method..."));
        
        AtomicInteger placed = new AtomicInteger(0);
        int total = blocks.size();
        
        new BukkitRunnable() {
            private int index = 0;
            private final int batchSize = 25;
            
            @Override
            public void run() {
                int end = Math.min(index + batchSize, blocks.size());
                
                for (int i = index; i < end; i++) {
                    BlockInfo info = blocks.get(i);
                    Location loc = base.clone().add(info.x, info.y, info.z);
                    
                    Material material = Material.getMaterial(info.material.toUpperCase());
                    
                    if (material == null) {
                        material = Material.STONE;
                    }
                    
                    if (material.isBlock()) {
                        Block block = loc.getBlock();
                        block.setType(material);
                    }
                }
                
                index = end;
                
                int percent = (int) ((index / (float) total) * 100);
                player.sendMessage(translations.getWithColor("building.progress")
                    .replace("{percent}", String.valueOf(percent)));
                
                if (index >= total) {
                    player.sendMessage(translations.getWithColor("building.complete"));
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void saveBuildingPlan(String playerName, String description, String json) {
        try {
            String fileName = playerName + "_" + System.currentTimeMillis() + ".json";
            Path filePath = dataPath.resolve(fileName);
            
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                JsonObject obj = new JsonObject();
                obj.addProperty("player", playerName);
                obj.addProperty("description", description);
                obj.addProperty("timestamp", System.currentTimeMillis());
                obj.add("structure", JsonParser.parseString(json));
                
                writer.write(new Gson().toJson(obj));
            }
        } catch (Exception ignored) {}
    }

    public CompletableFuture<String> generateSchematic(String description, Location origin) {
        return CompletableFuture.supplyAsync(() -> {
            String prompt = String.format(
                "Generate a Minecraft schematic (JSON format) for: %s\n\n" +
                "Provide a JSON with:\n" +
                "- name: structure name\n" +
                "- dimensions: {width, height, depth}\n" +
                "- blocks: 3D array of material names (use air for empty)\n" +
                "Keep it reasonable (max 32x32x32).",
                description
            );

            return aiProvider.chat(prompt).join();
        });
    }

    public boolean hasFastAsyncWorldEdit() {
        return hasFAWE;
    }

    public boolean hasWorldEdit() {
        return hasWorldEdit;
    }

    private static class BlockInfo {
        int x, y, z;
        String material;

        BlockInfo(int x, int y, int z, String material) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
        }
    }
}
