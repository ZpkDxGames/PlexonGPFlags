package com.plexon.gpflags.config;

import com.plexon.gpflags.flag.ClaimFlag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ConfigValidator {
    public static final int CURRENT_SCHEMA = 2;

    private ConfigValidator() {}

    public static int validate(FileConfiguration config) {
        int schema = SchemaVersion.requireSupported(config.get("schema-version"), 1, CURRENT_SCHEMA, "config.yml");
        for (String path : List.of("features.onboarding", "features.auto-claim", "features.shovel", "features.claim-list", "features.flags", "features.trust", "features.resize", "features.teleport", "features.visualizer", "features.abandon", "claims.teleport.cancel-on-movement", "claims.teleport.cancel-on-damage", "flags.subclaims-inherit-parent", "migration.import-legacy-flags")) requireBoolean(config, path);
        for (String path : List.of("claims.auto-claim-sizes.small", "claims.auto-claim-sizes.medium", "claims.auto-claim-sizes.large", "claims.shovel-cooldown-seconds", "claims.prompt-timeout-seconds", "claims.teleport.warmup-seconds", "claims.visualizer.duration-seconds", "claims.visualizer.interval-ticks", "claims.visualizer.max-particles-per-update", "claims.visualizer.edge-spacing", "claims.visualizer.wall-height")) requireInteger(config, path);
        requireStringList(config, "flags.natural-spawn-reasons");
        requireStringList(config, "flags.spawner-spawn-reasons");
        requireStringList(config, "flags.interaction-materials");
        requireStringList(config, "flags.interaction-material-suffixes");
        requireStringList(config, "flags.container-materials");
        for (String raw : config.getStringList("flags.natural-spawn-reasons")) requireSpawnReason(raw);
        for (String raw : config.getStringList("flags.spawner-spawn-reasons")) requireSpawnReason(raw);
        for (String raw : config.getStringList("flags.interaction-materials")) requireMaterial(raw, false);
        for (String raw : config.getStringList("flags.container-materials")) requireMaterial(raw, false);
        Object fillerRaw = config.get("gui.filler");
        if (!(fillerRaw instanceof String filler) || filler.isBlank()) throw new IllegalArgumentException("gui.filler must be a material name");
        requireMaterial(filler, true);
        ConfigurationSection titles = config.getConfigurationSection("gui.titles");
        if (titles != null) for (String key : titles.getKeys(false)) if (!(titles.get(key) instanceof String)) throw new IllegalArgumentException("gui.titles." + key + " must be a string");
        validateDefaults(config.getConfigurationSection("defaults"), "defaults");
        validateWorldDefaults(config.getConfigurationSection("world-defaults"));
        Object legacyFolderRaw = config.get("migration.legacy-folder");
        if (!(legacyFolderRaw instanceof String legacyFolder) || !legacyFolder.matches("[A-Za-z0-9._-]+") || legacyFolder.equals(".") || legacyFolder.equals("..")) throw new IllegalArgumentException("migration.legacy-folder must be a safe single folder name");
        return schema;
    }

    private static void validateDefaults(ConfigurationSection section, String path) {
        if (section == null) throw new IllegalArgumentException(path + " section is required");
        Set<String> seen = new HashSet<>();
        for (String key : section.getKeys(false)) {
            ClaimFlag flag = ClaimFlag.parseStableId(key).orElseThrow(() -> new IllegalArgumentException("Unknown flag id in " + path + ": " + key));
            if (!seen.add(flag.key())) throw new IllegalArgumentException("Duplicate flag id in " + path + ": " + key);
            if (!(section.get(key) instanceof Boolean)) throw new IllegalArgumentException(path + "." + key + " must be boolean");
        }
        for (ClaimFlag flag : ClaimFlag.values()) if (!seen.contains(flag.key())) throw new IllegalArgumentException(path + " is missing flag " + flag.key());
    }

    private static void validateWorldDefaults(ConfigurationSection worlds) {
        if (worlds == null) return;
        for (String worldKey : worlds.getKeys(false)) {
            final UUID worldId;
            try { worldId = UUID.fromString(worldKey); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("world-defaults key must be a world UUID: " + worldKey); }
            if (Bukkit.getWorld(worldId) == null) throw new IllegalArgumentException("Configured world-defaults UUID is not loaded/known: " + worldKey);
            ConfigurationSection flags = worlds.getConfigurationSection(worldKey);
            if (flags == null) throw new IllegalArgumentException("world-defaults." + worldKey + " must be a section");
            for (String flagId : flags.getKeys(false)) {
                ClaimFlag.parseStableId(flagId).orElseThrow(() -> new IllegalArgumentException("Unknown flag id in world-defaults." + worldKey + ": " + flagId));
                if (!(flags.get(flagId) instanceof Boolean)) throw new IllegalArgumentException("world-defaults." + worldKey + "." + flagId + " must be boolean");
            }
        }
    }

    private static void requireBoolean(FileConfiguration config, String path) { if (!(config.get(path) instanceof Boolean)) throw new IllegalArgumentException(path + " must be boolean"); }
    private static void requireInteger(FileConfiguration config, String path) { Object value = config.get(path); if (!(value instanceof Number number) || Math.rint(number.doubleValue()) != number.doubleValue()) throw new IllegalArgumentException(path + " must be an integer"); }
    private static void requireStringList(FileConfiguration config, String path) { Object value = config.get(path); if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String))) throw new IllegalArgumentException(path + " must be a string list"); }
    private static void requireSpawnReason(String value) { try { CreatureSpawnEvent.SpawnReason.valueOf(value.trim().toUpperCase(Locale.ROOT)); } catch (RuntimeException error) { throw new IllegalArgumentException("Unknown creature spawn reason: " + value); } }
    private static Material requireMaterial(String value, boolean rejectAir) { Material material = Material.matchMaterial(value); if (material == null || rejectAir && material.isAir()) throw new IllegalArgumentException("Unknown/invalid material: " + value); return material; }
}
