package com.plexon.gpflags.service;

import com.plexon.gpflags.PlexonGPFlags;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One central chat-prompt listener; prompt callbacks always run on the server thread. */
public final class PromptService implements Listener {
    @FunctionalInterface public interface Handler { void accept(Player player, String input); }
    private record Prompt(long expiresAtNanos, Handler handler) {}

    private final PlexonGPFlags plugin;
    private final Map<UUID, Prompt> pending = new ConcurrentHashMap<>();
    private BukkitTask sweepTask;

    public PromptService(PlexonGPFlags plugin) { this.plugin = plugin; }

    public void start(Player player, String messageKey, Handler handler) {
        int timeout = plugin.settings().claims().promptTimeoutSeconds();
        long expires = System.nanoTime() + timeout * 1_000_000_000L;
        pending.put(player.getUniqueId(), new Prompt(expires, handler));
        player.closeInventory();
        plugin.messages().send(player, messageKey, Map.of("seconds", Integer.toString(timeout)));
        ensureSweep();
    }

    public void cancelAll() {
        pending.clear();
        if (sweepTask != null) { sweepTask.cancel(); sweepTask = null; }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Prompt prompt = pending.remove(uuid);
        if (prompt == null) return;
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            if (input.equalsIgnoreCase("cancel")) {
                plugin.messages().send(player, "prompt-cancelled");
                return;
            }
            if (System.nanoTime() > prompt.expiresAtNanos()) {
                plugin.messages().send(player, "prompt-expired");
                return;
            }
            prompt.handler().accept(player, input);
        });
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { pending.remove(event.getPlayer().getUniqueId()); }

    private void ensureSweep() {
        if (sweepTask != null) return;
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.nanoTime();
            for (UUID uuid : List.copyOf(pending.keySet())) {
                Prompt prompt = pending.get(uuid);
                if (prompt == null || prompt.expiresAtNanos() > now) continue;
                if (pending.remove(uuid, prompt)) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) plugin.messages().send(player, "prompt-expired");
                }
            }
            if (pending.isEmpty() && sweepTask != null) {
                sweepTask.cancel();
                sweepTask = null;
            }
        }, 20L, 20L);
    }
}
