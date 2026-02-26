package com.aiserver.assistant.permissions;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PermissionManager {
    public static final String ADMIN_PERMISSION = "ai.admin";
    public static final String FIX_PERMISSION = "ai.fix";
    public static final String SCAN_PERMISSION = "ai.scan";
    public static final String BUILD_PERMISSION = "ai.build";
    public static final String ITEM_PERMISSION = "ai.item";
    public static final String TRADE_PERMISSION = "ai.trade";
    public static final String SCAN_PLUGINS_PERMISSION = "ai.scanplugins";
    public static final String RELOAD_PERMISSION = "ai.reload";

    public static boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission(ADMIN_PERMISSION) || sender.isOp();
    }

    public static boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        return sender.hasPermission(permission) || sender.isOp();
    }

    public static boolean hasFixPermission(CommandSender sender) {
        return hasPermission(sender, FIX_PERMISSION);
    }

    public static boolean hasScanPermission(CommandSender sender) {
        return hasPermission(sender, SCAN_PERMISSION);
    }

    public static boolean hasBuildPermission(CommandSender sender) {
        return hasPermission(sender, BUILD_PERMISSION);
    }

    public static boolean hasItemPermission(CommandSender sender) {
        return hasPermission(sender, ITEM_PERMISSION);
    }

    public static boolean hasTradePermission(CommandSender sender) {
        return hasPermission(sender, TRADE_PERMISSION);
    }

    public static boolean hasScanPluginsPermission(CommandSender sender) {
        return hasPermission(sender, SCAN_PLUGINS_PERMISSION);
    }

    public static boolean hasReloadPermission(CommandSender sender) {
        return hasPermission(sender, RELOAD_PERMISSION);
    }

    public static boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }

    public static Player getPlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        return null;
    }
}
