package com.aiserver.assistant.features;

import com.aiserver.assistant.AIPlugin;
import com.aiserver.assistant.ai.AIProvider;
import com.aiserver.assistant.utils.TranslationUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ConfigEditor {
    private final AIPlugin plugin;
    private final AIProvider aiProvider;
    private final TranslationUtil translations;
    private final Path dataPath;

    public ConfigEditor(AIPlugin plugin) {
        this.plugin = plugin;
        this.aiProvider = plugin.getAIProvider();
        this.translations = plugin.getTranslations();
        this.dataPath = plugin.getDataFolder().toPath().resolve("config_editor");
        
        if (!Files.exists(dataPath)) {
            try {
                Files.createDirectories(dataPath);
            } catch (Exception ignored) {}
        }
    }

    public CompletableFuture<String> editConfig(String spec) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String[] parts = spec.split(" ", 3);
                
                if (parts.length < 2) {
                    return "Usage: /ai config <plugin> <setting> <value>";
                }
                
                String pluginName = parts[0];
                String setting = parts[1];
                String value = parts.length > 2 ? parts[2] : null;
                
                Path pluginsFolder = plugin.getDataFolder().toPath().getParent().resolve("plugins");
                
                File[] configFiles = findConfigFiles(pluginsFolder.toFile(), pluginName);
                
                if (configFiles == null || configFiles.length == 0) {
                    return "Plugin not found: " + pluginName;
                }
                
                if (value == null) {
                    return readConfigValue(configFiles[0], setting);
                }
                
                return modifyConfigValue(configFiles[0], setting, value);

            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        });
    }

    public CompletableFuture<String> viewConfig(String pluginName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path pluginsFolder = plugin.getDataFolder().toPath().getParent().resolve("plugins");
                
                File[] configFiles = findConfigFiles(pluginsFolder.toFile(), pluginName);
                
                if (configFiles == null || configFiles.length == 0) {
                    return "Plugin not found: " + pluginName;
                }
                
                FileConfiguration config = YamlConfiguration.loadConfiguration(configFiles[0]);
                StringBuilder output = new StringBuilder();
                output.append("=== ").append(pluginName).append(" config ===\n");
                
                Map<String, Object> values = config.getValues(true);
                int count = 0;
                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    output.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                    count++;
                    if (count >= 50) {
                        output.append("\n... and more (use /ai config <plugin> <key> to view specific)");
                        break;
                    }
                }
                
                return output.toString();

            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        });
    }

    public CompletableFuture<String> analyzeConfig(String pluginName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path pluginsFolder = plugin.getDataFolder().toPath().getParent().resolve("plugins");
                
                File[] configFiles = findConfigFiles(pluginsFolder.toFile(), pluginName);
                
                if (configFiles == null || configFiles.length == 0) {
                    return "Plugin not found: " + pluginName;
                }
                
                FileConfiguration config = YamlConfiguration.loadConfiguration(configFiles[0]);
                String configContent = config.saveToString();
                
                String prompt = String.format(
                    "You are a Minecraft server expert. Analyze this config file and suggest improvements.\n\n" +
                    "PLUGIN: %s\n\n" +
                    "CONFIG:\n%s\n\n" +
                    "Provide:\n" +
                    "1. Current settings summary\n" +
                    "2. Suggested optimizations\n" +
                    "3. Common issues to fix\n" +
                    "4. Performance improvements",
                    pluginName, configContent
                );

                return aiProvider.chat(prompt).join();

            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        });
    }

    public CompletableFuture<String> backupConfig(String pluginName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path pluginsFolder = plugin.getDataFolder().toPath().getParent().resolve("plugins");
                
                File[] configFiles = findConfigFiles(pluginsFolder.toFile(), pluginName);
                
                if (configFiles == null || configFiles.length == 0) {
                    return "Plugin not found: " + pluginName;
                }
                
                File source = configFiles[0];
                String backupName = pluginName + "_" + System.currentTimeMillis() + ".yml";
                Path backupPath = dataPath.resolve(backupName);
                
                Files.copy(source.toPath(), backupPath);
                
                return "Backup created: " + backupName;

            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        });
    }

    private File[] findConfigFiles(File pluginsFolder, String pluginName) {
        File[] jarFiles = pluginsFolder.listFiles((dir, name) -> 
            name.toLowerCase().contains(pluginName.toLowerCase()) && name.endsWith(".jar")
        );
        
        if (jarFiles == null || jarFiles.length == 0) {
            return null;
        }
        
        List<File> configFiles = new ArrayList<>();
        
        for (File jar : jarFiles) {
            File[] ymlFiles = pluginsFolder.listFiles((dir, name) -> 
                name.toLowerCase().contains(jar.getName().replace(".jar", "").toLowerCase()) &&
                (name.endsWith(".yml") || name.endsWith(".yaml"))
            );
            
            if (ymlFiles != null) {
                configFiles.addAll(Arrays.asList(ymlFiles));
            }
        }
        
        return configFiles.toArray(new File[0]);
    }

    private String readConfigValue(File configFile, String key) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            
            if (!config.contains(key)) {
                return "Key not found: " + key + "\nAvailable keys: " + 
                    String.join(", ", config.getKeys(true));
            }
            
            Object value = config.get(key);
            return key + " = " + value;

        } catch (Exception e) {
            return "Error reading config: " + e.getMessage();
        }
    }

    private String modifyConfigValue(File configFile, String key, String value) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            
            Object parsedValue = parseValue(value);
            config.set(key, parsedValue);
            
            config.save(configFile);
            
            return "Updated " + key + " = " + parsedValue;

        } catch (Exception e) {
            return "Error modifying config: " + e.getMessage();
        }
    }

    private Object parseValue(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {}
        
        if (value.startsWith("[") && value.endsWith("]")) {
            String[] items = value.substring(1, value.length() - 1).split(",");
            List<String> list = new ArrayList<>();
            for (String item : items) {
                list.add(item.trim());
            }
            return list;
        }
        
        return value;
    }

    public List<String> getLoadedPlugins() {
        List<String> plugins = new ArrayList<>();
        org.bukkit.plugin.Plugin[] loaded = plugin.getServer().getPluginManager().getPlugins();
        for (org.bukkit.plugin.Plugin p : loaded) {
            plugins.add(p.getName());
        }
        return plugins;
    }
}
