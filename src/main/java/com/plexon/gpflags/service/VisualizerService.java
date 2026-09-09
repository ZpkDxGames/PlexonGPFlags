package com.plexon.gpflags.service;

import com.plexon.gpflags.PlexonGPFlags;
import com.plexon.gpflags.claim.ClaimService;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded selected-claim visualizer; intentionally never scans GriefPrevention#getClaims(). */
public final class VisualizerService implements Listener {
    private static final Particle.DustOptions CORNER = new Particle.DustOptions(Color.fromRGB(255, 180, 40), 1.2F);
    private static final Particle.DustOptions EDGE = new Particle.DustOptions(Color.fromRGB(65, 201, 2), 0.8F);
    private record Session(long claimId, long expiresAtNanos) {}

    private final PlexonGPFlags plugin;
    private final ClaimService claims;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private BukkitTask ticker;

    public VisualizerService(PlexonGPFlags plugin, ClaimService claims) {
        this.plugin = plugin;
        this.claims = claims;
    }

    public void show(Player player, Claim claim) {
        Claim current = claims.byId(claims.safeId(claim));
        if (current == null || !claims.canManage(player, current)) return;
        int seconds = plugin.settings().claims().visualizerDurationSeconds();
        sessions.put(player.getUniqueId(), new Session(current.getID(), System.nanoTime() + seconds * 1_000_000_000L));
        plugin.messages().send(player, "visualizer-started", Map.of("seconds", Integer.toString(seconds)));
        ensureTicker();
        render(player, current);
    }

    public void cancelAll() {
        sessions.clear();
        if (ticker != null) { ticker.cancel(); ticker = null; }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); stopIfIdle(); }

    private void ensureTicker() {
        if (ticker != null) return;
        long interval = plugin.settings().claims().visualizerIntervalTicks();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.nanoTime();
            for (UUID uuid : java.util.List.copyOf(sessions.keySet())) {
                Session session = sessions.get(uuid);
                if (session == null) continue;
                if (session.expiresAtNanos() <= now) { sessions.remove(uuid); continue; }
                Player player = Bukkit.getPlayer(uuid);
                Claim claim = claims.byId(session.claimId());
                if (player == null || claim == null || !claims.canManage(player, claim)) { sessions.remove(uuid); continue; }
                render(player, claim);
            }
            stopIfIdle();
        }, interval, interval);
    }

    private void render(Player player, Claim claim) {
        Location a = claim.getLesserBoundaryCorner();
        Location b = claim.getGreaterBoundaryCorner();
        World world = a.getWorld();
        if (!player.getWorld().equals(world)) return;
        int max = plugin.settings().claims().visualizerMaxParticles();
        int spacing = plugin.settings().claims().visualizerEdgeSpacing();
        int height = plugin.settings().claims().visualizerWallHeight();
        double y = player.getLocation().getY();
        int count = 0;
        int x1 = a.getBlockX(), x2 = b.getBlockX(), z1 = a.getBlockZ(), z2 = b.getBlockZ();
        count = corner(player, x1, z1, y, height, max, count);
        count = corner(player, x2, z1, y, height, max, count);
        count = corner(player, x1, z2, y, height, max, count);
        count = corner(player, x2, z2, y, height, max, count);
        for (int x = x1 + spacing; x < x2 && count < max; x += spacing) {
            count = edge(player, x, z1, y, height, max, count);
            count = edge(player, x, z2, y, height, max, count);
        }
        for (int z = z1 + spacing; z < z2 && count < max; z += spacing) {
            count = edge(player, x1, z, y, height, max, count);
            count = edge(player, x2, z, y, height, max, count);
        }
    }

    private int corner(Player player, int x, int z, double y, int height, int max, int count) {
        if (!player.getWorld().isChunkLoaded(x >> 4, z >> 4)) return count;
        for (int h = 0; h <= height && count < max; h++, count++) player.spawnParticle(Particle.DUST, x + 0.5D, y + h, z + 0.5D, 1, CORNER);
        return count;
    }

    private int edge(Player player, int x, int z, double y, int height, int max, int count) {
        if (!player.getWorld().isChunkLoaded(x >> 4, z >> 4)) return count;
        for (int h = 0; h < height && count < max; h++, count++) player.spawnParticle(Particle.DUST, x + 0.5D, y + h, z + 0.5D, 1, EDGE);
        return count;
    }

    private void stopIfIdle() {
        if (!sessions.isEmpty() || ticker == null) return;
        ticker.cancel();
        ticker = null;
    }
}
