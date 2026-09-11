package com.plexon.gpflags.gui;

import com.plexon.gpflags.PlexonGPFlags;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Binds PlexonGPFlags inventory instances to the actor and configuration generation that opened them. */
public final class MenuSessionGuard implements Listener {
    private record Session(UUID actor, long generation) {}
    private final PlexonGPFlags plugin;
    private final Map<Inventory, Session> sessions = new IdentityHashMap<>();
    private final AtomicLong staleActions = new AtomicLong();
    public MenuSessionGuard(PlexonGPFlags plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inventory = event.getInventory();
        if (isPlexonInventory(inventory)) sessions.put(inventory, new Session(player.getUniqueId(), plugin.configurationGeneration()));
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player player) rejectStale(player, event.getView().getTopInventory(), event); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) { if (event.getWhoClicked() instanceof Player player) rejectStale(player, event.getView().getTopInventory(), event); }
    @EventHandler public void onClose(InventoryCloseEvent event) { sessions.remove(event.getInventory()); }
    public long staleActionCount() { return staleActions.get(); }
    private void rejectStale(Player actor, Inventory inventory, org.bukkit.event.Cancellable event) {
        Session session = sessions.get(inventory);
        if (session == null || session.actor().equals(actor.getUniqueId()) && session.generation() == plugin.configurationGeneration()) return;
        event.setCancelled(true); staleActions.incrementAndGet(); actor.closeInventory();
    }
    private static boolean isPlexonInventory(Inventory inventory) { InventoryHolder holder = inventory.getHolder(); return holder != null && holder.getClass().getEnclosingClass() == MenuService.class; }
}
