package com.aiserver.assistant.features;

import com.aiserver.assistant.AIPlugin;
import com.aiserver.assistant.ai.AIProvider;
import com.aiserver.assistant.utils.TranslationUtil;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class AntiCheatAI implements Listener {
    private final AIPlugin plugin;
    private final AIProvider aiProvider;
    private final TranslationUtil translations;
    private final Map<UUID, PlayerData> playerDataMap;
    private final Path dataPath;
    private final int alertThreshold;
    private final boolean logSuspicious;

    public AntiCheatAI(AIPlugin plugin) {
        this.plugin = plugin;
        this.aiProvider = plugin.getAIProvider();
        this.translations = plugin.getTranslations();
        this.playerDataMap = new ConcurrentHashMap<>();
        this.dataPath = plugin.getDataFolder().toPath().resolve("anticheat_data");
        this.alertThreshold = plugin.getConfigUtils().getInt("anticheat.alert-threshold", 3);
        this.logSuspicious = plugin.getConfigUtils().getBoolean("anticheat.log-suspicious", true);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        if (!Files.exists(dataPath)) {
            try {
                Files.createDirectories(dataPath);
            } catch (IOException ignored) {}
        }

        startPeriodicCleanup();
    }

    public CompletableFuture<String> scanPlayer(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Player player = plugin.getServer().getPlayer(playerName);
                String playerStats;
                
                if (player == null) {
                    OfflinePlayer offlineData = findOfflinePlayer(playerName);
                    if (offlineData == null) {
                        return translations.get("error.player-not-found").replace("{player}", playerName);
                    }
                    playerStats = getOfflinePlayerStats(offlineData);
                } else {
                    playerStats = getPlayerStats(player);
                }
                
                String prompt = String.format(
                    "You are an AI anti-cheat analyst. Analyze this player data for potential cheating/raiding.\n\n" +
                    "PLAYER: %s\n\n" +
                    "DATA:\n%s\n\n" +
                    "Analyze for:\n" +
                    "1. Speed/hacking indicators\n" +
                    "2. Inventory anomalies\n" +
                    "3. Block interaction patterns\n" +
                    "4. Combat statistics\n" +
                    "5. Movement patterns\n\n" +
                    "Provide a risk assessment (Low/Medium/High) with specific reasons.",
                    playerName, playerStats
                );

                return aiProvider.chat(prompt).join();

            } catch (Exception e) {
                return "Error scanning player: " + e.getMessage();
            }
        });
    }

    private String getOfflinePlayerStats(OfflinePlayer player) {
        StringBuilder stats = new StringBuilder();
        
        stats.append("Name: ").append(player.getName()).append("\n");
        stats.append("UUID: ").append(player.getUniqueId()).append("\n");
        stats.append("OP: ").append(player.isOp()).append("\n");
        stats.append("Banned: ").append(player.isBanned()).append("\n");
        stats.append("Whitelisted: ").append(player.isWhitelisted()).append("\n");
        
        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            stats.append("\n[AI TRACKED DATA]\n");
            stats.append("Speed Violations: ").append(data.speedViolations).append("\n");
            stats.append("Fly Violations: ").append(data.flyViolations).append("\n");
            stats.append("Scaffold Violations: ").append(data.scaffoldViolations).append("\n");
            stats.append("KillAura Indicators: ").append(data.killauraIndicators).append("\n");
        }
        
        return stats.toString();
    }

    private String getPlayerStats(Player player) {
        StringBuilder stats = new StringBuilder();
        
        UUID uuid = player.getUniqueId();
        
        stats.append("Name: ").append(player.getName()).append("\n");
        stats.append("IP: ").append(player.getAddress()).append("\n");
        stats.append("GameMode: ").append(player.getGameMode()).append("\n");
        stats.append("OP: ").append(player.isOp()).append("\n");
        
        Location loc = player.getLocation();
        stats.append("World: ").append(loc.getWorld().getName()).append("\n");
        stats.append("Position: ").append(String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ())).append("\n");
        
        stats.append("Health: ").append(player.getHealth()).append("/").append(player.getMaxHealth()).append("\n");
        stats.append("Food Level: ").append(player.getFoodLevel()).append("\n");
        
        stats.append("Inventory Size: ").append(player.getInventory().getSize()).append("\n");
        stats.append("Held Item: ").append(player.getInventory().getItemInMainHand()).append("\n");
        
        stats.append("Is Flying: ").append(player.isFlying()).append("\n");
        stats.append("Allow Flight: ").append(player.getAllowFlight()).append("\n");
        
        long playTime = player.getPlayerTime();
        stats.append("Play Time (ticks): ").append(playTime).append("\n");
        
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            stats.append("\n[AI TRACKED DATA]\n");
            stats.append("Speed Violations: ").append(data.speedViolations).append("\n");
            stats.append("Fly Violations: ").append(data.flyViolations).append("\n");
            stats.append("Scaffold Violations: ").append(data.scaffoldViolations).append("\n");
            stats.append("KillAura Indicators: ").append(data.killauraIndicators).append("\n");
        }

        return stats.toString();
    }

    private OfflinePlayer findOfflinePlayer(String name) {
        return plugin.getServer().getOfflinePlayer(name);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getConfigUtils().getBoolean("anticheat.enabled", true)) return;
        
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (from.getWorld().equals(to.getWorld())) {
            double distance = from.distance(to);
            
            if (distance > 20) {
                recordViolation(player, "teleport", 5);
            } else if (distance > 10) {
                double expectedMax = getExpectedMaxSpeed(player);
                if (distance > expectedMax) {
                    recordViolation(player, "speed", 1);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfigUtils().getBoolean("anticheat.enabled", true)) return;
        
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (from.getWorld().equals(to.getWorld())) {
            double distance = from.distance(to);
            
            if (distance > 100 && !player.isOp()) {
                recordViolation(player, "teleport", 3);
            }
        }
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        if (!plugin.getConfigUtils().getBoolean("anticheat.enabled", true)) return;
        
        Player player = event.getPlayer();
        
        if (event.isFlying() && !player.getAllowFlight() && !player.isOp()) {
            recordViolation(player, "fly", 2);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getConfigUtils().getBoolean("anticheat.enabled", true)) return;
        
        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK ||
            event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            recordViolation(event.getPlayer(), "interact", 1);
        }
    }

    private double getExpectedMaxSpeed(Player player) {
        double baseSpeed = 0.3;
        
        if (player.isSprinting()) {
            baseSpeed *= 1.3;
        }
        
        if (player.isSneaking()) {
            baseSpeed *= 0.3;
        }
        
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            baseSpeed *= 2.0;
        }
        
        return baseSpeed * 3;
    }

    private void recordViolation(Player player, String type, int severity) {
        UUID uuid = player.getUniqueId();
        
        PlayerData data = playerDataMap.computeIfAbsent(uuid, k -> new PlayerData());
        
        switch (type) {
            case "speed" -> data.speedViolations += severity;
            case "fly" -> data.flyViolations += severity;
            case "scaffold" -> data.scaffoldViolations += severity;
            case "killaura" -> data.killauraIndicators += severity;
            case "teleport" -> data.teleportViolations += severity;
            case "interact" -> data.interactCount++;
        }
        
        data.lastActivity = System.currentTimeMillis();
        
        int totalViolations = data.speedViolations + data.flyViolations + 
                             data.scaffoldViolations + data.killauraIndicators + 
                             data.teleportViolations;
        
        if (totalViolations >= alertThreshold) {
            alertStaff(player, type, totalViolations);
            
            if (logSuspicious) {
                logSuspiciousActivity(player, type, totalViolations);
            }
        }
    }

    private void alertStaff(Player player, String type, int violations) {
        String message = translations.getWithColor("anticheat.suspicious")
            .replace("{player}", player.getName());
        
        String violationMsg = translations.getWithColor("anticheat.violation")
            .replace("{player}", player.getName())
            .replace("{type}", type)
            .replace("{details}", "Total violations: " + violations);
        
        for (Player staff : plugin.getServer().getOnlinePlayers()) {
            if (staff.hasPermission("ai.admin") || staff.isOp()) {
                staff.sendMessage(message);
                staff.sendMessage(violationMsg);
            }
        }
    }

    private void logSuspiciousActivity(Player player, String type, int violations) {
        try {
            Path logFile = dataPath.resolve("suspicious_activity.log");
            String entry = String.format("[%s] %s - Type: %s, Violations: %d\n",
                new Date(), player.getName(), type, violations);
            
            Files.writeString(logFile, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    private void startPeriodicCleanup() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long timeout = 30 * 60 * 1000;
                
                playerDataMap.entrySet().removeIf(entry ->
                    now - entry.getValue().lastActivity > timeout
                );
            }
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L);
    }

    private static class PlayerData {
        int speedViolations = 0;
        int flyViolations = 0;
        int scaffoldViolations = 0;
        int killauraIndicators = 0;
        int teleportViolations = 0;
        int interactCount = 0;
        long lastActivity = System.currentTimeMillis();
    }
}
