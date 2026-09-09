package net.plexon.claimflags.event;

import net.plexon.claimflags.api.FlagOverride;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** @deprecated Listen to com.plexon.gpflags.event.PlexonGPFlagChangedEvent. */
@Deprecated(forRemoval = false)
public final class PlexonClaimFlagChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player actor;
    private final long claimId;
    private final Long parentClaimId;
    private final boolean subdivision;
    private final String flagId;
    private final FlagOverride oldExplicitValue;
    private final FlagOverride newExplicitValue;
    private final boolean oldEffectiveValue;
    private final boolean newEffectiveValue;
    private final String eventId;
    private final String transactionId;
    private final FlagChangeSource changeSource;

    public PlexonClaimFlagChangedEvent(Player actor, long claimId, Long parentClaimId, boolean subdivision,
            String flagId, FlagOverride oldExplicitValue, FlagOverride newExplicitValue,
            boolean oldEffectiveValue, boolean newEffectiveValue, String eventId,
            String transactionId, FlagChangeSource changeSource) {
        this.actor = actor;
        this.claimId = claimId;
        this.parentClaimId = parentClaimId;
        this.subdivision = subdivision;
        this.flagId = flagId;
        this.oldExplicitValue = oldExplicitValue;
        this.newExplicitValue = newExplicitValue;
        this.oldEffectiveValue = oldEffectiveValue;
        this.newEffectiveValue = newEffectiveValue;
        this.eventId = eventId;
        this.transactionId = transactionId;
        this.changeSource = changeSource;
    }

    public Player getActor() { return actor; }
    public long getClaimId() { return claimId; }
    public @Nullable Long getParentClaimId() { return parentClaimId; }
    public boolean isSubdivision() { return subdivision; }
    public String getFlagId() { return flagId; }
    public FlagOverride getOldExplicitValue() { return oldExplicitValue; }
    public FlagOverride getNewExplicitValue() { return newExplicitValue; }
    public boolean getOldEffectiveValue() { return oldEffectiveValue; }
    public boolean getNewEffectiveValue() { return newEffectiveValue; }
    public String getEventId() { return eventId; }
    public String getTransactionId() { return transactionId; }
    public FlagChangeSource getChangeSource() { return changeSource; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
