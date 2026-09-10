package com.plexon.gpflags.flag;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** A value of true means PlexonGPFlags prevents the described behavior. */
public enum ClaimFlag {
    NATURAL_MOBS("natural-mobs", "Mobs", "Natural mobs", "Prevents natural creature spawns.", Material.ZOMBIE_HEAD),
    SPAWNER_MOBS("spawner-mobs", "Mobs", "Spawner mobs", "Prevents creature spawns from spawners.", Material.SPAWNER),
    PVP("pvp", "Players", "PvP", "Prevents player-versus-player damage.", Material.DIAMOND_SWORD),
    BUILDING("building", "Players", "Building", "Prevents non-bypassed player block place/break.", Material.DIAMOND_PICKAXE),
    INTERACTIONS("interactions", "Players", "Interactions", "Prevents configured block interactions.", Material.OAK_DOOR),
    CONTAINERS("containers", "Players", "Containers", "Prevents configured container access.", Material.CHEST),
    EXPLOSIONS("explosions", "Environment", "Explosions", "Protects claim blocks from explosions.", Material.TNT),
    FIRE("fire", "Environment", "Fire", "Prevents ignition, fire spread and burning.", Material.FLINT_AND_STEEL),
    CROP_TRAMPLING("crop-trampling", "Environment", "Crop trampling", "Prevents farmland trampling.", Material.WHEAT),
    MOB_GRIEFING("mob-griefing", "Mobs", "Mob griefing", "Prevents non-player entity block changes and mob explosion damage.", Material.CREEPER_HEAD);

    private static final Map<String, ClaimFlag> BY_ID;
    static {
        Map<String, ClaimFlag> ids = new LinkedHashMap<>();
        for (ClaimFlag flag : values()) if (ids.put(flag.key, flag) != null) throw new IllegalStateException("Duplicate claim flag id: " + flag.key);
        BY_ID = Map.copyOf(ids);
    }

    private final String key;
    private final String category;
    private final String displayName;
    private final String description;
    private final Material icon;

    ClaimFlag(String key, String category, String displayName, String description, Material icon) { this.key = key; this.category = category; this.displayName = displayName; this.description = description; this.icon = icon; }
    public String key() { return key; }
    public String category() { return category; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public Material icon() { return icon; }

    public static Optional<ClaimFlag> parseStableId(String input) { if (input == null) return Optional.empty(); return Optional.ofNullable(BY_ID.get(input.trim().toLowerCase(Locale.ROOT))); }
    public static Optional<ClaimFlag> parse(String input) {
        Optional<ClaimFlag> stable = parseStableId(input == null ? null : input.replace('_', '-'));
        if (stable.isPresent()) return stable;
        return input == null ? Optional.empty() : Arrays.stream(values()).filter(flag -> flag.name().equalsIgnoreCase(input.trim())).findFirst();
    }
}
