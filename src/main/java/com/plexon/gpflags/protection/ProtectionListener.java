package com.plexon.gpflags.protection;

import com.plexon.gpflags.PlexonGPFlags;
import com.plexon.gpflags.claim.ClaimService;
import com.plexon.gpflags.config.RuntimeSettings;
import com.plexon.gpflags.flag.ClaimFlag;
import com.plexon.gpflags.flag.FlagStore;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

/** Gameplay protection hot paths. No disk/config parsing/remote integration access occurs here. */
public final class ProtectionListener implements Listener {
    private final PlexonGPFlags plugin; private final ClaimService claims; private final FlagStore store; private final Map<UUID, Long> warningCooldown = new HashMap<>(); private final LongAdder decisions = new LongAdder(); private final LongAdder denials = new LongAdder();
    public ProtectionListener(PlexonGPFlags plugin, ClaimService claims, FlagStore store) { this.plugin = plugin; this.claims = claims; this.store = store; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onCreatureSpawn(CreatureSpawnEvent event) {
        RuntimeSettings.FlagRules rules = plugin.settings().flags(); CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason(); boolean natural = rules.naturalSpawnReasons().contains(reason); boolean spawner = rules.spawnerSpawnReasons().contains(reason); if (!natural && !spawner) return;
        Claim claim = claims.at(event.getLocation()); if (claim == null) return; if (natural && blocked(claim, ClaimFlag.NATURAL_MOBS) || spawner && blocked(claim, ClaimFlag.SPAWNER_MOBS)) { event.setCancelled(true); denials.increment(); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return; Player attacker = attackingPlayer(event.getDamager()); if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        Claim victimClaim = claims.at(victim.getLocation()); Claim attackerClaim = claims.at(attacker.getLocation());
        boolean victimBlocked = victimClaim != null && blocked(victimClaim, ClaimFlag.PVP); boolean attackerBlocked = attackerClaim != null && blocked(attackerClaim, ClaimFlag.PVP);
        boolean victimBypass = victimBlocked && claims.bypassesPlayerRestriction(attacker, victimClaim); boolean attackerBypass = attackerBlocked && claims.bypassesPlayerRestriction(attacker, attackerClaim);
        if (!shouldDenyPvp(victimBlocked, victimBypass, attackerBlocked, attackerBypass)) return; event.setCancelled(true); denials.increment(); warn(attacker);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onBreak(BlockBreakEvent event) { Claim claim = claims.at(event.getBlock().getLocation()); if (claim == null || !blocked(claim, ClaimFlag.BUILDING) || claims.bypassesPlayerRestriction(event.getPlayer(), claim)) return; event.setCancelled(true); denials.increment(); warn(event.getPlayer()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onPlace(BlockPlaceEvent event) { Claim claim = claims.at(event.getBlockPlaced().getLocation()); if (claim == null || !blocked(claim, ClaimFlag.BUILDING) || claims.bypassesPlayerRestriction(event.getPlayer(), claim)) return; event.setCancelled(true); denials.increment(); warn(event.getPlayer()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock(); if (block == null) return; Material material = block.getType(); boolean farmland = material == Material.FARMLAND && event.getAction() == Action.PHYSICAL; RuntimeSettings.FlagRules rules = plugin.settings().flags(); boolean container = rules.containerMaterials().contains(material); boolean interaction = rules.interaction(material); if (!farmland && !container && !interaction) return;
        Claim claim = claims.at(block.getLocation()); if (claim == null) return; boolean bypass = claims.bypassesPlayerRestriction(event.getPlayer(), claim);
        if (farmland && !bypass && blocked(claim, ClaimFlag.CROP_TRAMPLING)) { event.setCancelled(true); denials.increment(); warn(event.getPlayer()); return; }
        if (bypass) return; if (container && blocked(claim, ClaimFlag.CONTAINERS)) { event.setCancelled(true); denials.increment(); warn(event.getPlayer()); return; } if (interaction && blocked(claim, ClaimFlag.INTERACTIONS)) { event.setCancelled(true); denials.increment(); warn(event.getPlayer()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onInventoryOpen(InventoryOpenEvent event) { if (!(event.getPlayer() instanceof Player player)) return; Location location = event.getInventory().getLocation(); if (location == null || location.getWorld() == null) return; if (!plugin.settings().flags().containerMaterials().contains(location.getBlock().getType())) return; Claim claim = claims.at(location); if (claim == null || !blocked(claim, ClaimFlag.CONTAINERS) || claims.bypassesPlayerRestriction(player, claim)) return; event.setCancelled(true); denials.increment(); warn(player); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onEntityExplode(EntityExplodeEvent event) { boolean mob = mobCausedExplosion(event.getEntity()); int before = event.blockList().size(); event.blockList().removeIf(block -> enabled(block.getLocation(), ClaimFlag.EXPLOSIONS) || mob && enabled(block.getLocation(), ClaimFlag.MOB_GRIEFING)); addDenied(before - event.blockList().size()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onBlockExplode(BlockExplodeEvent event) { int before = event.blockList().size(); event.blockList().removeIf(block -> enabled(block.getLocation(), ClaimFlag.EXPLOSIONS)); addDenied(before - event.blockList().size()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onIgnite(BlockIgniteEvent event) { if (enabled(event.getBlock().getLocation(), ClaimFlag.FIRE)) { event.setCancelled(true); denials.increment(); } }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onSpread(BlockSpreadEvent event) { if (enabled(event.getBlock().getLocation(), ClaimFlag.FIRE)) { event.setCancelled(true); denials.increment(); } }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onBurn(BlockBurnEvent event) { if (enabled(event.getBlock().getLocation(), ClaimFlag.FIRE)) { event.setCancelled(true); denials.increment(); } }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onEntityChangeBlock(EntityChangeBlockEvent event) { Claim claim = claims.at(event.getBlock().getLocation()); if (claim == null) return; if (event.getBlock().getType() == Material.FARMLAND && event.getTo() == Material.DIRT && blocked(claim, ClaimFlag.CROP_TRAMPLING)) { event.setCancelled(true); denials.increment(); return; } if (!(event.getEntity() instanceof Player) && blocked(claim, ClaimFlag.MOB_GRIEFING)) { event.setCancelled(true); denials.increment(); } }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onEntityInteract(EntityInteractEvent event) { Claim claim = claims.at(event.getBlock().getLocation()); if (claim == null) return; if (event.getBlock().getType() == Material.FARMLAND && blocked(claim, ClaimFlag.CROP_TRAMPLING)) { event.setCancelled(true); denials.increment(); return; } if (!(event.getEntity() instanceof Player) && blocked(claim, ClaimFlag.MOB_GRIEFING)) { event.setCancelled(true); denials.increment(); } }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onDeleted(ClaimDeletedEvent event) { store.removeClaimTree(event.getClaim()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { warningCooldown.remove(event.getPlayer().getUniqueId()); }
    public long decisionCount() { return decisions.sum(); } public long denialCount() { return denials.sum(); }
    static boolean shouldDenyPvp(boolean victimBlocked, boolean victimBypass, boolean attackerBlocked, boolean attackerBypass) { return victimBlocked && !victimBypass || attackerBlocked && !attackerBypass; }
    private boolean blocked(Claim claim, ClaimFlag flag) { decisions.increment(); return store.effective(claim, flag); }
    private boolean enabled(Location location, ClaimFlag flag) { Claim claim = claims.at(location); return claim != null && blocked(claim, flag); }
    private void addDenied(int count) { if (count > 0) denials.add(count); }
    private void warn(Player player) { long now = System.nanoTime(); long last = warningCooldown.getOrDefault(player.getUniqueId(), 0L); if (now - last < 1_500_000_000L) return; warningCooldown.put(player.getUniqueId(), now); plugin.messages().send(player, "protected-action"); }
    private static Player attackingPlayer(Entity damager) { if (damager instanceof Player player) return player; if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player; return null; }
    private static boolean mobCausedExplosion(Entity source) { if (source instanceof Player) return false; if (source instanceof Projectile projectile) { ProjectileSource shooter = projectile.getShooter(); return shooter instanceof Entity && !(shooter instanceof Player); } return switch (source.getType()) { case CREEPER, WITHER, WITHER_SKULL -> true; default -> false; }; }
}
