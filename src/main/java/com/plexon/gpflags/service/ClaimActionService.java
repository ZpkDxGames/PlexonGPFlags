package com.plexon.gpflags.service;

import com.plexon.gpflags.PlexonGPFlags;
import com.plexon.gpflags.claim.ClaimService;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/** Direct, main-thread claim mutations delegated to GriefPrevention's own datastore/API. */
public final class ClaimActionService {
    public enum Direction { NORTH, SOUTH, EAST, WEST }
    public record CreatePreview(int side, long requiredBlocks, int availableBlocks, boolean possible) {}
    public record ResizePreview(int offset, int maxExpand, int maxShrink, int width, int length, int blockDelta, boolean valid) {}

    private final PlexonGPFlags plugin;
    private final ClaimService claims;
    private final Map<UUID, Long> shovelCooldown = new java.util.HashMap<>();

    public ClaimActionService(PlexonGPFlags plugin, ClaimService claims) {
        this.plugin = plugin;
        this.claims = claims;
    }

    /** Authoritative creation preview used by the GUI; creation uses the same calculation. */
    public CreatePreview previewCreate(Player player, int requestedSide) {
        int min = GriefPrevention.instance.config_claims_minWidth;
        int side = Math.max(min, requestedSide);
        long needed = (long) side * side;
        int available = Math.max(0, claims.remainingClaimBlocks(player));
        return new CreatePreview(side, needed, available, needed <= Integer.MAX_VALUE && needed <= available);
    }

    public int maximumAffordableSquareSide(Player player) {
        return (int) Math.floor(Math.sqrt(Math.max(0, claims.remainingClaimBlocks(player))));
    }

    public Claim createSquare(Player player, int requestedSide) {
        CreatePreview preview = previewCreate(player, requestedSide);
        if (!preview.possible()) {
            plugin.messages().send(player, "claim-create-failed");
            return null;
        }
        int side = preview.side();
        Location center = player.getLocation();
        int x1 = center.getBlockX() - side / 2;
        int z1 = center.getBlockZ() - side / 2;
        CreateClaimResult result = GriefPrevention.instance.dataStore.createClaim(
                center.getWorld(), x1, x1 + side - 1,
                center.getWorld().getMinHeight(), center.getWorld().getMaxHeight(),
                z1, z1 + side - 1, player.getUniqueId(), null, null, player);
        if (!result.succeeded || result.claim == null) {
            plugin.messages().send(player, "claim-create-failed");
            return null;
        }
        plugin.messages().send(player, "claim-created", Map.of(
                "size", Integer.toString(side), "blocks", Long.toString(preview.requiredBlocks())));
        return result.claim;
    }

    public void giveShovel(Player player) {
        int cooldown = plugin.settings().claims().shovelCooldownSeconds();
        long now = System.nanoTime();
        long deadline = shovelCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (deadline > now) {
            long seconds = Math.max(1L, (deadline - now + 999_999_999L) / 1_000_000_000L);
            plugin.messages().send(player, "shovel-cooldown", Map.of("seconds", Long.toString(seconds)));
            return;
        }
        ItemStack shovel = new ItemStack(Material.GOLDEN_SHOVEL);
        player.getInventory().addItem(shovel).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        if (cooldown > 0) shovelCooldown.put(player.getUniqueId(), now + cooldown * 1_000_000_000L);
        plugin.messages().send(player, "shovel-given");
    }

    public ResizePreview preview(Player player, Claim claim, Direction direction, int requestedOffset) {
        Location lesser = claim.getLesserBoundaryCorner();
        Location greater = claim.getGreaterBoundaryCorner();
        int width = greater.getBlockX() - lesser.getBlockX() + 1;
        int length = greater.getBlockZ() - lesser.getBlockZ() + 1;
        int maxExpand = maxExpand(player, direction, width, length);
        int maxShrink = maxShrink(claim, direction, lesser, greater, width, length);
        int offset = Math.max(-maxShrink, Math.min(maxExpand, requestedOffset));
        int newWidth = width + (direction == Direction.EAST || direction == Direction.WEST ? offset : 0);
        int newLength = length + (direction == Direction.NORTH || direction == Direction.SOUTH ? offset : 0);
        int blockDelta = newWidth * newLength - width * length;
        int minWidth = GriefPrevention.instance.config_claims_minWidth;
        int minArea = GriefPrevention.instance.config_claims_minArea;
        boolean valid = offset != 0 && newWidth >= minWidth && newLength >= minWidth && (long) newWidth * newLength >= minArea;
        return new ResizePreview(offset, maxExpand, maxShrink, newWidth, newLength, blockDelta, valid);
    }

