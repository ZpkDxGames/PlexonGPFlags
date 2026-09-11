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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Native Paper inventory router. Presentation only; all mutations delegate to existing authorities. */
public final class MenuService implements Listener {
    private enum Screen {
        HUB, CLAIMS, AREAS, DASHBOARD, FLAGS, FLAG_DETAILS,
        TRUST, TRUST_DETAILS, TRUST_LEVEL, TRUST_REMOVE_CONFIRM,
        AUTOCLAIM, RESIZE_DIRECTION, RESIZE, ABANDON_CONFIRM
    }

    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int[] FLAG_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

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

    public void reload() {
        filler = named(plugin.settings().filler(), Component.text(" "), List.of());
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) player.closeInventory();
        }
    }

    public void openHub(Player player) {
        Holder holder = holder(Screen.HUB, null, 1, "", "", 0, ClaimNavigationContext.home(), 27,
                title("hub", Map.of(), "<dark_gray>Claims Home"));
        Inventory inv = holder.inventory();
        fill(inv);
        Claim standing = claims.at(player.getLocation());
        if (standing == null) {
            set(inv, 10, button(Material.GRAY_CONCRETE, "<gray><b>Current Claim</b></gray>",
                    "<gray>You are not standing inside a claim.</gray>"));
        } else if (!claims.canManage(player, standing)) {
            set(inv, 10, button(Material.RED_STAINED_GLASS_PANE, "<red><b>Current Claim</b></red>",
                    "<gray>You are inside a claim you cannot manage.</gray>"));
        } else {
            set(inv, 10, claimItem(player, standing, "<green>Click to manage this area.</green>"));
        }
        if (plugin.settings().features().claimList()) {
            set(inv, 12, button(Material.MAP, "<aqua><b>My Claims</b></aqua>",
                    "<gray>Browse and manage your claims.</gray>"));
        }
        if (plugin.settings().features().autoClaim()) {
            set(inv, 14, button(Material.ENCHANTED_BOOK, "<yellow><b>Create Claim</b></yellow>",
                    "<gray>Choose a preset or enter a custom size.</gray>"));
        }
        if (plugin.settings().features().shovel()) {
            set(inv, 16, button(Material.GOLDEN_SHOVEL, "<gold><b>Claim Shovel</b></gold>",
                    "<gray>Get the claim-selection tool.</gray>"));
        }
        set(inv, 22, button(Material.BARRIER, "<red>Close</red>", "<gray>Return to the game.</gray>"));
        player.openInventory(inv);
    }

    public void openClaims(Player player, int requestedPage) {
        openClaims(player, requestedPage, ClaimNavigationContext.claims(requestedPage));
    }

    private void openClaims(Player player, int requestedPage, ClaimNavigationContext context) {
        List<Claim> all = claims.ownedTopLevel(player);
        int pages = Math.max(1, (all.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
        int page = Math.max(1, Math.min(pages, requestedPage));
        ClaimNavigationContext navigation = ClaimNavigationContext.claims(page);
        Holder holder = holder(Screen.CLAIMS, null, page, "", "", 0, navigation, 54,
                title("claims", Map.of("page", Integer.toString(page), "pages", Integer.toString(pages)),
                        "<dark_gray>My Claims <gray>• <white>%page%/%pages%"));
        Inventory inv = holder.inventory();
        fill(inv);
        long standingId = claims.safeId(claims.at(player.getLocation()));
        int from = (page - 1) * CONTENT_SLOTS.length;
        for (int index = 0; index < CONTENT_SLOTS.length && from + index < all.size(); index++) {
            Claim claim = all.get(from + index);
            int slot = CONTENT_SLOTS[index];
            holder.claimTargets.put(slot, new ClaimTarget(claims.safeId(claim), ClaimPresentation.Token.from(claim)));
            set(inv, slot, claimItem(player, claim, claims.safeId(claim) == standingId
                    ? "<green>You are standing in this claim.</green>"
                    : "<aqua>Click to open its dashboard.</aqua>"));
        }
        if (all.isEmpty()) {
            set(inv, 22, button(Material.PAPER, "<gray><b>No claims yet</b></gray>",
                    "<gray>Create a claim from Claims Home when you are ready.</gray>"));
        }
        if (page > 1) set(inv, 45, button(Material.ARROW, "<yellow>Previous</yellow>", "<gray>Page " + (page - 1) + "</gray>"));
        set(inv, 48, button(Material.NETHER_STAR, "<green>Claims Home</green>", "<gray>Return to Claims Home.</gray>"));
        set(inv, 51, button(Material.PAPER, "<white>Page " + page + " / " + pages + "</white>",
                "<gray>" + all.size() + " main claim(s).</gray>"));
        set(inv, 52, button(Material.BARRIER, "<red>Close</red>", "<gray>Return to the game.</gray>"));
        if (page < pages) set(inv, 53, button(Material.ARROW, "<yellow>Next</yellow>", "<gray>Page " + (page + 1) + "</gray>"));
        player.openInventory(inv);
    }

    public void openAreas(Player player, Claim selected) {
        openAreas(player, selected, ClaimNavigationContext.home());
    }

    private void openAreas(Player player, Claim selected, ClaimNavigationContext context) {
        Claim parent = claims.parentOf(selected);
        if (!valid(player, parent, context)) return;
        ClaimNavigationContext navigation = context.areasParentClaimId() < 0
                ? context.beginAreas(claims.safeId(parent), claims.safeId(selected))
                : context.withAreasPage(context.areasPage());
        List<Claim> areas = new ArrayList<>();
        areas.add(parent);
        areas.addAll(parent.children);
        int pages = Math.max(1, (areas.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
        int page = Math.max(1, Math.min(pages, navigation.areasPage()));
        navigation = navigation.withAreasPage(page);
        Holder holder = holder(Screen.AREAS, parent, page, "", "", 0, navigation, 54,
                plugin.messages().raw("<dark_gray>Claim Areas <gray>•</gray> <white>" + safe(ClaimPresentation.label(parent)) + "</white>"));
        Inventory inv = holder.inventory();
        fill(inv);
        long selectedId = claims.safeId(selected);
        int from = (page - 1) * CONTENT_SLOTS.length;
        for (int index = 0; index < CONTENT_SLOTS.length && from + index < areas.size(); index++) {
            Claim area = areas.get(from + index);
            int slot = CONTENT_SLOTS[index];
            holder.claimTargets.put(slot, new ClaimTarget(claims.safeId(area), ClaimPresentation.Token.from(area)));
            set(inv, slot, claimItem(player, area, claims.safeId(area) == selectedId
                    ? "<green>Currently selected area.</green>"
                    : "<aqua>Click to manage this area.</aqua>"));
        }
        if (page > 1) set(inv, 45, button(Material.ARROW, "<yellow>Previous</yellow>", "<gray>Page " + (page - 1) + "</gray>"));
        set(inv, 48, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to the originating claim dashboard.</gray>"));
        set(inv, 51, button(Material.OAK_FENCE, "<white>Selected Areas</white>",
                "<gray>Main Claim + " + parent.children.size() + " subdivision(s).</gray>"));
        set(inv, 52, button(Material.BARRIER, "<red>Close</red>", "<gray>Return to the game.</gray>"));
        if (page < pages) set(inv, 53, button(Material.ARROW, "<yellow>Next</yellow>", "<gray>Page " + (page + 1) + "</gray>"));
        player.openInventory(inv);
    }

    public void openDashboard(Player player, Claim claim) {
        openDashboard(player, claim, ClaimNavigationContext.home());
    }

    private void openDashboard(Player player, Claim claim, ClaimNavigationContext navigation) {
        if (!valid(player, claim, navigation)) return;
        String label = ClaimPresentation.label(claim);
        Holder holder = holder(Screen.DASHBOARD, claim, 1, "", "", 0, navigation, 54,
                title("dashboard", Map.of("claim", label), "<dark_gray>Claim Dashboard <gray>• <white>%claim%"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 4, claimItem(player, claim, "<gray>This is the area you are managing.</gray>"));
        if (plugin.settings().features().flags()) {
            set(inv, 10, button(Material.REDSTONE_TORCH, "<gold><b>Rules</b></gold>",
                    "<gray>Review what this area allows or blocks.</gray>"));
        }
        if (plugin.settings().features().trust()) {
            set(inv, 12, button(Material.PLAYER_HEAD, "<aqua><b>Trusted Players</b></aqua>",
                    "<gray>Review, add, change or remove access.</gray>"));
        }
        set(inv, 14, button(Material.OAK_FENCE, "<green><b>Claim Areas</b></green>",
                claim.parent == null ? "<gray>Manage the main claim and its subdivisions.</gray>"
                        : "<gray>Switch between this subdivision and its main claim.</gray>"));
        if (plugin.settings().features().resize() && claim.parent == null) {
            set(inv, 16, button(Material.PISTON, "<yellow><b>Resize</b></yellow>",
                    "<gray>Preview a border change before applying it.</gray>"));
        }
        if (plugin.settings().features().teleport()) {
            int warmup = plugin.settings().claims().teleportWarmupSeconds();
            String cancel = teleportCancelSummary();
            set(inv, 28, named(Material.ENDER_PEARL, plugin.messages().raw("<light_purple><b>Teleport</b></light_purple>"), List.of(
                    plugin.messages().raw("<gray>Target:</gray> <white>" + safe(label) + "</white>"),
                    plugin.messages().raw("<gray>Warmup:</gray> <white>" + warmup + "s</white>"),
                    plugin.messages().raw("<gray>Cancel:</gray> <white>" + safe(cancel) + "</white>"),
                    Component.empty(),
                    plugin.messages().raw("<light_purple>Click to begin teleport.</light_purple>"))));
        }
        if (plugin.settings().features().visualizer()) {
            set(inv, 30, button(Material.GLOWSTONE_DUST, "<gold><b>Show Boundary</b></gold>",
                    "<gray>Show this claim's boundary for " + plugin.settings().claims().visualizerDurationSeconds() + "s.</gray>"));
        }
        if (plugin.settings().features().abandon() && claim.parent == null) {
            set(inv, 34, button(Material.TNT, "<red><b>Abandon Claim</b></red>",
                    "<gray>Remove this claim only after confirmation.</gray>"));
        }
        set(inv, 48, button(Material.ARROW, "<yellow>Back</yellow>", dashboardBackLore(navigation)));
        set(inv, 51, button(claim.parent == null ? Material.GRASS_BLOCK : Material.OAK_FENCE,
                "<white>" + label + "</white>", claim.parent == null
                        ? "<gray>Main Claim</gray>" : "<gray>Subdivision of the main claim.</gray>"));
        set(inv, 52, button(Material.BARRIER, "<red>Close</red>", "<gray>Return to the game.</gray>"));
        player.openInventory(inv);
    }

    public void openFlags(Player player, Claim claim) {
        openFlags(player, claim, ClaimNavigationContext.home());
    }

    private void openFlags(Player player, Claim claim, ClaimNavigationContext navigation) {
        if (!valid(player, claim, navigation)) return;
        Holder holder = holder(Screen.FLAGS, claim, 1, "", "", 0, navigation, 54,
                title("flags", Map.of("claim", ClaimPresentation.label(claim)), "<dark_gray>Rules <gray>• <white>%claim%"));
        Inventory inv = holder.inventory();
        fill(inv);
        ClaimFlag[] values = ClaimFlag.values();
        for (int i = 0; i < values.length; i++) {
            ClaimFlag flag = values[i];
            FlagPresentation presentation = flagPresentation(claim, flag);
            int slot = FLAG_SLOTS[i];
            holder.flagTargets.put(slot, flag);
            boolean blocked = "Blocked".equals(presentation.behavior());
            set(inv, slot, named(flag.icon(), plugin.messages().raw((blocked ? "<red>" : "<green>")
                    + safe(plugin.messages().flagName(flag)) + (blocked ? "</red>" : "</green>")), List.of(
                    plugin.messages().raw("<gray>Current behavior:</gray> " + behavior(presentation.behavior())),
                    plugin.messages().raw("<gray>This claim:</gray> <white>" + safe(presentation.source()) + "</white>"),
                    Component.empty(),
                    plugin.messages().raw("<aqua>Click to review or change this rule.</aqua>"))));
        }
        set(inv, 48, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to the claim dashboard.</gray>"));
        set(inv, 51, button(Material.COMPARATOR, "<white>Rules</white>",
                "<gray>Select a rule for explicit Allow / Block controls.</gray>"));
        set(inv, 52, button(Material.BARRIER, "<red>Close</red>", "<gray>Return to the game.</gray>"));
        player.openInventory(inv);
    }

    private void openFlagDetails(Player player, Claim claim, ClaimFlag flag, ClaimNavigationContext navigation) {
        if (!valid(player, claim, navigation)) return;
        FlagPresentation presentation = flagPresentation(claim, flag);
        Holder holder = holder(Screen.FLAG_DETAILS, claim, 1, flag.key(), "", 0, navigation, 27,
                title("rule", Map.of("rule", plugin.messages().flagName(flag)), "<dark_gray>Rule Details <gray>• <white>%rule%"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 4, named(flag.icon(), plugin.messages().raw("<white><b>" + safe(plugin.messages().flagName(flag)) + "</b></white>"), List.of(
                plugin.messages().raw("<gray>Current behavior:</gray> " + behavior(presentation.behavior())),
                plugin.messages().raw("<gray>This claim:</gray> <white>" + safe(presentation.source()) + "</white>"),
                Component.empty(),
                plugin.messages().raw("<gray>Choose exactly how this claim should handle the rule.</gray>"))));
        set(inv, 10, button(Material.LIME_CONCRETE, "<green><b>Allow</b></green>",
                "<gray>Allow this behavior in the selected claim.</gray>"));
        set(inv, 12, button(Material.RED_CONCRETE, "<red><b>Block</b></red>",
                "<gray>Block this behavior in the selected claim.</gray>"));
        set(inv, 14, button(Material.LIGHT_GRAY_CONCRETE, "<gray><b>" + presentation.resetLabel() + "</b></gray>",
                "<gray>" + presentation.resetDescription() + "</gray>"));
        set(inv, 22, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to Rules.</gray>"));
        set(inv, 26, button(Material.BARRIER, "<red>Close</red>", "<gray>Return to the game.</gray>"));
        player.openInventory(inv);
    }

    private void openTrust(Player player, Claim claim, int requestedPage, ClaimNavigationContext navigation) {
        if (!valid(player, claim, navigation)) return;
        LinkedHashMap<String, String> trusted = trusted(claim);
        List<Map.Entry<String, String>> entries = List.copyOf(trusted.entrySet());
        int pages = Math.max(1, (entries.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
        int page = Math.max(1, Math.min(pages, requestedPage));
        ClaimNavigationContext context = navigation.withTrustPage(page);
        Holder holder = holder(Screen.TRUST, claim, page, "", "", 0, context, 54,
                title("trust", Map.of("claim", ClaimPresentation.label(claim)), "<dark_gray>Trusted Players <gray>• <white>%claim%"));
        Inventory inv = holder.inventory();
        fill(inv);
        int from = (page - 1) * CONTENT_SLOTS.length;
        for (int index = 0; index < CONTENT_SLOTS.length && from + index < entries.size(); index++) {
            Map.Entry<String, String> entry = entries.get(from + index);
            int slot = CONTENT_SLOTS[index];
            String display = displayTrustName(entry.getKey(), from + index + 1);
            holder.trustTargets.put(slot, new TrustTarget(entry.getKey(), display, entry.getValue()));
            set(inv, slot, named(Material.PLAYER_HEAD, Component.text(display, NamedTextColor.WHITE), List.of(
                    Component.text("Access: " + entry.getValue(), NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("Click to review access.", NamedTextColor.AQUA))));
        }
        if (entries.isEmpty()) {
            set(inv, 22, button(Material.PAPER, "<gray><b>No trusted players</b></gray>",
                    "<gray>Use Add Player to grant claim access.</gray>"));
        }
        if (page > 1) set(inv, 45, button(Material.ARROW, "<yellow>Previous</yellow>", "<gray>Page " + (page - 1) + "</gray>"));
        set(inv, 48, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to the claim dashboard.</gray>"));
        set(inv, 49, button(Material.EMERALD, "<green><b>Add Player</b></green>",
                "<gray>Enter a player name, then choose Access / Container / Build / Manage.</gray>"));
        set(inv, 51, button(Material.PAPER, "<white>Page " + page + " / " + pages + "</white>",
                "<gray>" + entries.size() + " trusted entr" + (entries.size() == 1 ? "y" : "ies") + ".</gray>"));
        set(inv, 52, button(Material.BARRIER, "<red>Close</red>", "<gray>Return to the game.</gray>"));
        if (page < pages) set(inv, 53, button(Material.ARROW, "<yellow>Next</yellow>", "<gray>Page " + (page + 1) + "</gray>"));
        player.openInventory(inv);
    }

    private void openTrustDetails(Player player, Claim claim, TrustTarget target, ClaimNavigationContext navigation) {
        if (!valid(player, claim, navigation)) return;
        String currentLevel = trusted(claim).get(target.entry());
        if (currentLevel == null) {
            plugin.messages().send(player, "claim-changed");
            openTrust(player, claim, navigation.trustPage(), navigation);
            return;
        }
        Holder holder = holder(Screen.TRUST_DETAILS, claim, 1, target.entry(), target.displayName(), 0, navigation, 27,
                title("trust-details", Map.of("player", target.displayName()), "<dark_gray>Trusted Player <gray>• <white>%player%"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 4, named(Material.PLAYER_HEAD, Component.text(target.displayName(), NamedTextColor.WHITE), List.of(
                Component.text("Current access: " + currentLevel, NamedTextColor.YELLOW),
                Component.text("This access belongs to the selected claim.", NamedTextColor.GRAY))));
        set(inv, 10, button(Material.NAME_TAG, "<aqua><b>Change Access</b></aqua>",
                "<gray>Choose Access, Container, Build or Manage.</gray>"));
        set(inv, 14, button(Material.RED_DYE, "<red><b>Remove Trust</b></red>",
                "<gray>Review a confirmation before revoking access.</gray>"));
        set(inv, 22, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to Trusted Players.</gray>"));
        set(inv, 26, button(Material.BARRIER, "<red>Close</red>", "<gray>Return to the game.</gray>"));
        player.openInventory(inv);
    }

    private void openTrustLevel(Player player, Claim claim, String targetEntry, String displayName,
                                String mode, ClaimNavigationContext navigation) {
        if (!valid(player, claim, navigation)) return;
        Holder holder = holder(Screen.TRUST_LEVEL, claim, 1, targetEntry, mode + "\n" + displayName, 0, navigation, 27,
                plugin.messages().raw("<dark_gray>Choose Access <gray>•</gray> <white>" + safe(displayName) + "</white>"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 10, button(Material.OAK_DOOR, "<aqua>Access</aqua>", "<gray>Buttons, doors and basic access.</gray>"));
        set(inv, 12, button(Material.CHEST, "<gold>Container</gold>", "<gray>Container and inventory access.</gray>"));
        set(inv, 14, button(Material.IRON_PICKAXE, "<yellow>Build</yellow>", "<gray>Place and break blocks.</gray>"));
        set(inv, 16, button(Material.NETHER_STAR, "<red>Manage</red>", "<gray>Full claim-management access.</gray>"));
        set(inv, 22, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return without changing access.</gray>"));
        set(inv, 26, button(Material.BARRIER, "<red>Close</red>", "<gray>Return to the game.</gray>"));
        player.openInventory(inv);
    }

    private void openTrustRemoveConfirm(Player player, Claim claim, TrustTarget target,
                                        ClaimNavigationContext navigation) {
        if (!valid(player, claim, navigation)) return;
        Holder holder = holder(Screen.TRUST_REMOVE_CONFIRM, claim, 1, target.entry(), target.displayName(), 0,
                navigation, 27, plugin.messages().raw("<dark_red>Confirm Trust Removal</dark_red>"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 4, named(Material.PLAYER_HEAD, Component.text(target.displayName(), NamedTextColor.WHITE), List.of(
                Component.text("Current access: " + target.level(), NamedTextColor.YELLOW),
                Component.text("Removing trust revokes this player's claim access.", NamedTextColor.GRAY))));
        set(inv, 11, button(Material.ARROW, "<yellow>Cancel</yellow>", "<gray>Keep the current access.</gray>"));
        set(inv, 15, button(Material.RED_CONCRETE, "<red><b>Remove Trust</b></red>",
                "<gray>Confirm that this player should lose access.</gray>"));
        set(inv, 22, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to player details.</gray>"));
        player.openInventory(inv);
    }

    public void openAutoClaim(Player player) {
        Holder holder = holder(Screen.AUTOCLAIM, null, 1, "", "", 0, ClaimNavigationContext.home(), 36,
                title("autoclaim", Map.of(), "<dark_gray>Create Claim"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 10, sizeItem("Small", actions.previewCreate(player, plugin.settings().claims().smallSize()), Material.LIME_CONCRETE));
        set(inv, 12, sizeItem("Medium", actions.previewCreate(player, plugin.settings().claims().mediumSize()), Material.YELLOW_CONCRETE));
        set(inv, 14, sizeItem("Large", actions.previewCreate(player, plugin.settings().claims().largeSize()), Material.ORANGE_CONCRETE));
        set(inv, 16, sizeItem("Use Available Blocks", actions.previewCreate(player, actions.maximumAffordableSquareSide(player)), Material.RED_CONCRETE));
        set(inv, 22, button(Material.NAME_TAG, "<aqua><b>Custom Size</b></aqua>",
                "<gray>Enter a side length in chat; the preview uses current claim blocks.</gray>"));
        set(inv, 31, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to Claims Home.</gray>"));
        set(inv, 35, button(Material.BARRIER, "<red>Close</red>", "<gray>Return to the game.</gray>"));
        player.openInventory(inv);
    }

    private void openResizeDirections(Player player, Claim claim, ClaimNavigationContext navigation) {
        if (!valid(player, claim, navigation) || claim.parent != null) return;
        Holder holder = holder(Screen.RESIZE_DIRECTION, claim, 1, "", "", 0, navigation, 27,
                plugin.messages().raw("<dark_gray>Resize <gray>•</gray> <white>Main Claim</white>"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 10, button(Material.BLUE_CONCRETE, "<aqua>North</aqua>", "<gray>Preview moving the north border.</gray>"));
        set(inv, 12, button(Material.BLUE_CONCRETE, "<aqua>South</aqua>", "<gray>Preview moving the south border.</gray>"));
        set(inv, 14, button(Material.BLUE_CONCRETE, "<aqua>East</aqua>", "<gray>Preview moving the east border.</gray>"));
        set(inv, 16, button(Material.BLUE_CONCRETE, "<aqua>West</aqua>", "<gray>Preview moving the west border.</gray>"));
        set(inv, 22, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to the claim dashboard.</gray>"));
        set(inv, 26, button(Material.BARRIER, "<red>Close</red>", "<gray>Return to the game.</gray>"));
        player.openInventory(inv);
    }

    private void openResize(Player player, Claim claim, ClaimActionService.Direction direction, int requestedOffset,
                            ClaimNavigationContext navigation) {
        if (!valid(player, claim, navigation) || claim.parent != null) return;
        ClaimActionService.ResizePreview preview = actions.preview(player, claim, direction, requestedOffset);
        Holder holder = holder(Screen.RESIZE, claim, 1, direction.name(), "", preview.offset(), navigation, 27,
                title("resize", Map.of("direction", prettyDirection(direction)), "<dark_gray>Resize <gray>• <white>%direction%"));
        Inventory inv = holder.inventory();
        fill(inv);
        set(inv, 1, button(Material.RED_CONCRETE, "<red><b>-MAX</b></red>", "<gray>Shrink as far as safely allowed.</gray>"));
        set(inv, 2, button(Material.RED_STAINED_GLASS_PANE, "<red>-5</red>", "<gray>Shrink five blocks.</gray>"));
        set(inv, 3, button(Material.PINK_STAINED_GLASS_PANE, "<red>-1</red>", "<gray>Shrink one block.</gray>"));
        String claimBlocks = preview.blockDelta() > 0
                ? preview.blockDelta() + " claim blocks required"
                : preview.blockDelta() < 0 ? Math.abs(preview.blockDelta()) + " claim blocks returned" : "No claim blocks changed";
        set(inv, 4, named(Material.BOOK, plugin.messages().raw("<yellow><b>Resize Preview</b></yellow>"), List.of(
                Component.text("Direction: " + prettyDirection(direction), NamedTextColor.WHITE),
                Component.text("Border change: " + signed(preview.offset()) + " block(s)", NamedTextColor.GRAY),
                Component.text("New size: " + preview.width() + " × " + preview.length(), NamedTextColor.GRAY),
                Component.text(claimBlocks, preview.blockDelta() > 0 ? NamedTextColor.RED : NamedTextColor.GREEN))));
        set(inv, 5, button(Material.LIME_STAINED_GLASS_PANE, "<green>+1</green>", "<gray>Expand one block.</gray>"));
        set(inv, 6, button(Material.GREEN_STAINED_GLASS_PANE, "<green>+5</green>", "<gray>Expand five blocks.</gray>"));
        set(inv, 7, button(Material.GREEN_CONCRETE, "<green><b>+MAX</b></green>", "<gray>Use the current maximum expansion.</gray>"));
        set(inv, 18, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Choose another direction.</gray>"));
        set(inv, 22, button(preview.valid() ? Material.LIME_WOOL : Material.GRAY_WOOL,
                preview.valid() ? "<green><b>Confirm Resize</b></green>" : "<gray>No Change</gray>",
                preview.valid() ? "<gray>Apply exactly the previewed boundary change.</gray>" : "<gray>Select a non-zero valid change first.</gray>"));
        set(inv, 26, button(Material.BARRIER, "<red>Close</red>", "<gray>Return to the game.</gray>"));
        player.openInventory(inv);
    }

    private void openAbandonConfirm(Player player, Claim claim, ClaimNavigationContext navigation) {
        if (!valid(player, claim, navigation) || claim.parent != null) return;
        ClaimPresentation.Card card = ClaimPresentation.card(claim, claims.safeId(claims.at(player.getLocation())) == claims.safeId(claim));
        Holder holder = holder(Screen.ABANDON_CONFIRM, claim, 1, "", "", 0, navigation, 27,
                title("confirm", Map.of(), "<dark_red>Confirm Claim Removal"));
        Inventory inv = holder.inventory();
        fill(inv);
        List<Component> summary = new ArrayList<>();
        summary.add(Component.text(card.world() + " • center " + card.centerX() + ", " + card.centerZ(), NamedTextColor.GRAY));
        summary.add(Component.text("Size: " + card.width() + " × " + card.length(), NamedTextColor.YELLOW));
        summary.add(Component.text("This claim will be removed.", NamedTextColor.RED));
        if (card.subdivisions() > 0) summary.add(Component.text("Its " + card.subdivisions() + " subdivision(s) are part of this claim.", NamedTextColor.RED));
        set(inv, 4, named(Material.TNT, plugin.messages().raw("<red><b>Abandon Main Claim?</b></red>"), summary));
        set(inv, 11, button(Material.ARROW, "<yellow>Cancel</yellow>", "<gray>Keep this claim.</gray>"));
        set(inv, 15, button(Material.RED_CONCRETE, "<red><b>Confirm Abandon</b></red>",
                "<gray>Remove this claim and return its claim blocks.</gray>"));
        set(inv, 22, button(Material.ARROW, "<yellow>Back</yellow>", "<gray>Return to the claim dashboard.</gray>"));
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
            case FLAGS -> flagsClick(player, holder, slot);
            case FLAG_DETAILS -> flagDetailsClick(player, holder, slot);
            case TRUST -> trustClick(player, holder, slot);
            case TRUST_DETAILS -> trustDetailsClick(player, holder, slot);
            case TRUST_LEVEL -> trustLevelClick(player, holder, slot);
            case TRUST_REMOVE_CONFIRM -> trustRemoveConfirmClick(player, holder, slot);
            case AUTOCLAIM -> autoClaimClick(player, holder, slot);
            case RESIZE_DIRECTION -> resizeDirectionClick(player, holder, slot);
            case RESIZE -> resizeClick(player, holder, slot);
            case ABANDON_CONFIRM -> abandonConfirmClick(player, holder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < size)) event.setCancelled(true);
    }

    private void hubClick(Player player, int slot) {
        if (slot == 10) {
            Claim claim = claims.at(player.getLocation());
            if (claim == null) plugin.messages().send(player, "no-claim");
            else if (!claims.canManage(player, claim)) plugin.messages().send(player, claim.isAdminClaim() ? "admin-claim-denied" : "not-owner");
            else openDashboard(player, claim, ClaimNavigationContext.home());
        } else if (slot == 12 && plugin.settings().features().claimList()) openClaims(player, 1);
        else if (slot == 14 && plugin.settings().features().autoClaim()) openAutoClaim(player);
        else if (slot == 16 && plugin.settings().features().shovel()) actions.giveShovel(player);
        else if (slot == 22) player.closeInventory();
    }

    private void claimsClick(Player player, Holder holder, int slot) {
        ClaimTarget target = holder.claimTargets.get(slot);
        if (target != null) {
            Claim claim = validateTarget(player, target, holder.navigation);
            if (claim != null) openDashboard(player, claim, ClaimNavigationContext.claims(holder.page));
            return;
        }
        if (slot == 45 && holder.page > 1) openClaims(player, holder.page - 1);
        else if (slot == 48) openHub(player);
        else if (slot == 52) player.closeInventory();
        else if (slot == 53) openClaims(player, holder.page + 1);
    }

    private void areasClick(Player player, Holder holder, int slot) {
        Claim parent = revalidate(player, holder);
        if (parent == null) return;
        ClaimTarget target = holder.claimTargets.get(slot);
        if (target != null) {
            Claim selected = validateTarget(player, target, holder.navigation);
            if (selected != null) openDashboard(player, selected, holder.navigation.selectArea());
            return;
        }
        if (slot == 45 && holder.page > 1) openAreasPage(player, parent, holder.navigation, holder.page - 1);
        else if (slot == 48) {
            Claim back = claims.byId(holder.navigation.areasReturnClaimId());
            if (back != null && claims.canManage(player, back)) openDashboard(player, back, holder.navigation.leaveAreas());
            else safeScreen(player, holder.navigation);
        } else if (slot == 52) player.closeInventory();
        else if (slot == 53) openAreasPage(player, parent, holder.navigation, holder.page + 1);
    }

    private void openAreasPage(Player player, Claim parent, ClaimNavigationContext navigation, int page) {
        Claim selected = claims.byId(navigation.areasReturnClaimId());
        if (selected == null) selected = parent;
        openAreas(player, selected, navigation.withAreasPage(page));
    }

    private void dashboardClick(Player player, Holder holder, int slot) {
        Claim claim = revalidate(player, holder);
        if (claim == null) return;
        if (slot == 10 && plugin.settings().features().flags()) openFlags(player, claim, holder.navigation);
        else if (slot == 12 && plugin.settings().features().trust()) openTrust(player, claim, 1, holder.navigation);
        else if (slot == 14) openAreas(player, claim, holder.navigation.beginAreas(claims.safeId(claims.parentOf(claim)), claims.safeId(claim)));
        else if (slot == 16 && plugin.settings().features().resize() && claim.parent == null) openResizeDirections(player, claim, holder.navigation);
        else if (slot == 28 && plugin.settings().features().teleport()) { player.closeInventory(); teleports.request(player, claim); }
        else if (slot == 30 && plugin.settings().features().visualizer()) { player.closeInventory(); visualizer.show(player, claim); }
        else if (slot == 34 && plugin.settings().features().abandon() && claim.parent == null) openAbandonConfirm(player, claim, holder.navigation);
        else if (slot == 48) dashboardBack(player, claim, holder.navigation);
        else if (slot == 52) player.closeInventory();
    }

    private void dashboardBack(Player player, Claim claim, ClaimNavigationContext navigation) {
        if (navigation.dashboardFromAreas() && navigation.areasParentClaimId() >= 0) {
            Claim parent = claims.byId(navigation.areasParentClaimId());
            if (parent != null) openAreas(player, claim, navigation.withAreasPage(navigation.areasPage()));
            else safeScreen(player, navigation);
        } else if (navigation.origin() == ClaimNavigationContext.Origin.CLAIMS && plugin.settings().features().claimList()) {
            openClaims(player, navigation.claimsPage());
        } else {
            openHub(player);
        }
    }

    private void flagsClick(Player player, Holder holder, int slot) {
        Claim claim = revalidate(player, holder);
        if (claim == null) return;
        ClaimFlag flag = holder.flagTargets.get(slot);
        if (flag != null) openFlagDetails(player, claim, flag, holder.navigation);
        else if (slot == 48) openDashboard(player, claim, holder.navigation);
        else if (slot == 52) player.closeInventory();
    }

    private void flagDetailsClick(Player player, Holder holder, int slot) {
        Claim claim = revalidate(player, holder);
        if (claim == null) return;
        ClaimFlag flag = ClaimFlag.parseStableId(holder.data).orElse(null);
        if (flag == null) { safeScreen(player, holder.navigation); return; }
        if (slot == 22) { openFlags(player, claim, holder.navigation); return; }
        if (slot == 26) { player.closeInventory(); return; }
        FlagOverride value;
        if (slot == 10) value = FlagPresentation.allowOverride();
        else if (slot == 12) value = FlagPresentation.blockOverride();
        else if (slot == 14) value = FlagPresentation.resetOverride();
        else return;
        if (!holder.submitOnce()) return;
        FlagChangeResult result = flags.set(player, claim, flag, value, "GUI");
        if (result.status() == FlagChangeResult.Status.PERSISTENCE_FAILED) {
            plugin.messages().send(player, "persistence-error");
        } else if (result.status() == FlagChangeResult.Status.INVALID_CLAIM || result.status() == FlagChangeResult.Status.NOT_AUTHORIZED) {
            plugin.messages().send(player, "claim-changed");
            safeScreen(player, holder.navigation);
            return;
        } else if (result.success()) {
            plugin.messages().send(player, "flag-changed", Map.of(
                    "flag", plugin.messages().flagName(flag),
                    "state", store.effective(claim, flag) ? "Blocked" : "Allowed",
                    "area", claims.areaLabel(claim)));
        }
        openFlagDetails(player, claim, flag, holder.navigation);
    }

    private void trustClick(Player player, Holder holder, int slot) {
        Claim claim = revalidate(player, holder);
        if (claim == null) return;
        TrustTarget target = holder.trustTargets.get(slot);
        if (target != null) { openTrustDetails(player, claim, target, holder.navigation); return; }
        if (slot == 45 && holder.page > 1) openTrust(player, claim, holder.page - 1, holder.navigation);
        else if (slot == 48) openDashboard(player, claim, holder.navigation);
        else if (slot == 49 && holder.submitOnce()) beginAddTrustPrompt(player, claim, holder);
        else if (slot == 52) player.closeInventory();
        else if (slot == 53) openTrust(player, claim, holder.page + 1, holder.navigation);
    }

    private void beginAddTrustPrompt(Player player, Claim claim, Holder holder) {
        long claimId = claims.safeId(claim);
        ClaimPresentation.Token token = ClaimPresentation.Token.from(claim);
        ClaimNavigationContext navigation = holder.navigation;
        prompts.start(player, "prompt-player", (actor, input) -> {
            Claim current = revalidatePrompt(actor, claimId, token, navigation);
            if (current == null) return;
            if (!input.matches("[A-Za-z0-9_]{1,16}")) {
                plugin.messages().send(actor, "prompt-invalid");
                openTrust(actor, current, navigation.trustPage(), navigation);
                return;
            }
            openTrustLevel(actor, current, input, input, "ADD", navigation);
        });
    }

    private void trustDetailsClick(Player player, Holder holder, int slot) {
        Claim claim = revalidate(player, holder);
        if (claim == null) return;
        String level = trusted(claim).get(holder.data);
        if (level == null) {
            plugin.messages().send(player, "claim-changed");
            openTrust(player, claim, holder.navigation.trustPage(), holder.navigation);
            return;
        }
        TrustTarget target = new TrustTarget(holder.data, holder.aux, level);
        if (slot == 10) openTrustLevel(player, claim, target.entry(), target.displayName(), "CHANGE", holder.navigation);
        else if (slot == 14) openTrustRemoveConfirm(player, claim, target, holder.navigation);
        else if (slot == 22) openTrust(player, claim, holder.navigation.trustPage(), holder.navigation);
        else if (slot == 26) player.closeInventory();
    }

    private void trustLevelClick(Player player, Holder holder, int slot) {
        Claim claim = revalidate(player, holder);
        if (claim == null) return;
        String[] modeAndName = holder.aux.split("\\n", 2);
        String mode = modeAndName.length > 0 ? modeAndName[0] : "ADD";
        String displayName = modeAndName.length > 1 ? modeAndName[1] : holder.data;
        if (slot == 22) {
            if ("CHANGE".equals(mode)) {
                String level = trusted(claim).get(holder.data);
                if (level != null) openTrustDetails(player, claim, new TrustTarget(holder.data, displayName, level), holder.navigation);
                else openTrust(player, claim, holder.navigation.trustPage(), holder.navigation);
            } else openTrust(player, claim, holder.navigation.trustPage(), holder.navigation);
            return;
        }
        if (slot == 26) { player.closeInventory(); return; }
        ClaimPermission permission;
        String label;
        if (slot == 10) { permission = ClaimPermission.Access; label = "Access"; }
        else if (slot == 12) { permission = ClaimPermission.Inventory; label = "Container"; }
        else if (slot == 14) { permission = ClaimPermission.Build; label = "Build"; }
        else if (slot == 16) { permission = ClaimPermission.Manage; label = "Manage"; }
        else return;
        if (!holder.submitOnce()) return;
        UUID targetId = targetUuid(holder.data);
        if (targetId == null) {
            plugin.messages().send(player, "prompt-invalid");
            openTrust(player, claim, holder.navigation.trustPage(), holder.navigation);
            return;
        }
        actions.grantTrust(player, claim, targetId, permission, displayName, label);
        openTrust(player, claim, holder.navigation.trustPage(), holder.navigation);
    }

    private void trustRemoveConfirmClick(Player player, Holder holder, int slot) {
        Claim claim = revalidate(player, holder);
        if (claim == null) return;
        String level = trusted(claim).get(holder.data);
        TrustTarget target = new TrustTarget(holder.data, holder.aux, level == null ? "Unknown" : level);
        if (slot == 11 || slot == 22) { openTrustDetails(player, claim, target, holder.navigation); return; }
        if (slot != 15 || !holder.submitOnce()) return;
        if (level == null) {
            plugin.messages().send(player, "claim-changed");
            openTrust(player, claim, holder.navigation.trustPage(), holder.navigation);
            return;
        }
        actions.removeTrust(player, claim, holder.data, holder.aux);
        openTrust(player, claim, holder.navigation.trustPage(), holder.navigation);
    }

    private void autoClaimClick(Player player, Holder holder, int slot) {
        if (slot == 31) { openHub(player); return; }
        if (slot == 35) { player.closeInventory(); return; }
        if (slot == 22) {
            if (!holder.submitOnce()) return;
            prompts.start(player, "prompt-size", (actor, input) -> {
                int parsed;
                try { parsed = Integer.parseInt(input); }
                catch (NumberFormatException error) { plugin.messages().send(actor, "prompt-invalid"); openAutoClaim(actor); return; }
                ClaimActionService.CreatePreview preview = actions.previewCreate(actor, parsed);
                if (!preview.possible()) { plugin.messages().send(actor, "claim-create-failed"); openAutoClaim(actor); return; }
                Claim created = actions.createSquare(actor, parsed);
                if (created != null) openDashboard(actor, created, ClaimNavigationContext.home()); else openAutoClaim(actor);
            });
            return;
        }
        int requested;
        if (slot == 10) requested = plugin.settings().claims().smallSize();
        else if (slot == 12) requested = plugin.settings().claims().mediumSize();
        else if (slot == 14) requested = plugin.settings().claims().largeSize();
        else if (slot == 16) requested = actions.maximumAffordableSquareSide(player);
        else return;
        if (!holder.submitOnce()) return;
        ClaimActionService.CreatePreview preview = actions.previewCreate(player, requested);
        if (!preview.possible()) { plugin.messages().send(player, "claim-create-failed"); openAutoClaim(player); return; }
        Claim created = actions.createSquare(player, requested);
        if (created != null) openDashboard(player, created, ClaimNavigationContext.home()); else openAutoClaim(player);
    }

    private void resizeDirectionClick(Player player, Holder holder, int slot) {
        Claim claim = revalidate(player, holder);
        if (claim == null || claim.parent != null) return;
        ClaimActionService.Direction direction = switch (slot) {
            case 10 -> ClaimActionService.Direction.NORTH;
            case 12 -> ClaimActionService.Direction.SOUTH;
            case 14 -> ClaimActionService.Direction.EAST;
            case 16 -> ClaimActionService.Direction.WEST;
            default -> null;
        };
        if (direction != null) openResize(player, claim, direction, 0, holder.navigation);
        else if (slot == 22) openDashboard(player, claim, holder.navigation);
        else if (slot == 26) player.closeInventory();
    }

    private void resizeClick(Player player, Holder holder, int slot) {
        Claim claim = revalidate(player, holder);
        if (claim == null || claim.parent != null) return;
        ClaimActionService.Direction direction;
        try { direction = ClaimActionService.Direction.valueOf(holder.data); }
        catch (IllegalArgumentException error) { safeScreen(player, holder.navigation); return; }
        ClaimActionService.ResizePreview preview = actions.preview(player, claim, direction, holder.offset);
        if (slot == 1) openResize(player, claim, direction, -preview.maxShrink(), holder.navigation);
        else if (slot == 2) openResize(player, claim, direction, holder.offset - 5, holder.navigation);
        else if (slot == 3) openResize(player, claim, direction, holder.offset - 1, holder.navigation);
        else if (slot == 5) openResize(player, claim, direction, holder.offset + 1, holder.navigation);
        else if (slot == 6) openResize(player, claim, direction, holder.offset + 5, holder.navigation);
        else if (slot == 7) openResize(player, claim, direction, preview.maxExpand(), holder.navigation);
        else if (slot == 18) openResizeDirections(player, claim, holder.navigation);
        else if (slot == 22 && preview.valid() && holder.submitOnce()) {
            Claim resized = actions.resize(player, claim, direction, holder.offset);
            if (resized != null) openDashboard(player, resized, holder.navigation);
            else safeScreen(player, holder.navigation);
        } else if (slot == 26) player.closeInventory();
    }

    private void abandonConfirmClick(Player player, Holder holder, int slot) {
        Claim claim = revalidate(player, holder);
        if (claim == null || claim.parent != null) return;
        if (slot == 11 || slot == 22) { openDashboard(player, claim, holder.navigation); return; }
        if (slot == 15 && holder.submitOnce()) {
            player.closeInventory();
            if (actions.delete(player, claim)) {
                if (holder.navigation.origin() == ClaimNavigationContext.Origin.CLAIMS && plugin.settings().features().claimList()) {
                    openClaims(player, holder.navigation.claimsPage());
                } else openHub(player);
            }
        }
    }

    private Claim revalidate(Player player, Holder holder) {
        Claim claim = claims.byId(holder.claimId);
        if (claim == null || !claim.inDataStore) {
            plugin.messages().send(player, "claim-missing");
            safeScreen(player, holder.navigation);
            return null;
        }
        if (holder.token != null && !holder.token.matches(claim)) {
            plugin.messages().send(player, "claim-changed");
            safeScreen(player, holder.navigation);
            return null;
        }
        if (!claims.canManage(player, claim)) {
            plugin.messages().send(player, claim.isAdminClaim() ? "admin-claim-denied" : "not-owner");
            safeScreen(player, holder.navigation);
            return null;
        }
        return claim;
    }

    private Claim revalidatePrompt(Player player, long claimId, ClaimPresentation.Token token,
                                   ClaimNavigationContext navigation) {
        Claim claim = claims.byId(claimId);
        if (claim == null || !claim.inDataStore || !token.matches(claim) || !claims.canManage(player, claim)) {
            plugin.messages().send(player, "claim-changed");
            safeScreen(player, navigation);
            return null;
        }
        return claim;
    }

    private Claim validateTarget(Player player, ClaimTarget target, ClaimNavigationContext navigation) {
        Claim claim = claims.byId(target.claimId());
        if (claim == null || !target.token().matches(claim)) {
            plugin.messages().send(player, "claim-changed");
            safeScreen(player, navigation);
            return null;
        }
        if (!claims.canManage(player, claim)) {
            plugin.messages().send(player, claim.isAdminClaim() ? "admin-claim-denied" : "not-owner");
            safeScreen(player, navigation);
            return null;
        }
        return claim;
    }

    private boolean valid(Player player, Claim claim, ClaimNavigationContext navigation) {
        if (claim == null || !claim.inDataStore || claim.getID() == null) {
            plugin.messages().send(player, "claim-missing");
            safeScreen(player, navigation);
            return false;
        }
        if (!claims.canManage(player, claim)) {
            plugin.messages().send(player, claim.isAdminClaim() ? "admin-claim-denied" : "not-owner");
            safeScreen(player, navigation);
            return false;
        }
        return true;
    }

    private void safeScreen(Player player, ClaimNavigationContext navigation) {
        if (navigation != null && navigation.origin() == ClaimNavigationContext.Origin.CLAIMS
                && plugin.settings().features().claimList()) openClaims(player, navigation.claimsPage());
        else openHub(player);
    }

    private FlagPresentation flagPresentation(Claim claim, ClaimFlag flag) {
        return FlagPresentation.of(store.effective(claim, flag), store.explicitValue(claim, flag),
                claim.parent != null, plugin.settings().flags().subclaimsInheritParent());
    }

    private LinkedHashMap<String, String> trusted(Claim claim) {
        ArrayList<String> build = new ArrayList<>(), inventory = new ArrayList<>(), access = new ArrayList<>(), manage = new ArrayList<>();
        claim.getPermissions(build, inventory, access, manage);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String value : access) result.put(value, "Access");
        for (String value : inventory) result.put(value, "Container");
        for (String value : build) result.put(value, "Build");
        for (String value : manage) result.put(value, "Manage");
        return result;
    }

    private String displayTrustName(String entry, int ordinal) {
        try {
            UUID uuid = UUID.fromString(entry);
            Player online = Bukkit.getPlayer(uuid);
            return online != null ? online.getName() : "Offline Player " + ordinal;
        } catch (IllegalArgumentException ignored) {
            return entry == null || entry.isBlank() ? "Trusted Player " + ordinal : safe(entry);
        }
    }

    private UUID targetUuid(String entry) {
        try { return UUID.fromString(entry); }
        catch (IllegalArgumentException ignored) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(entry);
            return target.getUniqueId();
        }
    }

    private ItemStack claimItem(Player player, Claim claim, String actionLore) {
        long standingId = claims.safeId(claims.at(player.getLocation()));
        ClaimPresentation.Card card = ClaimPresentation.card(claim, claims.safeId(claim) == standingId);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(card.relation(), NamedTextColor.AQUA));
        lore.add(Component.text(card.world() + " • center " + card.centerX() + ", " + card.centerZ(), NamedTextColor.GRAY));
        lore.add(Component.text("Size: " + card.width() + " × " + card.length(), NamedTextColor.YELLOW));
        lore.add(Component.text("Area: " + card.area() + " blocks", NamedTextColor.WHITE));
        if (card.subdivisions() > 0) lore.add(Component.text("Subdivisions: " + card.subdivisions(), NamedTextColor.AQUA));
        if (card.current()) lore.add(Component.text("You are standing here.", NamedTextColor.GREEN));
        lore.add(Component.empty());
        lore.add(plugin.messages().raw(actionLore));
        return named(claim.parent == null ? Material.GRASS_BLOCK : Material.OAK_FENCE,
                plugin.messages().raw("<gradient:#55FF55:#41C902><b>" + safe(card.label()) + "</b></gradient>"), lore);
    }

    private ItemStack sizeItem(String label, ClaimActionService.CreatePreview preview, Material material) {
        Material actual = preview.possible() ? material : Material.BARRIER;
        String color = preview.possible() ? "<green>" : "<red>";
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Size: " + preview.side() + " × " + preview.side(), NamedTextColor.WHITE));
        lore.add(Component.text("Required: " + preview.requiredBlocks() + " claim blocks", NamedTextColor.GRAY));
        lore.add(Component.text("Available: " + preview.availableBlocks() + " claim blocks", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(preview.possible() ? "Available to create." : "Not enough claim blocks.",
                preview.possible() ? NamedTextColor.GREEN : NamedTextColor.RED));
        return named(actual, plugin.messages().raw(color + "<b>" + label + "</b>" + (preview.possible() ? "</green>" : "</red>")), lore);
    }

    private Component title(String key, Map<String, String> values, String fallback) {
        String raw = plugin.getConfig().getString("gui.titles." + key, fallback);
        if (raw == null) raw = fallback;
        raw = raw.replace("#%claim%", "%claim%");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            raw = raw.replace("%" + entry.getKey() + "%", safe(entry.getValue()));
        }
        return plugin.messages().raw(raw);
    }

    private Holder holder(Screen screen, Claim claim, int page, String data, String aux, int offset,
                          ClaimNavigationContext navigation, int size, Component title) {
        long claimId = claims.safeId(claim);
        ClaimPresentation.Token token = claim == null || claim.getID() == null ? null : ClaimPresentation.Token.from(claim);
        Holder holder = new Holder(screen, claimId, page, data, aux, offset,
                navigation == null ? ClaimNavigationContext.home() : navigation, token);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.attach(inventory);
        return holder;
    }

    private void fill(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
    }

    private void set(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
    }

    private ItemStack button(Material material, String name, String lore) {
        return named(material, plugin.messages().raw(name), List.of(plugin.messages().raw(lore)));
    }

    private ItemStack named(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private String teleportCancelSummary() {
        boolean move = plugin.settings().claims().teleportCancelMovement();
        boolean damage = plugin.settings().claims().teleportCancelDamage();
        if (move && damage) return "movement or damage";
        if (move) return "movement";
        if (damage) return "damage";
        return "none";
    }

    private String dashboardBackLore(ClaimNavigationContext navigation) {
        if (navigation.dashboardFromAreas()) return "<gray>Return to Claim Areas.</gray>";
        if (navigation.origin() == ClaimNavigationContext.Origin.CLAIMS) return "<gray>Return to My Claims page " + navigation.claimsPage() + ".</gray>";
        return "<gray>Return to Claims Home.</gray>";
    }

    private static String behavior(String value) {
        return "Blocked".equals(value) ? "<red><b>Blocked</b></red>" : "<green><b>Allowed</b></green>";
    }

    private static String prettyDirection(ClaimActionService.Direction direction) {
        String lower = direction.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
    private static String safe(String value) { return value == null ? "" : value.replace("<", "").replace(">", ""); }

    private record ClaimTarget(long claimId, ClaimPresentation.Token token) {}
    private record TrustTarget(String entry, String displayName, String level) {}

    private static final class Holder implements InventoryHolder {
        private final Screen screen;
        private final long claimId;
        private final int page;
        private final String data;
        private final String aux;
        private final int offset;
        private final ClaimNavigationContext navigation;
        private final ClaimPresentation.Token token;
        private final Map<Integer, ClaimTarget> claimTargets = new HashMap<>();
        private final Map<Integer, ClaimFlag> flagTargets = new HashMap<>();
        private final Map<Integer, TrustTarget> trustTargets = new HashMap<>();
        private Inventory inventory;
        private boolean submitted;

        private Holder(Screen screen, long claimId, int page, String data, String aux, int offset,
                       ClaimNavigationContext navigation, ClaimPresentation.Token token) {
            this.screen = screen;
            this.claimId = claimId;
            this.page = page;
            this.data = data;
            this.aux = aux;
            this.offset = offset;
            this.navigation = navigation;
            this.token = token;
        }

        private boolean submitOnce() {
            if (submitted) return false;
            submitted = true;
            return true;
        }

        private void attach(Inventory inventory) { this.inventory = inventory; }
        @Override public @NotNull Inventory getInventory() { return Objects.requireNonNull(inventory); }
        private Inventory inventory() { return getInventory(); }
    }
}
