package com.aiserver.assistant.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TranslationUtil {
    private final Path dataPath;
    private String currentLanguage;
    private FileConfiguration messages;
    private final Map<String, FileConfiguration> loadedLanguages;

    public TranslationUtil(Path dataPath) {
        this.dataPath = dataPath;
        this.loadedLanguages = new HashMap<>();
    }

    public void loadLanguage(String languageCode) {
        this.currentLanguage = languageCode;
        
        Path messagesPath = dataPath.resolve("messages_" + languageCode + ".properties");
        
        if (!Files.exists(messagesPath)) {
            messagesPath = dataPath.resolve("messages.properties");
        }
        
        if (Files.exists(messagesPath)) {
            messages = YamlConfiguration.loadConfiguration(messagesPath.toFile());
        } else {
            messages = new YamlConfiguration();
        }
        
        loadedLanguages.put(languageCode, messages);
    }

    public String get(String key) {
        return get(key, key);
    }

    public String get(String key, String defaultValue) {
        if (messages != null && messages.contains(key)) {
            return messages.getString(key);
        }
        
        for (FileConfiguration lang : loadedLanguages.values()) {
            if (lang.contains(key)) {
                return lang.getString(key);
            }
        }
        
        return defaultValue;
    }

    public String get(String key, Map<String, String> placeholders) {
        String message = get(key);
        
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        
        return message;
    }

    public String translateColorCodes(String message) {
        if (message == null) return "";
        
        return message
            .replace("&0", "§0")
            .replace("&1", "§1")
            .replace("&2", "§2")
            .replace("&3", "§3")
            .replace("&4", "§4")
            .replace("&5", "§5")
            .replace("&6", "§6")
            .replace("&7", "§7")
            .replace("&8", "§8")
            .replace("&9", "§9")
            .replace("&a", "§a")
            .replace("&b", "§b")
            .replace("&c", "§c")
            .replace("&d", "§d")
            .replace("&e", "§e")
            .replace("&f", "§f")
            .replace("&k", "§k")
            .replace("&l", "§l")
            .replace("&m", "§m")
            .replace("&n", "§n")
            .replace("&o", "§o")
            .replace("&r", "§r");
    }

    public String getWithColor(String key) {
        return translateColorCodes(get(key));
    }

    public String getWithColor(String key, Map<String, String> placeholders) {
        return translateColorCodes(get(key, placeholders));
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public Set<String> getAvailableLanguages() {
        return loadedLanguages.keySet();
    }

    public static String[] getSupportedLanguages() {
        return new String[]{"en", "vi"};
    }

    public static String getLanguageDisplayName(String code) {
        return switch (code) {
            case "en" -> "English";
            case "vi" -> "Vietnamese";
            default -> code;
        };
    }
}
