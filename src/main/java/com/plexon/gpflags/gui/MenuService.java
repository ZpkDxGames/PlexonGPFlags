package com.plexon.gpflags.gui;

import com.plexon.gpflags.PlexonGPFlags;
import com.plexon.gpflags.api.FlagChangeResult;
import com.plexon.gpflags.claim.ClaimService;
import com.plexon.gpflags.flag.ClaimFlag;
import com.plexon.gpflags.flag.FlagOverride;
import com.plexon.gpflags.flag.FlagService;
import com.plexon.gpflags.flag.FlagStore;
import com.plexon.gpflags.service.ClaimActionService;
import com.plexon.gpflags.service.PromptService;
import com.plexon.gpflags.service.TeleportService;
import com.plexon.gpflags.service.VisualizerService;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** Native Paper inventory router. No shaded GUI framework and no repeating menu redraw task. */
public final class MenuService implements Listener {
    private enum Screen { HUB, CLAIMS, AREAS, DASHBOARD, FLAGS, TRUST, TRUST_LEVEL, AUTOCLAIM, RESIZE_DIRECTION, RESIZE, CONFIRM }

    private static final int[] FLAG_SLOTS = {10,11,12,13,14,15,16,19,20,21};
    private final PlexonGPFlags plugin;
    private final ClaimService claims;
    private final FlagStore store;
    private final FlagService flags;
    private final ClaimActionService actions;
    private final PromptService prompts;
    private final TeleportService teleports;
    private final VisualizerService visualizer;
    private ItemStack filler;

    public MenuService(PlexonGPFlags plugin, ClaimService claims, FlagStore store, FlagService flags,
                       ClaimActionService actions, PromptService prompts, TeleportService teleports,
                       VisualizerService visualizer) {
        this.plugin = plugin;
        this.claims = claims;
        this.store = store;
        this.flags = flags;
        this.actions = actions;
        this.prompts = prompts;
        this.teleports = teleports;
        this.visualizer = visualizer;
        reload();
    }

