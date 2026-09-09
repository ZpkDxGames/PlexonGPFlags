package com.plexon.gpflags.service;

import com.plexon.gpflags.PlexonGPFlags;
import com.plexon.gpflags.claim.ClaimService;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shared warmup coordinator. No listener/task is registered per teleport. */
public final class TeleportService implements Listener {
    private record Pending(long claimId, UUID worldId, int x, int y, int z, long deadlineNanos) {}

    private final PlexonGPFlags plugin;
    private final ClaimService claims;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private BukkitTask ticker;

    public TeleportService(PlexonGPFlags plugin, ClaimService claims) {
        this.plugin = plugin;
        this.claims = claims;
    }

    public void request(Player player, Claim claim) {
        Claim current = claims.byId(claims.safeId(claim));
        if (current == null || !claims.canManage(player, current)) {
            plugin.messages().send(player, "claim-missing");
            return;
        }
        int warmup = plugin.settings().claims().teleportWarmupSeconds();
        if (warmup <= 0) { execute(player, current.getID()); return; }
        Location from = player.getLocation();
        pending.put(player.getUniqueId(), new Pending(current.getID(), from.getWorld().getUID(),
                from.getBlockX(), from.getBlockY(), from.getBlockZ(), System.nanoTime() + warmup * 1_000_000_000L));
        plugin.messages().send(player, "teleport-start", Map.of("seconds", Integer.toString(warmup)));
        ensureTicker();
    }

    public boolean pending(UUID playerId) { return pending.containsKey(playerId); }

    public void cancel(UUID playerId, boolean notify) {
        if (pending.remove(playerId) == null) return;
        Player player = Bukkit.getPlayer(playerId);
        if (notify && player != null) plugin.messages().send(player, "teleport-cancelled");
        stopIfIdle();
    }

    public void cancelAll() {
        pending.clear();
        if (ticker != null) { ticker.cancel(); ticker = null; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Pending active = pending.get(event.getPlayer().getUniqueId());
        if (active == null || !plugin.settings().claims().teleportCancelMovement()) return;
        Location to = event.getTo();
        if (to == null || !to.getWorld().getUID().equals(active.worldId())
                || to.getBlockX() != active.x() || to.getBlockY() != active.y() || to.getBlockZ() != active.z()) {
            cancel(event.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !pending.containsKey(player.getUniqueId())) return;
        if (plugin.settings().claims().teleportCancelDamage()) cancel(player.getUniqueId(), true);
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) { cancel(event.getEntity().getUniqueId(), false); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { cancel(event.getPlayer().getUniqueId(), false); }

    private void ensureTicker() {
        if (ticker != null) return;
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.nanoTime();
            for (UUID uuid : java.util.List.copyOf(pending.keySet())) {
                Pending active = pending.get(uuid);
                if (active == null) continue;
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) { pending.remove(uuid); continue; }
                if (now >= active.deadlineNanos()) {
                    pending.remove(uuid);
                    execute(player, active.claimId());
                } else {
                    long seconds = Math.max(1L, (active.deadlineNanos() - now + 999_999_999L) / 1_000_000_000L);
                    player.sendActionBar(plugin.messages().raw("<gray>Claim teleport <dark_gray>•</dark_gray> <white>" + seconds + "s</white>"));
                }
            }
            stopIfIdle();
        }, 5L, 5L);
    }

    private void execute(Player player, long claimId) {
        Claim claim = claims.byId(claimId);
        if (claim == null || !claims.canManage(player, claim)) {
            plugin.messages().send(player, "claim-missing");
            return;
        }
        Location a = claim.getLesserBoundaryCorner();
        Location b = claim.getGreaterBoundaryCorner();
        World world = a.getWorld();
        int x = (a.getBlockX() + b.getBlockX()) / 2;
        int z = (a.getBlockZ() + b.getBlockZ()) / 2;
        world.getChunkAtAsync(x >> 4, z >> 4).whenComplete((chunk, loadError) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (loadError != null || !player.isOnline()) return;
            Claim current = claims.byId(claimId);
            if (current == null || !claims.canManage(player, current)) return;
            int y = Math.min(world.getMaxHeight() - 2, world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
            Location destination = new Location(world, x + 0.5D, y, z + 0.5D, player.getYaw(), player.getPitch());
            player.teleportAsync(destination).whenComplete((success, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error == null && Boolean.TRUE.equals(success)) plugin.messages().send(player, "teleport-success", Map.of("claim", Long.toString(claimId)));
                else plugin.messages().send(player, "teleport-cancelled");
            }));
        }));
    }

    private void stopIfIdle() {
        if (!pending.isEmpty() || ticker == null) return;
        ticker.cancel();
        ticker = null;
    }
}