    public Claim resize(Player player, Claim original, Direction direction, int requestedOffset) {
        Claim claim = claims.byId(claims.safeId(original));
        if (claim == null || !claims.canManage(player, claim)) {
            plugin.messages().send(player, "claim-missing");
            return null;
        }
        ResizePreview preview = preview(player, claim, direction, requestedOffset);
        if (!preview.valid()) {
            plugin.messages().send(player, "claim-resize-failed");
            return null;
        }
        Location lesser = claim.getLesserBoundaryCorner();
        Location greater = claim.getGreaterBoundaryCorner();
        int x1 = lesser.getBlockX();
        int x2 = greater.getBlockX();
        int z1 = lesser.getBlockZ();
        int z2 = greater.getBlockZ();
        switch (direction) {
            case NORTH -> z1 -= preview.offset();
            case SOUTH -> z2 += preview.offset();
            case WEST -> x1 -= preview.offset();
            case EAST -> x2 += preview.offset();
        }
        CreateClaimResult result = GriefPrevention.instance.dataStore.resizeClaim(
                claim, x1, x2, lesser.getWorld().getMinHeight(), lesser.getWorld().getMaxHeight(), z1, z2, player);
        if (!result.succeeded) {
            plugin.messages().send(player, "claim-resize-failed");
            return null;
        }
        Claim resized = result.claim == null ? claim : result.claim;
        plugin.messages().send(player, "claim-resized", Map.of(
                "width", Integer.toString(preview.width()), "length", Integer.toString(preview.length())));
        return resized;
    }

    public void grantTrust(Player actor, Claim claim, UUID target, ClaimPermission permission, String targetName, String level) {
        Claim current = claims.byId(claims.safeId(claim));
        if (current == null || !claims.canManage(actor, current)) return;
        current.setPermission(target.toString(), permission);
        GriefPrevention.instance.dataStore.saveClaim(current);
        plugin.messages().send(actor, "trust-added", Map.of("player", targetName, "level", level));
    }

    public void removeTrust(Player actor, Claim claim, String entry) {
        removeTrust(actor, claim, entry, entry);
    }

    public void removeTrust(Player actor, Claim claim, String entry, String displayName) {
        Claim current = claims.byId(claims.safeId(claim));
        if (current == null || !claims.canManage(actor, current)) return;
        current.dropPermission(entry);
        GriefPrevention.instance.dataStore.saveClaim(current);
        plugin.messages().send(actor, "trust-removed", Map.of("player", displayName));
    }

    public boolean delete(Player actor, Claim claim) {
        Claim current = claims.byId(claims.safeId(claim));
        if (current == null || !claims.canManage(actor, current)) return false;
        GriefPrevention.instance.dataStore.deleteClaim(current);
        plugin.messages().send(actor, "claim-deleted");
        return true;
    }

    public void clearPlayer(UUID playerId) { shovelCooldown.remove(playerId); }

    private int maxExpand(Player player, Direction direction, int width, int length) {
        PlayerData data = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
        int remaining = Math.max(0, data.getRemainingClaimBlocks());
        int opposite = direction == Direction.NORTH || direction == Direction.SOUTH ? width : length;
        return opposite <= 0 ? 0 : remaining / opposite;
    }

    private int maxShrink(Claim claim, Direction direction, Location lesser, Location greater, int width, int length) {
        int minWidth = GriefPrevention.instance.config_claims_minWidth;
        int minArea = GriefPrevention.instance.config_claims_minArea;
        boolean ns = direction == Direction.NORTH || direction == Direction.SOUTH;
        int axis = ns ? length : width;
        int other = ns ? width : length;
        int fromWidth = axis - minWidth;
        int minimumAxisForArea = other <= 0 ? axis : (int) Math.ceil((double) minArea / other);
        int maximum = Math.min(fromWidth, axis - minimumAxisForArea);
        for (Claim child : claim.children) {
            Location a = child.getLesserBoundaryCorner();
            Location b = child.getGreaterBoundaryCorner();
            int limit = switch (direction) {
                case NORTH -> a.getBlockZ() - lesser.getBlockZ();
                case SOUTH -> greater.getBlockZ() - b.getBlockZ();
                case WEST -> a.getBlockX() - lesser.getBlockX();
                case EAST -> greater.getBlockX() - b.getBlockX();
            };
            maximum = Math.min(maximum, limit);
        }
        return Math.max(0, maximum);
    }
}
