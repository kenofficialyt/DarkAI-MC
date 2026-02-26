package com.aiserver.assistant;

import com.aiserver.assistant.ai.*;
import com.aiserver.assistant.commands.AICommand;
import com.aiserver.assistant.utils.ConfigUtils;
import com.aiserver.assistant.utils.TranslationUtil;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class AIPlugin extends JavaPlugin {
    private ConfigUtils configUtils;
    private TranslationUtil translations;
    private AIProvider aiProvider;
    private AIContextManager contextManager;
    private CommandExecutor commandExecutor;

    @Override
    public void onEnable() {
        getLogger().info("AI Server Assistant v1.0.0 - Starting...");

        saveDefaultConfig();
        
        loadConfiguration();
        
        initializeAI();
        
        initializeContextManager();
        
        registerCommands();
        
        startAutoSave();

        getLogger().info("AI Server Assistant enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("AI Server Assistant - Shutting down...");
        
        if (contextManager != null) {
            contextManager.saveToFile();
        }
        
        getLogger().info("AI Server Assistant disabled.");
    }

    private void loadConfiguration() {
        configUtils = new ConfigUtils(getDataFolder().toPath());
        configUtils.load();

        String language = configUtils.getString("language", "en");
        
        translations = new TranslationUtil(getDataFolder().toPath());
        
        File[] langFiles = getDataFolder().listFiles((dir, name) -> 
            name.startsWith("messages_") && name.endsWith(".properties")
        );
        
        if (langFiles != null) {
            for (File f : langFiles) {
                String name = f.getName();
                String code = name.replace("messages_", "").replace(".properties", "");
                translations.loadLanguage(code);
            }
        }
        
        translations.loadLanguage(language);
        
        copyResourceIfNotExists("config.yml");
    }

    private void copyResourceIfNotExists(String resourceName) {
        Path targetPath = getDataFolder().toPath().resolve(resourceName);
        
        if (!Files.exists(targetPath)) {
            saveResource(resourceName, true);
        }
    }

    private void initializeAI() {
        String providerType = configUtils.getString("ai.provider", "openai").toLowerCase();
        
        switch (providerType) {
            case "openai" -> {
                OpenAIProvider openAI = new OpenAIProvider();
                openAI.setApiKey(configUtils.getString("ai.openai-key", ""));
                openAI.setModel(configUtils.getString("ai.model", "gpt-4"));
                openAI.setMaxTokens(configUtils.getInt("ai.max-tokens", 2000));
                openAI.setTemperature(configUtils.getDouble("ai.temperature", 0.7));
                aiProvider = openAI;
            }
            case "anthropic" -> {
                AnthropicProvider anthropic = new AnthropicProvider();
                anthropic.setApiKey(configUtils.getString("ai.anthropic-key", ""));
                anthropic.setModel(configUtils.getString("ai.model", "claude-3-sonnet-20240229"));
                anthropic.setMaxTokens(configUtils.getInt("ai.max-tokens", 2000));
                anthropic.setTemperature(configUtils.getDouble("ai.temperature", 0.7));
                aiProvider = anthropic;
            }
            case "ollama" -> {
                OllamaProvider ollama = new OllamaProvider();
                ollama.setOllamaUrl(configUtils.getString("ai.ollama-url", "http://localhost:11434"));
                ollama.setModel(configUtils.getString("ai.model", "llama2"));
                ollama.setMaxTokens(configUtils.getInt("ai.max-tokens", 2000));
                ollama.setTemperature(configUtils.getDouble("ai.temperature", 0.7));
                aiProvider = ollama;
            }
            default -> {
                getLogger().warning("Unknown AI provider: " + providerType + ", using OpenAI");
                aiProvider = new OpenAIProvider();
            }
        }

        if (!aiProvider.isAvailable()) {
            getLogger().warning("AI provider not configured properly. Please set API key in config.yml");
        } else {
            getLogger().info("AI Provider: " + aiProvider.getName() + " - Available");
        }
    }

    private void initializeContextManager() {
        int autoSaveInterval = configUtils.getInt("limits.auto-save-interval", 300);
        
        contextManager = new AIContextManager(
            getDataFolder().toPath().resolve("data"),
            20
        );
        
        try {
            contextManager.loadFromFile();
        } catch (Exception e) {
            getLogger().warning("Could not load conversation history: " + e.getMessage());
        }
    }

    private void registerCommands() {
        commandExecutor = new AICommand(this);
        
        getCommand("ai").setExecutor(commandExecutor);
        
        getLogger().info("Registered command: /ai");
    }

    private void startAutoSave() {
        int autoSaveInterval = configUtils.getInt("limits.auto-save-interval", 300);
        
        long ticks = autoSaveInterval * 20L;
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (contextManager != null) {
                    contextManager.saveToFile();
                    contextManager.cleanupOldConversations();
                }
            }
        }.runTaskTimer(this, ticks, ticks);
    }

    public void reloadConfig() {
        loadConfiguration();
        initializeAI();
    }

    public ConfigUtils getConfigUtils() {
        return configUtils;
    }

    public TranslationUtil getTranslations() {
        return translations;
    }

    public AIProvider getAIProvider() {
        return aiProvider;
    }

    public AIContextManager getContextManager() {
        return contextManager;
    }
}
