package com.plexon.gpflags.flag;

import com.plexon.gpflags.claim.ClaimService;
import com.plexon.gpflags.config.RuntimeSettings;
import com.plexon.gpflags.config.SchemaVersion;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.World;
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
    public static final int CURRENT_SCHEMA = 2;
    public record Candidate(YamlConfiguration yaml, Map<Long, EnumMap<ClaimFlag, Boolean>> explicit, int sourceSchema, int unknownFlags) {}

    private final JavaPlugin plugin;
    private final ClaimService claims;
    private final Supplier<RuntimeSettings> settings;
    private final Map<Long, EnumMap<ClaimFlag, Boolean>> explicit = new HashMap<>();
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();
    private boolean healthy = true;
    private String lastError = "";
    private int sourceSchema = CURRENT_SCHEMA;
    private int unknownFlagCount;
    private String migrationStatus = "NOT_NEEDED";

    public FlagStore(JavaPlugin plugin, ClaimService claims, Supplier<RuntimeSettings> settings) {
        this.plugin = plugin; this.claims = claims; this.settings = settings; this.file = new File(plugin.getDataFolder(), "flags.yml");
        migrateLegacyIfNeeded();
        if (!load()) throw new IllegalStateException("Could not safely load flags.yml: " + lastError);
    }

    public synchronized Candidate prepareCandidate() {
        YamlConfiguration loaded = new YamlConfiguration();
        Map<Long, EnumMap<ClaimFlag, Boolean>> next = new HashMap<>();
        int unknown = 0;
        try {
            if (file.isFile()) loaded.load(file);
            int schema = SchemaVersion.requireSupported(loaded.get("schema-version"), 1, CURRENT_SCHEMA, "flags.yml");
            ConfigurationSection root = loaded.getConfigurationSection("claims");
            if (root != null) for (String idKey : root.getKeys(false)) {
                final long id;
                try { id = Long.parseLong(idKey); } catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid claim id in flags.yml: " + idKey); }
                ConfigurationSection flags = root.getConfigurationSection(idKey + ".flags");
                if (flags == null) continue;
                EnumMap<ClaimFlag, Boolean> values = new EnumMap<>(ClaimFlag.class);
                for (String flagId : new ArrayList<>(flags.getKeys(false))) {
                    ClaimFlag flag = ClaimFlag.parseStableId(flagId).orElse(null);
                    Object raw = flags.get(flagId);
                    if (flag == null) {
                        loaded.set("quarantine.claims." + idKey + ".flags." + flagId, raw);
                        loaded.set("claims." + idKey + ".flags." + flagId, null);
                        unknown++;
                        continue;
                    }
                    if (!(raw instanceof Boolean value)) throw new IllegalArgumentException("claims." + idKey + ".flags." + flagId + " must be boolean");
                    values.put(flag, value);
                }
                if (!values.isEmpty()) next.put(id, values);
            }
            return new Candidate(loaded, next, schema, unknown);
        } catch (IOException | InvalidConfigurationException error) { throw new IllegalStateException("Could not parse flags.yml", error); }
    }

    public synchronized void applyCandidate(Candidate candidate) {
        explicit.clear();
        candidate.explicit().forEach((id, values) -> explicit.put(id, new EnumMap<>(values)));
        yaml = candidate.yaml(); sourceSchema = candidate.sourceSchema(); unknownFlagCount = candidate.unknownFlags(); healthy = true; lastError = "";
        if (unknownFlagCount > 0) plugin.getLogger().warning("Quarantined " + unknownFlagCount + " unknown flag value(s) from flags.yml.");
    }

    public synchronized boolean load() {
        try { applyCandidate(prepareCandidate()); return true; }
        catch (RuntimeException error) { fail("Could not load flags.yml", error); return false; }
    }

    public boolean effective(Claim claim, ClaimFlag flag) {
        if (claim == null || flag == null) return false;
        Boolean direct = explicitValue(claim, flag);
        if (direct != null) return direct;
        if (claim.parent != null && settings.get().flags().subclaimsInheritParent()) return effective(claim.parent, flag);
        Boolean world = worldDefaultValue(claim, flag);
        return world != null ? world : settings.get().defaults().getOrDefault(flag, false);
    }
    public Boolean explicitValue(Claim claim, ClaimFlag flag) { if (claim == null || claim.getID() == null || flag == null) return null; Map<ClaimFlag, Boolean> values = explicit.get(claim.getID()); return values == null ? null : values.get(flag); }
    public Boolean worldDefaultValue(Claim claim, ClaimFlag flag) { if (claim == null || flag == null || claim.getLesserBoundaryCorner() == null) return null; World world = claim.getLesserBoundaryCorner().getWorld(); if (world == null) return null; Map<ClaimFlag, Boolean> values = settings.get().worldDefaults().get(world.getUID()); return values == null ? null : values.get(flag); }
    public String source(Claim claim, ClaimFlag flag) { if (explicitValue(claim, flag) != null) return "EXPLICIT"; if (claim != null && claim.parent != null && settings.get().flags().subclaimsInheritParent()) return "PARENT"; if (worldDefaultValue(claim, flag) != null) return "WORLD"; return "GLOBAL"; }
    public boolean inherited(Claim claim, ClaimFlag flag) { return claim != null && claim.parent != null && settings.get().flags().subclaimsInheritParent() && explicitValue(claim, flag) == null; }

    public synchronized boolean set(Claim claim, ClaimFlag flag, Boolean value) {
        if (claim == null || claim.getID() == null || flag == null) return false;
        Map<Long, EnumMap<ClaimFlag, Boolean>> before = copy(); String yamlBefore = yaml.saveToString(); long id = claim.getID();
        if (value == null) { EnumMap<ClaimFlag, Boolean> values = explicit.get(id); if (values != null) { values.remove(flag); if (values.isEmpty()) explicit.remove(id); } }
        else explicit.computeIfAbsent(id, ignored -> new EnumMap<>(ClaimFlag.class)).put(flag, value);
        persistClaim(claim);
        if (save()) return true;
        restore(before, yamlBefore); return false;
    }

    public synchronized boolean removeClaimTree(Claim claim) {
        if (claim == null) return true;
        Map<Long, EnumMap<ClaimFlag, Boolean>> before = copy(); String yamlBefore = yaml.saveToString(); removeMemory(claim);
        if (save()) return true; restore(before, yamlBefore); return false;
    }

    public synchronized boolean save() {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath()); yaml.set("schema-version", CURRENT_SCHEMA);
            Path target = file.toPath(); Path temp = target.resolveSibling(file.getName() + ".tmp");
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            sourceSchema = CURRENT_SCHEMA; healthy = true; lastError = ""; return true;
        } catch (IOException error) { fail("Could not save flags.yml", error); return false; }
    }

    public synchronized Candidate snapshot() {
        try { YamlConfiguration copyYaml = new YamlConfiguration(); copyYaml.loadFromString(yaml.saveToString()); return new Candidate(copyYaml, copy(), sourceSchema, unknownFlagCount); }
        catch (InvalidConfigurationException impossible) { throw new IllegalStateException("Could not snapshot in-memory flag state", impossible); }
    }

    public int recordCount() { return explicit.size(); }
    public int explicitOverrideCount() { int total = 0; for (Map<ClaimFlag, Boolean> values : explicit.values()) total += values.size(); return total; }
    public int worldDefaultCount() { int total = 0; for (Map<ClaimFlag, Boolean> values : settings.get().worldDefaults().values()) total += values.size(); return total; }
    public boolean healthy() { return healthy; }
    public String lastError() { return lastError; }
    public int sourceSchema() { return sourceSchema; }
    public int unknownFlagCount() { return unknownFlagCount; }
    public String migrationStatus() { return migrationStatus; }

    private void migrateLegacyIfNeeded() {
        RuntimeSettings runtime = settings.get();
        if (!runtime.importLegacyFlags() || file.exists()) return;
        Path plugins = plugin.getDataFolder().toPath().toAbsolutePath().normalize().getParent();
        if (plugins == null) return;
        Path legacy = plugins.resolve(runtime.legacyFolder()).resolve("flags.yml").normalize();
        if (!legacy.startsWith(plugins) || !Files.isRegularFile(legacy)) return;
        Path data = plugin.getDataFolder().toPath(); Path backup = data.resolve("migration-backups").resolve(runtime.legacyFolder() + "-flags.yml"); Path marker = data.resolve("legacy-import.complete");
        try {
            Files.createDirectories(backup.getParent());
            Files.copy(legacy, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            Files.copy(legacy, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            Files.writeString(marker, "source=" + legacy + System.lineSeparator() + "backup=" + backup + System.lineSeparator(), StandardCharsets.UTF_8);
            migrationStatus = "IMPORTED_BACKED_UP";
            plugin.getLogger().info("Imported legacy claim flags from " + legacy + " with backup " + backup + ".");
        } catch (IOException error) {
            try { Files.deleteIfExists(file.toPath()); Files.deleteIfExists(marker); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw new IllegalStateException("Could not safely import legacy flags.yml", error);
        }
    }

    private void persistClaim(Claim claim) {
        long id = claims.safeId(claim); String root = "claims." + id;
        yaml.set(root + ".owner", claim.getOwnerID() == null ? "ADMIN" : claim.getOwnerID().toString());
        yaml.set(root + ".type", claim.parent == null ? "MAIN" : "SUBCLAIM"); yaml.set(root + ".bounds", claims.boundsLabel(claim)); yaml.set(root + ".flags", null);
        Map<ClaimFlag, Boolean> values = explicit.get(id); if (values != null) for (Map.Entry<ClaimFlag, Boolean> entry : values.entrySet()) yaml.set(root + ".flags." + entry.getKey().key(), entry.getValue());
        if (values == null || values.isEmpty()) yaml.set(root, null);
    }
    private void removeMemory(Claim claim) { for (Claim child : new ArrayList<>(claim.children)) removeMemory(child); if (claim.getID() == null) return; explicit.remove(claim.getID()); yaml.set("claims." + claim.getID(), null); }
    private Map<Long, EnumMap<ClaimFlag, Boolean>> copy() { Map<Long, EnumMap<ClaimFlag, Boolean>> copy = new HashMap<>(); explicit.forEach((id, values) -> copy.put(id, new EnumMap<>(values))); return copy; }
    private void restore(Map<Long, EnumMap<ClaimFlag, Boolean>> before, String yamlBefore) { explicit.clear(); explicit.putAll(before); try { YamlConfiguration restored = new YamlConfiguration(); restored.loadFromString(yamlBefore); yaml = restored; } catch (InvalidConfigurationException impossible) { plugin.getLogger().log(Level.SEVERE, "Could not restore flag snapshot after failed persistence", impossible); } }
    private void fail(String message, Exception error) { healthy = false; lastError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); plugin.getLogger().log(Level.SEVERE, message, error); }
}
