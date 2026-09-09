package com.plexon.gpflags.compat;

import com.plexon.gpflags.PlexonGPFlags;
import com.plexon.gpflags.api.FlagChangeResult;
import com.plexon.gpflags.claim.ClaimService;
import com.plexon.gpflags.event.PlexonGPFlagChangedEvent;
import com.plexon.gpflags.flag.ClaimFlag;
import com.plexon.gpflags.flag.FlagService;
import com.plexon.gpflags.flag.FlagStore;
import me.ryanhamshire.GriefPrevention.Claim;
import net.plexon.claimflags.api.ClaimFlagView;
import net.plexon.claimflags.api.PlexonClaimFlagsAPI;
import net.plexon.claimflags.event.FlagChangeSource;
import net.plexon.claimflags.event.PlexonClaimFlagChangedEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Read/write compatibility facade for PlexonClaimFlags 1.1.x consumers. */
@SuppressWarnings("deprecation")
public final class LegacyClaimFlagsBridge implements PlexonClaimFlagsAPI, Listener {
    private final PlexonGPFlags plugin;
    private final ClaimService claims;
    private final FlagStore store;
    private final FlagService service;

    public LegacyClaimFlagsBridge(PlexonGPFlags plugin, ClaimService claims, FlagStore store, FlagService service) {
        this.plugin = plugin;
        this.claims = claims;
        this.store = store;
        this.service = service;
    }

    @Override
    public Optional<ClaimFlagView> flag(String flagId) {
        return ClaimFlag.parse(flagId).map(this::view);
    }

    @Override
    public Collection<ClaimFlagView> flags() {
        return Arrays.stream(ClaimFlag.values()).map(this::view).toList();
    }

    @Override
    public boolean effectiveValue(long claimId, String flagId) {
        Claim claim = claims.byId(claimId);
        ClaimFlag flag = ClaimFlag.parse(flagId).orElse(null);
        return claim != null && flag != null && store.effective(claim, flag);
    }

    @Override
    public Optional<Boolean> explicitValue(long claimId, String flagId) {
        Claim claim = claims.byId(claimId);
        ClaimFlag flag = ClaimFlag.parse(flagId).orElse(null);
        if (claim == null || flag == null) return Optional.empty();
        return Optional.ofNullable(store.explicitValue(claim, flag));
    }

    @Override
    public Map<String, Boolean> effectiveValues(long claimId) {
        Claim claim = claims.byId(claimId);
        if (claim == null) return Map.of();
        Map<String, Boolean> values = new LinkedHashMap<>();
        for (ClaimFlag flag : ClaimFlag.values()) values.put(flag.key(), store.effective(claim, flag));
        return Map.copyOf(values);
    }

    @Override
    public net.plexon.claimflags.api.FlagChangeResult setExplicit(Player actor, long claimId, String flagId,
            net.plexon.claimflags.api.FlagOverride value) {
        Claim claim = claims.byId(claimId);
        ClaimFlag flag = ClaimFlag.parse(flagId).orElse(null);
        com.plexon.gpflags.flag.FlagOverride mapped = value == null ? null : switch (value) {
            case ON -> com.plexon.gpflags.flag.FlagOverride.ON;
            case OFF -> com.plexon.gpflags.flag.FlagOverride.OFF;
            case INHERIT -> com.plexon.gpflags.flag.FlagOverride.INHERIT;
        };
        FlagChangeResult result = service.set(actor, claim, flag, mapped, "API");
        net.plexon.claimflags.api.FlagChangeResult.Status status =
                net.plexon.claimflags.api.FlagChangeResult.Status.valueOf(result.status().name());
        return new net.plexon.claimflags.api.FlagChangeResult(status, result.claimId(), result.flagId(),
                result.effectiveValue(), result.transactionId(), result.eventId(), result.message());
    }

    @EventHandler
    public void onModernEvent(PlexonGPFlagChangedEvent event) {
        FlagChangeSource source;
        try { source = FlagChangeSource.valueOf(event.source().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException error) { source = FlagChangeSource.API; }
        Bukkit.getPluginManager().callEvent(new PlexonClaimFlagChangedEvent(
                event.actor(), event.claimId(), event.parentClaimId(), event.subclaim(), event.flagId(),
                net.plexon.claimflags.api.FlagOverride.valueOf(event.oldOverride().name()),
                net.plexon.claimflags.api.FlagOverride.valueOf(event.newOverride().name()),
                event.oldEffective(), event.newEffective(), event.eventId(), event.transactionId(), source));
    }

    private ClaimFlagView view(ClaimFlag flag) {
        return new ClaimFlagView(flag.key(), plugin.messages().flagName(flag), flag.icon().name(),
                plugin.settings().defaults().getOrDefault(flag, false));
    }
}