    public void reload() { filler = named(plugin.settings().filler(), Component.text(" "), List.of()); }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) player.closeInventory();
        }
    }

    public void openHub(Player player) {
        Holder holder = holder(Screen.HUB, -1, 1, "", 0, 27, title("hub", Map.of(), "<dark_gray>Plexon Claims"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 10, button(Material.GRASS_BLOCK, "<gradient:#55FF55:#41C902><b>Current Claim</b></gradient>",
                "<gray>Manage the claim or subdivision you are standing in.</gray>"));
        if (plugin.settings().features().claimList()) set(inv, 12, button(Material.MAP, "<aqua><b>My Claims</b></aqua>", "<gray>Browse your top-level claims.</gray>"));
        if (plugin.settings().features().autoClaim()) set(inv, 14, button(Material.ENCHANTED_BOOK, "<yellow><b>Create Claim</b></yellow>", "<gray>Create a centered claim from a preset or custom size.</gray>"));
        if (plugin.settings().features().shovel()) set(inv, 16, button(Material.GOLDEN_SHOVEL, "<gold><b>Claim Shovel</b></gold>", "<gray>Request a GriefPrevention claim shovel.</gray>"));
        set(inv, 22, button(Material.BARRIER, "<red>Close</red>", "<gray>Close this menu.</gray>"));
        player.openInventory(inv);
    }

    public void openClaims(Player player, int requestedPage) {
        List<Claim> all = claims.ownedTopLevel(player);
        int pages = Math.max(1, (all.size() + 44) / 45);
        int page = Math.max(1, Math.min(pages, requestedPage));
        Holder holder = holder(Screen.CLAIMS, -1, page, "", 0, 54,
                title("claims", Map.of("page", Integer.toString(page), "pages", Integer.toString(pages)), "<dark_gray>My Claims"));
        Inventory inv = holder.inventory();
        fill(inv);
        int from = (page - 1) * 45;
        for (int slot = 0; slot < 45 && from + slot < all.size(); slot++) {
            Claim claim = all.get(from + slot);
            holder.claimTargets.put(slot, claims.safeId(claim));
            set(inv, slot, claimItem(claim));
        }
        if (page > 1) set(inv, 45, button(Material.ARROW, "<yellow>Previous Page</yellow>", "<gray>Page " + (page - 1) + "</gray>"));
        set(inv, 49, button(Material.NETHER_STAR, "<green>Claim Hub</green>", "<gray>Return to the main claim panel.</gray>"));
        if (page < pages) set(inv, 53, button(Material.ARROW, "<yellow>Next Page</yellow>", "<gray>Page " + (page + 1) + "</gray>"));
        player.openInventory(inv);
    }

    public void openAreas(Player player, Claim selected) {
        Claim parent = claims.parentOf(selected);
        if (!valid(player, parent)) return;
        Holder holder = holder(Screen.AREAS, claims.safeId(parent), 1, "", 0, 54,
                plugin.messages().raw("<dark_gray>Claim Areas <gray>• <white>#" + claims.safeId(parent) + "</white>"));
        Inventory inv = holder.inventory();
        fill(inv);
        List<Claim> areas = new ArrayList<>();
        areas.add(parent);
        areas.addAll(parent.children);
        for (int slot = 0; slot < Math.min(45, areas.size()); slot++) {
            Claim claim = areas.get(slot);
            holder.claimTargets.put(slot, claims.safeId(claim));
            set(inv, slot, claimItem(claim));
        }
        set(inv, 49, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to claim dashboard.</gray>"));
        player.openInventory(inv);
    }

    public void openDashboard(Player player, Claim claim) {
        if (!valid(player, claim)) return;
        Holder holder = holder(Screen.DASHBOARD, claims.safeId(claim), 1, "", 0, 54,
                title("dashboard", Map.of("claim", Long.toString(claims.safeId(claim))), "<dark_gray>Claim Dashboard"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 4, claimItem(claim));
        if (plugin.settings().features().flags()) set(inv, 10, button(Material.REDSTONE_TORCH, "<red><b>Claim Flags</b></red>", "<gray>Control PvP, mobs, fire, containers and more.</gray>"));
        if (plugin.settings().features().trust()) set(inv, 12, button(Material.PLAYER_HEAD, "<aqua><b>Trust</b></aqua>", "<gray>Add, review or remove trusted players.</gray>"));
        if (plugin.settings().features().resize() && claim.parent == null) set(inv, 14, button(Material.PISTON, "<yellow><b>Resize</b></yellow>", "<gray>Expand or shrink this claim.</gray>"));
        if (plugin.settings().features().teleport()) set(inv, 16, button(Material.ENDER_PEARL, "<light_purple><b>Teleport</b></light_purple>", "<gray>Warm up and teleport to this claim.</gray>"));
        if (plugin.settings().features().visualizer()) set(inv, 28, button(Material.GLOWSTONE_DUST, "<gold><b>Show Boundary</b></gold>", "<gray>Render only this claim for a few seconds.</gray>"));
        set(inv, 30, button(Material.OAK_FENCE, "<green><b>Claim Areas</b></green>", "<gray>Switch between the parent claim and subdivisions.</gray>"));
        if (plugin.settings().features().abandon() && claim.parent == null) set(inv, 34, button(Material.TNT, "<red><b>Abandon Claim</b></red>", "<gray>Delete this claim after confirmation.</gray>"));
        set(inv, 49, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to your claim list.</gray>"));
        player.openInventory(inv);
    }

    public void openFlags(Player player, Claim claim) {
        if (!valid(player, claim)) return;
        Holder holder = holder(Screen.FLAGS, claims.safeId(claim), 1, "", 0, 54,
                title("flags", Map.of("claim", Long.toString(claims.safeId(claim))), "<dark_gray>Claim Flags"));
        Inventory inv = holder.inventory();
        fill(inv);
        ClaimFlag[] values = ClaimFlag.values();
        for (int i = 0; i < values.length; i++) {
            ClaimFlag flag = values[i];
            int slot = FLAG_SLOTS[i];
            boolean effective = store.effective(claim, flag);
            Boolean explicit = store.explicitValue(claim, flag);
            String source = store.inherited(claim, flag) ? "Inherited" : explicit == null ? "Server default" : "Explicit";
            String state = effective ? "<red>BLOCKED</red>" : "<green>ALLOWED</green>";
            List<Component> lore = List.of(
                    plugin.messages().raw("<gray>Effective: " + state + "</gray>"),
                    Component.text("Source: " + source, NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Left click: toggle", NamedTextColor.YELLOW),
                    Component.text("Right click: reset/inherit", NamedTextColor.AQUA));
            set(inv, slot, named(flag.icon(), plugin.messages().raw((effective ? "<red>" : "<green>") + plugin.messages().flagName(flag) + "</" + (effective ? "red>" : "green>")), lore));
        }
        set(inv, 49, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to the claim dashboard.</gray>"));
        player.openInventory(inv);
    }

    public void openTrust(Player player, Claim claim, int requestedPage) {
        if (!valid(player, claim)) return;
        LinkedHashMap<String, String> trusted = trusted(claim);
        List<Map.Entry<String,String>> entries = List.copyOf(trusted.entrySet());
        int pages = Math.max(1, (entries.size() + 44) / 45);
        int page = Math.max(1, Math.min(pages, requestedPage));
        Holder holder = holder(Screen.TRUST, claims.safeId(claim), page, "", 0, 54,
                title("trust", Map.of("claim", Long.toString(claims.safeId(claim))), "<dark_gray>Trusted Players"));
        Inventory inv = holder.inventory();
        fill(inv);
        int from = (page - 1) * 45;
        for (int slot = 0; slot < 45 && from + slot < entries.size(); slot++) {
            Map.Entry<String,String> entry = entries.get(from + slot);
            holder.textTargets.put(slot, entry.getKey());
            List<Component> lore = List.of(Component.text("Level: " + entry.getValue(), NamedTextColor.YELLOW),
                    Component.text("Click to remove", NamedTextColor.RED));
            set(inv, slot, named(Material.PLAYER_HEAD, Component.text(displayTrustName(entry.getKey()), NamedTextColor.WHITE), lore));
        }
        if (page > 1) set(inv, 45, button(Material.ARROW, "<yellow>Previous</yellow>", "<gray>Page " + (page - 1) + "</gray>"));
        set(inv, 48, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to dashboard.</gray>"));
        set(inv, 49, button(Material.EMERALD, "<green><b>Add Player</b></green>", "<gray>Type a player name, then choose access level.</gray>"));
        if (page < pages) set(inv, 53, button(Material.ARROW, "<yellow>Next</yellow>", "<gray>Page " + (page + 1) + "</gray>"));
        player.openInventory(inv);
    }

    private void openTrustLevel(Player player, Claim claim, String target) {
        if (!valid(player, claim)) return;
        Holder holder = holder(Screen.TRUST_LEVEL, claims.safeId(claim), 1, target, 0, 27,
                plugin.messages().raw("<dark_gray>Trust <gray>• <white>" + safe(target) + "</white>"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 10, button(Material.OAK_DOOR, "<aqua>Access</aqua>", "<gray>Buttons, doors and basic access.</gray>"));
        set(inv, 12, button(Material.CHEST, "<gold>Container</gold>", "<gray>Container/inventory access.</gray>"));
        set(inv, 14, button(Material.IRON_PICKAXE, "<yellow>Build</yellow>", "<gray>Place and break blocks.</gray>"));
        set(inv, 16, button(Material.NETHER_STAR, "<red>Manage</red>", "<gray>Full claim management trust.</gray>"));
        set(inv, 22, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to trusted players.</gray>"));
        player.openInventory(inv);
    }

    public void openAutoClaim(Player player) {
        int remaining = claims.remainingClaimBlocks(player);
        Holder holder = holder(Screen.AUTOCLAIM, -1, 1, "", 0, 36,
                title("autoclaim", Map.of(), "<dark_gray>Create Claim"));
        Inventory inv = holder.inventory();
        fill(inv);
        int small = plugin.settings().claims().smallSize();
        int medium = plugin.settings().claims().mediumSize();
        int large = plugin.settings().claims().largeSize();
        set(inv, 10, sizeItem("Small Claim", small, remaining, Material.LIME_CONCRETE));
        set(inv, 12, sizeItem("Medium Claim", medium, remaining, Material.YELLOW_CONCRETE));
        set(inv, 14, sizeItem("Large Claim", large, remaining, Material.ORANGE_CONCRETE));
        int all = (int) Math.floor(Math.sqrt(Math.max(0, remaining)));
        set(inv, 16, sizeItem("Use Available Blocks", all, remaining, Material.RED_CONCRETE));
        set(inv, 22, button(Material.NAME_TAG, "<aqua><b>Custom Size</b></aqua>", "<gray>Type a side length in chat.</gray>"));
        set(inv, 31, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to claim hub.</gray>"));
        player.openInventory(inv);
    }

    private void openResizeDirections(Player player, Claim claim) {
        if (!valid(player, claim) || claim.parent != null) return;
        Holder holder = holder(Screen.RESIZE_DIRECTION, claims.safeId(claim), 1, "", 0, 27,
                plugin.messages().raw("<dark_gray>Resize Direction"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 10, button(Material.BLUE_CONCRETE, "<aqua>North</aqua>", "<gray>Move the north border.</gray>"));
        set(inv, 12, button(Material.BLUE_CONCRETE, "<aqua>South</aqua>", "<gray>Move the south border.</gray>"));
        set(inv, 14, button(Material.BLUE_CONCRETE, "<aqua>East</aqua>", "<gray>Move the east border.</gray>"));
        set(inv, 16, button(Material.BLUE_CONCRETE, "<aqua>West</aqua>", "<gray>Move the west border.</gray>"));
        set(inv, 22, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to dashboard.</gray>"));
        player.openInventory(inv);
    }

    private void openResize(Player player, Claim claim, ClaimActionService.Direction direction, int requestedOffset) {
        if (!valid(player, claim) || claim.parent != null) return;
        ClaimActionService.ResizePreview preview = actions.preview(player, claim, direction, requestedOffset);
        Holder holder = holder(Screen.RESIZE, claims.safeId(claim), 1, direction.name(), preview.offset(), 27,
                title("resize", Map.of("direction", direction.name()), "<dark_gray>Resize Claim"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 1, button(Material.RED_CONCRETE, "<red><b>-MAX</b></red>", "<gray>Shrink as far as safely allowed.</gray>"));
        set(inv, 2, button(Material.RED_STAINED_GLASS_PANE, "<red>-5</red>", "<gray>Shrink five blocks.</gray>"));
        set(inv, 3, button(Material.PINK_STAINED_GLASS_PANE, "<red>-1</red>", "<gray>Shrink one block.</gray>"));
        String delta = preview.blockDelta() > 0 ? "+" + preview.blockDelta() : Integer.toString(preview.blockDelta());
        set(inv, 4, named(Material.BOOK, plugin.messages().raw("<yellow><b>Preview</b></yellow>"), List.of(
                Component.text("Offset: " + (preview.offset() >= 0 ? "+" : "") + preview.offset(), NamedTextColor.WHITE),
                Component.text("Size: " + preview.width() + " × " + preview.length(), NamedTextColor.GRAY),
                Component.text("Claim blocks: " + delta, preview.blockDelta() > 0 ? NamedTextColor.RED : NamedTextColor.GREEN))));
        set(inv, 5, button(Material.LIME_STAINED_GLASS_PANE, "<green>+1</green>", "<gray>Expand one block.</gray>"));
        set(inv, 6, button(Material.GREEN_STAINED_GLASS_PANE, "<green>+5</green>", "<gray>Expand five blocks.</gray>"));
        set(inv, 7, button(Material.GREEN_CONCRETE, "<green><b>+MAX</b></green>", "<gray>Use the maximum available claim blocks.</gray>"));
        set(inv, 18, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Choose another direction.</gray>"));
        set(inv, 22, button(preview.valid() ? Material.LIME_WOOL : Material.GRAY_WOOL,
                preview.valid() ? "<green><b>Confirm Resize</b></green>" : "<gray>No Change</gray>", "<gray>Apply this boundary change.</gray>"));
        player.openInventory(inv);
    }

    private void openConfirm(Player player, Claim claim) {
        if (!valid(player, claim) || claim.parent != null) return;
        Holder holder = holder(Screen.CONFIRM, claims.safeId(claim), 1, "", 0, 27,
                title("confirm", Map.of(), "<dark_red>Confirm Claim Removal"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 11, button(Material.RED_DYE, "<red><b>Cancel</b></red>", "<gray>Keep this claim.</gray>"));
        set(inv, 13, button(Material.TNT, "<yellow><b>Delete Claim #" + claims.safeId(claim) + "?</b></yellow>", "<red>This cannot be undone.</red>"));
        set(inv, 15, button(Material.LIME_DYE, "<green><b>Confirm</b></green>", "<gray>Return the claim blocks and delete it.</gray>"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        switch (holder.screen) {
            case HUB -> hubClick(player, slot);
            case CLAIMS -> claimsClick(player, holder, slot);
            case AREAS -> areasClick(player, holder, slot);
            case DASHBOARD -> dashboardClick(player, holder, slot);
            case FLAGS -> flagsClick(player, holder, slot, event.isRightClick());
            case TRUST -> trustClick(player, holder, slot);
            case TRUST_LEVEL -> trustLevelClick(player, holder, slot);
            case AUTOCLAIM -> autoClaimClick(player, slot);
            case RESIZE_DIRECTION -> resizeDirectionClick(player, holder, slot);
            case RESIZE -> resizeClick(player, holder, slot);
            case CONFIRM -> confirmClick(player, holder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < size)) event.setCancelled(true);
    }

    private void hubClick(Player player, int slot) {
        if (slot == 10) { Claim claim = claims.at(player.getLocation()); if (claim == null) plugin.messages().send(player, "no-claim"); else openDashboard(player, claim); }
        else if (slot == 12 && plugin.settings().features().claimList()) openClaims(player, 1);
        else if (slot == 14 && plugin.settings().features().autoClaim()) openAutoClaim(player);
        else if (slot == 16 && plugin.settings().features().shovel()) actions.giveShovel(player);
        else if (slot == 22) player.closeInventory();
    }

    private void claimsClick(Player player, Holder holder, int slot) {
        Long id = holder.claimTargets.get(slot);
        if (id != null) { Claim claim = claims.byId(id); if (claim != null) openDashboard(player, claim); return; }
        if (slot == 45) openClaims(player, holder.page - 1);
        else if (slot == 49) openHub(player);
        else if (slot == 53) openClaims(player, holder.page + 1);
    }

    private void areasClick(Player player, Holder holder, int slot) {
        Long id = holder.claimTargets.get(slot);
        if (id != null) { Claim claim = claims.byId(id); if (claim != null) openDashboard(player, claim); return; }
        if (slot == 49) { Claim parent = claims.byId(holder.claimId); if (parent != null) openDashboard(player, parent); }
    }

    private void dashboardClick(Player player, Holder holder, int slot) {
        Claim claim = claims.byId(holder.claimId);
        if (!valid(player, claim)) return;
        if (slot == 10 && plugin.settings().features().flags()) openFlags(player, claim);
        else if (slot == 12 && plugin.settings().features().trust()) openTrust(player, claim, 1);
        else if (slot == 14 && plugin.settings().features().resize() && claim.parent == null) openResizeDirections(player, claim);
        else if (slot == 16 && plugin.settings().features().teleport()) { player.closeInventory(); teleports.request(player, claim); }
        else if (slot == 28 && plugin.settings().features().visualizer()) { player.closeInventory(); visualizer.show(player, claim); }
        else if (slot == 30) openAreas(player, claim);
        else if (slot == 34 && plugin.settings().features().abandon() && claim.parent == null) openConfirm(player, claim);
        else if (slot == 49) openClaims(player, 1);
    }

    private void flagsClick(Player player, Holder holder, int slot, boolean rightClick) {
        Claim claim = claims.byId(holder.claimId);
        if (!valid(player, claim)) return;
        if (slot == 49) { openDashboard(player, claim); return; }
        int index = -1;
        for (int i = 0; i < FLAG_SLOTS.length; i++) if (FLAG_SLOTS[i] == slot) { index = i; break; }
        if (index < 0) return;
        ClaimFlag flag = ClaimFlag.values()[index];
        FlagOverride override = rightClick ? FlagOverride.INHERIT : store.effective(claim, flag) ? FlagOverride.OFF : FlagOverride.ON;
        FlagChangeResult result = flags.set(player, claim, flag, override, "GUI");
        if (result.status() == FlagChangeResult.Status.PERSISTENCE_FAILED) plugin.messages().send(player, "persistence-error");
        else if (result.success()) plugin.messages().send(player, "flag-changed", Map.of(
                "flag", plugin.messages().flagName(flag), "state", store.effective(claim, flag) ? "<red>BLOCKED</red>" : "<green>ALLOWED</green>",
                "area", claims.areaLabel(claim)));
        openFlags(player, claim);
    }

    private void trustClick(Player player, Holder holder, int slot) {
        Claim claim = claims.byId(holder.claimId);
        if (!valid(player, claim)) return;
        String entry = holder.textTargets.get(slot);
        if (entry != null) { actions.removeTrust(player, claim, entry); openTrust(player, claim, holder.page); return; }
        if (slot == 45) openTrust(player, claim, holder.page - 1);
        else if (slot == 48) openDashboard(player, claim);
        else if (slot == 49) prompts.start(player, "prompt-player", (actor, input) -> {
            if (!input.matches("[A-Za-z0-9_]{1,16}")) { plugin.messages().send(actor, "prompt-invalid"); openTrust(actor, claim, holder.page); return; }
            openTrustLevel(actor, claims.byId(holder.claimId), input);
        });
        else if (slot == 53) openTrust(player, claim, holder.page + 1);
    }

    private void trustLevelClick(Player player, Holder holder, int slot) {
        Claim claim = claims.byId(holder.claimId);
        if (!valid(player, claim)) return;
        if (slot == 22) { openTrust(player, claim, 1); return; }
        ClaimPermission permission;
        String label;
        if (slot == 10) { permission = ClaimPermission.Access; label = "access"; }
        else if (slot == 12) { permission = ClaimPermission.Inventory; label = "container"; }
        else if (slot == 14) { permission = ClaimPermission.Build; label = "build"; }
        else if (slot == 16) { permission = ClaimPermission.Manage; label = "manage"; }
        else return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(holder.data);
        actions.grantTrust(player, claim, target.getUniqueId(), permission, holder.data, label);
        openTrust(player, claim, 1);
    }

    private void autoClaimClick(Player player, int slot) {
        int side;
        if (slot == 10) side = plugin.settings().claims().smallSize();
        else if (slot == 12) side = plugin.settings().claims().mediumSize();
        else if (slot == 14) side = plugin.settings().claims().largeSize();
        else if (slot == 16) side = (int) Math.floor(Math.sqrt(Math.max(0, claims.remainingClaimBlocks(player))));
        else if (slot == 22) {
            prompts.start(player, "prompt-size", (actor, input) -> {
                int parsed;
                try { parsed = Integer.parseInt(input); } catch (NumberFormatException error) { plugin.messages().send(actor, "prompt-invalid"); openAutoClaim(actor); return; }
                Claim created = actions.createSquare(actor, parsed);
                if (created != null) openDashboard(actor, created); else openAutoClaim(actor);
            });
            return;
        } else if (slot == 31) { openHub(player); return; } else return;
        Claim created = actions.createSquare(player, side);
        if (created != null) openDashboard(player, created); else openAutoClaim(player);
    }

    private void resizeDirectionClick(Player player, Holder holder, int slot) {
        Claim claim = claims.byId(holder.claimId);
        if (!valid(player, claim)) return;
        ClaimActionService.Direction direction = switch (slot) {
            case 10 -> ClaimActionService.Direction.NORTH;
            case 12 -> ClaimActionService.Direction.SOUTH;
            case 14 -> ClaimActionService.Direction.EAST;
            case 16 -> ClaimActionService.Direction.WEST;
            default -> null;
        };
        if (direction != null) openResize(player, claim, direction, 0);
        else if (slot == 22) openDashboard(player, claim);
    }

    private void resizeClick(Player player, Holder holder, int slot) {
        Claim claim = claims.byId(holder.claimId);
        if (!valid(player, claim)) return;
        ClaimActionService.Direction direction = ClaimActionService.Direction.valueOf(holder.data);
        ClaimActionService.ResizePreview preview = actions.preview(player, claim, direction, holder.offset);
        if (slot == 1) openResize(player, claim, direction, -preview.maxShrink());
        else if (slot == 2) openResize(player, claim, direction, holder.offset - 5);
        else if (slot == 3) openResize(player, claim, direction, holder.offset - 1);
        else if (slot == 5) openResize(player, claim, direction, holder.offset + 1);
        else if (slot == 6) openResize(player, claim, direction, holder.offset + 5);
        else if (slot == 7) openResize(player, claim, direction, preview.maxExpand());
        else if (slot == 18) openResizeDirections(player, claim);
        else if (slot == 22 && preview.valid()) {
            Claim resized = actions.resize(player, claim, direction, holder.offset);
            if (resized != null) openDashboard(player, resized); else openResize(player, claim, direction, holder.offset);
        }
    }

    private void confirmClick(Player player, Holder holder, int slot) {
        Claim claim = claims.byId(holder.claimId);
        if (!valid(player, claim)) return;
        if (slot == 11) openDashboard(player, claim);
        else if (slot == 15) { player.closeInventory(); if (actions.delete(player, claim)) openClaims(player, 1); }
    }

    private boolean valid(Player player, Claim claim) {
        if (claim == null || !claim.inDataStore) { plugin.messages().send(player, "claim-missing"); return false; }
        if (!claims.canManage(player, claim)) { plugin.messages().send(player, claim.isAdminClaim() ? "admin-claim-denied" : "not-owner"); return false; }
        return true;
    }

    private LinkedHashMap<String,String> trusted(Claim claim) {
        ArrayList<String> build = new ArrayList<>(), inventory = new ArrayList<>(), access = new ArrayList<>(), manage = new ArrayList<>();
        claim.getPermissions(build, inventory, access, manage);
        LinkedHashMap<String,String> result = new LinkedHashMap<>();
        for (String value : access) result.put(value, "Access");
        for (String value : inventory) result.put(value, "Container");
        for (String value : build) result.put(value, "Build");
        for (String value : manage) result.put(value, "Manage");
        return result;
    }

    private String displayTrustName(String entry) {
        try {
            UUID uuid = UUID.fromString(entry);
            Player online = Bukkit.getPlayer(uuid);
            return online != null ? online.getName() : entry;
        } catch (IllegalArgumentException ignored) { return entry; }
    }

    private ItemStack claimItem(Claim claim) {
        LocationData data = dimensions(claim);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(claims.boundsLabel(claim), NamedTextColor.GRAY));
        lore.add(Component.text("Size: " + data.width + " × " + data.length, NamedTextColor.YELLOW));
        lore.add(Component.text("Area: " + data.area + " blocks", NamedTextColor.WHITE));
        if (claim.parent == null && !claim.children.isEmpty()) lore.add(Component.text("Subclaims: " + claim.children.size(), NamedTextColor.AQUA));
        lore.add(Component.empty());
        lore.add(Component.text("Click to manage", NamedTextColor.GREEN));
        return named(claim.parent == null ? Material.GRASS_BLOCK : Material.OAK_FENCE,
                plugin.messages().raw("<gradient:#55FF55:#41C902><b>" + safe(claims.areaLabel(claim)) + "</b></gradient>"), lore);
    }

    private ItemStack sizeItem(String label, int side, int available, Material material) {
        int min = GriefPrevention.instance.config_claims_minWidth;
        long needed = (long) side * side;
        boolean valid = side >= min && needed <= available;
        Material actual = valid ? material : Material.BARRIER;
        return named(actual, plugin.messages().raw((valid ? "<green><b>" : "<red><b>") + label + "</b>" + (valid ? "</green>" : "</red>")), List.of(
                Component.text("Size: " + side + " × " + side, NamedTextColor.WHITE),
                Component.text("Cost: " + needed + " claim blocks", NamedTextColor.GRAY),
                Component.text("Available: " + available, NamedTextColor.GRAY)));
    }

    private Component title(String key, Map<String,String> values, String fallback) {
        String raw = plugin.getConfig().getString("gui.titles." + key, fallback);
        if (raw == null) raw = fallback;
        for (Map.Entry<String,String> entry : values.entrySet()) raw = raw.replace("%" + entry.getKey() + "%", safe(entry.getValue()));
        return plugin.messages().raw(raw);
    }

    private Holder holder(Screen screen, long claimId, int page, String data, int offset, int size, Component title) {
        Holder holder = new Holder(screen, claimId, page, data, offset);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.attach(inventory);
        return holder;
    }

    private void fill(Inventory inventory) { for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler); }
    private void set(Inventory inventory, int slot, ItemStack item) { if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item); }
    private ItemStack button(Material material, String name, String lore) { return named(material, plugin.messages().raw(name), List.of(plugin.messages().raw(lore))); }

    private ItemStack named(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static String safe(String value) { return value == null ? "" : value.replace("<", "").replace(">", ""); }
    private static LocationData dimensions(Claim claim) {
        var a = claim.getLesserBoundaryCorner(); var b = claim.getGreaterBoundaryCorner();
        int width = b.getBlockX() - a.getBlockX() + 1;
        int length = b.getBlockZ() - a.getBlockZ() + 1;
        return new LocationData(width, length, (long) width * length);
    }
    private record LocationData(int width, int length, long area) {}

    private static final class Holder implements InventoryHolder {
        private final Screen screen;
        private final long claimId;
        private final int page;
        private final String data;
        private final int offset;
        private final Map<Integer,Long> claimTargets = new HashMap<>();
        private final Map<Integer,String> textTargets = new HashMap<>();
        private Inventory inventory;
        private Holder(Screen screen, long claimId, int page, String data, int offset) {
            this.screen = screen; this.claimId = claimId; this.page = page; this.data = data; this.offset = offset;
        }
        private void attach(Inventory inventory) { this.inventory = inventory; }
        @Override public @NotNull Inventory getInventory() { return Objects.requireNonNull(inventory); }
        private Inventory inventory() { return getInventory(); }
    }
}
