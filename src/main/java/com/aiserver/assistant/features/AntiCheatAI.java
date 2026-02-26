package com.aiserver.assistant.features;

import com.aiserver.assistant.AIPlugin;
import com.aiserver.assistant.ai.AIProvider;
import com.aiserver.assistant.utils.TranslationUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.*;
import java.net.InetAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;

public class AntiCheatAI implements Listener {
    private final AIPlugin plugin;
    private final AIProvider aiProvider;
    private final TranslationUtil translations;
    private final Map<UUID, PlayerData> playerDataMap;
    private final Map<UUID, ViolationRecord> violationRecords;
    private final Path dataPath;
    private final int alertThreshold;
    private final boolean logSuspicious;
    private final int banThreshold;

    public AntiCheatAI(AIPlugin plugin) {
        this.plugin = plugin;
        this.aiProvider = plugin.getAIProvider();
        this.translations = plugin.getTranslations();
        this.playerDataMap = new ConcurrentHashMap<>();
        this.violationRecords = new ConcurrentHashMap<>();
        this.dataPath = plugin.getDataFolder().toPath().resolve("anticheat_data");
        this.alertThreshold = plugin.getConfigUtils().getInt("anticheat.alert-threshold", 3);
        this.logSuspicious = plugin.getConfigUtils().getBoolean("anticheat.log-suspicious", true);
        this.banThreshold = 3;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        if (!Files.exists(dataPath)) {
            try {
                Files.createDirectories(dataPath);
            } catch (IOException ignored) {}
        }

        loadViolationRecords();
        startPeriodicCleanup();
    }

    public CompletableFuture<String> scanPlayer(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Player player = plugin.getServer().getPlayer(playerName);
                String playerStats;
                
                if (player == null) {
                    OfflinePlayer offlineData = plugin.getServer().getOfflinePlayer(playerName);
                    if (!offlineData.hasPlayedBefore()) {
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

    private String getPlayerStats(Player player) {
        StringBuilder stats = new StringBuilder();
        
        UUID uuid = player.getUniqueId();
        
        stats.append("Name: ").append(player.getName()).append("\n");
        stats.append("UUID: ").append(uuid).append("\n");
        if (player.getAddress() != null) {
            stats.append("IP: ").append(player.getAddress().getAddress()).append("\n");
        }
        stats.append("GameMode: ").append(player.getGameMode()).append("\n");
        stats.append("OP: ").append(player.isOp()).append("\n");
        
        Location loc = player.getLocation();
        stats.append("World: ").append(loc.getWorld().getName()).append("\n");
        stats.append("Position: ").append(String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ())).append("\n");
        
        stats.append("Health: ").append(player.getHealth()).append("/").append(player.getMaxHealth()).append("\n");
        stats.append("Food Level: ").append(player.getFoodLevel()).append("\n");
        
        stats.append("Inventory Size: ").append(player.getInventory().getSize()).append("\n");
        
        stats.append("Is Flying: ").append(player.isFlying()).append("\n");
        stats.append("Allow Flight: ").append(player.getAllowFlight()).append("\n");
        
        long playTime = player.getPlayerTime();
        stats.append("Play Time (ticks): ").append(playTime).append("\n");
        
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            stats.append("\n[VIOLATIONS]\n");
            stats.append("Total Violations: ").append(data.totalViolations).append("\n");
            stats.append("Speed: ").append(data.speedViolations).append("\n");
            stats.append("Fly: ").append(data.flyViolations).append("\n");
            stats.append("Scaffold: ").append(data.scaffoldViolations).append("\n");
            stats.append("KillAura: ").append(data.killauraViolations).append("\n");
            stats.append("Reach: ").append(data.reachViolations).append("\n");
            stats.append("AimAssist: ").append(data.aimAssistViolations).append("\n");
            stats.append("FastBreak: ").append(data.fastBreakViolations).append("\n");
            stats.append("InventoryMove: ").append(data.inventoryMoveViolations).append("\n");
            stats.append("Jesus: ").append(data.jesusViolations).append("\n");
            stats.append("NoSlowdown: ").append(data.noSlowdownViolations).append("\n");
        }

        ViolationRecord record = violationRecords.get(uuid);
        if (record != null) {
            stats.append("\n[OFFENSE RECORD]\n");
            stats.append("Total Offenses: ").append(record.totalOffenses).append("\n");
            stats.append("Kick Count: ").append(record.kickCount).append("\n");
            stats.append("Last Offense: ").append(new Date(record.lastOffenseTime)).append("\n");
        }
        
        return stats.toString();
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
            stats.append("\n[VIOLATIONS]\n");
            stats.append("Total Violations: ").append(data.totalViolations).append("\n");
        }
        
        ViolationRecord record = violationRecords.get(uuid);
        if (record != null) {
            stats.append("\n[OFFENSE RECORD]\n");
            stats.append("Total Offenses: ").append(record.totalOffenses).append("\n");
            stats.append("Kick Count: ").append(record.kickCount).append("\n");
        }
        
        return stats.toString();
    }

