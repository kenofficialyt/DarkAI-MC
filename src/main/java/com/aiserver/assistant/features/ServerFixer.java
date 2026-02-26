package com.aiserver.assistant.features;

import com.aiserver.assistant.AIPlugin;
import com.aiserver.assistant.ai.AIProvider;
import com.aiserver.assistant.ai.AIContextManager;
import com.aiserver.assistant.utils.LogAnalyzer;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class ServerFixer {
    private final AIPlugin plugin;
    private final AIProvider aiProvider;
    private final AIContextManager contextManager;

    public ServerFixer(AIPlugin plugin) {
        this.plugin = plugin;
        this.aiProvider = plugin.getAIProvider();
        this.contextManager = plugin.getContextManager();
    }

    public CompletableFuture<String> analyzeIssue(String issue) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String serverInfo = getServerInfo();
                String recentLogs = LogAnalyzer.analyzeRecentLogs(getLogsPath(), 200);
                String errorSummary = LogAnalyzer.summarizeErrors(recentLogs);

                String prompt = String.format(
                    "You are a Minecraft server expert. Analyze the following issue and provide a solution.\n\n" +
                    "ISSUE: %s\n\n" +
                    "SERVER INFO:\n%s\n\n" +
                    "RECENT ERRORS:\n%s\n\n" +
                    "Provide a clear, actionable solution. Include:\n" +
                    "1. Root cause analysis\n" +
                    "2. Step-by-step fix instructions\n" +
                    "3. Prevention tips",
                    issue, serverInfo, errorSummary
                );

                if (contextManager != null) {
                    return aiProvider.chatWithContext(prompt, contextManager.getConversationHistory(null)).join();
                }
                return aiProvider.chat(prompt).join();

            } catch (Exception e) {
                return "Error analyzing issue: " + e.getMessage();
            }
        });
    }

    public CompletableFuture<String> analyzeConfig(String configName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path configPath = plugin.getDataFolder().toPath().getParent().resolve(configName);
                String configContent = "";
                
                if (configPath.toFile().exists()) {
                    configContent = new String(java.nio.file.Files.readAllBytes(configPath));
                }

                String prompt = String.format(
                    "You are a Minecraft server expert. Analyze this configuration file and suggest improvements.\n\n" +
                    "CONFIG FILE: %s\n\n" +
                    "CONTENT:\n%s\n\n" +
                    "Provide configuration improvements with explanations.",
                    configName, configContent
                );

                return aiProvider.chat(prompt).join();

            } catch (Exception e) {
                return "Error analyzing config: " + e.getMessage();
            }
        });
    }

    private String getServerInfo() {
        StringBuilder info = new StringBuilder();
        
        info.append("Server Version: ").append(plugin.getServer().getVersion()).append("\n");
        info.append("Bukkit Version: ").append(plugin.getServer().getBukkitVersion()).append("\n");
        
        int pluginCount = plugin.getServer().getPluginManager().getPlugins().length;
        info.append("Plugins: ").append(pluginCount).append(" loaded\n");
        
        info.append("Online Players: ").append(plugin.getServer().getOnlinePlayers().size()).append("\n");
        
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        
        info.append(String.format("Memory: %dMB / %dMB (max), %dMB free\n", 
            totalMemory - freeMemory, maxMemory, freeMemory));

        return info.toString();
    }

    private Path getLogsPath() {
        return plugin.getDataFolder().toPath().getParent().resolve("logs");
    }
}
