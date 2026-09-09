package com.plexon.gpflags.config;

import com.plexon.gpflags.flag.ClaimFlag;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** Immutable runtime-ready configuration. Gameplay listeners never query YAML directly. */
public record RuntimeSettings(
        Features features,
        Claims claims,
        FlagRules flags,
        EnumMap<ClaimFlag, Boolean> defaults,
        Material filler,
        boolean importLegacyFlags,
        String legacyFolder
) {
    public static RuntimeSettings load(FileConfiguration config, Logger logger) {
        Features features = new Features(
                config.getBoolean("features.onboarding", true),
                config.getBoolean("features.auto-claim", true),
                config.getBoolean("features.shovel", true),
                config.getBoolean("features.claim-list", true),
                config.getBoolean("features.flags", true),
                config.getBoolean("features.trust", true),
                config.getBoolean("features.resize", true),
                config.getBoolean("features.teleport", true),
                config.getBoolean("features.visualizer", true),
                config.getBoolean("features.abandon", true)
        );
        Claims claims = new Claims(
                Math.max(1, config.getInt("claims.auto-claim-sizes.small", 10)),
                Math.max(1, config.getInt("claims.auto-claim-sizes.medium", 20)),
                Math.max(1, config.getInt("claims.auto-claim-sizes.large", 30)),
                Math.max(0, config.getInt("claims.shovel-cooldown-seconds", 3600)),
                Math.max(5, config.getInt("claims.prompt-timeout-seconds", 60)),
                Math.max(0, config.getInt("claims.teleport.warmup-seconds", 3)),
                config.getBoolean("claims.teleport.cancel-on-movement", true),
                config.getBoolean("claims.teleport.cancel-on-damage", true),
                Math.max(1, config.getInt("claims.visualizer.duration-seconds", 8)),
                Math.max(2, config.getInt("claims.visualizer.interval-ticks", 10)),
                Math.max(20, config.getInt("claims.visualizer.max-particles-per-update", 260)),
                Math.max(1, config.getInt("claims.visualizer.edge-spacing", 2)),
                Math.max(1, config.getInt("claims.visualizer.wall-height", 2))
        );
        FlagRules flagRules = new FlagRules(
                config.getBoolean("flags.subclaims-inherit-parent", true),
                spawnReasons(config.getStringList("flags.natural-spawn-reasons"), logger),
                spawnReasons(config.getStringList("flags.spawner-spawn-reasons"), logger),
                materials(config.getStringList("flags.interaction-materials"), logger),
                suffixes(config.getStringList("flags.interaction-material-suffixes")),
                materials(config.getStringList("flags.container-materials"), logger)
        );
        EnumMap<ClaimFlag, Boolean> defaults = new EnumMap<>(ClaimFlag.class);
        for (ClaimFlag flag : ClaimFlag.values()) {
            defaults.put(flag, config.getBoolean("defaults." + flag.key(), false));
        }
        Material filler = Material.matchMaterial(config.getString("gui.filler", "BLACK_STAINED_GLASS_PANE"));
        if (filler == null || filler.isAir()) filler = Material.BLACK_STAINED_GLASS_PANE;
        String legacy = config.getString("migration.legacy-folder", "PlexonClaimFlags");
        return new RuntimeSettings(features, claims, flagRules, defaults, filler,
                config.getBoolean("migration.import-legacy-flags", true), legacy == null ? "PlexonClaimFlags" : legacy);
    }

    private static EnumSet<CreatureSpawnEvent.SpawnReason> spawnReasons(List<String> values, Logger logger) {
        EnumSet<CreatureSpawnEvent.SpawnReason> result = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            try { result.add(CreatureSpawnEvent.SpawnReason.valueOf(value.trim().toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException error) { logger.warning("Ignoring unknown spawn reason: " + value); }
        }
        return result;
    }

    private static Set<Material> materials(List<String> values, Logger logger) {
        EnumSet<Material> result = EnumSet.noneOf(Material.class);
        for (String value : values) {
            Material material = Material.matchMaterial(value == null ? "" : value);
            if (material == null) logger.warning("Ignoring unknown material in PlexonGPFlags config: " + value);
            else result.add(material);
        }
        return Set.copyOf(result);
    }

    private static Set<String> suffixes(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) if (value != null && !value.isBlank()) result.add(value.toUpperCase(Locale.ROOT));
        return Set.copyOf(result);
    }

    public record Features(boolean onboarding, boolean autoClaim, boolean shovel, boolean claimList,
                           boolean flags, boolean trust, boolean resize, boolean teleport,
                           boolean visualizer, boolean abandon) {}

    public record Claims(int smallSize, int mediumSize, int largeSize, int shovelCooldownSeconds,
                         int promptTimeoutSeconds, int teleportWarmupSeconds, boolean teleportCancelMovement,
                         boolean teleportCancelDamage, int visualizerDurationSeconds, int visualizerIntervalTicks,
                         int visualizerMaxParticles, int visualizerEdgeSpacing, int visualizerWallHeight) {}

    public record FlagRules(boolean subclaimsInheritParent,
                            Set<CreatureSpawnEvent.SpawnReason> naturalSpawnReasons,
                            Set<CreatureSpawnEvent.SpawnReason> spawnerSpawnReasons,
                            Set<Material> interactionMaterials,
                            Set<String> interactionSuffixes,
                            Set<Material> containerMaterials) {
        public boolean interaction(Material material) {
            if (interactionMaterials.contains(material)) return true;
            String name = material.name();
            for (String suffix : interactionSuffixes) if (name.endsWith(suffix)) return true;
            return false;
        }
    }
}
