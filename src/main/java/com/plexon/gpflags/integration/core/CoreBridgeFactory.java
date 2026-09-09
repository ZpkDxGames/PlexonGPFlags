package com.plexon.gpflags.integration.core;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoreBridgeFactory {
    private static final String BRIDGE = "com.plexon.gpflags.integration.core.PlexonCoreBridge";

    private CoreBridgeFactory() {}

    public static CoreBridge resolve(JavaPlugin plugin) {
        Plugin corePlugin = Bukkit.getPluginManager().getPlugin("PlexonCore");
        if (corePlugin == null) {
            return new StandaloneCoreBridge("STANDALONE", "PlexonCore is not installed");
        }
        if (!corePlugin.isEnabled()) {
            return new StandaloneCoreBridge("CORE_DISABLED", "PlexonCore is installed but disabled");
        }
        try {
            Class<?> type = Class.forName(BRIDGE, true, CoreBridgeFactory.class.getClassLoader());
            return (CoreBridge) type.getConstructor(JavaPlugin.class).newInstance(plugin);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            plugin.getLogger().log(Level.WARNING, "PlexonCore API unavailable; GPFlags continues standalone.", cause);
            return new StandaloneCoreBridge("CORE_UNAVAILABLE", "PlexonCore API service unavailable");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "PlexonCore linkage unavailable; GPFlags continues standalone.", error);
            return new StandaloneCoreBridge("CORE_UNAVAILABLE", "PlexonCore API linkage unavailable");
        }
    }
}
