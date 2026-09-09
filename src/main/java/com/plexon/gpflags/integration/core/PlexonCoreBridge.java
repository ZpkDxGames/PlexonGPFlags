package com.plexon.gpflags.integration.core;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleDescriptor;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import java.time.Instant;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlexonCoreBridge implements CoreBridge {
    private static final Set<String> CAPABILITIES = Set.of(
        "griefprevention-flags", "claim-gui", "legacy-claimflags-api", "flag-persistence", "claim-actions");

    private final JavaPlugin plugin;
    private final PlexonCoreAPI core;
    private final boolean compatible;
    private boolean ownsRegistration;
    private String state = "NOT_REGISTERED";
    private String detail = "PlexonCore API resolved";

    public PlexonCoreBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<PlexonCoreAPI> registration =
            Bukkit.getServicesManager().getRegistration(PlexonCoreAPI.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("PlexonCore API service is not registered");
        }
        core = registration.getProvider();
        compatible = ModuleVersionRange.parse(SUPPORTED_API_RANGE).contains(core.version());
        if (!compatible) {
            state = "INCOMPATIBLE";
            detail = "Core API " + core.version().apiVersion() + " outside " + SUPPORTED_API_RANGE;
        }
    }

    @Override public String mode() { return compatible && ownsRegistration ? "CORE" : "STANDALONE"; }
    @Override public String state() { return state; }
    @Override public String detail() { return detail; }

    @Override
    public void registerStarting() {
        if (!compatible) return;
        var result = core.modules().register(new ModuleDescriptor(
            MODULE_ID, "PlexonGPFlags", plugin.getName(), plugin.getPluginMeta().getVersion(), plugin,
            ModuleVersionRange.parse(SUPPORTED_API_RANGE), CAPABILITIES, ModuleState.STARTING,
            "Initializing GriefPrevention flag runtime", Instant.now()));
        ModuleDescriptor registered = result.descriptor();
        ownsRegistration = registered != null && registered.plugin() == plugin;
        state = registered == null ? "NOT_REGISTERED" : registered.state().name();
        detail = result.message();
        if (!result.success() && !ownsRegistration) {
            plugin.getLogger().warning("PlexonCore GPFlags registration rejected: " + result.message());
        }
    }

    @Override public void markReady(String detail) { update(ModuleState.READY, detail); }
    @Override public void markFailed(String detail) { update(ModuleState.FAILED, detail); }

    private void update(ModuleState newState, String newDetail) {
        if (!compatible || !ownsRegistration) return;
        if (!core.modules().updateState(MODULE_ID, plugin, newState, newDetail)) {
            ownsRegistration = false;
            state = "NOT_OWNER";
            detail = "Core module ownership changed; state update rejected";
            return;
        }
        state = newState.name();
        detail = newDetail == null ? "" : newDetail;
    }

    @Override
    public void unregister() {
        if (!ownsRegistration) return;
        core.modules().unregisterOwnedBy(plugin);
        ownsRegistration = false;
        state = "UNREGISTERED";
    }
}
