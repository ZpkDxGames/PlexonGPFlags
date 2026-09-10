package com.plexon.gpflags.config;

import com.plexon.gpflags.flag.ClaimFlag;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/** Immutable runtime-ready configuration. Gameplay listeners never query YAML directly. */
public record RuntimeSettings(int schemaVersion, Features features, Claims claims, FlagRules flags,
        EnumMap<ClaimFlag, Boolean> defaults, Map<UUID, Map<ClaimFlag, Boolean>> worldDefaults,
        Material filler, boolean importLegacyFlags, String legacyFolder) {

    public static RuntimeSettings load(FileConfiguration config, Logger logger) {
        int schemaVersion = ConfigValidator.validate(config);
        Features features = new Features(config.getBoolean("features.onboarding"), config.getBoolean("features.auto-claim"),
                config.getBoolean("features.shovel"), config.getBoolean("features.claim-list"), config.getBoolean("features.flags"),
                config.getBoolean("features.trust"), config.getBoolean("features.resize"), config.getBoolean("features.teleport"),
                config.getBoolean("features.visualizer"), config.getBoolean("features.abandon"));
        Claims claims = new Claims(Math.max(1, config.getInt("claims.auto-claim-sizes.small")), Math.max(1, config.getInt("claims.auto-claim-sizes.medium")),
                Math.max(1, config.getInt("claims.auto-claim-sizes.large")), Math.max(0, config.getInt("claims.shovel-cooldown-seconds")),
                Math.max(5, config.getInt("claims.prompt-timeout-seconds")), Math.max(0, config.getInt("claims.teleport.warmup-seconds")),
                config.getBoolean("claims.teleport.cancel-on-movement"), config.getBoolean("claims.teleport.cancel-on-damage"),
                Math.max(1, config.getInt("claims.visualizer.duration-seconds")), Math.max(2, config.getInt("claims.visualizer.interval-ticks")),
                Math.max(20, config.getInt("claims.visualizer.max-particles-per-update")), Math.max(1, config.getInt("claims.visualizer.edge-spacing")),
                Math.max(1, config.getInt("claims.visualizer.wall-height")));
        FlagRules flagRules = new FlagRules(config.getBoolean("flags.subclaims-inherit-parent"),
                spawnReasons(config.getStringList("flags.natural-spawn-reasons")), spawnReasons(config.getStringList("flags.spawner-spawn-reasons")),
                materials(config.getStringList("flags.interaction-materials")), suffixes(config.getStringList("flags.interaction-material-suffixes")),
                materials(config.getStringList("flags.container-materials")));
        EnumMap<ClaimFlag, Boolean> defaults = new EnumMap<>(ClaimFlag.class);
        for (ClaimFlag flag : ClaimFlag.values()) defaults.put(flag, config.getBoolean("defaults." + flag.key()));
        Map<UUID, Map<ClaimFlag, Boolean>> worldDefaults = new HashMap<>();
        ConfigurationSection worlds = config.getConfigurationSection("world-defaults");
        if (worlds != null) for (String worldKey : worlds.getKeys(false)) {
            UUID worldId = UUID.fromString(worldKey);
            ConfigurationSection values = worlds.getConfigurationSection(worldKey);
            EnumMap<ClaimFlag, Boolean> compiled = new EnumMap<>(ClaimFlag.class);
            if (values != null) for (String flagId : values.getKeys(false)) compiled.put(ClaimFlag.parseStableId(flagId).orElseThrow(), values.getBoolean(flagId));
            worldDefaults.put(worldId, Map.copyOf(compiled));
        }
        Material filler = Material.matchMaterial(config.getString("gui.filler", "BLACK_STAINED_GLASS_PANE"));
        String legacy = config.getString("migration.legacy-folder", "PlexonClaimFlags");
        return new RuntimeSettings(schemaVersion, features, claims, flagRules, defaults, Map.copyOf(worldDefaults),
                filler == null ? Material.BLACK_STAINED_GLASS_PANE : filler, config.getBoolean("migration.import-legacy-flags"),
                legacy == null ? "PlexonClaimFlags" : legacy);
    }

    private static EnumSet<CreatureSpawnEvent.SpawnReason> spawnReasons(List<String> values) {
        EnumSet<CreatureSpawnEvent.SpawnReason> result = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        for (String value : values) result.add(CreatureSpawnEvent.SpawnReason.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        return result;
    }
    private static Set<Material> materials(List<String> values) { EnumSet<Material> result = EnumSet.noneOf(Material.class); for (String value : values) result.add(Material.matchMaterial(value)); return Set.copyOf(result); }
    private static Set<String> suffixes(List<String> values) { Set<String> result = new HashSet<>(); for (String value : values) if (!value.isBlank()) result.add(value.toUpperCase(Locale.ROOT)); return Set.copyOf(result); }

    public record Features(boolean onboarding, boolean autoClaim, boolean shovel, boolean claimList, boolean flags, boolean trust, boolean resize, boolean teleport, boolean visualizer, boolean abandon) {}
    public record Claims(int smallSize, int mediumSize, int largeSize, int shovelCooldownSeconds, int promptTimeoutSeconds, int teleportWarmupSeconds,
                         boolean teleportCancelMovement, boolean teleportCancelDamage, int visualizerDurationSeconds, int visualizerIntervalTicks,
                         int visualizerMaxParticles, int visualizerEdgeSpacing, int visualizerWallHeight) {}
    public record FlagRules(boolean subclaimsInheritParent, Set<CreatureSpawnEvent.SpawnReason> naturalSpawnReasons,
                            Set<CreatureSpawnEvent.SpawnReason> spawnerSpawnReasons, Set<Material> interactionMaterials,
                            Set<String> interactionSuffixes, Set<Material> containerMaterials) {
        public boolean interaction(Material material) { if (interactionMaterials.contains(material)) return true; String name = material.name(); for (String suffix : interactionSuffixes) if (name.endsWith(suffix)) return true; return false; }
    }
}
