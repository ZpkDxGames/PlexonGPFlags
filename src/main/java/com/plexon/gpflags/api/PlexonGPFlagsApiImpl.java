package com.plexon.gpflags.api;

import com.plexon.gpflags.claim.ClaimService;
import com.plexon.gpflags.flag.ClaimFlag;
import com.plexon.gpflags.flag.FlagOverride;
import com.plexon.gpflags.flag.FlagService;
import com.plexon.gpflags.flag.FlagStore;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PlexonGPFlagsApiImpl implements PlexonGPFlagsAPI {
    private final ClaimService claims;
    private final FlagStore store;
    private final FlagService service;

    public PlexonGPFlagsApiImpl(ClaimService claims, FlagStore store, FlagService service) {
        this.claims = claims;
        this.store = store;
        this.service = service;
    }

    @Override public Collection<String> flags() { return Arrays.stream(ClaimFlag.values()).map(ClaimFlag::key).toList(); }

    @Override public boolean effectiveValue(long claimId, String flagId) {
        Claim claim = claims.byId(claimId);
        ClaimFlag flag = ClaimFlag.parse(flagId).orElse(null);
        return claim != null && flag != null && store.effective(claim, flag);
    }

    @Override public Optional<Boolean> explicitValue(long claimId, String flagId) {
        Claim claim = claims.byId(claimId);
        ClaimFlag flag = ClaimFlag.parse(flagId).orElse(null);
        if (claim == null || flag == null) return Optional.empty();
        return Optional.ofNullable(store.explicitValue(claim, flag));
    }

    @Override public Map<String, Boolean> effectiveValues(long claimId) {
        Claim claim = claims.byId(claimId);
        if (claim == null) return Map.of();
        Map<String, Boolean> values = new LinkedHashMap<>();
        for (ClaimFlag flag : ClaimFlag.values()) values.put(flag.key(), store.effective(claim, flag));
        return Map.copyOf(values);
    }

    @Override public FlagChangeResult setExplicit(Player actor, long claimId, String flagId, FlagOverride value) {
        Claim claim = claims.byId(claimId);
        ClaimFlag flag = ClaimFlag.parse(flagId).orElse(null);
        return service.set(actor, claim, flag, value, "API");
    }
}
