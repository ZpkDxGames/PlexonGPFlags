package com.plexon.gpflags.api;

import com.plexon.gpflags.flag.FlagOverride;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface PlexonGPFlagsAPI {
    Collection<String> flags();
    boolean effectiveValue(long claimId, String flagId);
    Optional<Boolean> explicitValue(long claimId, String flagId);
    Map<String, Boolean> effectiveValues(long claimId);
    FlagChangeResult setExplicit(Player actor, long claimId, String flagId, FlagOverride value);
}
