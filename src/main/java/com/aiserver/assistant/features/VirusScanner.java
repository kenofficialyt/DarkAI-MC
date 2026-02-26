package com.aiserver.assistant.features;

import com.aiserver.assistant.AIPlugin;
import com.aiserver.assistant.ai.AIProvider;
import com.aiserver.assistant.utils.TranslationUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VirusScanner {
    private final AIPlugin plugin;
    private final AIProvider aiProvider;
    private final TranslationUtil translations;
    private final Path dataPath;
    private final Gson gson;

    private static final Set<String> SUSPICIOUS_PATTERNS = Set.of(
        "Runtime.getRuntime().exec",
        "ProcessBuilder",
        "javax.script.ScriptEngineManager",
        "base64_decode",
        "eval(",
        "exec(",
        "curl(",
        "wget(",
        "ProcessImpl",
        "UNIXProcess",
        "Deserialization",
        "ObjectInputStream",
        "readObject",
        "crypto",
        "Cipher",
        "SecretKey",
        "HttpURLConnection",
        "URLConnection",
        "Socket(",
        "ServerSocket",
        "InetAddress.getByName",
        "Process p =",
        "Runtime r =",
        "class Loader",
        "defineClass",
        "custom classloader",
        "native method"
    );

    private static final Set<String> KNOWN_MALICIOUS_PATTERNS = Set.of(
        "rm -rf",
        "format c:",
        "del /f /s",
        "shred",
        "wannacry",
        "coinminer",
        "cryptominer",
        "monero",
        "bitcoin",
        "backdoor",
        "keylogger",
        "rat ",
        "remote access trojan"
    );

    private static final Set<String> SAFE_PLUGINS = Set.of(
        "spigot",
        "paper",
        "bukkit",
        "craftbukkit",
        "vault",
        "worldedit",
        "worldguard",
        " Essentials",
        "ProtocolLib",
        "PlaceholderAPI",
        "LuckPerms",
        "CMI",
        "GriefDefender",
        "CoreProtect"
    );

    public VirusScanner(AIPlugin plugin) {
        this.plugin = plugin;
        this.aiProvider = plugin.getAIProvider();
        this.translations = plugin.getTranslations();
        this.dataPath = plugin.getDataFolder().toPath().resolve("virus_scans");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        if (!Files.exists(dataPath)) {
            try {
                Files.createDirectories(dataPath);
            } catch (Exception ignored) {}
        }
    }

    public CompletableFuture<String> scanPlugins() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path pluginsFolder = plugin.getDataFolder().toPath().getParent().resolve("plugins");
                
                if (!Files.exists(pluginsFolder)) {
                    return "Plugins folder not found";
                }

                List<Path> pluginJars = Files.list(pluginsFolder)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .toList();

                if (pluginJars.isEmpty()) {
                    return "No plugins found to scan";
                }

                List<ScanResult> results = new ArrayList<>();
                
                for (Path jarPath : pluginJars) {
                    ScanResult result = scanPlugin(jarPath);
                    results.add(result);
                }

                saveScanReport(results);

                long threatCount = results.stream()
                    .filter(r -> r.threatLevel > 0)
                    .count();

                return translations.get("scanner.complete")
                    .replace("{count}", String.valueOf(threatCount));

            } catch (Exception e) {
                return "Error scanning plugins: " + e.getMessage();
            }
        });
    }

    private ScanResult scanPlugin(Path jarPath) {
        String pluginName = jarPath.getFileName().toString().replace(".jar", "");
        
        for (String safe : SAFE_PLUGINS) {
            if (pluginName.toLowerCase().contains(safe.toLowerCase())) {
                return new ScanResult(pluginName, jarPath.toString(), 0, new ArrayList<>());
            }
        }

        List<String> findings = new ArrayList<>();
        int threatLevel = 0;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarPath))) {
            ZipEntry entry;
            byte[] buffer = new byte[1024];
            
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().toLowerCase();
                
                if (entryName.endsWith(".class") || entryName.endsWith(".java") || 
                    entryName.endsWith(".txt") || entryName.endsWith(".xml")) {
                    
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    String content = baos.toString().toLowerCase();
                    
                    for (String pattern : KNOWN_MALICIOUS_PATTERNS) {
                        if (content.contains(pattern.toLowerCase())) {
                            findings.add("MALICIOUS: Known malicious pattern - " + pattern);
                            threatLevel = Math.max(threatLevel, 3);
                        }
                    }
                    
                    for (String pattern : SUSPICIOUS_PATTERNS) {
                        if (content.contains(pattern.toLowerCase())) {
                            findings.add("SUSPICIOUS: " + pattern);
                            threatLevel = Math.max(threatLevel, 1);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (threatLevel > 0) {
            String prompt = String.format(
                "You are a security analyst. Analyze this Minecraft plugin for threats.\n\n" +
                "PLUGIN: %s\n\n" +
                "FINDINGS:\n%s\n\n" +
                "Provide:\n" +
                "1. Threat assessment (Low/Medium/High/Critical)\n" +
                "2. Detailed explanation of risks\n" +
                "3. Recommended action\n\n" +
                "If the patterns found are false positives (common in legitimate plugins), explain why.",
                pluginName, String.join("\n", findings)
            );

            try {
                String aiAnalysis = aiProvider.chat(prompt).join();
                findings.add("\n[AI ANALYSIS]\n" + aiAnalysis);
            } catch (Exception ignored) {}
        }

        return new ScanResult(pluginName, jarPath.toString(), threatLevel, findings);
    }

    private void saveScanReport(List<ScanResult> results) {
        try {
            JsonObject report = new JsonObject();
            report.addProperty("timestamp", System.currentTimeMillis());
            report.addProperty("total_plugins", results.size());
            report.addProperty("threats_found", results.stream().filter(r -> r.threatLevel > 0).count());

            JsonArray pluginsArray = new JsonArray();
            
            for (ScanResult result : results) {
                JsonObject pluginObj = new JsonObject();
                pluginObj.addProperty("name", result.pluginName);
                pluginObj.addProperty("path", result.path);
                pluginObj.addProperty("threat_level", result.threatLevel);
                
                JsonArray findingsArray = new JsonArray();
                for (String finding : result.findings) {
                    findingsArray.add(finding);
                }
                pluginObj.add("findings", findingsArray);
                
                pluginsArray.add(pluginObj);
            }
            
            report.add("plugins", pluginsArray);

            String fileName = "scan_" + System.currentTimeMillis() + ".json";
            Path reportPath = dataPath.resolve(fileName);
            
            try (FileWriter writer = new FileWriter(reportPath.toFile())) {
                writer.write(gson.toJson(report));
            }

        } catch (Exception ignored) {}
    }

    public CompletableFuture<String> scanSinglePlugin(String pluginName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path pluginsFolder = plugin.getDataFolder().toPath().getParent().resolve("plugins");
                
                Optional<Path> jarPath = Files.list(pluginsFolder)
                    .filter(p -> p.getFileName().toString().contains(pluginName))
                    .filter(p -> p.toString().endsWith(".jar"))
                    .findFirst();

                if (jarPath.isEmpty()) {
                    return "Plugin not found: " + pluginName;
                }

                ScanResult result = scanPlugin(jarPath.get());

                if (result.threatLevel == 0) {
                    return translations.get("scanner.clean").replace("{plugin}", result.pluginName);
                } else {
                    StringBuilder response = new StringBuilder();
                    response.append(translations.get("scanner.threat")
                        .replace("{plugin}", result.pluginName)
                        .replace("{type}", "Threat Level: " + result.threatLevel));
                    response.append("\n\nFindings:\n");
                    response.append(String.join("\n", result.findings));
                    return response.toString();
                }

            } catch (Exception e) {
                return "Error scanning plugin: " + e.getMessage();
            }
        });
    }

    private static class ScanResult {
        String pluginName;
        String path;
        int threatLevel;
        List<String> findings;

        ScanResult(String pluginName, String path, int threatLevel, List<String> findings) {
            this.pluginName = pluginName;
            this.path = path;
            this.threatLevel = threatLevel;
            this.findings = findings;
        }
    }
}
