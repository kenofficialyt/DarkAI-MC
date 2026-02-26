package com.aiserver.assistant.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ConfigUtils {
    private final Path configPath;
    private FileConfiguration config;

    public ConfigUtils(Path dataFolder) {
        this.configPath = dataFolder.resolve("config.yml");
    }

    public void load() {
        if (!Files.exists(configPath)) {
            return;
        }
        config = YamlConfiguration.loadConfiguration(configPath.toFile());
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public String getString(String path, String defaultValue) {
        return config != null ? config.getString(path, defaultValue) : defaultValue;
    }

    public int getInt(String path, int defaultValue) {
        return config != null ? config.getInt(path, defaultValue) : defaultValue;
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        return config != null ? config.getBoolean(path, defaultValue) : defaultValue;
    }

    public double getDouble(String path, double defaultValue) {
        return config != null ? config.getDouble(path, defaultValue) : defaultValue;
    }

    public Map<String, Object> getSection(String path) {
        if (config != null && config.contains(path)) {
            return config.getConfigurationSection(path).getValues(true);
        }
        return new HashMap<>();
    }

    public void set(String path, Object value) {
        if (config != null) {
            config.set(path, value);
        }
    }

    public void save() {
        if (config != null) {
            try {
                config.save(configPath.toFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
