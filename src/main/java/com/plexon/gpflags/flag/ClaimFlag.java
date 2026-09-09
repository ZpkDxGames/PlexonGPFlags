package com.plexon.gpflags.flag;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** A value of true means PlexonGPFlags prevents the described behavior. */
public enum ClaimFlag {
    NATURAL_MOBS("natural-mobs", Material.ZOMBIE_HEAD),
    SPAWNER_MOBS("spawner-mobs", Material.SPAWNER),
    PVP("pvp", Material.DIAMOND_SWORD),
    BUILDING("building", Material.DIAMOND_PICKAXE),
    INTERACTIONS("interactions", Material.OAK_DOOR),
    CONTAINERS("containers", Material.CHEST),
    EXPLOSIONS("explosions", Material.TNT),
    FIRE("fire", Material.FLINT_AND_STEEL),
    CROP_TRAMPLING("crop-trampling", Material.WHEAT),
    MOB_GRIEFING("mob-griefing", Material.CREEPER_HEAD);

    private final String key;
    private final Material icon;

    ClaimFlag(String key, Material icon) {
        this.key = key;
        this.icon = icon;
    }

    public String key() { return key; }
    public Material icon() { return icon; }

    public static Optional<ClaimFlag> parse(String input) {
        if (input == null) return Optional.empty();
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return Arrays.stream(values())
                .filter(flag -> flag.key.equals(normalized) || flag.name().equalsIgnoreCase(input))
                .findFirst();
    }
}
