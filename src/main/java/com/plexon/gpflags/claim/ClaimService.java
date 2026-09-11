package com.plexon.gpflags.claim;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ClaimService {
    public Claim at(Location location) {
        if (location == null || GriefPrevention.instance == null || GriefPrevention.instance.dataStore == null) return null;
        return GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
    }

    public Claim byId(long id) {
        if (id < 0 || GriefPrevention.instance == null || GriefPrevention.instance.dataStore == null) return null;
        return GriefPrevention.instance.dataStore.getClaim(id);
    }

    public Claim parentOf(Claim claim) { return claim != null && claim.parent != null ? claim.parent : claim; }

    public List<Claim> subclaimsOf(Claim claim) {
        Claim parent = parentOf(claim);
        return parent == null ? List.of() : List.copyOf(parent.children);
    }

    public List<Claim> ownedTopLevel(Player player) {
        PlayerData data = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
        List<Claim> result = new ArrayList<>();
        for (Claim claim : data.getClaims()) if (claim.parent == null && claim.inDataStore) result.add(claim);
        result.sort(Comparator.comparingLong(this::safeId));
        return List.copyOf(result);
    }

    public int remainingClaimBlocks(Player player) {
        return GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId()).getRemainingClaimBlocks();
    }

    public boolean canManage(Player player, Claim claim) {
        if (player == null || claim == null) return false;
        if (claim.isAdminClaim()) return has(player, "plexongpflags.adminclaims", "plexonclaimflags.adminclaims");
        UUID owner = claim.getOwnerID();
        return owner != null && owner.equals(player.getUniqueId()) || has(player, "plexongpflags.admin", "plexonclaimflags.admin");
    }

    public boolean bypassesPlayerRestriction(Player player, Claim claim) {
        if (player == null) return false;
        if (has(player, "plexongpflags.bypass", "plexonclaimflags.bypass")) return true;
        UUID owner = claim == null ? null : claim.getOwnerID();
        return owner != null && owner.equals(player.getUniqueId());
    }

    /** Player-facing label only; numeric GriefPrevention IDs remain hidden routing state. */
    public String areaLabel(Claim claim) {
        if (claim == null) return "Claim";
        if (claim.parent == null) return "Main Claim";
        int index = claim.parent.children.indexOf(claim);
        return index < 0 ? "Subdivision" : "Subdivision " + (index + 1);
    }

    public String boundsLabel(Claim claim) {
        if (claim == null) return "Unknown";
        Location a = claim.getLesserBoundaryCorner();
        Location b = claim.getGreaterBoundaryCorner();
        return a.getWorld().getName() + " " + a.getBlockX() + "," + a.getBlockZ() + " → " + b.getBlockX() + "," + b.getBlockZ();
    }

    public long safeId(Claim claim) {
        Long id = claim == null ? null : claim.getID();
        return id == null ? -1L : id;
    }

    private static boolean has(Player player, String modern, String legacy) {
        return player.hasPermission(modern) || player.hasPermission(legacy);
    }
}
