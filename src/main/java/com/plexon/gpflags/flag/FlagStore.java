package com.plexon.gpflags.flag;

import com.plexon.gpflags.claim.ClaimService;
import com.plexon.gpflags.config.RuntimeSettings;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;

/** In-memory authoritative runtime flag map with infrequent atomic YAML persistence. */
public final class FlagStore {
    private final JavaPlugin plugin;
    private final ClaimService claims;
    private final Supplier<RuntimeSettings> settings;
    private final Map<Long, EnumMap<ClaimFlag, Boolean>> explicit = new HashMap<>();
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();
    private boolean healthy = true;
    private String lastError = "";

    public FlagStore(JavaPlugin plugin, ClaimService claims, Supplier<RuntimeSettings> settings) {
        this.plugin = plugin;
        this.claims = claims;
        this.settings = settings;
        this.file = new File(plugin.getDataFolder(), "flags.yml");
        migrateLegacyIfNeeded();
        if (!load()) throw new IllegalStateException("Could not safely load flags.yml: " + lastError);
    }

    public synchronized boolean load() {
        YamlConfiguration loaded = new YamlConfiguration();
        Map<Long, EnumMap<ClaimFlag, Boolean>> next = new HashMap<>();
        try {
            if (file.isFile()) loaded.load(file);
            ConfigurationSection root = loaded.getConfigurationSection("claims");
            if (root != null) {
                for (String idKey : root.getKeys(false)) {
                    long id;
                    try { id = Long.parseLong(idKey); }
                    catch (NumberFormatException ignored) { plugin.getLogger().warning("Ignoring invalid claim id in flags.yml: " + idKey); continue; }
                    ConfigurationSection flags = root.getConfigurationSection(idKey + ".flags");
                    if (flags == null) continue;
                    EnumMap<ClaimFlag, Boolean> values = new EnumMap<>(ClaimFlag.class);
                    for (ClaimFlag flag : ClaimFlag.values()) if (flags.contains(flag.key())) values.put(flag, flags.getBoolean(flag.key()));
                    if (!values.isEmpty()) next.put(id, values);
                }
            }
        } catch (IOException | InvalidConfigurationException error) {
            fail("Could not load flags.yml", error);
            return false;
        }
        explicit.clear();
        explicit.putAll(next);
        yaml = loaded;
        healthy = true;
        lastError = "";
        return true;
    }

    public boolean effective(Claim claim, ClaimFlag flag) {
        if (claim == null) return false;
        Boolean direct = explicitValue(claim, flag);
        if (direct != null) return direct;
        if (claim.parent != null && settings.get().flags().subclaimsInheritParent()) return effective(claim.parent, flag);
        return settings.get().defaults().getOrDefault(flag, false);
    }

    public Boolean explicitValue(Claim claim, ClaimFlag flag) {
        if (claim == null || claim.getID() == null) return null;
        Map<ClaimFlag, Boolean> values = explicit.get(claim.getID());
        return values == null ? null : values.get(flag);
    }

    public boolean inherited(Claim claim, ClaimFlag flag) {
        return claim != null && claim.parent != null && settings.get().flags().subclaimsInheritParent()
                && explicitValue(claim, flag) == null;
    }

    public synchronized boolean set(Claim claim, ClaimFlag flag, Boolean value) {
        if (claim == null || claim.getID() == null) return false;
        Map<Long, EnumMap<ClaimFlag, Boolean>> before = copy();
        String yamlBefore = yaml.saveToString();
        long id = claim.getID();
        if (value == null) {
            EnumMap<ClaimFlag, Boolean> values = explicit.get(id);
            if (values != null) {
                values.remove(flag);
                if (values.isEmpty()) explicit.remove(id);
            }
        } else explicit.computeIfAbsent(id, ignored -> new EnumMap<>(ClaimFlag.class)).put(flag, value);
        persistClaim(claim);
        if (save()) return true;
        restore(before, yamlBefore);
        return false;
    }

    public synchronized boolean removeClaimTree(Claim claim) {
        if (claim == null) return true;
        Map<Long, EnumMap<ClaimFlag, Boolean>> before = copy();
        String yamlBefore = yaml.saveToString();
        removeMemory(claim);
        if (save()) return true;
        restore(before, yamlBefore);
        return false;
    }

    public synchronized boolean save() {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Path target = file.toPath();
            Path temp = target.resolveSibling(file.getName() + ".tmp");
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            healthy = true;
            lastError = "";
            return true;
        } catch (IOException error) {
            fail("Could not save flags.yml", error);
            return false;
        }
    }

    public int recordCount() { return explicit.size(); }
    public boolean healthy() { return healthy; }
    public String lastError() { return lastError; }

    private void migrateLegacyIfNeeded() {
        RuntimeSettings runtime = settings.get();
        if (!runtime.importLegacyFlags() || file.exists()) return;
        Path plugins = plugin.getDataFolder().toPath().toAbsolutePath().normalize().getParent();
        if (plugins == null) return;
        Path legacy = plugins.resolve(runtime.legacyFolder()).resolve("flags.yml");
        if (!Files.isRegularFile(legacy)) return;
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Files.copy(legacy, file.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            plugin.getLogger().info("Imported legacy claim flags from " + legacy + ".");
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "Could not import legacy flags.yml; starting without migrated overrides", error);
        }
    }

    private void persistClaim(Claim claim) {
        long id = claims.safeId(claim);
        String root = "claims." + id;
        yaml.set(root + ".owner", claim.getOwnerID() == null ? "ADMIN" : claim.getOwnerID().toString());
        yaml.set(root + ".type", claim.parent == null ? "MAIN" : "SUBCLAIM");
        yaml.set(root + ".bounds", claims.boundsLabel(claim));
        yaml.set(root + ".flags", null);
        Map<ClaimFlag, Boolean> values = explicit.get(id);
        if (values != null) for (Map.Entry<ClaimFlag, Boolean> entry : values.entrySet()) yaml.set(root + ".flags." + entry.getKey().key(), entry.getValue());
        if (values == null || values.isEmpty()) yaml.set(root, null);
    }

    private void removeMemory(Claim claim) {
        for (Claim child : new ArrayList<>(claim.children)) removeMemory(child);
        if (claim.getID() == null) return;
        explicit.remove(claim.getID());
        yaml.set("claims." + claim.getID(), null);
    }

    private Map<Long, EnumMap<ClaimFlag, Boolean>> copy() {
        Map<Long, EnumMap<ClaimFlag, Boolean>> copy = new HashMap<>();
        explicit.forEach((id, values) -> copy.put(id, new EnumMap<>(values)));
        return copy;
    }

    private void restore(Map<Long, EnumMap<ClaimFlag, Boolean>> before, String yamlBefore) {
        explicit.clear();
        explicit.putAll(before);
        try {
            YamlConfiguration restored = new YamlConfiguration();
            restored.loadFromString(yamlBefore);
            yaml = restored;
        } catch (InvalidConfigurationException impossible) {
            plugin.getLogger().log(Level.SEVERE, "Could not restore flag snapshot after failed persistence", impossible);
        }
    }

    private void fail(String message, Exception error) {
        healthy = false;
        lastError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        plugin.getLogger().log(Level.SEVERE, message, error);
    }
}
