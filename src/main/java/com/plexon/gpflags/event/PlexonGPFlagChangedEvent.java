package com.plexon.gpflags.event;

import com.plexon.gpflags.flag.FlagOverride;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PlexonGPFlagChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player actor;
    private final long claimId;
    private final Long parentClaimId;
    private final String flagId;
    private final FlagOverride oldOverride;
    private final FlagOverride newOverride;
    private final boolean oldEffective;
    private final boolean newEffective;
    private final String eventId;
    private final String transactionId;
    private final String source;

    public PlexonGPFlagChangedEvent(Player actor, long claimId, Long parentClaimId, String flagId,
                                    FlagOverride oldOverride, FlagOverride newOverride,
                                    boolean oldEffective, boolean newEffective,
                                    String eventId, String transactionId, String source) {
        this.actor = actor;
        this.claimId = claimId;
        this.parentClaimId = parentClaimId;
        this.flagId = flagId;
        this.oldOverride = oldOverride;
        this.newOverride = newOverride;
        this.oldEffective = oldEffective;
        this.newEffective = newEffective;
        this.eventId = eventId;
        this.transactionId = transactionId;
        this.source = source;
    }

    public Player actor() { return actor; }
    public long claimId() { return claimId; }
    public Long parentClaimId() { return parentClaimId; }
    public boolean subclaim() { return parentClaimId != null; }
    public String flagId() { return flagId; }
    public FlagOverride oldOverride() { return oldOverride; }
    public FlagOverride newOverride() { return newOverride; }
    public boolean oldEffective() { return oldEffective; }
    public boolean newEffective() { return newEffective; }
    public String eventId() { return eventId; }
    public String transactionId() { return transactionId; }
    public String source() { return source; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
