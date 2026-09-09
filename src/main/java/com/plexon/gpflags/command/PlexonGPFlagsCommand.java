package com.plexon.gpflags.command;

import com.plexon.gpflags.PlexonGPFlags;
import com.plexon.gpflags.api.FlagChangeResult;
import com.plexon.gpflags.claim.ClaimService;
import com.plexon.gpflags.flag.ClaimFlag;
import com.plexon.gpflags.flag.FlagOverride;
import com.plexon.gpflags.flag.FlagService;
import com.plexon.gpflags.flag.FlagStore;
import com.plexon.gpflags.gui.MenuService;
import me.ryanhamshire.GriefPrevention.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlexonGPFlagsCommand implements CommandExecutor, TabCompleter {
    private final PlexonGPFlags plugin;
    private final ClaimService claims;
    private final FlagStore store;
    private final FlagService flags;
    private final MenuService menus;

    public PlexonGPFlagsCommand(PlexonGPFlags plugin, ClaimService claims, FlagStore store, FlagService flags, MenuService menus) {
        this.plugin = plugin; this.claims = claims; this.store = store; this.flags = flags; this.menus = menus;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("gpegui-reload")) return reload(sender);
        if (args.length == 0) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Use /gpflags diagnostics or /gpflags reload from console."); return true; }
            menus.openHub(player); return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) return reload(sender);
        if (sub.equals("diagnostics")) return diagnostics(sender);
        if (!(sender instanceof Player player)) { sender.sendMessage("This subcommand is player-only."); return true; }
        switch (sub) {
            case "claims" -> menus.openClaims(player, 1);
            case "claim" -> {
                Claim claim = claims.at(player.getLocation());
                if (claim == null) plugin.messages().send(player, "no-claim"); else menus.openDashboard(player, claim);
            }
            case "flags" -> {
                Claim claim = claims.at(player.getLocation());
                if (claim == null) plugin.messages().send(player, "no-claim"); else menus.openFlags(player, claim);
            }
            case "set" -> set(player, args);
            default -> menus.openHub(player);
        }
        return true;
    }

    private void set(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage(Component.text("Usage: /gpflags set <flag> <on|off|inherit>")); return; }
        Claim claim = claims.at(player.getLocation());
        if (claim == null) { plugin.messages().send(player, "no-claim"); return; }
        ClaimFlag flag = ClaimFlag.parse(args[1]).orElse(null);
        FlagOverride override = FlagOverride.parse(args[2]).orElse(null);
        if (flag == null || override == null) { plugin.messages().send(player, "prompt-invalid"); return; }
        FlagChangeResult result = flags.set(player, claim, flag, override, "COMMAND");
        if (result.status() == FlagChangeResult.Status.PERSISTENCE_FAILED) plugin.messages().send(player, "persistence-error");
        else if (result.success()) plugin.messages().send(player, "flag-changed", Map.of(
                "flag", plugin.messages().flagName(flag),
                "state", store.effective(claim, flag) ? "<red>BLOCKED</red>" : "<green>ALLOWED</green>",
                "area", claims.areaLabel(claim)));
    }

    private boolean reload(CommandSender sender) {
        if (!admin(sender)) { if (sender instanceof Player player) plugin.messages().send(player, "no-permission"); return true; }
        boolean success = plugin.reloadPlugin();
        if (sender instanceof Player player) plugin.messages().send(player, success ? "reloaded" : "persistence-error");
        else sender.sendMessage(success ? "PlexonGPFlags reloaded." : "PlexonGPFlags reload completed with a flag persistence error.");
        return true;
    }

    private boolean diagnostics(CommandSender sender) {
        if (!admin(sender)) return true;
        sender.sendMessage(Component.text("PlexonGPFlags " + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(Component.text("GriefPrevention: " + (plugin.getServer().getPluginManager().isPluginEnabled("GriefPrevention") ? "ready" : "missing")));
        sender.sendMessage(Component.text("PlexonCore: " + plugin.coreMode()));
        sender.sendMessage(Component.text("Flag records: " + store.recordCount()));
        sender.sendMessage(Component.text("Flag persistence: " + (store.healthy() ? "healthy" : "degraded: " + store.lastError())));
        sender.sendMessage(Component.text("Pending teleports: " + plugin.teleports().pendingCount()));
        sender.sendMessage(Component.text("Public API: " + (plugin.publicApiRegistered() ? "registered" : "not registered")));
        return true;
    }

    private boolean admin(CommandSender sender) {
        return sender.hasPermission("plexongpflags.admin") || sender.hasPermission("plexonclaimflags.admin");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) return filter(List.of("claim", "claims", "flags", "set", "reload", "diagnostics"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) return filter(Arrays.stream(ClaimFlag.values()).map(ClaimFlag::key).toList(), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) return filter(List.of("on", "off", "inherit"), args[2]);
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
