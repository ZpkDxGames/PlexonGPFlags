package com.plexon.gpflags.flag;

import com.plexon.gpflags.api.FlagChangeResult;
import com.plexon.gpflags.claim.ClaimService;
import com.plexon.gpflags.event.PlexonGPFlagChangedEvent;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class FlagService {
    private final JavaPlugin plugin;
    private final ClaimService claims;
    private final FlagStore store;
    private final AtomicLong rejectedMutations = new AtomicLong();
    public FlagService(JavaPlugin plugin, ClaimService claims, FlagStore store) { this.plugin = plugin; this.claims = claims; this.store = store; }

    public FlagChangeResult set(Player actor, Claim claim, ClaimFlag flag, FlagOverride override, String source) {
        if (!Bukkit.isPrimaryThread()) return rejected(result(FlagChangeResult.Status.NOT_PRIMARY_THREAD, claims.safeId(claim), flag, null, null, null, "Mutation must run on primary thread"));
        if (actor == null || claim == null || !claim.inDataStore || claim.getID() == null) return rejected(result(FlagChangeResult.Status.INVALID_CLAIM, claims.safeId(claim), flag, null, null, null, "Claim unavailable"));
        if (flag == null || override == null) return rejected(result(FlagChangeResult.Status.INVALID_FLAG, claims.safeId(claim), flag, null, null, null, "Invalid flag/override"));
        if (!claims.canManage(actor, claim)) return rejected(result(FlagChangeResult.Status.NOT_AUTHORIZED, claims.safeId(claim), flag, store.effective(claim, flag), null, null, "Not authorized"));
        Boolean oldExplicit = store.explicitValue(claim, flag);
        Boolean newExplicit = override.explicitValue();
        boolean oldEffective = store.effective(claim, flag);
        if (Objects.equals(oldExplicit, newExplicit)) return result(FlagChangeResult.Status.NO_CHANGE, claim.getID(), flag, oldEffective, null, null, "No change");
        if (!store.set(claim, flag, newExplicit)) return rejected(result(FlagChangeResult.Status.PERSISTENCE_FAILED, claim.getID(), flag, oldEffective, null, null, "Persistence failed"));
        boolean newEffective = store.effective(claim, flag);
        String transactionId = UUID.randomUUID().toString();
        String eventId = transactionId + ":flag:" + flag.key();
        Long parentId = claim.parent == null || claim.parent.getID() == null ? null : claim.parent.getID();
        Bukkit.getPluginManager().callEvent(new PlexonGPFlagChangedEvent(actor, claim.getID(), parentId, flag.key(), FlagOverride.fromExplicit(oldExplicit), FlagOverride.fromExplicit(newExplicit), oldEffective, newEffective, eventId, transactionId, source == null ? "API" : source));
        return result(FlagChangeResult.Status.SUCCESS, claim.getID(), flag, newEffective, transactionId, eventId, "Updated");
    }
    public long rejectedMutationCount() { return rejectedMutations.get(); }
    private FlagChangeResult rejected(FlagChangeResult result) { rejectedMutations.incrementAndGet(); return result; }
    private static FlagChangeResult result(FlagChangeResult.Status status, long claimId, ClaimFlag flag, Boolean effective, String transactionId, String eventId, String message) { return new FlagChangeResult(status, claimId, flag == null ? "" : flag.key(), effective, transactionId, eventId, message); }
}
