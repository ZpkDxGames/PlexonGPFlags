package com.plexon.gpflags;

import com.plexon.gpflags.api.PlexonGPFlagsAPI;
import com.plexon.gpflags.api.PlexonGPFlagsApiImpl;
import com.plexon.gpflags.claim.ClaimService;
import com.plexon.gpflags.command.PlexonGPFlagsCommand;
import com.plexon.gpflags.compat.LegacyClaimFlagsBridge;
import com.plexon.gpflags.config.RuntimeSettings;
import com.plexon.gpflags.flag.FlagService;
import com.plexon.gpflags.flag.FlagStore;
import com.plexon.gpflags.gui.MenuService;
import com.plexon.gpflags.protection.ProtectionListener;
import com.plexon.gpflags.service.ClaimActionService;
import com.plexon.gpflags.service.PromptService;
import com.plexon.gpflags.service.TeleportService;
import com.plexon.gpflags.service.VisualizerService;
import com.plexon.gpflags.text.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class PlexonGPFlags extends JavaPlugin implements Listener {
    private RuntimeSettings settings;
    private Messages messages;
    private ClaimService claims;
    private FlagStore store;
    private FlagService flags;
    private ClaimActionService actions;
    private PromptService prompts;
    private TeleportService teleports;
    private VisualizerService visualizer;
    private MenuService menus;
    private PlexonGPFlagsAPI api;
    private LegacyClaimFlagsBridge legacyApi;
    private boolean apiRegistered;
    private boolean legacyApiRegistered;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            settings = RuntimeSettings.load(getConfig(), getLogger());
            messages = new Messages(this);
            claims = new ClaimService();
            store = new FlagStore(this, claims, () -> settings);
            flags = new FlagService(this, claims, store);
            actions = new ClaimActionService(this, claims);
            prompts = new PromptService(this);
            teleports = new TeleportService(this, claims);
            visualizer = new VisualizerService(this, claims);
            menus = new MenuService(this, claims, store, flags, actions, prompts, teleports, visualizer);

            ProtectionListener protection = new ProtectionListener(this, claims, store);
            getServer().getPluginManager().registerEvents(protection, this);
            getServer().getPluginManager().registerEvents(prompts, this);
            getServer().getPluginManager().registerEvents(teleports, this);
            getServer().getPluginManager().registerEvents(visualizer, this);
            getServer().getPluginManager().registerEvents(menus, this);
            getServer().getPluginManager().registerEvents(this, this);

            PlexonGPFlagsCommand handler = new PlexonGPFlagsCommand(this, claims, store, flags, menus);
            bind("gpflags", handler);
            bind("gpegui-reload", handler);

            api = new PlexonGPFlagsApiImpl(claims, store, flags);
            getServer().getServicesManager().register(PlexonGPFlagsAPI.class, api, this, ServicePriority.Normal);
            apiRegistered = true;

            legacyApi = new LegacyClaimFlagsBridge(this, claims, store, flags);
            getServer().getServicesManager().register(net.plexon.claimflags.api.PlexonClaimFlagsAPI.class,
                    legacyApi, this, ServicePriority.Normal);
            getServer().getPluginManager().registerEvents(legacyApi, this);
            legacyApiRegistered = true;

            getLogger().info("PlexonGPFlags " + getPluginMeta().getVersion() + " enabled. Core mode: " + coreMode());
        } catch (RuntimeException | LinkageError error) {
            getLogger().log(Level.SEVERE, "PlexonGPFlags failed to initialize safely.", error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (menus != null) menus.closeAll();
        if (prompts != null) prompts.cancelAll();
        if (teleports != null) teleports.cancelAll();
        if (visualizer != null) visualizer.cancelAll();
        if (store != null) store.save();
        getServer().getServicesManager().unregisterAll(this);
        apiRegistered = false;
        legacyApiRegistered = false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!settings.features().onboarding() || event.getPlayer().hasPlayedBefore()) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (event.getPlayer().isOnline()) messages.send(event.getPlayer(), "onboarding");
        }, 60L);
    }

    public boolean reloadPlugin() {
        reloadConfig();
        settings = RuntimeSettings.load(getConfig(), getLogger());
        messages.reload();
        boolean loaded = store.load();
        teleports.cancelAll();
        visualizer.cancelAll();
        menus.reload();
        return loaded;
    }

    private void bind(String name, PlexonGPFlagsCommand handler) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException("Command /" + name + " is missing from plugin.yml");
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    public String coreMode() {
        var core = getServer().getPluginManager().getPlugin("PlexonCore");
        if (core == null) return "STANDALONE";
        return core.isEnabled() ? "CORE_DETECTED" : "CORE_DISABLED";
    }

    public RuntimeSettings settings() { return settings; }
    public Messages messages() { return messages; }
    public TeleportService teleports() { return teleports; }
    public boolean publicApiRegistered() { return apiRegistered; }
    public boolean legacyApiRegistered() { return legacyApiRegistered; }
}