    // ========== MOVEMENT DETECTION ==========
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getConfigUtils().getBoolean("anticheat.enabled", true)) return;
        if (!plugin.getConfigUtils().getBoolean("anticheat.detection.movement", true)) return;
        
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (from.getWorld().equals(to.getWorld())) {
            double distance = from.distance(to);
            
            double expectedMax = getExpectedMaxSpeed(player);
            
            if (distance > 20) {
                recordViolation(player, "teleport", 5);
            } else if (distance > expectedMax && !player.isOp()) {
                if (player.isFlying() && !player.getAllowFlight()) {
                    recordViolation(player, "fly", 2);
                } else if (distance > expectedMax * 1.5) {
                    recordViolation(player, "speed", 1);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfigUtils().getBoolean("anticheat.enabled", true)) return;
        if (!plugin.getConfigUtils().getBoolean("anticheat.detection.movement", true)) return;
        
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
        if (!plugin.getConfigUtils().getBoolean("anticheat.detection.movement", true)) return;
        
        Player player = event.getPlayer();
        
        if (event.isFlying() && !player.getAllowFlight() && !player.isOp()) {
            recordViolation(player, "fly", 2);
        }
    }

    // ========== COMBAT DETECTION ==========
    
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfigUtils().getBoolean("anticheat.enabled", true)) return;
        if (!plugin.getConfigUtils().getBoolean("anticheat.detection.combat", true)) return;
        
        if (event.getDamager() instanceof Player attacker) {
            if (attacker.isOp()) return;
            
            double reach = getReach(event.getDamager(), event.getEntity());
            if (reach > 3.5) {
                recordViolation(attacker, "reach", 2);
            }
            
            PlayerData data = playerDataMap.get(attacker.getUniqueId());
            if (data != null) {
                data.combatHits++;
                data.lastCombatTime = System.currentTimeMillis();
                
                if (data.combatHits > 20) {
                    recordViolation(attacker, "autoclicker", 1);
                }
                
                long timeDiff = System.currentTimeMillis() - data.lastHitTime;
                if (timeDiff < 50 && timeDiff > 0) {
                    data.rapidFireViolations++;
                    if (data.rapidFireViolations > 10) {
                        recordViolation(attacker, "autoclicker", 2);
                    }
                }
                data.lastHitTime = System.currentTimeMillis();
            }
            
            Location attackerLoc = attacker.getLocation();
            Location targetLoc = event.getEntity().getLocation();
            double yawDiff = Math.abs(attackerLoc.getYaw() - targetLoc.getYaw());
            if (yawDiff > 180) yawDiff = 360 - yawDiff;
            
            if (yawDiff > 90) {
                data = playerDataMap.computeIfAbsent(attacker.getUniqueId(), k -> new PlayerData());
                data.aimAssistViolations++;
            }
        }
    }

    // ========== MISC DETECTION ==========
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfigUtils().getBoolean("anticheat.enabled", true)) return;
        if (!plugin.getConfigUtils().getBoolean("anticheat.detection.misc", true)) return;
        
        Player player = event.getPlayer();
        if (player.isOp()) return;
        
        long timeDiff = System.currentTimeMillis() - playerDataMap.computeIfAbsent(
            player.getUniqueId(), k -> new PlayerData()).lastBreakTime;
        
        if (timeDiff < 50) {
            recordViolation(player, "fastbreak", 1);
        }
        
        playerDataMap.get(player.getUniqueId()).lastBreakTime = System.currentTimeMillis();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfigUtils().getBoolean("anticheat.enabled", true)) return;
        if (!plugin.getConfigUtils().getBoolean("anticheat.detection.misc", true)) return;
        
        Player player = event.getPlayer();
        if (player.isOp()) return;
        
        Location loc = event.getBlockPlaced().getLocation();
        Location playerLoc = player.getLocation();
        
        if (loc.distance(playerLoc) > 4.5) {
            recordViolation(player, "scaffold", 1);
        }
        
        PlayerData data = playerDataMap.computeIfAbsent(player.getUniqueId(), k -> new PlayerData());
        data.blocksPlaced++;
        
        if (data.blocksPlaced > 30) {
            recordViolation(player, "fastplace", 1);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getConfigUtils().getBoolean("anticheat.enabled", true)) return;
        
        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            PlayerData data = playerDataMap.computeIfAbsent(
                event.getPlayer().getUniqueId(), k -> new PlayerData());
            data.lastInteractTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        if (!plugin.getConfigUtils().getBoolean("anticheat.enabled", true)) return;
        if (!plugin.getConfigUtils().getBoolean("anticheat.detection.movement", true)) return;
        
        Player player = event.getPlayer();
        Vector velocity = event.getVelocity();
        
        if (velocity.getY() > 0.5 && !player.isOp()) {
            PlayerData data = playerDataMap.computeIfAbsent(player.getUniqueId(), k -> new PlayerData());
            data.velocityViolations++;
            
            if (data.velocityViolations > 5) {
                recordViolation(player, "velocity", 2);
            }
        }
    }

    @EventHandler
    public void onPlayerEditBook(PlayerEditBookEvent event) {
        if (!plugin.getConfigUtils().getBoolean("anticheat.enabled", true)) return;
        
        Player player = event.getPlayer();
        if (player.isOp()) return;
        
        if (event.getNewBookMeta().getPages().size() > 1) {
            recordViolation(player, "bookbanning", 3);
        }
    }

    // ========== ADVANCED DETECTION ==========
    
    @EventHandler
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (!plugin.getConfigUtils().getBoolean("anticheat.enabled", true)) return;
        if (!plugin.getConfigUtils().getBoolean("anticheat.detection.advanced", true)) return;
        
        Player player = event.getPlayer();
        PlayerData data = playerDataMap.computeIfAbsent(player.getUniqueId(), k -> new PlayerData());
        
        long timeDiff = System.currentTimeMillis() - data.lastAnimationTime;
        
        if (timeDiff < 50) {
            data.animationViolations++;
            if (data.animationViolations > 15) {
                recordViolation(player, "autoclicker", 1);
            }
        }
        
        data.lastAnimationTime = System.currentTimeMillis();
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
        
        if (player.getInventory().getItemInOffHand() != null) {
            baseSpeed *= 0.99;
        }
        
        return baseSpeed * 3;
    }

    private double getReach(org.bukkit.entity.Entity damager, org.bukkit.entity.Entity damaged) {
        Location damagerLoc = damager.getLocation().add(0, 1.5, 0);
        Location damagedLoc = damaged.getLocation();
        
        if (damaged instanceof Player) {
            damagedLoc = damagedLoc.add(0, 1.5, 0);
        }
        
        return damagerLoc.distance(damagedLoc);
    }

    private void recordViolation(Player player, String type, int severity) {
        if (player.isOp()) return;
        
        UUID uuid = player.getUniqueId();
        
        PlayerData data = playerDataMap.computeIfAbsent(uuid, k -> new PlayerData());
        
        switch (type) {
            case "speed" -> data.speedViolations += severity;
            case "fly" -> data.flyViolations += severity;
            case "scaffold" -> data.scaffoldViolations += severity;
            case "killaura", "autoclicker" -> data.killauraViolations += severity;
            case "reach" -> data.reachViolations += severity;
            case "aimassist" -> data.aimAssistViolations += severity;
            case "fastbreak" -> data.fastBreakViolations += severity;
            case "inventorymove" -> data.inventoryMoveViolations += severity;
            case "jesus" -> data.jesusViolations += severity;
            case "noslowdown" -> data.noSlowdownViolations += severity;
            case "teleport" -> data.teleportViolations += severity;
            case "velocity" -> data.velocityViolations += severity;
            case "fastplace" -> data.fastPlaceViolations += severity;
            case "bookbanning" -> data.bookBanningViolations += severity;
        }
        
        data.totalViolations += severity;
        data.lastActivity = System.currentTimeMillis();
        
        int totalViolations = data.speedViolations + data.flyViolations + 
                             data.scaffoldViolations + data.killauraViolations + 
                             data.reachViolations + data.aimAssistViolations +
                             data.fastBreakViolations + data.teleportViolations +
                             data.velocityViolations;
        
        if (totalViolations >= alertThreshold) {
            handleOffense(player, type, totalViolations);
            
            if (logSuspicious) {
                logSuspiciousActivity(player, type, totalViolations);
            }
        }
    }

    private void handleOffense(Player player, String type, int violations) {
        UUID uuid = player.getUniqueId();
        ViolationRecord record = violationRecords.computeIfAbsent(uuid, k -> new ViolationRecord());
        
        record.totalOffenses++;
        record.lastOffenseTime = System.currentTimeMillis();
        record.lastViolationType = type;
        record.violationTypes.add(type + ":" + violations);
        
        if (record.kickCount >= banThreshold - 1) {
            banPlayer(player, record);
        } else {
            kickPlayer(player, record, type, violations);
        }
        
        saveViolationRecords();
    }

    private void kickPlayer(Player player, ViolationRecord record, String type, int violations) {
        record.kickCount++;
        
        String kickMessage = translations.getWithColor("anticheat.kick");
        
        String violationMsg = translations.getWithColor("anticheat.violation")
            .replace("{player}", player.getName())
            .replace("{type}", type)
            .replace("{details}", "Offense #" + record.totalOffenses + " | Total violations: " + violations);
        
        alertStaff(player, type, violations);
        
        player.kickPlayer(kickMessage + "\n\n" + violationMsg);
    }

    private void banPlayer(Player player, ViolationRecord record) {
        String ip = "unknown";
        if (player.getAddress() != null) {
            ip = player.getAddress().getAddress().getHostAddress();
        }
        
        record.banned = true;
        record.banTime = System.currentTimeMillis();
        record.bannedIP = ip;
        
        plugin.getServer().getBanList(org.bukkit.BanList.Type.IP).addBan(
            ip,
            "DarkAI MC - Banned for repeated cheating\nOffenses: " + record.totalOffenses,
            null,
            "DarkAI MC Anti-Cheat"
        );
        
        plugin.getServer().getBanList(org.bukkit.BanList.Type.NAME).addBan(
            player.getName(),
            "DarkAI MC - Banned for repeated cheating\n" +
            "Total offenses: " + record.totalOffenses + "\n" +
            "Contact server admin to appeal",
            null,
            "DarkAI MC Anti-Cheat"
        );
        
        player.kickPlayer(
            "DarkAI MC - Banned for repeated cheating\n" +
            "Total offenses: " + record.totalOffenses
        );
        
        alertStaff(player, "BANNED", record.totalOffenses);
        
        String logMsg = String.format("[DarkAI Anti-Cheat] BANNED: %s (IP: %s) - Total offenses: %d",
            player.getName(), ip, record.totalOffenses);
        plugin.getLogger().info(logMsg);
    }

    private void alertStaff(Player player, String type, int violations) {
        String message = translations.getWithColor("anticheat.suspicious")
            .replace("{player}", player.getName());
        
        String violationMsg = translations.getWithColor("anticheat.violation")
            .replace("{player}", player.getName())
            .replace("{type}", type)
            .replace("{details}", "Violations: " + violations);
        
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

    private void loadViolationRecords() {
        Path file = dataPath.resolve("violation_records.json");
        if (!Files.exists(file)) return;
        
        try {
            String content = Files.readString(file);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            
            for (String key : root.keySet()) {
                UUID uuid = UUID.fromString(key);
                JsonObject obj = root.getAsJsonObject(key);
                
                ViolationRecord record = new ViolationRecord();
                record.totalOffenses = obj.get("totalOffenses").getAsInt();
                record.kickCount = obj.get("kickCount").getAsInt();
                record.lastOffenseTime = obj.get("lastOffenseTime").getAsLong();
                record.banned = obj.has("banned") && obj.get("banned").getAsBoolean();
                record.banTime = obj.has("banTime") ? obj.get("banTime").getAsLong() : 0;
                record.bannedIP = obj.has("bannedIP") ? obj.get("bannedIP").getAsString() : null;
                
                violationRecords.put(uuid, record);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveViolationRecords() {
        try {
            Path file = dataPath.resolve("violation_records.json");
            JsonObject root = new JsonObject();
            
            for (Map.Entry<UUID, ViolationRecord> entry : violationRecords.entrySet()) {
                JsonObject obj = new JsonObject();
                ViolationRecord record = entry.getValue();
                
                obj.addProperty("totalOffenses", record.totalOffenses);
                obj.addProperty("kickCount", record.kickCount);
                obj.addProperty("lastOffenseTime", record.lastOffenseTime);
                obj.addProperty("banned", record.banned);
                obj.addProperty("banTime", record.banTime);
                if (record.bannedIP != null) {
                    obj.addProperty("bannedIP", record.bannedIP);
                }
                
                root.add(entry.getKey().toString(), obj);
            }
            
            Files.writeString(file, root.toString());
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
                
                saveViolationRecords();
            }
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L);
    }

    private static class PlayerData {
        int speedViolations = 0;
        int flyViolations = 0;
        int scaffoldViolations = 0;
        int killauraViolations = 0;
        int reachViolations = 0;
        int aimAssistViolations = 0;
        int fastBreakViolations = 0;
        int fastPlaceViolations = 0;
        int inventoryMoveViolations = 0;
        int jesusViolations = 0;
        int noSlowdownViolations = 0;
        int teleportViolations = 0;
        int velocityViolations = 0;
        int bookBanningViolations = 0;
        int totalViolations = 0;
        
        int rapidFireViolations = 0;
        int animationViolations = 0;
        
        long lastActivity = System.currentTimeMillis();
        long lastBreakTime = 0;
        long lastHitTime = 0;
        long lastCombatTime = 0;
        long lastInteractTime = 0;
        long lastAnimationTime = 0;
        
        int combatHits = 0;
        int blocksPlaced = 0;
    }

    private static class ViolationRecord {
        int totalOffenses = 0;
        int kickCount = 0;
        long lastOffenseTime = 0;
        String lastViolationType = "";
        List<String> violationTypes = new ArrayList<>();
        
        boolean banned = false;
        long banTime = 0;
        String bannedIP = null;
    }
}
