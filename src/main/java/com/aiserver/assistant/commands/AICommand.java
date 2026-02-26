package com.aiserver.assistant.commands;

import com.aiserver.assistant.AIPlugin;
import com.aiserver.assistant.ai.AIProvider;
import com.aiserver.assistant.features.*;
import com.aiserver.assistant.permissions.PermissionManager;
import com.aiserver.assistant.utils.TranslationUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AICommand implements CommandExecutor {
    private final AIPlugin plugin;
    private final TranslationUtil translations;
    private final Map<UUID, Long> rateLimitMap;
    private final int rateLimitSeconds;

    private ServerFixer serverFixer;
    private AntiCheatAI antiCheatAI;
    private BuildingAssistant buildingAssistant;
    private ItemCreator itemCreator;
    private VirusScanner virusScanner;
    private ConfigEditor configEditor;

    public AICommand(AIPlugin plugin) {
        this.plugin = plugin;
        this.translations = plugin.getTranslations();
        this.rateLimitMap = new ConcurrentHashMap<>();
        this.rateLimitSeconds = plugin.getConfigUtils().getInt("limits.requests-per-minute", 10);
        
        if (plugin.getConfigUtils().getBoolean("features.server-fixer", true)) {
            this.serverFixer = new ServerFixer(plugin);
        }
        if (plugin.getConfigUtils().getBoolean("features.anti-cheat", true)) {
            this.antiCheatAI = new AntiCheatAI(plugin);
        }
        if (plugin.getConfigUtils().getBoolean("features.building", true)) {
            this.buildingAssistant = new BuildingAssistant(plugin);
        }
        if (plugin.getConfigUtils().getBoolean("features.item-creator", true)) {
            this.itemCreator = new ItemCreator(plugin);
        }
        if (plugin.getConfigUtils().getBoolean("features.virus-scanner", true)) {
            this.virusScanner = new VirusScanner(plugin);
        }
        if (plugin.getConfigUtils().getBoolean("features.config-editor", true)) {
            this.configEditor = new ConfigEditor(plugin);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionManager.hasAdminPermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help", "?" -> showHelp(sender);
            case "fix" -> handleFix(sender, args);
            case "scan" -> handleScan(sender, args);
            case "build" -> handleBuild(sender, args);
            case "item" -> handleItem(sender, args);
            case "trade" -> handleTrade(sender, args);
            case "mmoitem", "mmo" -> handleMMOItem(sender, args);
            case "enchant", "ae" -> handleEnchantment(sender, args);
            case "itemsadder", "ia" -> handleItemsAdder(sender, args);
            case "oraxen" -> handleOraxen(sender, args);
            case "mythicmobs", "mm" -> handleMythicMobs(sender, args);
            case "scan-plugins", "scanplugins" -> handleScanPlugins(sender);
            case "config" -> handleConfig(sender, args);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(translations.getWithColor("unknown-command"));
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(translations.translateColorCodes("&b=== DarkAI MC ==="));
        
        if (PermissionManager.hasFixPermission(sender)) {
            sender.sendMessage(translations.getWithColor("help.fix"));
        }
        if (PermissionManager.hasScanPermission(sender)) {
            sender.sendMessage(translations.getWithColor("help.scan"));
        }
        if (PermissionManager.hasBuildPermission(sender)) {
            sender.sendMessage(translations.getWithColor("help.build"));
        }
        if (PermissionManager.hasItemPermission(sender)) {
            sender.sendMessage(translations.getWithColor("help.item"));
            sender.sendMessage("&7/ai mmoitem <spec> - Create MMOItems item");
            sender.sendMessage("&7/ai enchant <spec> - Create custom enchantment");
            sender.sendMessage("&7/ai itemsadder <spec> - Create ItemsAdder item");
            sender.sendMessage("&7/ai oraxen <spec> - Create Oraxen item");
            sender.sendMessage("&7/ai mythicmobs <spec> - Create MythicMobs item");
        }
        if (PermissionManager.hasTradePermission(sender)) {
            sender.sendMessage(translations.getWithColor("help.trade"));
        }
        if (PermissionManager.hasScanPluginsPermission(sender)) {
            sender.sendMessage(translations.getWithColor("help.scanplugins"));
        }
        if (PermissionManager.hasReloadPermission(sender)) {
            sender.sendMessage(translations.getWithColor("help.reload"));
        }
    }

    private void handleFix(CommandSender sender, String[] args) {
        if (!PermissionManager.hasFixPermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return;
        }

        if (serverFixer == null) {
            sender.sendMessage(translations.getWithColor("error.feature-disabled"));
            return;
        }

        if (!checkRateLimit(sender)) return;

        if (args.length < 2) {
            sender.sendMessage(translations.getWithColor("error.invalid-arguments"));
            return;
        }

        String issue = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        
        sender.sendMessage(translations.getWithColor("prefix") + "Analyzing issue...");
        
        serverFixer.analyzeIssue(issue).thenAccept(result -> {
            sender.sendMessage(translations.getWithColor("success.fix").replace("{result}", result));
        }).exceptionally(e -> {
            sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
            return null;
        });
    }

    private void handleScan(CommandSender sender, String[] args) {
        if (!PermissionManager.hasScanPermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return;
        }

        if (antiCheatAI == null) {
            sender.sendMessage(translations.getWithColor("error.feature-disabled"));
            return;
        }

        if (!checkRateLimit(sender)) return;

        if (args.length < 2) {
            sender.sendMessage(translations.getWithColor("error.invalid-arguments"));
            return;
        }

        String playerName = args[1];
        
        sender.sendMessage(translations.getWithColor("prefix") + "Scanning player: " + playerName);
        
        antiCheatAI.scanPlayer(playerName).thenAccept(result -> {
            sender.sendMessage(translations.getWithColor("success.scan")
                .replace("{player}", playerName)
                .replace("{result}", result));
        }).exceptionally(e -> {
            sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
            return null;
        });
    }

    private void handleBuild(CommandSender sender, String[] args) {
        if (!PermissionManager.hasBuildPermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return;
        }

        if (buildingAssistant == null) {
            sender.sendMessage(translations.getWithColor("error.feature-disabled"));
            return;
        }

        if (!checkRateLimit(sender)) return;

        if (args.length < 2) {
            sender.sendMessage(translations.getWithColor("error.invalid-arguments"));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(translations.getWithColor("&cThis command requires a player"));
            return;
        }

        String description = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        
        sender.sendMessage(translations.getWithColor("building.start"));
        
        buildingAssistant.buildStructure(player, description).thenAccept(result -> {
            sender.sendMessage(translations.getWithColor("success.build").replace("{result}", result));
        }).exceptionally(e -> {
            sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
            return null;
        });
    }

    private void handleItem(CommandSender sender, String[] args) {
        if (!PermissionManager.hasItemPermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return;
        }

        if (itemCreator == null) {
            sender.sendMessage(translations.getWithColor("error.feature-disabled"));
            return;
        }

        if (!checkRateLimit(sender)) return;

        if (args.length < 2) {
            sender.sendMessage(translations.getWithColor("error.invalid-arguments"));
            return;
        }

        String spec = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        
        sender.sendMessage(translations.getWithColor("prefix") + "Creating item...");
        
        itemCreator.createItem(spec).thenAccept(result -> {
            sender.sendMessage(translations.getWithColor("success.item").replace("{result}", result));
        }).exceptionally(e -> {
            sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
            return null;
        });
    }

    private void handleTrade(CommandSender sender, String[] args) {
        if (!PermissionManager.hasTradePermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return;
        }

        if (itemCreator == null) {
            sender.sendMessage(translations.getWithColor("error.feature-disabled"));
            return;
        }

        if (!checkRateLimit(sender)) return;

        if (args.length < 2) {
            sender.sendMessage(translations.getWithColor("error.invalid-arguments"));
            return;
        }

        String spec = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        
        sender.sendMessage(translations.getWithColor("prefix") + "Creating trade...");
        
        itemCreator.createTrade(spec).thenAccept(result -> {
            sender.sendMessage(translations.getWithColor("success.trade").replace("{result}", result));
        }).exceptionally(e -> {
            sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
            return null;
        });
    }

    private void handleMMOItem(CommandSender sender, String[] args) {
        if (!PermissionManager.hasItemPermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return;
        }

        if (itemCreator == null) {
            sender.sendMessage(translations.getWithColor("error.feature-disabled"));
            return;
        }

        if (!checkRateLimit(sender)) return;

        if (args.length < 2) {
            sender.sendMessage(translations.getWithColor("error.invalid-arguments"));
            return;
        }

        String spec = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        
        sender.sendMessage(translations.getWithColor("prefix") + "Creating MMOItem...");
        
        itemCreator.createMMOItem(spec).thenAccept(result -> {
            sender.sendMessage(translations.getWithColor("&a") + result);
        }).exceptionally(e -> {
            sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
            return null;
        });
    }

    private void handleEnchantment(CommandSender sender, String[] args) {
        if (!PermissionManager.hasItemPermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return;
        }

        if (itemCreator == null) {
            sender.sendMessage(translations.getWithColor("error.feature-disabled"));
            return;
        }

        if (!checkRateLimit(sender)) return;

        if (args.length < 2) {
            sender.sendMessage(translations.getWithColor("error.invalid-arguments"));
            return;
        }

        String spec = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        
        sender.sendMessage(translations.getWithColor("prefix") + "Creating enchantment...");
        
        itemCreator.createEnchantment(spec).thenAccept(result -> {
            sender.sendMessage(translations.getWithColor("&a") + result);
        }).exceptionally(e -> {
            sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
            return null;
        });
    }

    private void handleItemsAdder(CommandSender sender, String[] args) {
        if (!PermissionManager.hasItemPermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return;
        }

        if (itemCreator == null) {
            sender.sendMessage(translations.getWithColor("error.feature-disabled"));
            return;
        }

        if (!checkRateLimit(sender)) return;

        if (args.length < 2) {
            sender.sendMessage(translations.getWithColor("error.invalid-arguments"));
            return;
        }

        String spec = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        
        sender.sendMessage(translations.getWithColor("prefix") + "Creating ItemsAdder item...");
        
        itemCreator.createItemsAdder(spec).thenAccept(result -> {
            sender.sendMessage(translations.getWithColor("&a") + result);
        }).exceptionally(e -> {
            sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
            return null;
        });
    }

    private void handleOraxen(CommandSender sender, String[] args) {
        if (!PermissionManager.hasItemPermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return;
        }

        if (itemCreator == null) {
            sender.sendMessage(translations.getWithColor("error.feature-disabled"));
            return;
        }

        if (!checkRateLimit(sender)) return;

        if (args.length < 2) {
            sender.sendMessage(translations.getWithColor("error.invalid-arguments"));
            return;
        }

        String spec = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        
        sender.sendMessage(translations.getWithColor("prefix") + "Creating Oraxen item...");
        
        itemCreator.createOraxen(spec).thenAccept(result -> {
            sender.sendMessage(translations.getWithColor("&a") + result);
        }).exceptionally(e -> {
            sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
            return null;
        });
    }

    private void handleMythicMobs(CommandSender sender, String[] args) {
        if (!PermissionManager.hasItemPermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return;
        }

        if (itemCreator == null) {
            sender.sendMessage(translations.getWithColor("error.feature-disabled"));
            return;
        }

        if (!checkRateLimit(sender)) return;

        if (args.length < 2) {
            sender.sendMessage(translations.getWithColor("error.invalid-arguments"));
            return;
        }

        String spec = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        
        sender.sendMessage(translations.getWithColor("prefix") + "Creating MythicMobs item...");
        
        itemCreator.createMythicMobs(spec).thenAccept(result -> {
            sender.sendMessage(translations.getWithColor("&a") + result);
        }).exceptionally(e -> {
            sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
            return null;
        });
    }

    private void handleScanPlugins(CommandSender sender) {
        if (!PermissionManager.hasScanPluginsPermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return;
        }

        if (virusScanner == null) {
            sender.sendMessage(translations.getWithColor("error.feature-disabled"));
            return;
        }

        if (!checkRateLimit(sender)) return;

        sender.sendMessage(translations.getWithColor("prefix") + "Scanning plugins...");
        
        virusScanner.scanPlugins().thenAccept(result -> {
            sender.sendMessage(translations.getWithColor("success.scanplugins").replace("{result}", result));
        }).exceptionally(e -> {
            sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
            return null;
        });
    }

    private void handleReload(CommandSender sender) {
        if (!PermissionManager.hasReloadPermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return;
        }

        plugin.reloadConfig();
        sender.sendMessage(translations.getWithColor("reloaded"));
    }

    private void handleConfig(CommandSender sender, String[] args) {
        if (!PermissionManager.hasFixPermission(sender)) {
            sender.sendMessage(translations.getWithColor("error.no-permission"));
            return;
        }

        if (configEditor == null) {
            sender.sendMessage(translations.getWithColor("error.feature-disabled"));
            return;
        }

        if (!checkRateLimit(sender)) return;

        if (args.length < 2) {
            sender.sendMessage("&7=== Config Editor ===");
            sender.sendMessage("&7/ai config view <plugin> - View plugin config");
            sender.sendMessage("&7/ai config edit <plugin> <key> <value> - Edit config");
            sender.sendMessage("&7/ai config analyze <plugin> - AI analyze config");
            sender.sendMessage("&7/ai config backup <plugin> - Backup config");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "view" -> {
                if (args.length < 3) {
                    sender.sendMessage(translations.getWithColor("error.invalid-arguments"));
                    return;
                }
                String pluginName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                sender.sendMessage(translations.getWithColor("prefix") + "Loading config...");
                configEditor.viewConfig(pluginName).thenAccept(result -> {
                    sender.sendMessage(translations.getWithColor("&f") + result);
                }).exceptionally(e -> {
                    sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
                    return null;
                });
            }
            case "edit" -> {
                if (args.length < 5) {
                    sender.sendMessage("&cUsage: /ai config edit <plugin> <key> <value>");
                    return;
                }
                String pluginName = args[2];
                String key = args[3];
                String value = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
                configEditor.editConfig(pluginName + " " + key + " " + value).thenAccept(result -> {
                    sender.sendMessage(translations.getWithColor("&a") + result);
                }).exceptionally(e -> {
                    sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
                    return null;
                });
            }
            case "analyze" -> {
                if (args.length < 3) {
                    sender.sendMessage(translations.getWithColor("error.invalid-arguments"));
                    return;
                }
                String pluginName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                sender.sendMessage(translations.getWithColor("prefix") + "Analyzing config...");
                configEditor.analyzeConfig(pluginName).thenAccept(result -> {
                    sender.sendMessage(translations.getWithColor("&f") + result);
                }).exceptionally(e -> {
                    sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
                    return null;
                });
            }
            case "backup" -> {
                if (args.length < 3) {
                    sender.sendMessage(translations.getWithColor("error.invalid-arguments"));
                    return;
                }
                String pluginName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                configEditor.backupConfig(pluginName).thenAccept(result -> {
                    sender.sendMessage(translations.getWithColor("&a") + result);
                }).exceptionally(e -> {
                    sender.sendMessage(translations.getWithColor("error.ai-failure").replace("{error}", e.getMessage()));
                    return null;
                });
            }
            default -> sender.sendMessage(translations.getWithColor("unknown-command"));
        }
    }

    private boolean checkRateLimit(CommandSender sender) {
        UUID uuid = sender instanceof Player p ? p.getUniqueId() : UUID.randomUUID();
        long now = System.currentTimeMillis();
        
        Long lastRequest = rateLimitMap.get(uuid);
        
        if (lastRequest != null) {
            long secondsSinceLastRequest = TimeUnit.MILLISECONDS.toSeconds(now - lastRequest);
            if (secondsSinceLastRequest < rateLimitSeconds) {
                sender.sendMessage(translations.getWithColor("error.rate-limit")
                    .replace("{seconds}", String.valueOf(rateLimitSeconds - secondsSinceLastRequest)));
                return false;
            }
        }
        
        rateLimitMap.put(uuid, now);
        return true;
    }
}
