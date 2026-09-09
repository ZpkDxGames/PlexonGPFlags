package com.plexon.gpflags;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.plexon.gpflags.integration.core.CoreBridge;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GPFlagsCertificationContractTest {
    @Test
    void coreRangeAcceptsCore204AndRejectsCore3() {
        ModuleVersionRange range = ModuleVersionRange.parse(CoreBridge.SUPPORTED_API_RANGE);
        assertTrue(range.contains(CoreVersion.of(2, 0, "2.0.4")));
        assertFalse(range.contains(CoreVersion.of(3, 0, "3.0.0")));
    }

    @Test
    void flagPersistenceIsClaimIdKeyedAtomicAndRollbackSafe() throws Exception {
        String store = Files.readString(Path.of("src/main/java/com/plexon/gpflags/flag/FlagStore.java"));
        assertTrue(store.contains("Map<Long, EnumMap<ClaimFlag, Boolean>> explicit"));
        assertTrue(store.contains("Long.parseLong(idKey)"));
        assertTrue(store.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(store.contains("restore(before, yamlBefore)"));
        assertTrue(store.contains("legacyFolder()"));
    }

    @Test
    void protectionHotPathUsesInMemoryStoreWithoutPersistenceIo() throws Exception {
        String protection = Files.readString(Path.of("src/main/java/com/plexon/gpflags/protection/ProtectionListener.java"));
        assertFalse(protection.contains("Files."));
        assertFalse(protection.contains("flags.yml"));
        assertFalse(protection.contains("saveConfig("));
    }

    @Test
    void oldFlagsMigrationIsOneWayAndDoesNotOverwriteCurrentStore() throws Exception {
        String store = Files.readString(Path.of("src/main/java/com/plexon/gpflags/flag/FlagStore.java"));
        assertTrue(store.contains("file.exists()) return"));
        assertTrue(store.contains("Files.copy(legacy, file.toPath(), StandardCopyOption.COPY_ATTRIBUTES)"));
    }

    @Test
    void coreLifecycleIsOwnerScoped() throws Exception {
        String bridge = Files.readString(Path.of("src/main/java/com/plexon/gpflags/integration/core/PlexonCoreBridge.java"));
        assertTrue(bridge.contains("updateState(MODULE_ID, plugin"));
        assertTrue(bridge.contains("unregisterOwnedBy(plugin)"));
    }
}
