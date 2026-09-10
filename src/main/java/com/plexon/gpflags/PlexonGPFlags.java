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
import com.plexon.gpflags.gui.MenuSessionGuard;
import com.plexon.gpflags.integration.core.CoreBridge;
import com.plexon.gpflags.integration.core.CoreBridgeFactory;
import com.plexon.gpflags.protection.ProtectionListener;
import com.plexon.gpflags.service.ClaimActionService;
import com.plexon.gpflags.service.PromptService;
import com.plexon.gpflags.service.TeleportService;
import com.plexon.gpflags.service.VisualizerService;
import com.plexon.gpflags.text.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
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
    private MenuSessionGuard menuSessions;
    private ProtectionListener protection;
    private PlexonGPFlagsAPI api;
    private LegacyClaimFlagsBridge legacyApi;
    private CoreBridge coreBridge;
    private boolean apiRegistered;
    private boolean legacyApiRegistered;
    private long configurationGeneration = 1L;
    private String lastReloadResult = "STARTUP";

    @Override public void onEnable() {
        coreBridge = CoreBridgeFactory.resolve(this); coreBridge.registerStarting();
        try {
            refuseActiveDeprecatedAddon(); saveDefaultConfig(); settings = RuntimeSettings.load(getConfig(), getLogger()); messages = new Messages(this); claims = new ClaimService();
            store = new FlagStore(this, claims, () -> settings); flags = new FlagService(this, claims, store); actions = new ClaimActionService(this, claims); prompts = new PromptService(this);
            teleports = new TeleportService(this, claims); visualizer = new VisualizerService(this, claims); menus = new MenuService(this, claims, store, flags, actions, prompts, teleports, visualizer);
            menuSessions = new MenuSessionGuard(this); protection = new ProtectionListener(this, claims, store);
            getServer().getPluginManager().registerEvents(menuSessions, this); getServer().getPluginManager().registerEvents(protection, this); getServer().getPluginManager().registerEvents(prompts, this);
            getServer().getPluginManager().registerEvents(teleports, this); getServer().getPluginManager().registerEvents(visualizer, this); getServer().getPluginManager().registerEvents(menus, this); getServer().getPluginManager().registerEvents(this, this);
            PlexonGPFlagsCommand handler = new PlexonGPFlagsCommand(this, claims, store, flags, menus); bind("gpflags", handler); bind("gpegui-reload", handler);
            api = new PlexonGPFlagsApiImpl(claims, store, flags); getServer().getServicesManager().register(PlexonGPFlagsAPI.class, api, this, ServicePriority.Normal); apiRegistered = true;
            legacyApi = new LegacyClaimFlagsBridge(this, claims, store, flags); getServer().getServicesManager().register(net.plexon.claimflags.api.PlexonClaimFlagsAPI.class, legacyApi, this, ServicePriority.Normal); getServer().getPluginManager().registerEvents(legacyApi, this); legacyApiRegistered = true;
            coreBridge.markReady("GriefPrevention claim flags, migration bridge, APIs and persistence ready"); getLogger().info("PlexonGPFlags " + getPluginMeta().getVersion() + " enabled. Core mode: " + coreMode());
        } catch (RuntimeException | LinkageError error) {
            if (coreBridge != null) coreBridge.markFailed("Startup failed: " + error.getClass().getSimpleName());
            getLogger().log(Level.SEVERE, "PlexonGPFlags failed to initialize safely.", error); getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override public void onDisable() {
        if (menus != null) menus.closeAll(); if (prompts != null) prompts.cancelAll(); if (teleports != null) teleports.cancelAll(); if (visualizer != null) visualizer.cancelAll(); if (store != null) store.save();
        getServer().getServicesManager().unregisterAll(this); if (coreBridge != null) coreBridge.unregister(); apiRegistered = false; legacyApiRegistered = false;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (!settings.features().onboarding() || event.getPlayer().hasPlayedBefore()) return;
        Bukkit.getScheduler().runTaskLater(this, () -> { if (event.getPlayer().isOnline()) messages.send(event.getPlayer(), "onboarding"); }, 60L);
    }

    public boolean reloadPlugin() {
        if (!Bukkit.isPrimaryThread()) { lastReloadResult = "FAILED: reload requested off primary thread"; return false; }
        RuntimeSettings previousSettings = settings; FlagStore.Candidate previousStore = store.snapshot();
        try {
            YamlConfiguration candidateConfig = new YamlConfiguration(); File configFile = new File(getDataFolder(), "config.yml"); if (configFile.isFile()) candidateConfig.load(configFile);
            RuntimeSettings nextSettings = RuntimeSettings.load(candidateConfig, getLogger()); FlagStore.Candidate nextStore = store.prepareCandidate();
            menus.closeAll(); reloadConfig(); settings = nextSettings; store.applyCandidate(nextStore); messages.reload(); teleports.cancelAll(); visualizer.cancelAll(); menus.reload(); configurationGeneration++; lastReloadResult = "SUCCESS"; return true;
        } catch (IOException | InvalidConfigurationException | RuntimeException error) {
            settings = previousSettings; store.applyCandidate(previousStore); lastReloadResult = "FAILED: " + error.getClass().getSimpleName() + ": " + (error.getMessage() == null ? "no detail" : error.getMessage());
            getLogger().log(Level.WARNING, "Rejected PlexonGPFlags reload; previous runtime remains active.", error); return false;
        }
    }

    private void refuseActiveDeprecatedAddon() {
        for (String name : new String[]{"PlexonClaimFlags", "GriefPreventionAddon"}) { Plugin legacy = getServer().getPluginManager().getPlugin(name); if (legacy != null && legacy.isEnabled()) throw new IllegalStateException("Deprecated " + name + " is active. Remove it before starting PlexonGPFlags."); }
    }
    private void bind(String name, PlexonGPFlagsCommand handler) { PluginCommand command = getCommand(name); if (command == null) throw new IllegalStateException("Command /" + name + " is missing from plugin.yml"); command.setExecutor(handler); command.setTabCompleter(handler); }
    public String coreMode() { return coreBridge == null ? "STANDALONE" : coreBridge.mode(); }
    public RuntimeSettings settings() { return settings; }
    public Messages messages() { return messages; }
    public TeleportService teleports() { return teleports; }
    public FlagService flags() { return flags; }
    public ProtectionListener protection() { return protection; }
    public long staleGuiActionCount() { return menuSessions == null ? 0L : menuSessions.staleActionCount(); }
    public long configurationGeneration() { return configurationGeneration; }
    public String lastReloadResult() { return lastReloadResult; }
    public boolean publicApiRegistered() { return apiRegistered; }
    public boolean legacyApiRegistered() { return legacyApiRegistered; }
}
